package org.example.orders.model.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Поле документа, по которому поисковый сервис строит запросы.
 * Попадает в index template с указанным типом OpenSearch.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IndexedField {

    /** Тип поля в маппинге OpenSearch: keyword, date, text, scaled_float и т.д. */
    String type();

    /** scaling_factor для типа scaled_float */
    long scalingFactor() default -1;
}
