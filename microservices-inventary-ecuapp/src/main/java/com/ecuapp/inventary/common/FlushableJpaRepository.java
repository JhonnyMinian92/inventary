package com.ecuapp.inventary.common;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository contract for explicit deletes that must surface database constraints
 * before the surrounding business transaction completes.
 */
public interface FlushableJpaRepository<T, ID> extends JpaRepository<T, ID> {
    default void deleteAndFlush(T entity) {
        delete(entity);
        flush();
    }
}
