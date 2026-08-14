package com.toolshare.common.exception;

public class UnauthorizedException extends ToolShareException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}
