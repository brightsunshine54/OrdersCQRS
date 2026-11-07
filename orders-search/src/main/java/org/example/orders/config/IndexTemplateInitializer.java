package org.example.orders.config;

import org.example.orders.model.OrderSnapshot;
import org.example.orders.service.IndexTemplateGenerator;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * При старте генерирует index template из аннотаций на {@link OrderSnapshot}
 * и применяет его в OpenSearch. Операция идемпотентна (upsert шаблона).
 */
@Component
public class IndexTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexTemplateInitializer.class);

    private static final int MAX_ATTEMPTS = 12;
    private static final long RETRY_DELAY_MS = 5000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final IndexTemplateGenerator templateGenerator;
    private final String templateName;
    private final String indexPattern;
    private final String searchAlias;

    public IndexTemplateInitializer(RestClient restClient,
                                    ObjectMapper objectMapper,
                                    IndexTemplateGenerator templateGenerator,
                                    @Value("${opensearch.template-name:orders-template}") String templateName,
                                    @Value("${opensearch.index-pattern:orders-*}") String indexPattern,
                                    @Value("${opensearch.search-alias:orders-search}") String searchAlias) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.templateGenerator = templateGenerator;
        this.templateName = templateName;
        this.indexPattern = indexPattern;
        this.searchAlias = searchAlias;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ObjectNode template = templateGenerator.generate(indexPattern, searchAlias, OrderSnapshot.class);
        String templateJson = objectMapper.writeValueAsString(template);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Request request = new Request("PUT", "/_index_template/" + templateName);
                request.setJsonEntity(templateJson);
                Response response = restClient.performRequest(request);
                log.info("Index template '{}' применён: {}", templateName,
                        response.getStatusLine().getStatusCode());
                return;
            } catch (Exception e) {
                log.warn("Не удалось применить index template '{}' (попытка {}/{}): {}",
                        templateName, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
    }
}
