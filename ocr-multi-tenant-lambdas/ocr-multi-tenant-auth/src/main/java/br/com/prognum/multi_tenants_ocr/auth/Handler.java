package br.com.prognum.multi_tenants_ocr.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
	private final URI cognitoUrl;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public Handler() {
		this(URI.create(System.getenv("COGNITO_URL")), new ObjectMapper(),
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
	}

	public Handler(URI cognitoUrl, ObjectMapper objectMapper, HttpClient httpClient) {
		this.cognitoUrl = cognitoUrl;
		this.objectMapper = new ObjectMapper();
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	}

	@Override
	public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
		try {
			AuthRequest authRequest = objectMapper.readValue(event.getBody(), AuthRequest.class);
			String accessToken = authenticateWithCognito(authRequest.getClientId(), authRequest.getClientSecret());
			return buildSuccessResponse(accessToken);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String authenticateWithCognito(String clientId, String clientSecret) throws Exception {
		HttpRequest request = buildCognitoRequest(clientId, clientSecret);

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		validateCognitoResponse(response);

		return extractAccessToken(response.body());
	}

	private HttpRequest buildCognitoRequest(String clientId, String clientSecret) {

		return HttpRequest.newBuilder().uri(cognitoUrl).header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(String
						.format("grant_type=client_credentials&client_id=%s&client_secret=%s", clientId, clientSecret)))
				.build();
	}

	private void validateCognitoResponse(HttpResponse<String> response) throws IOException {
		if (response.statusCode() != 200) {
			throw new IOException("Resposta status não-200: " + response.statusCode());
		}
	}

	private String extractAccessToken(String responseBody) throws Exception {
		Map<String, Object> cognitoResponseBody = objectMapper.readValue(responseBody,
				new TypeReference<Map<String, Object>>() {
				});

		return (String) cognitoResponseBody.get("access_token");
	}

	private APIGatewayV2HTTPResponse buildSuccessResponse(String accessToken) throws Exception {

		Map<String, Object> responseBody = new HashMap<>();

		responseBody.put("token", accessToken);

		String body = this.objectMapper.writeValueAsString(responseBody);

		return APIGatewayV2HTTPResponse.builder().withStatusCode(200)
				.withHeaders(Map.of("Content-Type", "application/json")).withBody(body).build();
	}

}