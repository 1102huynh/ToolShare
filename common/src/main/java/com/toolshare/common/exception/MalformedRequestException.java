package com.toolshare.common.exception;

public class MalformedRequestException extends ToolShareException {
    public MalformedRequestException(String message) {
        super("MALFORMED_REQUEST", message);
    }
}
