package com.cmanzano.orders.infrastructure.adapter.out.persistence.dynamodb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint    solo se informa en local (DynamoDB Local); en AWS se usa el endpoint regional.
 * @param createTable crea la tabla al arrancar (solo local/tests; en AWS la crea Terraform).
 */
@ConfigurationProperties("app.aws.dynamodb")
public record DynamoDbProperties(String region, String endpoint, String auditTable, boolean createTable) {
}
