package org.example.orders.service;

import org.example.orders.model.annotation.IndexedField;
import org.example.orders.model.annotation.NestedIndexed;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;

/**
 * Генерирует composable index template из аннотаций {@link IndexedField}
 * и {@link NestedIndexed} на классе документа. В маппинг попадают только
 * поисковые поля; остальное хранится в _source за счёт "dynamic": "false".
 */
public class IndexTemplateGenerator {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    public ObjectNode generate(String indexPattern, String searchAlias, Class<?> documentClass) {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("index_patterns").add(indexPattern);
        root.put("priority", 100);
        root.putObject("_meta").put("generated_from", documentClass.getName());

        ObjectNode template = root.putObject("template");
        ObjectNode settings = template.putObject("settings");
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 1);
        template.putObject("aliases").putObject(searchAlias);

        ObjectNode mappings = template.putObject("mappings");
        mappings.put("dynamic", "false");
        mappings.set("properties", buildProperties(documentClass));
        return root;
    }

    private ObjectNode buildProperties(Class<?> type) {
        ObjectNode properties = mapper.createObjectNode();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                IndexedField indexed = field.getAnnotation(IndexedField.class);
                if (indexed != null) {
                    properties.set(field.getName(), fieldMapping(indexed));
                    continue;
                }
                if (field.isAnnotationPresent(NestedIndexed.class)) {
                    ObjectNode nested = mapper.createObjectNode();
                    nested.put("type", "nested");
                    nested.set("properties", buildProperties(elementClass(field)));
                    properties.set(field.getName(), nested);
                }
            }
        }
        return properties;
    }

    private ObjectNode fieldMapping(IndexedField indexed) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", indexed.type());
        if (indexed.scalingFactor() > 0) {
            node.put("scaling_factor", indexed.scalingFactor());
        }
        if ("text".equals(indexed.type())) {
            ObjectNode keyword = node.putObject("fields").putObject("keyword");
            keyword.put("type", "keyword");
            keyword.put("ignore_above", 256);
        }
        return node;
    }

    private Class<?> elementClass(Field field) {
        if (field.getGenericType() instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        throw new IllegalStateException(
                "@NestedIndexed поддерживает только типизированные коллекции: " + field);
    }
}
