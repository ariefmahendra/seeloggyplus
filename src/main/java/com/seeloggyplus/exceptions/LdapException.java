package com.seeloggyplus.exceptions;

public class LdapException extends Exception{
    public LdapException(String message) {
        super(message);
    }

    public LdapException(String message, Throwable cause) {
        super(message, cause);
    }
}
