package br.com.prognum.multi_tenants_ocr.from_api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa a requisição recebida do API Gateway.
 * Contém o conteúdo do documento a ser processado.
 */
public class ApiRequest {
	@JsonProperty("tipoDocumento")
    private String documentType;
    
	private String correlationId;
    
    @JsonProperty("documento")
    private String document; 
    
    public ApiRequest() {
    }
    
    public ApiRequest(String documentType, String correlationId, String document) {
        this.documentType = documentType;
        this.correlationId = correlationId;
        this.document = document;
    }

	public String getDocumentType() {
		return documentType;
	}

	public void setDocumentType(String documentType) {
		this.documentType = documentType;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public void setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
	}

	public String getDocument() {
		return document;
	}

	public void setDocument(String document) {
		this.document = document;
	}
    
    
}