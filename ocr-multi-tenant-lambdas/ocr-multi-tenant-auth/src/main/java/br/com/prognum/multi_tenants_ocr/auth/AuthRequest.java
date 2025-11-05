package br.com.prognum.multi_tenants_ocr.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa a requisição de autenticação.
 */
public class AuthRequest {


	@JsonProperty("client_id")
    private String clientId;
	  
    @JsonProperty("client_secret")
    private String clientSecret;

    public AuthRequest() {
    }

    public AuthRequest(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

	@Override
	public String toString() {
		return "AuthRequest [clientId=" + clientId + ", clientSecret=" + clientSecret + "]";
	}
}