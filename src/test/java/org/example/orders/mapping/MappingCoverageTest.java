package org.example.orders.mapping;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.orders.model.OrderSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tripwire-тест на дрейф схемы: сравнивает структуру класса документа
 * с маппингом из orders-template.json. Падает при обновлении библиотеки,
 * если в классе появились/исчезли поля — значит, надо пересмотреть маппинг.
 * OpenSearch для теста не нужен.
 */
class MappingCoverageTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    // Поля, которые сознательно не индексируются и хранятся только в _source.
    // Новое поле из библиотеки, по которому поиск не нужен, добавляется сюда.
    private static final Set<String> KNOWN_UNINDEXED = Set.of();

    @Test
    void documentStructureMatchesIndexTemplate() throws Exception {
        Set<String> documentFields = new LinkedHashSet<>();
        Set<String> arbitraryMaps = new LinkedHashSet<>();
        collectDocumentFields(OrderSnapshot.class, "", documentFields, arbitraryMaps, new HashSet<>());

        JsonNode template;
        try (InputStream in = getClass().getResourceAsStream("/opensearch/orders-template.json")) {
            template = MAPPER.readTree(in);
        }
        Set<String> mappedFields = new LinkedHashSet<>();
        Set<String> disabledObjects = new LinkedHashSet<>();
        collectMapping(template.at("/template/mappings/properties"), "", mappedFields, disabledObjects);

        assertThat(disabledObjects)
                .as("Поля Map<String, Object> с произвольными данными должны быть объявлены "
                        + "в orders-template.json с \"enabled\": false, чтобы OpenSearch не парсил их содержимое")
                .containsAll(arbitraryMaps);

        Set<String> newFields = new LinkedHashSet<>(documentFields);
        newFields.addAll(arbitraryMaps);
        newFields.removeAll(mappedFields);
        newFields.removeAll(KNOWN_UNINDEXED);
        assertThat(newFields)
                .as("В документе появились поля, которых нет в маппинге. "
                        + "Добавьте их в orders-template.json, если по ним нужен поиск, "
                        + "или в KNOWN_UNINDEXED, если достаточно хранения в _source")
                .isEmpty();

        Set<String> staleFields = new LinkedHashSet<>(mappedFields);
        staleFields.removeAll(documentFields);
        staleFields.removeAll(arbitraryMaps);
        assertThat(staleFields)
                .as("В маппинге остались поля, которых нет в документе — "
                        + "проверьте, не переименованы ли они в библиотеке: поиск по ним молча сломается")
                .isEmpty();
    }

    private static void collectDocumentFields(Type type, String prefix, Set<String> fields,
                                              Set<String> maps, Set<Type> visiting) {
        Class<?> raw = rawClass(type);
        if (raw == null || raw == Object.class || Map.class.isAssignableFrom(raw)
                || JsonNode.class.isAssignableFrom(raw)) {
            maps.add(prefix);
            return;
        }
        if (isSimple(raw)) {
            fields.add(prefix);
            return;
        }
        if (raw.isArray()) {
            collectDocumentFields(raw.getComponentType(), prefix, fields, maps, visiting);
            return;
        }
        if (Collection.class.isAssignableFrom(raw)) {
            Type content = Object.class;
            if (type instanceof ParameterizedType parameterized) {
                content = parameterized.getActualTypeArguments()[0];
            }
            collectDocumentFields(content, prefix, fields, maps, visiting);
            return;
        }
        if (!visiting.add(type)) {
            if (!prefix.isEmpty()) {
                fields.add(prefix);
            }
            return;
        }
        if (!prefix.isEmpty()) {
            fields.add(prefix);
        }
        for (Class<?> c = raw; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())
                        || f.isAnnotationPresent(JsonIgnore.class)) {
                    continue;
                }
                String name = f.isAnnotationPresent(JsonProperty.class)
                        ? f.getAnnotation(JsonProperty.class).value()
                        : f.getName();
                collectDocumentFields(f.getGenericType(),
                        prefix.isEmpty() ? name : prefix + "." + name, fields, maps, visiting);
            }
        }
        visiting.remove(type);
    }

    private static void collectMapping(JsonNode properties, String prefix,
                                       Set<String> mapped, Set<String> disabled) {
        if (properties == null || properties.isMissingNode() || !properties.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : properties.properties()) {
            JsonNode body = entry.getValue();
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (body.has("enabled") && !body.get("enabled").asBoolean()) {
                disabled.add(path);
            }
            if (body.has("type") || body.has("properties")) {
                mapped.add(path);
            }
            if (body.has("properties")) {
                collectMapping(body.get("properties"), path, mapped, disabled);
            }
        }
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    private static boolean isSimple(Class<?> type) {
        return type.isPrimitive() || type.isEnum()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || type == Boolean.class || type == Character.class
                || type.getPackageName().startsWith("java.time")
                || Date.class.isAssignableFrom(type) || type == UUID.class
                || type == BigDecimal.class;
    }
}
