package org.example.orders.service;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Генерирует composable index template. Маппинг выводится Spring Data
 * из аннотаций @Field на классе документа (без обращения к кластеру),
 * поверх добавляется "dynamic": "false": неиндексируемые поля документа
 * хранятся только в _source.
 */
@Component
public class IndexTemplateGenerator {

    private final ElasticsearchOperations operations;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public IndexTemplateGenerator(ElasticsearchOperations operations) {
        this.operations = operations;
    }

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

        ObjectNode mappings = mapper.valueToTree(operations.indexOps(documentClass).createMapping());
        mappings.put("dynamic", "false");
        template.set("mappings", mappings);
        return root;
    }
}
