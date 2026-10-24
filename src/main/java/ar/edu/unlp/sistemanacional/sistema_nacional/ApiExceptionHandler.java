package ar.edu.unlp.sistemanacional.sistema_nacional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

// Se devuelve el código y el mensaje de error en el cuerpo de la respuesta
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
		return ResponseEntity.status(e.getStatusCode())
				.body(new ErrorResponse(e.getStatusCode().value(), e.getReason()));
	}

	public record ErrorResponse(int codigo, String mensaje) {
	}
}
