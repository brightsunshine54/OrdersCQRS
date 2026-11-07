package org.example.orders.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Выгружает живую OpenAPI-спецификацию в target/openapi/openapi.json —
 * из неё модуль orders-search-client генерирует Feign-клиента.
 */
@SpringBootTest(properties = "opensearch.template-init.enabled=false")
@AutoConfigureMockMvc
class OpenApiSpecExportTest {

    @Test
    void exportOpenApiSpec(@Autowired MockMvc mockMvc) throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Path output = Path.of("target/openapi/openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, spec);
    }
}
