package org.example.orders.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.opensearch.data.client.osc.OpenSearchTemplate;
import org.opensearch.data.core.OpenSearchMappingParametersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchSimpleTypes;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

@Configuration
public class OpenSearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient restClient(@Value("${opensearch.host:http://localhost:9200}") String host)
            throws java.net.URISyntaxException {
        return RestClient.builder(HttpHost.create(host)).build();
    }

    @Bean
    public OpenSearchClient openSearchClient(RestClient restClient, ObjectMapper objectMapper) {
        return new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper)));
    }

    @Bean
    public ElasticsearchOperations elasticsearchOperations(OpenSearchClient openSearchClient) {
        // в SimpleTypeHolder форка нет BigDecimal — добавляем, иначе scaled_float не собирается
        SimpleElasticsearchMappingContext context = new SimpleElasticsearchMappingContext();
        context.setSimpleTypeHolder(new SimpleTypeHolder(
                Set.of(BigDecimal.class, BigInteger.class), ElasticsearchSimpleTypes.HOLDER));
        return new OpenSearchTemplate(openSearchClient, new MappingElasticsearchConverter(context), new OpenSearchMappingParametersCustomizer());
    }
}
