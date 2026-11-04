package org.example.orders.service;

import org.example.orders.model.OrderSnapshot;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderIndexer {

    private static final Logger log = LoggerFactory.getLogger(OrderIndexer.class);

    private final OpenSearchClient openSearchClient;
    private final String indexPrefix;
    private final ZoneId zoneId;

    public OrderIndexer(OpenSearchClient openSearchClient,
                        @Value("${opensearch.index-prefix:orders}") String indexPrefix,
                        @Value("${app.index-zone:UTC}") String zoneId) {
        this.openSearchClient = openSearchClient;
        this.indexPrefix = indexPrefix;
        this.zoneId = ZoneId.of(zoneId);
    }

    public void indexAll(List<OrderSnapshot> snapshots) throws IOException {
        Map<String, List<OrderSnapshot>> byIndex = snapshots.stream()
                .collect(Collectors.groupingBy(this::indexNameFor));
        for (Map.Entry<String, List<OrderSnapshot>> entry : byIndex.entrySet()) {
            ensureIndexExists(entry.getKey());
            bulkIndex(entry.getKey(), entry.getValue());
        }
    }

    private String indexNameFor(OrderSnapshot snapshot) {
        Instant timestamp = snapshot.getUpdatedAt() != null ? snapshot.getUpdatedAt() : Instant.now();
        LocalDate day = timestamp.atZone(zoneId).toLocalDate();
        return indexPrefix + "-" + day;
    }

    private void ensureIndexExists(String indexName) throws IOException {
        if (openSearchClient.indices().exists(e -> e.index(indexName)).value()) {
            return;
        }
        try {
            openSearchClient.indices().create(c -> c.index(indexName));
            log.info("Создан индекс {}", indexName);
        } catch (OpenSearchException e) {
            if (e.status() != 400) {
                throw e;
            }
            log.debug("Индекс {} уже создан конкурентно", indexName);
        }
    }

    private void bulkIndex(String indexName, List<OrderSnapshot> snapshots) throws IOException {
        BulkRequest.Builder request = new BulkRequest.Builder();
        for (OrderSnapshot snapshot : snapshots) {
            request.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(snapshot.getOrderId())
                    .document(snapshot)));
        }
        BulkResponse response = openSearchClient.bulk(request.build());
        if (response.errors()) {
            List<String> failures = new ArrayList<>();
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    failures.add(item.id() + ": " + item.error().reason());
                }
            }
            throw new IOException("Bulk-запись в %s не удалась для документов: %s"
                    .formatted(indexName, failures));
        }
        log.debug("Bulk-запись {} документов в {}", snapshots.size(), indexName);
    }
}
