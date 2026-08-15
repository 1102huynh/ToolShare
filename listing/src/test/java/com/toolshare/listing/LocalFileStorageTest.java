package com.toolshare.listing;

import com.toolshare.listing.config.ListingStorageProperties;
import com.toolshare.listing.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void stores_files_under_configured_root_with_generated_key() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(properties(tempDir));

        String storageKey = storage.storeListingImage(UUID.fromString("11111111-1111-1111-1111-111111111111"), "image-bytes".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        Path storedFile = tempDir.toAbsolutePath().normalize().resolve(storageKey).normalize();

        assertThat(storageKey).startsWith("listings/11111111-1111-1111-1111-111111111111/images/");
        assertThat(storageKey).endsWith(".jpg");
        assertThat(storageKey).doesNotContain("..");
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.readString(storedFile)).isEqualTo("image-bytes");
    }

    @Test
    void path_traversal_is_not_possible_through_generated_storage_key() {
        LocalFileStorage storage = new LocalFileStorage(properties(tempDir));

        String storageKey = storage.storeListingImage(UUID.fromString("22222222-2222-2222-2222-222222222222"), new byte[]{1, 2, 3}, "image/png");

        assertThat(tempDir.toAbsolutePath().normalize().resolve(storageKey).normalize()).startsWith(tempDir.toAbsolutePath().normalize());
    }

    private ListingStorageProperties properties(Path root) {
        ListingStorageProperties properties = new ListingStorageProperties();
        properties.getStorage().getLocal().setRoot(root.toString());
        return properties;
    }
}