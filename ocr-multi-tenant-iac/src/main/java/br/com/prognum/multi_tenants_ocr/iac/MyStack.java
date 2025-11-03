package br.com.prognum.multi_tenants_ocr.iac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.aws_apigatewayv2_authorizers.HttpUserPoolAuthorizer;
import software.amazon.awscdk.aws_apigatewayv2_integrations.HttpLambdaIntegration;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.CorsHttpMethod;
import software.amazon.awscdk.services.apigatewayv2.CorsPreflightOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;
import software.amazon.awscdk.services.cognito.CognitoDomainOptions;
import software.amazon.awscdk.services.cognito.OAuthFlows;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.ResourceServerScope;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolDomainOptions;
import software.amazon.awscdk.services.cognito.UserPoolResourceServer;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueEncryption;
import software.constructs.Construct;

public class MyStack extends Stack {

    private static int INPUT_VISIBILITY_TIMEOUT_IN_SECS = 300;
    private static final int OUTPUT_VISIBILITY_TIMEOUT_IN_SECS = 300;
    private static final String BUCKET_NAME_TEMPLATE = "BUCKET_NAME_TEMPLATE";
    private static final String TABLE_NAME_TEMPLATE = "TABLE_NAME_TEMPLATE";
    private static final String OUTPUT_QUEUE_URL_TEMPLATE = "OUTPUT_QUEUE_URL_TEMPLATE";
    private static final String INPUT_QUEUE_URL_TEMPLATE = "INPUT_QUEUE_URL_TEMPLATE";
    private static final String COGNITO_URL = "COGNITO_URL";

    public MyStack(Construct scope, String system, String environment, Map<String, Object> envConfig,
            StackProps props) {
        super(scope, String.format("%s-%s-stack", system, environment), props);

        Tags.of(this).add("Environment", environment);
        Tags.of(this).add("System", system);

        UserPool userPool = UserPool.Builder.create(this, String.format("%s-%s-user-pool", system, environment))
                .removalPolicy(RemovalPolicy.RETAIN)
                .userPoolName(String.format("%s-%s-user-pool", system, environment))
                .build();
        String domainPrefix = (String) envConfig.get("domainPrefix");
        userPool.addDomain(String.format("%s-%s-user-pool-domain", system, environment),
                UserPoolDomainOptions.builder()
                        .cognitoDomain(CognitoDomainOptions.builder().domainPrefix(domainPrefix).build())
                        .build());

        String cognitoUrl = String
                .format("https://%s.auth.%s.amazoncognito.com/oauth2/token", domainPrefix, this.getRegion());

        ResourceServerScope resourceServerScope = ResourceServerScope.Builder.create()
                .scopeDescription("ocr")
                .scopeName("ocr")
                .build();

        UserPoolResourceServer userResourceServer = UserPoolResourceServer.Builder
                .create(this, String.format("%s-%s-user-pool-resource-server", system, environment))
                .userPoolResourceServerName(String.format("%s-%s-user-pool-resource-server", system, environment))
                .identifier("prognum")
                .userPool(userPool)
                .scopes(List.of(resourceServerScope))
                .build();

        String bucketNameTemplate = String.format("%s-%s-bucket-<tenantId>", system, environment.toLowerCase());
        String tableNameTemplate = String.format("%s-%s-table-<tenantId>", system, environment);
        String inputQueueNameTemplate = String.format("%s-%s-queue-input-<tenantId>", system, environment);
        String inputQueueUrlTemplate = QueueUtils.buildQueueUrlFromNameTemplate(getAccount(), getRegion(), inputQueueNameTemplate);
        String outputQueueNameTemplate = String.format("%s-%s-queue-output-<tenantId>", system, environment);
        String outputQueueUrlTemplate = QueueUtils.buildQueueUrlFromNameTemplate(getAccount(), getRegion(), outputQueueNameTemplate);

        String authFunctionName = String.format("%s-%s-auth-function", system, environment);
        Function authFunction = Function.Builder.create(this, authFunctionName)
                .functionName(authFunctionName)
                .code(getLambdaCode("ocr-multi-tenant-auth"))
                .handler("br.com.prognum.multi_tenants_ocr.auth.Handler::handleRequest")
                .runtime(Runtime.JAVA_17)
                .memorySize(512)
                .timeout(Duration.seconds(30))
                .environment(Map.of(COGNITO_URL, cognitoUrl))
                .build();

        String fromApiFunctionName = String.format("%s-%s-from-api-function", system, environment);
        Function fromApiFunction = Function.Builder.create(this, fromApiFunctionName)
                .functionName(fromApiFunctionName)
                .code(getLambdaCode("ocr-multi-tenant-from-api"))
                .handler("br.com.prognum.multi_tenants_ocr.from_api.Handler::handleRequest")
                .runtime(Runtime.JAVA_17)
                .memorySize(512)
                .timeout(Duration.seconds(30))
                .environment(Map.of(BUCKET_NAME_TEMPLATE,
                        bucketNameTemplate,
                        TABLE_NAME_TEMPLATE,
                        tableNameTemplate,
                        INPUT_QUEUE_URL_TEMPLATE,
                        inputQueueUrlTemplate,
                        OUTPUT_QUEUE_URL_TEMPLATE,
                        outputQueueUrlTemplate))
                .build();

        String toApiFunctionName = String.format("%s-%s-to-api-function", system, environment);
        Function toApiFunction = Function.Builder.create(this, toApiFunctionName)
                .functionName(toApiFunctionName)
                .code(getLambdaCode("ocr-multi-tenant-to-api"))
                .handler("br.com.prognum.multi_tenants_ocr.to_api.Handler::handleRequest")
                .runtime(Runtime.JAVA_17)
                .memorySize(512)
                .timeout(Duration.seconds(30))
                .environment(Map.of(TABLE_NAME_TEMPLATE, tableNameTemplate))
                .build();

        String fromQueueToTableFunctionName = String.format("%s-%s-from-queue-to-table-function", system, environment);
        Function fromQueueToTableFunction = Function.Builder.create(this, fromQueueToTableFunctionName)
                .functionName(fromQueueToTableFunctionName)
                .code(getLambdaCode("ocr-multi-tenant-process-queue"))
                .handler("br.com.prognum.multi_tenants_ocr.process_queue.Handler::handleRequest")
                .runtime(Runtime.JAVA_17)
                .memorySize(512)
                .timeout(Duration.seconds(30))
                .environment(Map.of(TABLE_NAME_TEMPLATE, tableNameTemplate))
                .build();


        @SuppressWarnings("unchecked") 
        List<String> tenantIds = (List<String>) envConfig.get("tenantIds");
        
        List<UserPoolClient> userPoolClients = new ArrayList<UserPoolClient>();
        List<Bucket> buckets = new ArrayList<Bucket>();
        for (int i = 0; i < tenantIds.size(); i++) {
            String tenantId = tenantIds.get(i);
            UserPoolClient userPoolClient = UserPoolClient.Builder
                    .create(this, String.format("%s-%s-user-pool-client-%s", system, environment, tenantId))
                    .userPoolClientName(String.format("%s-%s-user-pool-client-%s", system, environment, tenantId))
                    .userPool(userPool)
                    .generateSecret(true)
                    .enableTokenRevocation(true)
                    .oAuth(OAuthSettings.builder()
                            .flows(OAuthFlows.builder().clientCredentials(true).build())
                            .scopes(List.of(OAuthScope.resourceServer(userResourceServer, resourceServerScope)))
                            .build())
                    .build();

            userPoolClients.add(userPoolClient);

            Secret.Builder.create(this, String.format("%s-%s-secret-%s", system, environment, tenantId))
                    .secretName(String.format("%s-%s-secret-%s", system, environment, tenantId))
                    .secretObjectValue(Map.of("clientId",
                            SecretValue.unsafePlainText(userPoolClient.getUserPoolClientId()),
                            "clientSecret",
                            userPoolClient.getUserPoolClientSecret()))
                    .build();

            Key key = Key.Builder.create(this, String.format("%s-%s-key-%s", system, environment, tenantId))
                    .alias(String.format("%s-%s-key-%s", system, environment, tenantId))
                    .build();

            String tableId = String.format("%s-%s-table-%s", system, environment, tenantId);
            Table table = Table.Builder.create(this, tableId)
                    .tableName(tableId)
                    .partitionKey(Attribute.builder().name("pk").type(AttributeType.STRING).build())
                    .sortKey(Attribute.builder().name("sk").type(AttributeType.STRING).build())
                    .encryptionKey(key)
                    .removalPolicy(RemovalPolicy.RETAIN).deletionProtection(true)
                    .build();

            String bucketId = String.format("%s-%s-bucket-%s", system, environment.toLowerCase(), tenantId);
            Bucket bucket = Bucket.Builder.create(this, bucketId)
                    .bucketName(bucketId)
                    .removalPolicy(RemovalPolicy.RETAIN)
                    .encryptionKey(key)
                    .build();

            String inputQueueId = String.format("%s-%s-queue-input-%s.fifo", system, environment, tenantId);
            Queue inputQueue = Queue.Builder.create(this, inputQueueId)
                    .queueName(inputQueueId)
                    .encryption(QueueEncryption.KMS_MANAGED)
                    .visibilityTimeout(Duration.seconds(INPUT_VISIBILITY_TIMEOUT_IN_SECS))
                    .fifo(true)
                    .build();

            String outputQueueId = String.format("%s-%s-queue-output-%s.fifo", system, environment, tenantId);
            Queue outputQueue = Queue.Builder.create(this, outputQueueId)
                    .queueName(outputQueueId)
                    .encryption(QueueEncryption.KMS_MANAGED)
                    .visibilityTimeout(Duration.seconds(OUTPUT_VISIBILITY_TIMEOUT_IN_SECS))
                    .fifo(true)
                    .build();

            buckets.add(bucket);
            bucket.grantWrite(fromApiFunction);
            table.grantWriteData(fromApiFunction);

            table.grantFullAccess(toApiFunction);
            table.grantWriteData(fromQueueToTableFunction);
            
            fromQueueToTableFunction.addEventSource(
                    SqsEventSource.Builder.create(outputQueue).batchSize(10).reportBatchItemFailures(true).build());
            inputQueue.grantSendMessages(fromApiFunction);
        }

        HttpUserPoolAuthorizer httpUserPoolAuthorizer = HttpUserPoolAuthorizer.Builder
                .create(String.format("%s-%s-http-user-pool-authorizer", system, environment), userPool)
                .authorizerName(String.format("%s-%s-http-user-pool-authorizer", system, environment))
                .userPoolClients(userPoolClients)
                .build();

        HttpApi httpApi = HttpApi.Builder.create(this, String.format("%s-%s-api", system, environment))
                .description("API para processamento OCR multi-tenant")
                .apiName(String.format("%s-%s-api", system, environment))
                .corsPreflight(CorsPreflightOptions.builder()
                        .allowOrigins(Arrays.asList("*"))
                        .allowMethods(Arrays.asList(CorsHttpMethod.GET, CorsHttpMethod.POST))
                        .allowHeaders(Arrays.asList("Content-Type", "x-tenant-id", "x-request-id"))
                        .build())
                .build();

        HttpLambdaIntegration authIntegration = HttpLambdaIntegration.Builder.create("AuthIntegration", authFunction)
                .build();

        HttpLambdaIntegration fromApiIntegration = HttpLambdaIntegration.Builder
                .create("FromApiIntegration", fromApiFunction)
                .build();

        HttpLambdaIntegration toApiIntegration = HttpLambdaIntegration.Builder.create("ToApiIntegration", toApiFunction)
                .build();

        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/auth/token")
                .methods(Arrays.asList(HttpMethod.POST))
                .integration(authIntegration)
                .build());

        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/documents")
                .methods(Arrays.asList(HttpMethod.POST))
                .integration(fromApiIntegration)
                .authorizer(httpUserPoolAuthorizer)
                .build());

        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/documents")
                .methods(Arrays.asList(HttpMethod.GET))
                .integration(toApiIntegration)
                .authorizer(httpUserPoolAuthorizer)
                .build());

        CfnOutput.Builder.create(this, String.format("%s-%s-api-url-output", system, environment))
                .value(httpApi.getApiEndpoint())
                .description("HTTP API endpoint URL")
                .exportName(String.format("%s-%s-api-url-output", system, environment))
                .build();

        CfnOutput.Builder.create(this, String.format("%s-%s-cognito-url-output", system, environment))
                .value(cognitoUrl)
                .description("Cognito URL")
                .exportName(String.format("%s-%s-cognito-url-output", system, environment))
                .build();
    }

    private static Code getLambdaCode(String lambdaName) {
        return Code.fromAsset(String.format("../ocr-multi-tenant-lambdas/%1$s/target/%1$s-lambda.jar", lambdaName));
    }
}
