package org.example.orders.service;

import org.example.orders.model.OrderSnapshot;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.json.JsonData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class OrderSearchService {

    private final OpenSearchClient openSearchClient;
    private final String searchAlias;

    public OrderSearchService(OpenSearchClient openSearchClient,
                              @Value("${opensearch.search-alias:orders-search}") String searchAlias) {
        this.openSearchClient = openSearchClient;
        this.searchAlias = searchAlias;
    }

    public SearchResponse<OrderSnapshot> search(OrderSearchCriteria criteria) throws IOException {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (criteria.status() != null) {
            bool.filter(f -> f.term(t -> t.field("status").value(FieldValue.of(criteria.status()))));
        }
        if (criteria.customerId() != null) {
            bool.filter(f -> f.term(t -> t.field("customerId").value(FieldValue.of(criteria.customerId()))));
        }
        if (criteria.currency() != null) {
            bool.filter(f -> f.term(t -> t.field("currency").value(FieldValue.of(criteria.currency()))));
        }
        if (criteria.dateFrom() != null || criteria.dateTo() != null) {
            bool.filter(f -> f.range(r -> {
                r.field("updatedAt");
                if (criteria.dateFrom() != null) {
                    r.gte(JsonData.of(criteria.dateFrom().toString()));
                }
                if (criteria.dateTo() != null) {
                    r.lt(JsonData.of(criteria.dateTo().toString()));
                }
                return r;
            }));
        }
        if (criteria.q() != null && !criteria.q().isBlank()) {
            bool.must(m -> m.nested(n -> n
                    .path("items")
                    .query(nq -> nq.match(mm -> mm.field("items.name").query(FieldValue.of(criteria.q()))))));
        }
        Query query = Query.of(q -> q.bool(bool.build()));
        return openSearchClient.search(s -> s
                        .index(searchAlias)
                        .from(criteria.from())
                        .size(criteria.size())
                        .query(query)
                        .sort(sort -> sort.field(f -> f.field("updatedAt").order(SortOrder.Desc))),
                OrderSnapshot.class);
    }
}
