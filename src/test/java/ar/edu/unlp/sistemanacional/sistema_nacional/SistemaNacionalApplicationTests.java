package ar.edu.unlp.sistemanacional.sistema_nacional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"spring.datasource.url=jdbc:h2:mem:sistema;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class SistemaNacionalApplicationTests {
	@LocalServerPort
	private int port;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Autowired
	private JwtDecoder jwtDecoder;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void contextLoads() {
	}

	@Test
	void rootIsPublicAndReportsApiStatus() throws Exception {
		var request = HttpRequest.newBuilder(uri("/")).GET().build();
		var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"status\":\"running\""));
	}

	@Test
	void swaggerUiAndOpenApiDocumentArePublic() throws Exception {
		var uiRequest = HttpRequest.newBuilder(uri("/swagger-ui/index.html")).GET().build();
		var uiResponse = httpClient.send(uiRequest, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, uiResponse.statusCode());

		var docsRequest = HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build();
		var docsResponse = httpClient.send(docsRequest, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, docsResponse.statusCode());
		assertTrue(docsResponse.body().contains("bearerAuth"));
		assertTrue(docsResponse.body().contains("\"codigo_ong\""));
		assertTrue(docsResponse.body().contains("/api/recursos/comprometer"));
	}

	@Test
	void swaggerDescriptionsExplainHowToUseEveryService() throws Exception {
		var request = HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build();
		var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		var paths = objectMapper.readTree(response.body()).path("paths");

		assertTrue(paths.path("/api/auth/register").path("post").path("description").asText()
				.contains("username y password"));
		assertTrue(paths.path("/api/auth/login").path("post").path("description").asText()
				.contains("Authorization: Bearer"));
		assertTrue(paths.path("/api/ongs").path("post").path("description").asText()
				.contains("codigo_ong"));
		assertTrue(paths.path("/api/ongs/codigo/{codigo_ong}").path("get").path("description").asText()
				.contains("identificador compartido"));
		assertTrue(paths.path("/api/ongs/{id}").path("get").path("description").asText()
				.contains("/api/ongs/3"));
		assertTrue(paths.path("/api/ongs/codigo/{codigo_ong}").path("put").path("description").asText()
				.contains("APP-ONG-123"));
		assertTrue(paths.path("/api/ongs/codigo/{codigo_ong}").path("delete").path("description").asText()
				.contains("APP-ONG-123"));
		assertTrue(paths.path("/api/recursos").path("post").path("description").asText()
				.contains("cantidadTotal"));
		assertTrue(paths.path("/api/ofertas/validar").path("post").path("description").asText()
				.contains("habilitado_flag"));
		assertTrue(paths.path("/api/recursos/comprometer").path("post").path("description").asText()
				.contains("operación es atómica"));
		assertTrue(paths.path("/api/convocatorias/{id}/recursos/liberar").path("post").path("description").asText()
				.contains("/api/convocatorias/10/recursos/liberar"));
		assertTrue(paths.path("/api/recursos/{id}").path("get").path("description").asText()
				.contains("/api/recursos/8"));
		assertTrue(paths.path("/api/recursos/{id}").path("put").path("description").asText()
				.contains("/api/recursos/8"));
		assertTrue(paths.path("/api/recursos/{id}").path("delete").path("description").asText()
				.contains("/api/recursos/8"));

		paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(operation ->
				assertTrue(!operation.getValue().path("description").asText().isBlank(),
						"Falta describir cómo usar " + path.getKey() + " " + operation.getKey())));
	}

	@Test
	void registeredUserCanLoginAndAccessProtectedService() throws Exception {
		String username = "test-" + UUID.randomUUID();
		String password = "correct-password";
		String credentials = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
		var registration = postJson("/api/auth/register", credentials);
		assertEquals(201, registration.statusCode());

		String wrongCredentials = "{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}";
		var rejectedLogin = postJson("/api/auth/login", wrongCredentials);
		assertEquals(401, rejectedLogin.statusCode());

		var login = postJson("/api/auth/login", credentials);
		assertEquals(200, login.statusCode());
		var tokenMatcher = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(login.body());
		assertTrue(tokenMatcher.find());
		String accessToken = tokenMatcher.group(1);

		var unauthorizedRequest = HttpRequest.newBuilder(uri("/api/test/protected")).GET().build();
		var unauthorized = httpClient.send(unauthorizedRequest, HttpResponse.BodyHandlers.ofString());
		assertEquals(401, unauthorized.statusCode());

		var authorizedRequest = HttpRequest.newBuilder(uri("/api/test/protected"))
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		var protectedResponse = httpClient.send(authorizedRequest, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, protectedResponse.statusCode());
		assertTrue(protectedResponse.body().contains("\"username\":\"" + username + "\""));
		assertEquals(username, jwtDecoder.decode(accessToken).getSubject());
	}

	@Test
	void stableOngCodeIsUsedAcrossProtectedIntegrationEndpoints() throws Exception {
		String username = "integration-" + UUID.randomUUID();
		String password = "correct-password";
		var registration = postJson("/api/auth/register",
				"{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
		assertEquals(201, registration.statusCode());

		var login = postJson("/api/auth/login",
				"{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
		assertEquals(200, login.statusCode());
		var tokenMatcher = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(login.body());
		assertTrue(tokenMatcher.find());
		String accessToken = tokenMatcher.group(1);

		String ongName = "ONG integración " + UUID.randomUUID();
		String ongCode = "APP-ONG-" + UUID.randomUUID();
		var ongResponse = postJsonWithBearer("/api/ongs",
				"{\"codigo_ong\":\"" + ongCode + "\",\"nombre\":\"" + ongName
						+ "\",\"tiposRecursos\":[\"Alimentos\"]}",
				accessToken);
		assertEquals(201, ongResponse.statusCode());
		assertTrue(ongResponse.body().contains("\"codigo_ong\":\"" + ongCode + "\""));
		var lookupByCode = HttpRequest.newBuilder(uri("/api/ongs/codigo/" + ongCode))
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		var lookupResponse = httpClient.send(lookupByCode, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, lookupResponse.statusCode());
		assertTrue(lookupResponse.body().contains("\"codigo_ong\":\"" + ongCode + "\""));

		var updateByCode = HttpRequest.newBuilder(uri("/api/ongs/codigo/" + ongCode))
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(
						"{\"nombre\":\"ONG actualizada\",\"tiposRecursos\":[\"Alimentos\"]}"))
				.build();
		var updateResponse = httpClient.send(updateByCode, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, updateResponse.statusCode());
		assertTrue(updateResponse.body().contains("\"codigo_ong\":\"" + ongCode + "\""));

		var resourceResponse = postJsonWithBearer("/api/recursos",
				"{\"tipo\":\"Alimentos\",\"cantidadTotal\":5,\"cantidadBloqueada\":0,"
						+ "\"codigo_ong\":\"" + ongCode + "\"}",
				accessToken);
		assertEquals(201, resourceResponse.statusCode());
		assertTrue(resourceResponse.body().contains("\"codigo_ong\":\"" + ongCode + "\""));

		var offerResponse = postJsonWithBearer("/api/ofertas/validar",
				"[{\"codigo_ong\":\"" + ongCode
						+ "\",\"recurso_tipo\":\"Alimentos\",\"recurso_cantidad\":2}]",
				accessToken);
		assertEquals(200, offerResponse.statusCode());
		assertTrue(offerResponse.body().contains("\"habilitado_flag\":true"));

		var commitmentResponse = postJsonWithBearer("/api/recursos/comprometer",
				"{\"tipo_recurso\":\"Alimentos\",\"codigo_ong\":\"" + ongCode
						+ "\",\"cantidad\":2,\"id_convocatoria\":98712}",
				accessToken);
		assertEquals(200, commitmentResponse.statusCode());
		assertTrue(commitmentResponse.body().contains("\"codigo_ong\":\"" + ongCode + "\""));
	}

	private HttpResponse<String> postJson(String path, String body) throws Exception {
		var request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> postJsonWithBearer(String path, String body, String accessToken) throws Exception {
		var request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + accessToken)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}
}
