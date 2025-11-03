package br.com.prognum.multi_tenant_ocr.common.utils;

public interface Config {
    public String buildTableName(String tenantId);

    public String buildBucketName(String tenantId);

    public String buildInputQueueUrl(String tenantId);

    public String buildOutputQueueUrl(String tenantId);
}
