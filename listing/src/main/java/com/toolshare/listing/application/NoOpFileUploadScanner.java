package com.toolshare.listing.application;

import org.springframework.stereotype.Component;

@Component
public class NoOpFileUploadScanner implements FileUploadScanner {
    @Override
    public void scan(byte[] content, String contentType) {
    }
}