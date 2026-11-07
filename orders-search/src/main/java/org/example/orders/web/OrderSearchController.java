package org.example.orders.web;

import org.example.orders.model.OrderSnapshot;
import org.example.orders.service.OrderSearchCriteria;
import org.example.orders.service.OrderSearchService;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderSearchController {

    private final OrderSearchService orderSearchService;

    public OrderSearchController(OrderSearchService orderSearchService) {
        this.orderSearchService = orderSearchService;
    }

    @GetMapping("/search")
    public SearchResult search(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String customerId,
                               @RequestParam(required = false) String currency,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "0") int from,
                               @RequestParam(defaultValue = "20") int size) throws IOException {
        SearchResponse<OrderSnapshot> response = orderSearchService.search(
                new OrderSearchCriteria(status, customerId, currency, dateFrom, dateTo, q, from, size));
        List<OrderSnapshot> orders = response.hits().hits().stream()
                .map(Hit::source)
                .toList();
        long total = response.hits().total() != null ? response.hits().total().value() : orders.size();
        return new SearchResult(total, orders);
    }

    public record SearchResult(long total, List<OrderSnapshot> orders) {
    }
}
