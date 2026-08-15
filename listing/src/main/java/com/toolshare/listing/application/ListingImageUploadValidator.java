package com.toolshare.listing.application;

import com.toolshare.common.exception.ValidationException;
import com.toolshare.listing.config.ListingStorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Component
public class ListingImageUploadValidator {

    private final ListingStorageProperties properties;

    public ListingImageUploadValidator(ListingStorageProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file) {
        if (file == null) {
            throw new ValidationException("file is required");
        }

        if (file.isEmpty()) {
            throw new ValidationException("file must not be empty");
        }

        if (file.getSize() > properties.getUpload().getMaxFileSize().toBytes()) {
            throw new ValidationException("file exceeds the configured maximum size");
        }

        String contentType = file.getContentType();
        if (contentType == null || properties.getUpload().getAllowedMimeTypes().stream().noneMatch(allowed -> allowed.equalsIgnoreCase(contentType))) {
            throw new ValidationException("file content type is not allowed");
        }
    }
}