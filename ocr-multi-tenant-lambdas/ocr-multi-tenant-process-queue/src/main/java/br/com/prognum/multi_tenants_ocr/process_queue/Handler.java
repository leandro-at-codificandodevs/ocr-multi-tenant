package br.com.prognum.multi_tenants_ocr.process_queue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.MessageAttribute;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.prognum.multi_tenant_ocr.common.utils.Config;
import br.com.prognum.multi_tenant_ocr.common.utils.ConfigImpl;

/**
 * Lambda function que recebe o resultado do processamento OCR e atualiza
 * DynamoDB.
 */
public class Handler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final AmazonDynamoDB dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final Config config;

    public Handler() {
        this(AmazonDynamoDBClientBuilder.standard().build(), new ConfigImpl());
    }

    public Handler(AmazonDynamoDB dynamoDbClient, Config config) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = new ObjectMapper();
        this.config = config;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        context.getLogger().log(String.format("Received event: %s", event));
        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();

        for (SQSMessage message : event.getRecords()) {
            try {
                processMessage(message);
            } catch (Exception e) {
                e.printStackTrace();
                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
            }
        }

        return new SQSBatchResponse(batchItemFailures);
    }

    private void processMessage(SQSMessage message) throws JsonProcessingException {
        SqsResponse sqsResponse = parseSqsResponse(message);

        updateDynamoDB(sqsResponse);
    }

    private SqsResponse parseSqsResponse(SQSMessage message) throws JsonProcessingException {
        SqsResponse sqsResponse = new SqsResponse();

        Map<String, Object> messageBody = objectMapper.readValue(message.getBody(),
                new TypeReference<Map<String, Object>>() {
                });

        Map<String, Object> result = new HashMap<>();
        result.put("messageId", message.getMessageId());
        result.put("receiptHandle", message.getReceiptHandle());
        result.put("body", messageBody);
        result.put("attributes", message.getAttributes());
        result.put("messageAttributes", message.getMessageAttributes());
        sqsResponse.setMessage(objectMapper.writeValueAsString(result));

        MessageAttribute requestIdAttributeValue = message.getMessageAttributes().get("requestId");
        String requestId = requestIdAttributeValue != null ? requestIdAttributeValue.getStringValue() : null;
        requestId = requestId == null ? (String) messageBody.get("requestId") : requestId;
        sqsResponse.setRequestId(requestId);

        MessageAttribute tenantIdAttributeValue = message.getMessageAttributes().get("tenantId");
        String tenantId = tenantIdAttributeValue != null ? tenantIdAttributeValue.getStringValue() : null;
        tenantId = tenantId == null ? (String) messageBody.get("tenantId") : tenantId;
        sqsResponse.setTenantId(tenantId);

        return sqsResponse;
    }

    private void updateDynamoDB(SqsResponse response) {
        Map<String, AttributeValue> key = buildDynamoDBKey(response.getTenantId(), response.getRequestId());
        UpdateItemRequest updateRequest = buildUpdateRequest(response, key);

        dynamoDbClient.updateItem(updateRequest);
    }

    private Map<String, AttributeValue> buildDynamoDBKey(String tenantId, String requestId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("pk", new AttributeValue("TENANT_ID#" + tenantId));
        key.put("sk", new AttributeValue("REQUEST_ID#" + requestId));
        return key;
    }

    private UpdateItemRequest buildUpdateRequest(SqsResponse response, Map<String, AttributeValue> key) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        Map<String, String> expressionAttributeNames = new HashMap<>();
        StringBuilder updateExpression = new StringBuilder();
        buildUpdateExpression(response, expressionAttributeValues, expressionAttributeNames, updateExpression);

        return new UpdateItemRequest().withTableName(config.buildTableName(response.getTenantId()))
                .withKey(key)
                .withUpdateExpression(updateExpression.toString())
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues)
                .withConditionExpression("attribute_exists(pk) AND attribute_exists(sk)");
    }

    private void buildUpdateExpression(SqsResponse response, Map<String, AttributeValue> expressionAttributeValues,
            Map<String, String> expressionAttributeNames, StringBuilder updateExpression) {

        expressionAttributeValues.put(":queueResult", new AttributeValue(response.getMessage()));

        expressionAttributeValues.put(":status", new AttributeValue("PROCESSED"));
        expressionAttributeValues.put(":updatedAt", new AttributeValue(Instant.now().toString()));

        updateExpression.append("SET queueResult = :queueResult, #status = :status, updatedAt = :updatedAt");
        expressionAttributeNames.put("#status", "status");
    }

}