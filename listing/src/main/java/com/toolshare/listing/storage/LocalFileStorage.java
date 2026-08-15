package com.toolshare.listing.storage;

import com.toolshare.listing.application.FileStorage;
import com.toolshare.listing.config.ListingStorageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path rootDirectory;

    public LocalFileStorage(ListingStorageProperties properties) {
        this.rootDirectory = Paths.get(properties.getStorage().getLocal().getRoot()).toAbsolutePath().normalize();
    }

    @Override
    public String storeListingImage(UUID listingId, byte[] content, String contentType) {
        try {
            String extension = requireExtension(contentType);
            String storageKey = "listings/%s/images/%s.%s".formatted(listingId, UUID.randomUUID(), extension);
            Path targetFile = rootDirectory.resolve(storageKey).normalize();

            if (!targetFile.startsWith(rootDirectory)) {
                throw new IllegalStateException("Invalid storage key");
            }

            Files.createDirectories(targetFile.getParent());
            Files.write(targetFile, content);
            return storageKey;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store listing image", ex);
        }
    }

    private String requireExtension(String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("Content type is required");
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new IllegalArgumentException("Unsupported content type");
        };
    }
}