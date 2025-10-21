package br.com.prognum.multi_tenant_ocr.common.Utils;

public class ConfigImpl implements Config {
    private static final Config instance = new ConfigImpl();
    private static final String TEMPLATE = "<tenantId>";

    public static String staticBuildTableName(String tenantId) {
        return instance.buildTableName(tenantId);
    }

    public static String staticBuildBucketName(String tenantId) {
        return instance.buildBucketName(tenantId);
    }

    public static String staticGetInputQueueUrl() {
        return instance.getInputQueueUrl();
    }

    public static String staticGetOutputQueueUrl() {
        return instance.getOutputQueueUrl();
    }

    public String buildTableName(String tenantId) {
        String tableNameTemplate = System.getenv("TABLE_NAME_TEMPLATE");
        return tableNameTemplate.replace(TEMPLATE, tenantId);
    }

    public String buildBucketName(String tenantId) {
        String bucketNameTemplate = System.getenv("BUCKET_NAME_TEMPLATE");
        return bucketNameTemplate.replace(TEMPLATE, tenantId);
    }

    public String getInputQueueUrl() {
        return System.getenv("INPUT_QUEUE_URL");
    }

    public String getOutputQueueUrl() {
        return System.getenv("OUTPUT_QUEUE_URL");
    }
}
