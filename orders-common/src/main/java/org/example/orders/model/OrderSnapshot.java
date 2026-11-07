package org.example.orders.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Document(indexName = "orders")
public class OrderSnapshot {

    @Id
    private String orderId;

    @Field(type = FieldType.Keyword)
    private String customerId;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal totalAmount;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Nested)
    private List<OrderItem> items;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;

    @Field(type = FieldType.Object, enabled = false)
    private Map<String, Object> attributes;
}
