package org.example.orders.consumer;

import org.example.orders.model.OrderSnapshot;
import org.example.orders.service.OrderIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrderSnapshotConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderSnapshotConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderIndexer orderIndexer;

    public OrderSnapshotConsumer(ObjectMapper objectMapper, OrderIndexer orderIndexer) {
        this.objectMapper = objectMapper;
        this.orderIndexer = orderIndexer;
    }

    @KafkaListener(topics = "${app.kafka.topic:orders-snapshots}")
    public void consume(List<String> messages) {
        List<OrderSnapshot> snapshots = new ArrayList<>(messages.size());
        for (String message : messages) {
            try {
                snapshots.add(objectMapper.readValue(message, OrderSnapshot.class));
            } catch (Exception e) {
                log.error("Пропускаю некорректное сообщение: {}", message, e);
            }
        }
        if (snapshots.isEmpty()) {
            return;
        }
        try {
            orderIndexer.indexAll(snapshots);
            log.info("Сохранено {} заказов из пачки размером {}", snapshots.size(), messages.size());
        } catch (Exception e) {
            log.error("Не удалось сохранить пачку из {} заказов", snapshots.size(), e);
            throw new RuntimeException(e);
        }
    }
}
