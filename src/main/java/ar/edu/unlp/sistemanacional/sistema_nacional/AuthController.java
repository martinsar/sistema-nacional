package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final JwtEncoder jwtEncoder;
	private final AuthService authService;

	public AuthController(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder, AuthService authService) {
		this.authenticationManager = authenticationManager;
		this.jwtEncoder = jwtEncoder;
		this.authService = authService;
	}

	@PostMapping("/register")
	@Operation(summary = "Registrar usuario", description = "No requiere token. Envía un JSON con username y password (mínimo 8 caracteres), por ejemplo {\"username\":\"ayudante\",\"password\":\"clave-segura-123\"}. Registra el usuario para que luego pueda iniciar sesión en /api/auth/login.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Usuario registrado.",
					content = @Content(schema = @Schema(implementation = UserResponse.class))),
			@ApiResponse(responseCode = "400", description = "El nombre de usuario o la contraseña no cumplen los requisitos.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese nombre.",
					content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
	})
	public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
		authService.register(request.username(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(request.username().trim()));
	}

	@PostMapping("/login")
	@Operation(summary = "Iniciar sesión", description = "No requiere token. El cuerpo debe incluir username y password. Ejemplo: {\"username\":\"ayudante\",\"password\":\"clave-segura-123\"}. La respuesta contiene accessToken, que debe enviarse en las solicitudes protegidas como Authorization: Bearer <accessToken> o configurarse mediante el botón Authorize de Swagger. El token vence en una hora.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Autenticación exitosa.",
					content = @Content(schema = @Schema(implementation = TokenResponse.class))),
			@ApiResponse(responseCode = "401", description = "Credenciales incorrectas o incompletas.")
	})
	public TokenResponse login(@RequestBody LoginRequest request) {
		if (request.username() == null || request.password() == null) {
			throw new BadCredentialsException("Username and password are required");
		}

		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(1, ChronoUnit.HOURS);
		var claims = JwtClaimsSet.builder()
				.issuer("sistema-nacional")
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.subject(authentication.getName())
				.build();

		var headers = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
		return new TokenResponse(token, "Bearer", ChronoUnit.SECONDS.between(issuedAt, expiresAt));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Void> handleAuthenticationFailure() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	public record LoginRequest(String username, String password) {
	}

	public record RegisterRequest(String username, String password) {
	}

	public record UserResponse(String username) {
	}

	public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
	}
}