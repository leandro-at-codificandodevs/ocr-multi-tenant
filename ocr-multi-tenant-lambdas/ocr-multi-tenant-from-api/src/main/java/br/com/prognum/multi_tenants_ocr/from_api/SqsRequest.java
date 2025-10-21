package br.com.prognum.multi_tenants_ocr.from_api;

/**
 * DTO que representa a mensagem enviada para a fila SQS INPUT_QUEUE.
 */
public class SqsRequest {
    private String tenantId;
    private String requestId;
    private String correlationId;
    private String documentType;
    private String bucketName;
    private String bucketKey;
    private String outputQueueUrl;
    private String timestamp;
    
    public SqsRequest() {
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
    
    public String getDocumentType() {
        return documentType;
    }
    
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }
    
    public String getBucketName() {
        return bucketName;
    }
    
    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }
    
    public String getBucketKey() {
        return bucketKey;
    }
    
    public void setBucketKey(String bucketKey) {
        this.bucketKey = bucketKey;
    }
    
    public String getOutputQueueUrl() {
        return outputQueueUrl;
    }
    
    public void setOutputQueueUrl(String outputQueueUrl) {
        this.outputQueueUrl = outputQueueUrl;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}