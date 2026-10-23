package com.ecuapp.inventary.common;
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) { super(resource + " no encontrado: " + id); }
    public ResourceNotFoundException(String message) { super(message); }
}
