package com.ecuapp.inventary.common;

import java.time.Instant;

/**
 * Optional time filters are bound as concrete bounds: PostgreSQL cannot infer the type of a null
 * timestamp parameter in "(:from is null or ...)" and plain range predicates keep indexes usable.
 */
public final class TimeRange {
    public static final Instant MIN = Instant.parse("1970-01-01T00:00:00Z");
    public static final Instant MAX = Instant.parse("9999-12-31T00:00:00Z");

    private TimeRange() {}

    public static Instant lower(Instant from) { return from == null ? MIN : from; }

    public static Instant upper(Instant to) { return to == null ? MAX : to; }
}
