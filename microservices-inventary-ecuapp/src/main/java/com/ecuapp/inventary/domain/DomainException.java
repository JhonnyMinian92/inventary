package com.ecuapp.inventary.domain;

import java.util.Objects;

/** Domain failure carrying a stable code without depending on HTTP or Spring. */
public final class DomainException extends RuntimeException {
    private final DomainErrorCode code;

    public DomainException(DomainErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DomainErrorCode code() {
        return code;
    }
}
