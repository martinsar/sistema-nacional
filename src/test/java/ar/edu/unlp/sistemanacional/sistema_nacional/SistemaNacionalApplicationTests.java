package ar.edu.unlp.sistemanacional.sistema_nacional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;

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

	private HttpResponse<String> postJson(String path, String body) throws Exception {
		var request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}
}
