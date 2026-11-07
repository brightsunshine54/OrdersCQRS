package org.example.orders.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "orders", description = "Поиск заказов по снимкам в OpenSearch")
public class OrderSearchController {

    private final OrderSearchService orderSearchService;

    public OrderSearchController(OrderSearchService orderSearchService) {
        this.orderSearchService = orderSearchService;
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск заказов",
            description = "Bool-запрос по алиасу orders-search: точные фильтры по keyword-полям, "
                    + "диапазон по updatedAt, частичное совпадение по названию позиции (nested)")
    public SearchResult search(@Parameter(description = "Статус заказа, например PAID")
                               @RequestParam(required = false) String status,
                               @Parameter(description = "Идентификатор покупателя")
                               @RequestParam(required = false) String customerId,
                               @Parameter(description = "Валюта, например RUB")
                               @RequestParam(required = false) String currency,
                               @Parameter(description = "Нижняя граница updatedAt, ISO-8601")
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
                               @Parameter(description = "Верхняя граница updatedAt (не включительно), ISO-8601")
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
                               @Parameter(description = "Частичное совпадение по названию позиции заказа")
                               @RequestParam(required = false) String q,
                               @Parameter(description = "Смещение для пагинации")
                               @RequestParam(defaultValue = "0") int from,
                               @Parameter(description = "Размер страницы")
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
