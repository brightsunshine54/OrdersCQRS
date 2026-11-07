package org.example.orders.model.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Коллекция вложенных объектов, по полям которых нужен поиск.
 * Маппится как "type": "nested".
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NestedIndexed {
}
