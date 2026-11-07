package org.example.orders.config;

import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class IndexTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexTemplateInitializer.class);

    private static final int MAX_ATTEMPTS = 12;
    private static final long RETRY_DELAY_MS = 5000;

    private final RestClient restClient;
    private final Resource templateResource;
    private final String templateName;

    public IndexTemplateInitializer(RestClient restClient,
                                    @Value("classpath:opensearch/orders-template.json") Resource templateResource,
                                    @Value("${opensearch.template-name:orders-template}") String templateName) {
        this.restClient = restClient;
        this.templateResource = templateResource;
        this.templateName = templateName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String templateJson = new String(templateResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Request request = new Request("PUT", "/_index_template/" + templateName);
                request.setJsonEntity(templateJson);
                Response response = restClient.performRequest(request);
                log.info("Index template '{}' применён: {}", templateName, response.getStatusLine().getStatusCode());
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
