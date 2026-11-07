package org.example.orders.model;

import org.example.orders.model.annotation.IndexedField;
import org.example.orders.model.annotation.NestedIndexed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class OrderSnapshot {

    private String orderId;

    @IndexedField(type = "keyword")
    private String customerId;

    @IndexedField(type = "keyword")
    private String status;

    @IndexedField(type = "scaled_float", scalingFactor = 100)
    private BigDecimal totalAmount;

    @IndexedField(type = "keyword")
    private String currency;

    @NestedIndexed
    private List<OrderItem> items;

    @IndexedField(type = "date")
    private Instant updatedAt;

    private Map<String, Object> attributes;

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
