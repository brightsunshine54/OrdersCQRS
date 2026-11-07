package org.example.orders.service;

import java.time.Instant;

public record OrderSearchCriteria(
        String status,
        String customerId,
        String currency,
        Instant dateFrom,
        Instant dateTo,
        String q,
        int from,
        int size) {
}
