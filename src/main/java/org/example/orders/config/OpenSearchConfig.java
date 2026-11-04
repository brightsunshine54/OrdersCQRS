package org.example.orders.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

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
}
