package com.ecuapp.inventary.common;

/** Signals a malformed or unsupported query parameter that should return HTTP 400. */
public class InvalidQueryException extends RuntimeException {
    public InvalidQueryException(String message) {
        super(message);
    }
}
