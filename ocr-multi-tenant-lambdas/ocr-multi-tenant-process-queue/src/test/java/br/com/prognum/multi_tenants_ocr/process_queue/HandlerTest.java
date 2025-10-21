package br.com.prognum.multi_tenants_ocr.process_queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.MessageAttribute;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.prognum.multi_tenant_ocr.common.Utils.Config;

@ExtendWith(MockitoExtension.class)
@DisplayName("Handler")
class HandlerTest {

    @Mock
    private AmazonDynamoDB dynamoDbClient;
    
    @Mock
    private Context context;
    
    @Mock
    private LambdaLogger lambdaLogger;
    
    @Mock
    private Config config;
    
    private ObjectMapper objectMapper;
    private Handler handler;
    
    private static final String TENANT_ID = "70002320000166";
    private static final String REQUEST_ID = "req-456";
    private static final String MESSAGE_ID = "msg-123";
    private static final String TABLE_NAME = "integration-ocr-prognum-table-70002320000166";
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new Handler(dynamoDbClient, config);
        when(context.getLogger()).thenReturn(lambdaLogger);
    }
    
    @Nested
    @DisplayName("Processamento bem sucedido de mensagem única")
    class SingleMessageSuccessTests {
        
        private SQSEvent event;
        private SQSMessage message;
        private SQSBatchResponse response;
        
        @BeforeEach
        void setUpSingleMessage() throws Exception {
            event = createSQSEvent();
            message = createSQSMessage(
                MESSAGE_ID,
                TENANT_ID,
                REQUEST_ID,
                createMessageBody(TENANT_ID, REQUEST_ID, "OCR Result")
            );
            event.setRecords(Collections.singletonList(message));
            
            when(config.buildTableName(TENANT_ID)).thenReturn(TABLE_NAME);
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(new UpdateItemResult());
            
            response = handler.handleRequest(event, context);
        }
        
        @Test
        @DisplayName("deve retornar response não nulo")
        void shouldReturnNonNullResponse() {
            assertNotNull(response);
        }
        
        @Test
        @DisplayName("deve retornar lista vazia de falhas")
        void shouldReturnEmptyFailuresList() {
            //criar um método para isso
            SQSBatchResponse successResponse = new SQSBatchResponse();
            successResponse.setBatchItemFailures(Collections.emptyList());
            assertEquals(successResponse, response);
        }
        
        @Test
        @DisplayName("deve chamar updateItem uma vez")
        void shouldCallUpdateItemOnce() {
            verify(dynamoDbClient, times(1)).updateItem(any(UpdateItemRequest.class));
        }
        
        @Nested
        @DisplayName("UpdateItemRequest")
        class UpdateItemRequestTests {
            
            private UpdateItemRequest capturedRequest;
            
            @BeforeEach
            void captureRequest() {
                ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
                verify(dynamoDbClient).updateItem(captor.capture());
                capturedRequest = captor.getValue();
            }
            
            @Test
            @DisplayName("deve usar o nome correto da tabela")
            void shouldUseCorrectTableName() {
                assertEquals(TABLE_NAME, capturedRequest.getTableName());
            }
            
            @Test
            @DisplayName("deve ter a partition key correta")
            void shouldHaveCorrectPartitionKey() {
                assertEquals("TENANT_ID#" + TENANT_ID, capturedRequest.getKey().get("pk").getS());
            }
            
            @Test
            @DisplayName("deve ter a sort key correta")
            void shouldHaveCorrectSortKey() {
                assertEquals("REQUEST_ID#" + REQUEST_ID, capturedRequest.getKey().get("sk").getS());
            }
            
            @Test
            @DisplayName("deve ter o status PROCESSED")
            void shouldHaveProcessedStatus() {
                assertEquals("PROCESSED", capturedRequest.getExpressionAttributeValues().get(":status").getS());
            }
            
            @Test
            @DisplayName("deve ter updatedAt definido")
            void shouldHaveUpdatedAt() {
                assertNotNull(capturedRequest.getExpressionAttributeValues().get(":updatedAt").getS());
            }
            
            @Test
            @DisplayName("deve ter queueResult definido")
            void shouldHaveQueueResult() {
                assertNotNull(capturedRequest.getExpressionAttributeValues().get(":queueResult").getS());
            }
            
            @Test
            @DisplayName("deve ter a update expression correta")
            void shouldHaveCorrectUpdateExpression() {
                assertEquals("SET queueResult = :queueResult, #status = :status, updatedAt = :updatedAt", 
                           capturedRequest.getUpdateExpression());
            }
            
            @Test
            @DisplayName("deve ter o attribute name para status")
            void shouldHaveStatusAttributeName() {
                assertEquals("status", capturedRequest.getExpressionAttributeNames().get("#status"));
            }
            
            @Test
            @DisplayName("deve ter a condition expression correta")
            void shouldHaveCorrectConditionExpression() {
                assertEquals("attribute_exists(pk) AND attribute_exists(sk)", 
                           capturedRequest.getConditionExpression());
            }
        }
    }
    
    @Nested
    @DisplayName("Processamento com falha no DynamoDB")
    class DynamoDBFailureTests {
        
        private SQSEvent event;
        private SQSBatchResponse response;
        
        @BeforeEach
        void setUpFailure() throws Exception {
            event = createSQSEvent();
            SQSMessage message = createSQSMessage(
                MESSAGE_ID,
                TENANT_ID,
                REQUEST_ID,
                createMessageBody(TENANT_ID, REQUEST_ID, "OCR Result")
            );
            event.setRecords(Collections.singletonList(message));
            
            when(config.buildTableName(TENANT_ID)).thenReturn(TABLE_NAME);
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(new RuntimeException("DynamoDB error"));
            
            response = handler.handleRequest(event, context);
        }
        
        @Test
        @DisplayName("deve retornar response não nulo")
        void shouldReturnNonNullResponse() {
            assertNotNull(response);
        }
        
        @Test
        @DisplayName("deve ter uma falha na lista de falhas")
        void shouldHaveOneFailure() {
            assertEquals(1, response.getBatchItemFailures().size());
        }
        
        @Test
        @DisplayName("deve identificar corretamente a mensagem que falhou")
        void shouldIdentifyFailedMessage() {
            assertEquals(MESSAGE_ID, response.getBatchItemFailures().get(0).getItemIdentifier());
        }
    }
    
    @Nested
    @DisplayName("Processamento de múltiplas mensagens")
    class MultipleMessagesTests {
        
        private SQSEvent event;
        private SQSBatchResponse response;
        
        @BeforeEach
        void setUpMultipleMessages() throws Exception {
            event = createSQSEvent();
            
            SQSMessage message1 = createSQSMessage(
                "msg-1", TENANT_ID, "req-1",
                createMessageBody(TENANT_ID, "req-1", "Result 1")
            );
            
            SQSMessage message2 = createSQSMessage(
                "msg-2", TENANT_ID, "req-2",
                createMessageBody(TENANT_ID, "req-2", "Result 2")
            );
            
            SQSMessage message3 = createSQSMessage(
                "msg-3", "99426230000184", "req-3",
                createMessageBody("99426230000184", "req-3", "Result 3")
            );
            
            event.setRecords(Arrays.asList(message1, message2, message3));
            
            when(config.buildTableName(TENANT_ID)).thenReturn(TABLE_NAME);
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(new UpdateItemResult())
                .thenReturn(new UpdateItemResult())
                .thenThrow(new RuntimeException("DynamoDB error"));
            
            response = handler.handleRequest(event, context);
        }
        
        @Test
        @DisplayName("deve processar todas as mensagens")
        void shouldProcessAllMessages() {
            verify(dynamoDbClient, times(3)).updateItem(any(UpdateItemRequest.class));
        }
        
        @Test
        @DisplayName("deve ter apenas uma falha")
        void shouldHaveOneFailure() {
            assertEquals(1, response.getBatchItemFailures().size());
        }
        
        @Test
        @DisplayName("deve identificar a terceira mensagem como falha")
        void shouldIdentifyThirdMessageAsFailure() {
            assertEquals("msg-3", response.getBatchItemFailures().get(0).getItemIdentifier());
        }
    }
    
    @Nested
    @DisplayName("IDs vindos dos atributos da mensagem")
    class MessageAttributesTests {
        
        private UpdateItemRequest capturedRequest;
        
        @BeforeEach
        void setUpMessageAttributes() throws Exception {
            SQSEvent event = createSQSEvent();
            SQSMessage message = new SQSMessage();
            message.setMessageId(MESSAGE_ID);
            message.setReceiptHandle("receipt-123");
            message.setBody(createMessageBody(null, null, "OCR Result"));
            
            Map<String, MessageAttribute> messageAttributes = new HashMap<>();
            messageAttributes.put("tenantId", createMessageAttribute(TENANT_ID));
            messageAttributes.put("requestId", createMessageAttribute("req-789"));
            message.setMessageAttributes(messageAttributes);
            message.setAttributes(new HashMap<>());
            
            event.setRecords(Collections.singletonList(message));
            
            when(config.buildTableName(TENANT_ID)).thenReturn(TABLE_NAME);
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(new UpdateItemResult());
            
            handler.handleRequest(event, context);
            
            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());
            capturedRequest = captor.getValue();
        }
        
        @Test
        @DisplayName("deve usar tenantId dos atributos da mensagem")
        void shouldUseTenantIdFromMessageAttributes() {
            assertEquals("TENANT_ID#" + TENANT_ID, capturedRequest.getKey().get("pk").getS());
        }
        
        @Test
        @DisplayName("deve usar requestId dos atributos da mensagem")
        void shouldUseRequestIdFromMessageAttributes() {
            assertEquals("REQUEST_ID#req-789", capturedRequest.getKey().get("sk").getS());
        }
    }
    
    @Nested
    @DisplayName("IDs vindos do corpo da mensagem")
    class MessageBodyTests {
        
        private UpdateItemRequest capturedRequest;
        
        @BeforeEach
        void setUpMessageBody() throws Exception {
            SQSEvent event = createSQSEvent();
            SQSMessage message = new SQSMessage();
            message.setMessageId(MESSAGE_ID);
            message.setReceiptHandle("receipt-123");
            message.setBody(createMessageBody(TENANT_ID, "req-999", "OCR Result"));
            message.setMessageAttributes(new HashMap<>());
            message.setAttributes(new HashMap<>());
            
            event.setRecords(Collections.singletonList(message));
            
            when(config.buildTableName(TENANT_ID)).thenReturn(TABLE_NAME);
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(new UpdateItemResult());
            
            handler.handleRequest(event, context);
            
            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());
            capturedRequest = captor.getValue();
        }
        
        @Test
        @DisplayName("deve usar tenantId do corpo da mensagem")
        void shouldUseTenantIdFromMessageBody() {
            assertEquals("TENANT_ID#" + TENANT_ID, capturedRequest.getKey().get("pk").getS());
        }
        
        @Test
        @DisplayName("deve usar requestId do corpo da mensagem")
        void shouldUseRequestIdFromMessageBody() {
            assertEquals("REQUEST_ID#req-999", capturedRequest.getKey().get("sk").getS());
        }
    }
    
    @Nested
    @DisplayName("Mensagem com corpo inválido")
    class InvalidMessageBodyTests {
        
        private SQSBatchResponse response;
        
        @BeforeEach
        void setUpInvalidMessage() {
            SQSEvent event = createSQSEvent();
            SQSMessage message = new SQSMessage();
            message.setMessageId("msg-invalid");
            message.setBody("invalid json");
            message.setMessageAttributes(new HashMap<>());
            message.setAttributes(new HashMap<>());
            
            event.setRecords(Collections.singletonList(message));
            
            response = handler.handleRequest(event, context);
        }
        
        @Test
        @DisplayName("deve retornar response não nulo")
        void shouldReturnNonNullResponse() {
            assertNotNull(response);
        }
        
        @Test
        @DisplayName("deve ter uma falha")
        void shouldHaveOneFailure() {
            assertEquals(1, response.getBatchItemFailures().size());
        }
        
        @Test
        @DisplayName("deve identificar a mensagem inválida")
        void shouldIdentifyInvalidMessage() {
            assertEquals("msg-invalid", response.getBatchItemFailures().get(0).getItemIdentifier());
        }
        
        @Test
        @DisplayName("não deve chamar updateItem")
        void shouldNotCallUpdateItem() {
            verify(dynamoDbClient, never()).updateItem(any(UpdateItemRequest.class));
        }
    }
    
    // Métodos auxiliares
    
    private SQSEvent createSQSEvent() {
        SQSEvent event = new SQSEvent();
        event.setRecords(new ArrayList<>());
        return event;
    }
    
    private SQSMessage createSQSMessage(String messageId, String tenantId, 
                                       String requestId, String body) {
        SQSMessage message = new SQSMessage();
        message.setMessageId(messageId);
        message.setReceiptHandle("receipt-" + messageId);
        message.setBody(body);
        
        Map<String, MessageAttribute> messageAttributes = new HashMap<>();
        if (tenantId != null) {
            messageAttributes.put("tenantId", createMessageAttribute(tenantId));
        }
        if (requestId != null) {
            messageAttributes.put("requestId", createMessageAttribute(requestId));
        }
        message.setMessageAttributes(messageAttributes);
        message.setAttributes(new HashMap<>());
        
        return message;
    }
    
    private MessageAttribute createMessageAttribute(String value) {
        MessageAttribute attr = new MessageAttribute();
        attr.setStringValue(value);
        attr.setDataType("String");
        return attr;
    }
    
    private String createMessageBody(String tenantId, String requestId, 
                                   String result) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (tenantId != null) {
            body.put("tenantId", tenantId);
        }
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        body.put("result", result);
        body.put("timestamp", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        
        return objectMapper.writeValueAsString(body);
    }
}