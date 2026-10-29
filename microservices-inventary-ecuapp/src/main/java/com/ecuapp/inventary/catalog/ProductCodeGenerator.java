package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.common.ConflictException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates the internal product code (SKU) when the client does not provide one.
 * Uses an alphabet without ambiguous characters (0/O, 1/I/L) so the code can be read aloud or typed safely.
 */
@Component
public class ProductCodeGenerator {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 8;
    private static final int MAX_ATTEMPTS = 20;
    private final SecureRandom random = new SecureRandom();
    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public ProductCodeGenerator(ProductRepository products, ProductVariantRepository variants) {
        this.products = products;
        this.variants = variants;
    }

    /** Returns a random code not used by any product or variant; the unique constraint on product.sku is the final guard. */
    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = "PRD-" + randomPart();
            if (!products.existsBySkuIgnoreCase(candidate) && !variants.existsBySkuIgnoreCase(candidate)) return candidate;
        }
        throw new ConflictException("No se pudo generar un código único para el producto. Inténtelo de nuevo.");
    }

    private String randomPart() {
        char[] value = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return new String(value);
    }
}
