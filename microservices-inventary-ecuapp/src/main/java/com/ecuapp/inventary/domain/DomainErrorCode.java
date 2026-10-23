package com.ecuapp.inventary.domain;

/** Stable, framework-independent codes for business rule failures. */
public enum DomainErrorCode {
    INVALID_STATE_TRANSITION,
    INVALID_QUANTITY,
    INSUFFICIENT_STOCK,
    IMMUTABLE_POSTED_DOCUMENT,
    DUPLICATE_BUSINESS_KEY,
    RESOURCE_NOT_FOUND,
    CONCURRENT_MODIFICATION
}
