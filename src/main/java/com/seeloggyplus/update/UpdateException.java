package com.seeloggyplus.update;

/** Raised when an update manifest or asset is invalid. */
public class UpdateException extends Exception {

    public UpdateException(String message) {
        super(message);
    }

    public UpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
