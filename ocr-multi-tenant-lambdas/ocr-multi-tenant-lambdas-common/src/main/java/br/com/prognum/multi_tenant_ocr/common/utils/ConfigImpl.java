package br.com.prognum.multi_tenant_ocr.common.utils;

public class ConfigImpl implements Config {
	private static final Config instance = new ConfigImpl();
	private static final String TEMPLATE = "<tenantId>";

	public static Config getInstance() {
		return instance;
	}

	@Override
	public String buildTableName(String tenantId) {
		String tableNameTemplate = System.getenv("TABLE_NAME_TEMPLATE");
		return tableNameTemplate.replace(TEMPLATE, tenantId);
	}

	@Override
	public String buildBucketName(String tenantId) {
		String bucketNameTemplate = System.getenv("BUCKET_NAME_TEMPLATE");
		return bucketNameTemplate.replace(TEMPLATE, tenantId);
	}

	@Override
	public String buildInputQueueUrl(String tenantId) {
		String inputQueueUrlTemplate = System.getenv("INPUT_QUEUE_URL_TEMPLATE");
		return inputQueueUrlTemplate.replace(TEMPLATE, tenantId);
	}

	@Override
	public String buildOutputQueueUrl(String tenantId) {
		String outputQueueUrlTemplate = System.getenv("OUTPUT_QUEUE_URL_TEMPLATE");
		return outputQueueUrlTemplate.replace(TEMPLATE, tenantId);
	}
}
