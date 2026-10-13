package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecretKey jwtSecretKey(@Value("${app.security.jwt.secret}") String encodedSecret) {
		byte[] secret = Base64.getDecoder().decode(encodedSecret);
		if (secret.length < 32) {
			throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 bytes");
		}
		return new SecretKeySpec(secret, "HmacSHA256");
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		var jwk = new OctetSequenceKey.Builder(jwtSecretKey)
				.algorithm(JWSAlgorithm.HS256)
				.build();
		var jwkSource = new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk));
		return new NimbusJwtEncoder(jwkSource);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(request -> "/".equals(request.getServletPath())).permitAll()
						.requestMatchers("/favicon.ico").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
						.requestMatchers("/api/auth/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(UserRepository userRepository) {
		return username -> userRepository.findByUsername(username)
				.map(user -> User.withUsername(user.getUsername())
						.password(user.getPasswordHash())
						.roles(user.getRole())
						.build())
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}