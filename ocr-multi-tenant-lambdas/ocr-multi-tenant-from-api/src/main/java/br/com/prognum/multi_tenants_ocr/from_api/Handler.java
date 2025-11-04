package br.com.prognum.multi_tenants_ocr.from_api;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.prognum.multi_tenant_ocr.common.utils.Config;
import br.com.prognum.multi_tenant_ocr.common.utils.ConfigImpl;

/**
 * Lambda function que recebe requisições do API Gateway para processar
 * documentos.
 */
public class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final AmazonS3 s3Client;
    private final AmazonDynamoDB dynamoDbClient;
    private final AmazonSQS sqsClient;
    private final ObjectMapper objectMapper;
    private final Config config;

    public Handler() {
        this(AmazonS3ClientBuilder.standard().build(), AmazonDynamoDBClientBuilder.standard().build(),
                AmazonSQSClientBuilder.standard().build(), new ConfigImpl()

        );
    }

    public Handler(AmazonS3 s3Client, AmazonDynamoDB dynamoDbClient, AmazonSQS sqsClient, Config config) {
        this.s3Client = s3Client;
        this.dynamoDbClient = dynamoDbClient;
        this.sqsClient = sqsClient;
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            context.getLogger().log(String.format("Received event: %s", event));

            String tenantId = extractTenantId(event);
            String requestId = extractRequestId(event);
            ApiRequest apiRequest = parseApiRequest(event);
            String timestamp = Instant.now().toString();

            context.getLogger().log(String.format("Processing document: %s for tenant: %s", requestId, tenantId));

            String s3Key = saveDocumentToS3(tenantId, requestId, apiRequest);
            saveStateToDynamoDB(tenantId, requestId, apiRequest, s3Key, timestamp, context);
            sendMessageToQueue(tenantId, requestId, apiRequest, s3Key, timestamp, context);

            return buildSuccessResponse(tenantId, requestId, timestamp);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractTenantId(APIGatewayV2HTTPEvent event) {
        return event.getHeaders().get("x-tenant-id");
    }

    private String extractRequestId(APIGatewayV2HTTPEvent event) {
        String requestId = event.getHeaders().get("x-request-id");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }

    private ApiRequest parseApiRequest(APIGatewayV2HTTPEvent event) throws Exception {
        Map<String, Object> body = objectMapper.readValue(event.getBody(), new TypeReference<Map<String, Object>>() {
        });

        String documentType = (String) body.get("tipoDocumento");
        String correlationId = (String) body.get("correlationId");
        String document = (String) body.get("documento");

        return new ApiRequest(documentType, correlationId, document);
    }

    private String saveDocumentToS3(String tenantId, String requestId, ApiRequest apiRequest) {
        String bucketName = config.buildBucketName(tenantId);
        String s3Key = String.format("tenants/%s/requests/%s", tenantId, requestId);

        byte[] documentBytes = Base64.getDecoder().decode(apiRequest.getDocument());
        uploadBinaryToS3(bucketName, s3Key, documentBytes, tenantId, requestId);

        return s3Key;
    }

    private void uploadBinaryToS3(String bucketName, String s3Key, byte[] documentBytes, String tenantId,
            String requestId) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(documentBytes.length);
        metadata.setContentType("application/octet-stream");
        metadata.addUserMetadata("tenant-id", tenantId);
        metadata.addUserMetadata("request-id", requestId);

        s3Client.putObject(bucketName, s3Key, new ByteArrayInputStream(documentBytes), metadata);
    }

    private void saveStateToDynamoDB(String tenantId, String requestId, ApiRequest apiRequest, String s3Key,
            String timestamp, Context context) {

        String tableName = config.buildTableName(tenantId);
        String bucketName = config.buildBucketName(tenantId);
        String outputQueueUrl = config.buildOutputQueueUrl(tenantId);

        Map<String, AttributeValue> item = buildDynamoDBItem(tenantId,
                requestId,
                apiRequest,
                bucketName,
                s3Key,
                outputQueueUrl,
                timestamp);

        PutItemRequest putItemRequest = new PutItemRequest().withTableName(tableName).withItem(item);

        dynamoDbClient.putItem(putItemRequest);
        context.getLogger().log("State saved to DynamoDB with status PROCESSING");
    }

    private Map<String, AttributeValue> buildDynamoDBItem(String tenantId, String requestId, ApiRequest apiRequest,
            String bucketName, String s3Key, String outputQueueUrl, String timestamp) {

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", new AttributeValue(String.format("TENANT_ID#%s", tenantId)));
        item.put("sk", new AttributeValue(String.format("REQUEST_ID#%s", requestId)));
        item.put("tenantId", new AttributeValue(tenantId));
        item.put("requestId", new AttributeValue(requestId));
        item.put("bucketName", new AttributeValue(bucketName));
        item.put("bucketKey", new AttributeValue(s3Key));
        item.put("outputQueueUrl", new AttributeValue(outputQueueUrl));
        item.put("status", new AttributeValue("PROCESSING"));
        item.put("createdAt", new AttributeValue(timestamp));
        item.put("updatedAt", new AttributeValue(timestamp));
        item.put("documentType", new AttributeValue(apiRequest.getDocumentType()));
        item.put("correlationId", new AttributeValue(apiRequest.getCorrelationId()));

        return item;
    }

    private void sendMessageToQueue(String tenantId, String requestId, ApiRequest apiRequest, String s3Key,
            String timestamp, Context context) throws Exception {

        String inputQueueUrl = config.buildInputQueueUrl(tenantId);
        String bucketName = config.buildBucketName(tenantId);
        String outputQueueUrl = config.buildOutputQueueUrl(tenantId);

        SqsRequest sqsRequest = buildSqsRequest(tenantId,
                requestId,
                apiRequest,
                bucketName,
                s3Key,
                outputQueueUrl,
                timestamp);

        String messageJson = objectMapper.writeValueAsString(sqsRequest);

        SendMessageRequest sendMessageRequest = new SendMessageRequest().withQueueUrl(inputQueueUrl)
                .withMessageBody(messageJson)
                .withMessageGroupId(tenantId)
                .withMessageDeduplicationId(requestId);

        sqsClient.sendMessage(sendMessageRequest);
        context.getLogger().log(String.format("Message sent to input queue with group: %s", tenantId));
    }

    private SqsRequest buildSqsRequest(String tenantId, String requestId, ApiRequest apiRequest, String bucketName,
            String s3Key, String outputQueueUrl, String timestamp) {
        SqsRequest sqsRequest = new SqsRequest();
        sqsRequest.setTenantId(tenantId);
        sqsRequest.setRequestId(requestId);
        sqsRequest.setCorrelationId(apiRequest.getCorrelationId());
        sqsRequest.setDocumentType(apiRequest.getDocumentType());
        sqsRequest.setBucketName(bucketName);
        sqsRequest.setBucketKey(s3Key);
        sqsRequest.setOutputQueueUrl(outputQueueUrl);
        sqsRequest.setTimestamp(timestamp);

        return sqsRequest;
    }

    private APIGatewayV2HTTPResponse buildSuccessResponse(String tenantId, String requestId, String timestamp)
            throws Exception {

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("tenantId", tenantId);
        responseBody.put("requestId", requestId);
        responseBody.put("status", "PROCESSING");
        responseBody.put("createdAt", timestamp);
        responseBody.put("updatedAt", timestamp);

        String response = objectMapper.writeValueAsString(responseBody);

        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(response)
                .build();
    }
}
