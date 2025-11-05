package com.example.clipbot_backend;

public class AsrException extends RuntimeException {
    public AsrException(String message) { super(message); }
    public AsrException(String message, Throwable cause) { super(message, cause); }
}
