package org.example.orders.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.math.BigDecimal;

@Getter
@Setter
public class OrderItem {

    @Transient
    private String productId;

    @MultiField(mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256))
    private String name;

    @Transient
    private Integer quantity;

    @Transient
    private BigDecimal price;
}
