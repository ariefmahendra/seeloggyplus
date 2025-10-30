package com.seeloggyplus.exceptions.database;

public class FatalDatabaseException extends RuntimeException{
    public FatalDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
