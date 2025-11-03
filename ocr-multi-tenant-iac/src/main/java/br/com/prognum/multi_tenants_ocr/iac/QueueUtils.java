package br.com.prognum.multi_tenants_ocr.iac;

public class QueueUtils {
	public static String buildQueueUrlFromNameTemplate(String accountId, String region, String queueNameTemplate) {
		return String.format("https://sqs.%s.amazonaws.com/%s/%s", region, accountId, queueNameTemplate);
	}
}
