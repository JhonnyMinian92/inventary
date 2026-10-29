package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.common.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates dynamic product attributes against the active schema of a category.
 * Unknown keys are rejected; when a category has no schema only simple scalar values are accepted.
 */
@Component
public class AttributeSchemaValidator {
    public static final int MAX_ATTRIBUTES = 50;
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,79}$");
    private static final Set<String> CONSTRAINT_KEYS = Set.of("min", "max", "maxLength", "pattern", "maxItems");

    /** Validates a schema definition request before it is versioned. */
    public void validateDefinition(String key, CategoryAttributeDefinition.ValueType type, Map<String, Object> constraints, List<Object> options) {
        if (key == null || !KEY.matcher(key).matches())
            throw new BusinessRuleException("La clave de atributo debe usar minúsculas, números o guion bajo y empezar por una letra.");
        Map<String, Object> c = constraints == null ? Map.of() : constraints;
        for (String constraint : c.keySet())
            if (!CONSTRAINT_KEYS.contains(constraint)) throw new BusinessRuleException("Restricción no soportada para " + key + ": " + constraint + ".");
        if (c.containsKey("pattern")) {
            try { Pattern.compile(String.valueOf(c.get("pattern"))); }
            catch (PatternSyntaxException ex) { throw new BusinessRuleException("El patrón del atributo " + key + " no es una expresión regular válida."); }
        }
        for (String numeric : List.of("min", "max", "maxLength", "maxItems"))
            if (c.containsKey(numeric) && !(c.get(numeric) instanceof Number)) throw new BusinessRuleException("La restricción " + numeric + " de " + key + " debe ser numérica.");
        boolean needsOptions = type == CategoryAttributeDefinition.ValueType.ENUM;
        List<Object> o = options == null ? List.of() : options;
        if (needsOptions && o.isEmpty()) throw new BusinessRuleException("El atributo " + key + " de tipo ENUM requiere opciones.");
        if (!needsOptions && type != CategoryAttributeDefinition.ValueType.STRING_LIST && !o.isEmpty())
            throw new BusinessRuleException("Solo los atributos ENUM o STRING_LIST admiten opciones.");
        if (o.size() > 200) throw new BusinessRuleException("Un atributo admite como máximo 200 opciones.");
        if (o.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank() || s.length() > 120))
            throw new BusinessRuleException("Las opciones del atributo " + key + " deben ser textos de hasta 120 caracteres.");
        if (new HashSet<>(o).size() != o.size()) throw new BusinessRuleException("Las opciones del atributo " + key + " no pueden repetirse.");
    }

    /** Validates values against the active definitions; returns a defensive copy. */
    public Map<String, Object> validateValues(Map<String, Object> values, List<CategoryAttributeDefinition> definitions, boolean enforceRequired) {
        Map<String, Object> input = values == null ? Map.of() : values;
        if (input.size() > MAX_ATTRIBUTES) throw new BusinessRuleException("Se admiten como máximo " + MAX_ATTRIBUTES + " atributos.");
        Map<String, CategoryAttributeDefinition> byKey = new HashMap<>();
        definitions.forEach(d -> byKey.put(d.getAttributeKey(), d));
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || !KEY.matcher(key).matches()) throw new BusinessRuleException("Clave de atributo inválida: " + safe(key) + ".");
            if (value == null) continue;
            CategoryAttributeDefinition definition = byKey.get(key);
            if (definition == null) {
                if (!definitions.isEmpty()) throw new BusinessRuleException("El atributo " + key + " no está definido para la categoría.");
                if (!(value instanceof String || value instanceof Number || value instanceof Boolean) || (value instanceof String s && s.length() > 500))
                    throw new BusinessRuleException("Sin esquema de categoría solo se admiten valores simples para " + key + ".");
                result.put(key, value);
                continue;
            }
            result.put(key, validate(definition, value));
        }
        if (enforceRequired) {
            for (CategoryAttributeDefinition d : definitions)
                if (d.isRequired() && !result.containsKey(d.getAttributeKey()))
                    throw new BusinessRuleException("Complete la propiedad obligatoria " + d.getDisplayName() + ".");
        }
        return result;
    }

    private Object validate(CategoryAttributeDefinition d, Object value) {
        // Messages use the visible name: end users never see technical keys.
        String key = d.getDisplayName();
        Map<String, Object> c = d.getConstraints();
        switch (d.getValueType()) {
            case STRING -> {
                if (!(value instanceof String s)) throw invalid(key, "texto");
                int max = intConstraint(c, "maxLength", 500);
                if (s.length() > max) throw new BusinessRuleException("El atributo " + key + " supera " + max + " caracteres.");
                if (c.containsKey("pattern") && !Pattern.compile(String.valueOf(c.get("pattern"))).matcher(s).matches())
                    throw new BusinessRuleException("El atributo " + key + " no cumple el formato requerido.");
                return s;
            }
            case NUMBER -> {
                if (!(value instanceof Number n)) throw invalid(key, "número");
                BigDecimal number = new BigDecimal(n.toString());
                if (c.get("min") instanceof Number min && number.compareTo(new BigDecimal(min.toString())) < 0)
                    throw new BusinessRuleException("El atributo " + key + " es menor que el mínimo permitido.");
                if (c.get("max") instanceof Number max && number.compareTo(new BigDecimal(max.toString())) > 0)
                    throw new BusinessRuleException("El atributo " + key + " es mayor que el máximo permitido.");
                return n;
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean b)) throw invalid(key, "booleano");
                return b;
            }
            case DATE -> {
                if (!(value instanceof String s)) throw invalid(key, "fecha ISO yyyy-MM-dd");
                try { LocalDate.parse(s); } catch (DateTimeParseException ex) { throw invalid(key, "fecha ISO yyyy-MM-dd"); }
                return s;
            }
            case ENUM -> {
                if (!(value instanceof String s) || !d.getOptions().contains(s))
                    throw new BusinessRuleException("El valor del atributo " + key + " no está entre las opciones permitidas.");
                return s;
            }
            case STRING_LIST -> {
                if (!(value instanceof List<?> list)) throw invalid(key, "lista de textos");
                int maxItems = intConstraint(c, "maxItems", 50);
                if (list.size() > maxItems) throw new BusinessRuleException("El atributo " + key + " admite como máximo " + maxItems + " elementos.");
                List<String> copy = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof String s) || s.isBlank() || s.length() > 120) throw invalid(key, "lista de textos");
                    if (!d.getOptions().isEmpty() && !d.getOptions().contains(s))
                        throw new BusinessRuleException("El valor " + s + " del atributo " + key + " no está entre las opciones permitidas.");
                    copy.add(s);
                }
                return copy;
            }
            default -> throw new BusinessRuleException("Tipo de atributo no soportado.");
        }
    }

    private int intConstraint(Map<String, Object> c, String name, int fallback) {
        return c.get(name) instanceof Number n ? n.intValue() : fallback;
    }

    private BusinessRuleException invalid(String key, String expected) {
        return new BusinessRuleException("El atributo " + key + " debe ser de tipo " + expected + ".");
    }

    private String safe(String key) {
        if (key == null) return "";
        return key.length() > 40 ? key.substring(0, 40) : key;
    }
}
