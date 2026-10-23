package com.ecuapp.inventary.catalog.application;

import java.util.List;

/** Framework-independent paging request for application use cases. */
public record PageQuery(int page, int size, List<SortTerm> sort) {
    public PageQuery {
        if (page < 0) throw new IllegalArgumentException("El índice de página no puede ser negativo.");
        if (size < 1) throw new IllegalArgumentException("El tamaño de página debe ser positivo.");
        sort = sort == null ? List.of() : List.copyOf(sort);
    }

    public record SortTerm(String property, Direction direction) {
        public SortTerm {
            if (property == null || property.isBlank()) throw new IllegalArgumentException("La propiedad de ordenamiento es obligatoria.");
            direction = direction == null ? Direction.ASC : direction;
        }
    }

    public enum Direction { ASC, DESC }
}
