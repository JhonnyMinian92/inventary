package com.ecuapp.inventary.architecture;

import com.ecuapp.inventary.catalog.api.graphql.controller.GraphqlCatalogController;
import com.ecuapp.inventary.catalog.api.rest.controller.CatalogStructureController;
import com.ecuapp.inventary.catalog.api.rest.controller.CategoryController;
import com.ecuapp.inventary.catalog.api.rest.controller.ProductController;
import com.ecuapp.inventary.catalog.api.rest.controller.PublicCatalogController;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Architectural regression tests for the catalog inbound adapters.
 * They deliberately use reflection and existing dependencies; no runtime Spring context is required.
 */
class CatalogApiBoundaryTest {

    private static final List<Class<?>> API_ADAPTERS = List.of(
        ProductController.class,
        CategoryController.class,
        CatalogStructureController.class,
        PublicCatalogController.class,
        GraphqlCatalogController.class
    );

    @Test
    void apiAdaptersMustNotInjectSpringDataRepositories() {
        for (Class<?> adapter : API_ADAPTERS) {
            for (Field field : adapter.getDeclaredFields()) {
                assertFalse(
                    Repository.class.isAssignableFrom(field.getType()),
                    () -> adapter.getSimpleName() + " must not depend directly on repository field " + field.getName()
                );
            }
        }
    }

    @Test
    void apiAdaptersMustNotAcceptOrReturnJpaEntities() {
        for (Class<?> adapter : API_ADAPTERS) {
            for (Method method : adapter.getDeclaredMethods()) {
                assertFalse(
                    containsJpaEntity(method.getGenericReturnType()),
                    () -> adapter.getSimpleName() + "." + method.getName() + " exposes a JPA entity as its response"
                );

                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(RequestBody.class)) {
                        assertFalse(
                            containsJpaEntity(parameter.getParameterizedType()),
                            () -> adapter.getSimpleName() + "." + method.getName() + " accepts a JPA entity as its request body"
                        );
                    }
                }
            }
        }
    }

    private static boolean containsJpaEntity(Type type) {
        if (type instanceof Class<?> rawType) {
            return rawType.isAnnotationPresent(Entity.class);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            if (containsJpaEntity(parameterizedType.getRawType())) {
                return true;
            }
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (containsJpaEntity(argument)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsJpaEntity(arrayType.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcardType) {
            for (Type bound : wildcardType.getUpperBounds()) {
                if (containsJpaEntity(bound)) {
                    return true;
                }
            }
            for (Type bound : wildcardType.getLowerBounds()) {
                if (containsJpaEntity(bound)) {
                    return true;
                }
            }
        }
        return false;
    }
}
