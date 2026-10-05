package com.seeloggyplus.shared.database;

public class FatalDatabaseException extends RuntimeException{
    public FatalDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
