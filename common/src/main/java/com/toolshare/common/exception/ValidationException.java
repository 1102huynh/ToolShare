package com.toolshare.common.exception;

public class ValidationException extends ToolShareException {
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
