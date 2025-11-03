package br.com.prognum.multi_tenant_ocr.common.utils;

public class QueueUtils {
	public static String buildQueueUrlFromArn(String queueArn) {
		String[] arnParts = queueArn.split(":");

		if (arnParts.length < 6) {
			throw new IllegalArgumentException("Invalid SQS ARN: " + queueArn);
		}

		String region = arnParts[3];
		String accountId = arnParts[4];
		String queueName = arnParts[5];

		return String.format("https://sqs.%s.amazonaws.com/%s/%s", region, accountId, queueName);
	}
}
