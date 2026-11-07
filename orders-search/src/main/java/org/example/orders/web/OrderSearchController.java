package org.example.orders.web;

import org.example.orders.api.OrdersApi;
import org.example.orders.api.model.OrderItem;
import org.example.orders.api.model.SearchResult;
import org.example.orders.service.OrderSearchCriteria;
import org.example.orders.service.OrderSearchService;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@RestController
public class OrderSearchController implements OrdersApi {

    private final OrderSearchService orderSearchService;

    public OrderSearchController(OrderSearchService orderSearchService) {
        this.orderSearchService = orderSearchService;
    }

    @Override
    public SearchResult search(String status, String customerId, String currency,
                               OffsetDateTime dateFrom, OffsetDateTime dateTo,
                               String q, Integer from, Integer size) {
        try {
            SearchResponse<org.example.orders.model.OrderSnapshot> response = orderSearchService.search(
                    new OrderSearchCriteria(status, customerId, currency,
                            toInstant(dateFrom), toInstant(dateTo), q,
                            from != null ? from : 0, size != null ? size : 20));
            return toApi(response);
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка поиска в OpenSearch", e);
        }
    }

    private static SearchResult toApi(SearchResponse<org.example.orders.model.OrderSnapshot> response) {
        List<org.example.orders.api.model.OrderSnapshot> orders = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(OrderSearchController::toApi)
                .toList();
        long total = response.hits().total() != null ? response.hits().total().value() : orders.size();
        return new SearchResult().total(total).orders(orders);
    }

    private static org.example.orders.api.model.OrderSnapshot toApi(
            org.example.orders.model.OrderSnapshot snapshot) {
        return new org.example.orders.api.model.OrderSnapshot()
                .orderId(snapshot.getOrderId())
                .customerId(snapshot.getCustomerId())
                .status(snapshot.getStatus())
                .totalAmount(snapshot.getTotalAmount())
                .currency(snapshot.getCurrency())
                .items(snapshot.getItems() == null ? null
                        : snapshot.getItems().stream().map(OrderSearchController::toApi).toList())
                .updatedAt(snapshot.getUpdatedAt() == null ? null
                        : snapshot.getUpdatedAt().atOffset(ZoneOffset.UTC))
                .attributes(snapshot.getAttributes());
    }

    private static OrderItem toApi(org.example.orders.model.OrderItem item) {
        return new OrderItem()
                .productId(item.getProductId())
                .name(item.getName())
                .quantity(item.getQuantity())
                .price(item.getPrice());
    }

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }
}
