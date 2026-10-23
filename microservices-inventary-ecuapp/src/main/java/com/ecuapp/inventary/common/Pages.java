package com.ecuapp.inventary.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes client pagination: page >= 0, 1 <= size <= 100 and sort properties from an explicit allowlist.
 * Unknown properties are rejected with HTTP 400 instead of reaching the persistence layer.
 */
public final class Pages {
    public static final int MAX_SIZE = 100;

    private Pages() {}

    public static Pageable of(Pageable requested, Set<String> allowedSort, Sort defaultSort) {
        return of(requested, allowedSort.stream().collect(java.util.stream.Collectors.toMap(p -> p, p -> p)), defaultSort);
    }

    /** @param allowedSort public property name to entity property path */
    public static Pageable of(Pageable requested, Map<String, String> allowedSort, Sort defaultSort) {
        if (requested == null) return PageRequest.of(0, 20, defaultSort);
        if (requested.getPageNumber() < 0) throw new InvalidQueryException("El número de página no puede ser negativo.");
        int size = Math.max(1, Math.min(requested.getPageSize(), MAX_SIZE));
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : requested.getSort()) {
            String target = allowedSort.get(order.getProperty());
            if (target == null) throw new InvalidQueryException("Propiedad de ordenamiento no permitida: " + safe(order.getProperty()) + ".");
            orders.add(new Sort.Order(order.getDirection(), target));
        }
        return PageRequest.of(requested.getPageNumber(), size, orders.isEmpty() ? defaultSort : Sort.by(orders));
    }

    private static String safe(String value) {
        if (value == null) return "";
        String trimmed = value.length() > 60 ? value.substring(0, 60) : value;
        return trimmed.replaceAll("[^A-Za-z0-9_.]", "?");
    }
}
