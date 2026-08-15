package com.toolshare.listing.application;

public interface FileUploadScanner {
    void scan(byte[] content, String contentType);
}