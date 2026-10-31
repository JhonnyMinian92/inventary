package com.ecuapp.inventary.catalog.application.service;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.catalog.api.rest.dto.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

/**
 * Suggests which ready-made template fits a category from free text (category and/or product name).
 * Uses a curated Spanish vocabulary with accent and plural normalization: instant, offline and predictable.
 * It also adapts to each business by finding an existing category with a similar name whose properties can be reused.
 * The response contract does not depend on the technique, so it could later be backed by embeddings without client changes.
 */
@Service
public class CategorySuggestionService {
    private static final int MAX_TEXT = 300;
    private static final int MAX_SEARCH_TOKENS = 6;
    private static final Set<String> STOP_WORDS = Set.of("de", "del", "la", "las", "el", "los", "y", "para", "con", "sin", "en", "por", "un", "una", "otro", "otros", "otra", "otras", "varios", "general");

    /** Vocabulary per template, in the same order as the templates (earlier wins ties). */
    private static final Map<String, List<String>> VOCABULARY = new LinkedHashMap<>();
    static {
        VOCABULARY.put("ropa", List.of("ropa", "prenda", "camiseta", "camisa", "blusa", "polo", "pantalon", "jean", "short", "bermuda", "falda", "vestido", "chaqueta",
            "chompa", "buzo", "sudadera", "abrigo", "saco", "pijama", "lenceria", "interior", "boxer", "media", "calcetin", "gorra", "sombrero", "bufanda", "guante",
            "uniforme", "leggin", "licra", "traje", "terno", "corbata", "cinturon", "correa", "bolso", "cartera", "billetera", "bisuteria", "joya", "arete", "collar", "pulsera", "textil"));
        VOCABULARY.put("calzado", List.of("calzado", "zapato", "zapatilla", "tenis", "deportivo", "sandalia", "bota", "botin", "chancla", "chancleta", "mocasin", "tacon", "pantufla", "crocs"));
        VOCABULARY.put("alimentos", List.of("alimento", "comida", "bebida", "gaseosa", "cola", "jugo", "agua", "cerveza", "vino", "licor", "ron", "whisky", "snack", "galleta",
            "chocolate", "dulce", "golosina", "caramelo", "confite", "arroz", "azucar", "aceite", "harina", "leche", "lacteo", "yogur", "queso", "embutido", "carne", "pollo",
            "pescado", "marisco", "atun", "sardina", "enlatado", "conserva", "cafe", "te", "pan", "panaderia", "pastel", "cereal", "fruta", "verdura", "legumbre", "condimento",
            "especia", "salsa", "abarrote", "viveres", "grano", "frejol", "lenteja", "fideo", "pasta", "helado", "mermelada", "miel", "huevo", "mascota", "balanceado"));
        VOCABULARY.put("tecnologia", List.of("tecnologia", "electronico", "electronica", "celular", "telefono", "smartphone", "tablet", "laptop", "computadora", "computador",
            "pc", "monitor", "teclado", "mouse", "audifono", "auricular", "parlante", "bocina", "cargador", "usb", "memoria", "disco", "impresora", "camara", "tv", "televisor",
            "consola", "videojuego", "smartwatch", "router", "electrodomestico", "licuadora", "microondas", "refrigeradora", "nevera", "lavadora", "plancha", "ventilador", "gamer"));
        VOCABULARY.put("ferreteria", List.of("ferreteria", "herramienta", "martillo", "taladro", "destornillador", "desarmador", "llave", "tornillo", "clavo", "perno", "tuerca",
            "pintura", "brocha", "rodillo", "cemento", "arena", "bloque", "ladrillo", "tubo", "tuberia", "pvc", "cable", "electrico", "foco", "interruptor", "tomacorriente",
            "cerradura", "candado", "construccion", "repuesto", "manguera", "lija", "pegamento", "silicon", "varilla", "alambre", "plomeria", "grifo", "llanta", "aceite motor", "automotriz"));
        VOCABULARY.put("hogar", List.of("hogar", "casa", "cocina", "olla", "sarten", "plato", "vaso", "taza", "cubierto", "cuchara", "tenedor", "cuchillo", "mueble", "silla",
            "mesa", "sofa", "cama", "colchon", "almohada", "sabana", "edredon", "cortina", "toalla", "decoracion", "adorno", "lampara", "alfombra", "organizador", "limpieza",
            "escoba", "trapeador", "detergente", "desinfectante", "balde", "recipiente", "tupper", "termo", "jarra", "florero", "cojin", "bano", "jardin", "maceta", "plastico", "desechable"));
        VOCABULARY.put("belleza", List.of("belleza", "cosmetico", "cosmetica", "maquillaje", "labial", "rimel", "perfume", "colonia", "fragancia", "crema", "locion", "shampoo",
            "champu", "acondicionador", "jabon", "desodorante", "esmalte", "cabello", "pelo", "tinte", "gel", "bloqueador", "protector solar", "skincare", "serum",
            "peine", "cepillo", "afeitar", "rasuradora", "cuidado personal", "higiene", "spa", "barberia"));
        VOCABULARY.put("farmacia", List.of("farmacia", "medicamento", "medicina", "remedio", "pastilla", "tableta", "capsula", "jarabe", "vitamina", "suplemento", "analgesico",
            "antibiotico", "paracetamol", "ibuprofeno", "venda", "gasa", "alcohol", "jeringa", "termometro", "suero", "pomada", "unguento", "curita", "salud", "ortopedico",
            "mascarilla", "tapabocas", "guante medico", "botiquin", "insumo medico"));
        VOCABULARY.put("papeleria", List.of("papeleria", "cuaderno", "lapiz", "lapices", "esfero", "boligrafo", "pluma", "borrador", "regla", "papel", "carpeta", "archivador",
            "marcador", "resaltador", "tijera", "goma", "cinta", "grapadora", "oficina", "util", "utiles", "escolar", "mochila", "libro", "agenda", "sobre", "folder", "tinta",
            "toner", "cartulina", "impresion", "copia"));
        VOCABULARY.put("juguetes", List.of("juguete", "juego", "muneca", "peluche", "carrito", "pelota", "balon", "lego", "rompecabezas", "puzzle", "didactico", "figura", "nino", "infantil", "bebe"));
        VOCABULARY.put("servicios", List.of("servicio", "mantenimiento", "instalacion", "reparacion", "arreglo", "asesoria", "consultoria", "clase", "curso", "taller",
            "transporte", "envio", "delivery", "mano de obra", "diseno", "consulta", "alquiler", "renta", "suscripcion", "corte"));
    }

    private final AttributePresetCatalog presets;
    private final CategoryRepository categories;
    private final CategoryAttributeDefinitionRepository definitions;

    public CategorySuggestionService(AttributePresetCatalog presets, CategoryRepository categories, CategoryAttributeDefinitionRepository definitions) {
        this.presets = presets;
        this.categories = categories;
        this.definitions = definitions;
    }

    @Transactional(readOnly = true)
    public CatalogStructureDtos.CategorySuggestionView suggest(String text) {
        String normalized = normalize(text == null ? "" : text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
        Set<String> tokens = tokens(normalized);
        if (tokens.isEmpty()) return new CatalogStructureDtos.CategorySuggestionView(null, null, List.of(), null, null, List.of());

        String bestTemplate = null;
        List<String> bestTerms = List.of();
        for (Map.Entry<String, List<String>> entry : VOCABULARY.entrySet()) {
            List<String> matched = new ArrayList<>();
            for (String word : entry.getValue()) {
                boolean hit = word.contains(" ") ? (" " + normalized + " ").contains(" " + word + " ") : tokens.contains(stem(word));
                if (hit) matched.add(word);
            }
            if (matched.size() > bestTerms.size()) {
                bestTemplate = entry.getKey();
                bestTerms = matched;
            }
        }

        // Adapts to the business: reuse the properties of the most similar existing category.
        Map<UUID, Category> candidates = new LinkedHashMap<>();
        tokens.stream().filter(token -> token.length() >= 3).sorted(Comparator.comparingInt(String::length).reversed()).limit(MAX_SEARCH_TOKENS)
            .forEach(token -> categories.findTop20ByActiveTrueAndSlugContainingOrderByNameAsc(token).forEach(c -> candidates.putIfAbsent(c.getId(), c)));
        Category similar = null;
        int bestOverlap = 0;
        for (Category category : candidates.values()) {
            Set<String> other = tokens(normalize(category.getName()));
            other.retainAll(tokens);
            if (other.size() > bestOverlap) {
                List<CategoryAttributeDefinition> schema = definitions.findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(category.getId());
                if (!schema.isEmpty()) {
                    bestOverlap = other.size();
                    similar = category;
                }
            }
        }
        List<CatalogStructureDtos.AttributeDefinitionView> similarDefinitions = similar == null ? List.of()
            : definitions.findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(similar.getId()).stream().map(CatalogStructureDtos.AttributeDefinitionView::from).toList();

        String templateName = bestTemplate == null ? null : presets.templateName(bestTemplate);
        return new CatalogStructureDtos.CategorySuggestionView(bestTemplate, templateName, bestTerms,
            similar == null ? null : similar.getId(), similar == null ? null : similar.getName(), similarDefinitions);
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Light Spanish stemming so "camisetas" matches "camiseta" and "pantalones" matches "pantalon". */
    static String stem(String word) {
        if (word.length() > 5 && word.endsWith("es") && !word.endsWith("ces")) return word.substring(0, word.length() - 2);
        if (word.length() > 3 && word.endsWith("s")) return word.substring(0, word.length() - 1);
        return word;
    }

    private static Set<String> tokens(String normalized) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.isBlank() || STOP_WORDS.contains(token)) continue;
            result.add(stem(token));
            result.add(token);
        }
        return result;
    }
}
