package org.example.orders.service;

import org.example.orders.model.OrderSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class IndexTemplateGeneratorTest {

    private final ObjectNode template = new IndexTemplateGenerator()
            .generate("orders-*", "orders-search", OrderSnapshot.class);

    @Test
    void generatesTemplateSkeleton() {
        assertThat(template.at("/index_patterns/0").asText()).isEqualTo("orders-*");
        assertThat(template.at("/template/aliases").has("orders-search")).isTrue();
        assertThat(template.at("/template/mappings/dynamic").asText()).isEqualTo("false");
    }

    @Test
    void containsOnlySearchableFields() {
        JsonNode properties = template.at("/template/mappings/properties");

        assertThat(properties.at("/status/type").asText()).isEqualTo("keyword");
        assertThat(properties.at("/customerId/type").asText()).isEqualTo("keyword");
        assertThat(properties.at("/currency/type").asText()).isEqualTo("keyword");
        assertThat(properties.at("/updatedAt/type").asText()).isEqualTo("date");
        assertThat(properties.at("/totalAmount/type").asText()).isEqualTo("scaled_float");
        assertThat(properties.at("/totalAmount/scaling_factor").asLong()).isEqualTo(100);

        // поля без аннотаций не индексируются и хранятся только в _source
        assertThat(properties.has("orderId")).isFalse();
        assertThat(properties.has("attributes")).isFalse();
    }

    @Test
    void mapsItemsAsNestedWithSearchableName() {
        JsonNode items = template.at("/template/mappings/properties/items");

        assertThat(items.at("/type").asText()).isEqualTo("nested");
        assertThat(items.at("/properties/name/type").asText()).isEqualTo("text");
        assertThat(items.at("/properties/name/fields/keyword/type").asText()).isEqualTo("keyword");

        // остальные поля позиции не индексируются
        assertThat(items.at("/properties").has("productId")).isFalse();
        assertThat(items.at("/properties").has("quantity")).isFalse();
        assertThat(items.at("/properties").has("price")).isFalse();
    }
}
