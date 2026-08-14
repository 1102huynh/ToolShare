package com.toolshare.common.exception;

public class ResourceNotFoundException extends ToolShareException {
    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
}
