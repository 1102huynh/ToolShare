package com.toolshare.common.exception;

public abstract class ToolShareException extends RuntimeException {
    private final String code;

    protected ToolShareException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
