package org.example.orders.service;

import org.apache.hc.core5.http.HttpHost;
import org.example.orders.model.OrderSnapshot;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.data.client.osc.OpenSearchTemplate;
import org.opensearch.data.core.OpenSearchMappingParametersCustomizer;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchSimpleTypes;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IndexTemplateGeneratorTest {

    private final ObjectNode template = createTemplate();

    private static ObjectNode createTemplate() {
        // createMapping работает локально по MappingContext, кластер не нужен
        try {
            RestClient restClient = RestClient.builder(HttpHost.create("http://localhost:9200")).build();
            OpenSearchClient client = new OpenSearchClient(
                    new RestClientTransport(restClient, new JacksonJsonpMapper()));
            SimpleElasticsearchMappingContext context = new SimpleElasticsearchMappingContext();
            context.setSimpleTypeHolder(new SimpleTypeHolder(
                    Set.of(BigDecimal.class, BigInteger.class), ElasticsearchSimpleTypes.HOLDER));
            return new IndexTemplateGenerator(
                    new OpenSearchTemplate(client, new MappingElasticsearchConverter(context), new OpenSearchMappingParametersCustomizer()))
                    .generate("orders-*", "orders-search", OrderSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

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
        assertThat(properties.at("/totalAmount/scaling_factor").asDouble()).isEqualTo(100.0);

        // @Id не попадает в маппинг, произвольные данные хранятся без индексации
        assertThat(properties.has("orderId")).isFalse();
        assertThat(properties.at("/attributes/type").asText()).isEqualTo("object");
        assertThat(properties.at("/attributes/enabled").asBoolean()).isFalse();
    }

    @Test
    void mapsItemsAsNestedWithSearchableName() {
        JsonNode items = template.at("/template/mappings/properties/items");

        assertThat(items.at("/type").asText()).isEqualTo("nested");
        assertThat(items.at("/properties/name/type").asText()).isEqualTo("text");
        assertThat(items.at("/properties/name/fields/keyword/type").asText()).isEqualTo("keyword");

        // @Transient-поля позиции не индексируются
        assertThat(items.at("/properties").has("productId")).isFalse();
        assertThat(items.at("/properties").has("quantity")).isFalse();
        assertThat(items.at("/properties").has("price")).isFalse();
    }
}
