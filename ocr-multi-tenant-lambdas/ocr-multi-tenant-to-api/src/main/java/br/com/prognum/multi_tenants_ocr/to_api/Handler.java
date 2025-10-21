package br.com.prognum.multi_tenants_ocr.to_api;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import br.com.prognum.multi_tenant_ocr.common.Utils.Config;
import br.com.prognum.multi_tenant_ocr.common.Utils.ConfigImpl;

/**
 * Lambda function que consulta o resultado do processamento do documento na
 * DynamoDB Table.
 */
public class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final Config config;
    private final AmazonDynamoDB dynamoDbClient;
    private final int MAX_REQUESTED_COUNT = 0;

    public Handler() {
        this(AmazonDynamoDBClientBuilder.standard().build(), new ConfigImpl());
    }

    public Handler(AmazonDynamoDB dynamoDbClient, Config config) {
        this.config = config;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            System.out.println(event);
            String tenantId = extractTenantId(event);
            String requestId = extractRequestId(event);
            
            QueryResult result = queryDynamoDB(tenantId, requestId);
            
            Optional<Map<String, AttributeValue>> item = result.getItems().stream()
                    .findAny();
            System.out.println("Processed item: " + item);

            if (item.isEmpty()) {
                return buildNotFoundResponse();
            }

            String queueResult = parseQueueResult(item.get());
            return buildSuccessResponse(queueResult);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, AttributeValue> buildDynamoDBKey(String tenantId, String requestId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("pk", new AttributeValue("TENANT_ID#" + tenantId));
        key.put("sk", new AttributeValue("REQUEST_ID#" + requestId));
        return key;
    }

    private String extractTenantId(APIGatewayV2HTTPEvent event) {
        return event.getHeaders().get("x-tenant-id");
    }

    
    private String extractRequestId(APIGatewayV2HTTPEvent event) {
        return event.getHeaders().get("x-request-id");
    }

    private String parseQueueResult(Map<String, AttributeValue> item) {
        if (item.get("queueResult") == null)
            return null;

        return item.get("queueResult").getS();
    }

    private QueryResult queryDynamoDB(String tenantId, String requestId) {
        final Map<String, AttributeValue> key = buildDynamoDBKey(tenantId, requestId);
        QueryRequest queryRequest = new QueryRequest()
            .withTableName(ConfigImpl.staticBuildTableName(tenantId))
            .withKeyConditionExpression("pk = :pk AND sk = :sk")
            .withExpressionAttributeValues(Map.of(":pk",key.get("pk"),":sk",key.get("sk")));

        System.out.println(queryRequest);

        return dynamoDbClient.query(queryRequest);
    }

    private APIGatewayV2HTTPResponse buildNotFoundResponse() {
        return APIGatewayV2HTTPResponse.builder().withStatusCode(404).build();
    }

    private APIGatewayV2HTTPResponse buildSuccessResponse(String body) throws Exception {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body)
                .build();
    }
}
