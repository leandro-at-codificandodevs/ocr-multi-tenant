package br.com.prognum.multi_tenants_ocr.process_queue;

/**
 * DTO que representa a mensagem recebida da fila OUTPUT_QUEUE com o resultado
 * do processamento OCR.
 */
public class SqsResponse {

	private String tenantId;
	private String requestId;
	private String message;

	public SqsResponse() {
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

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	@Override
	public String toString() {
		return "SqsResponse [tenantId=" + tenantId + ", requestId=" + requestId + ", message=" + message + "]";
	}

}