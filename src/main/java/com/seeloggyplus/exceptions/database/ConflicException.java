package com.seeloggyplus.exceptions.database;

public class ConflicException extends Exception{
    public ConflicException(String message) {
        super(message);
    }

    public ConflicException(String message, Throwable cause) {
        super(message, cause);
    }
}
