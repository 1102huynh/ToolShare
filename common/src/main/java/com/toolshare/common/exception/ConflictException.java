package com.toolshare.common.exception;

public class ConflictException extends ToolShareException {
    public ConflictException(String message) {
        super("CONFLICT", message);
    }
}
