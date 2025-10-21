package br.com.prognum.multi_tenant_ocr.common.Utils;

public interface Config {
    public String buildTableName(String tenantId);

    public String buildBucketName(String tenantId);

    public String getInputQueueUrl();

    public String getOutputQueueUrl();
}
