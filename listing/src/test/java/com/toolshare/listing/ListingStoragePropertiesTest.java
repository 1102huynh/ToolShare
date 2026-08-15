package com.toolshare.listing;

import com.toolshare.listing.config.ListingStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ListingApplication.class)
class ListingStoragePropertiesTest {

    @Autowired
    private ListingStorageProperties properties;

    @Test
    void configuration_defaults_are_bound() {
        assertThat(properties.getStorage().getLocal().getRoot()).isEqualTo("./storage");
        assertThat(properties.getUpload().getMaxFileSize().toBytes()).isEqualTo(5L * 1024L * 1024L);
        assertThat(properties.getUpload().getAllowedMimeTypes()).containsExactly("image/jpeg", "image/png");
    }
}