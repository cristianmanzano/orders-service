package com.cmanzano.orders.infrastructure.adapter.out.persistence.dynamodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;
import java.util.concurrent.CompletionException;

@Configuration
public class DynamoDbConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbConfig.class);

    /**
     * En AWS las credenciales se resuelven con la cadena por defecto (IRSA / rol de la tarea),
     * nunca con claves embebidas. Solo en local se usan credenciales ficticias.
     */
    @Bean
    DynamoDbAsyncClient dynamoDbAsyncClient(DynamoDbProperties properties) {
        var builder = DynamoDbAsyncClient.builder().region(Region.of(properties.region()));
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }

    @Bean
    DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient(DynamoDbAsyncClient client) {
        return DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(client).build();
    }

    @Bean
    DynamoDbAsyncTable<OrderAuditItem> orderAuditTable(DynamoDbEnhancedAsyncClient enhancedClient,
                                                      DynamoDbProperties properties) {
        return enhancedClient.table(properties.auditTable(), TableSchema.fromBean(OrderAuditItem.class));
    }

    @Bean
    @ConditionalOnProperty(name = "app.aws.dynamodb.create-table", havingValue = "true")
    ApplicationRunner dynamoDbTableInitializer(DynamoDbAsyncClient client, DynamoDbProperties properties) {
        return args -> {
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(properties.auditTable())
                    .attributeDefinitions(
                            attribute(OrderAuditItem.PARTITION_KEY),
                            attribute(OrderAuditItem.SORT_KEY))
                    .keySchema(
                            KeySchemaElement.builder().attributeName(OrderAuditItem.PARTITION_KEY).keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName(OrderAuditItem.SORT_KEY).keyType(KeyType.RANGE).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            try {
                client.createTable(request).join();
                log.info("DynamoDB table '{}' created", properties.auditTable());
            } catch (CompletionException ex) {
                if (!(ex.getCause() instanceof ResourceInUseException)) {
                    throw ex;
                }
                log.info("DynamoDB table '{}' already exists", properties.auditTable());
            }
        };
    }

    private static AttributeDefinition attribute(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }
}
