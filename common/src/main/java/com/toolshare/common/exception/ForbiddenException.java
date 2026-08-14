package com.toolshare.common.exception;

public class ForbiddenException extends ToolShareException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
