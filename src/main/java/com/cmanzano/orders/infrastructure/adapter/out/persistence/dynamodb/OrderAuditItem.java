package com.cmanzano.orders.infrastructure.adapter.out.persistence.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Diseño de la tabla:
 * <ul>
 *   <li>PK {@code orderId}: agrupa toda la traza de un pedido en una sola partición.</li>
 *   <li>SK {@code sk = occurredAt#eventId}: ordena cronológicamente y hace idempotente la
 *   escritura (un evento re-entregado por Kafka sobrescribe el mismo ítem).</li>
 * </ul>
 */
@DynamoDbBean
public class OrderAuditItem {

    public static final String PARTITION_KEY = "orderId";
    public static final String SORT_KEY = "sk";

    private String orderId;
    private String sortKey;
    private String eventId;
    private String eventType;
    private String detail;
    private String occurredAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute(PARTITION_KEY)
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }
}
