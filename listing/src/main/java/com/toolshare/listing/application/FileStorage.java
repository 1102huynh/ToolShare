package com.toolshare.listing.application;

import java.util.UUID;

public interface FileStorage {
    String storeListingImage(UUID listingId, byte[] content, String contentType);
}