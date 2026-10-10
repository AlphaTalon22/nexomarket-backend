package cl.duoc.nexoramarket.ms_rabbit_admin.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import cl.duoc.nexoramarket.ms_rabbit_admin.dto.Respuestas.ErrorResponse;
import jakarta.validation.ConstraintViolationException;

/**
 * Traduce cada error a una respuesta HTTP clara y con el mismo formato JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Cuerpo JSON (o parámetros) que no cumplen las validaciones del DTO. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleBodyInvalido(MethodArgumentNotValidException ex) {
		List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.sorted()
				.toList();
		List<String> globales = ex.getBindingResult().getGlobalErrors().stream()
				.map(MessageSourceResolvable::getDefaultMessage)
				.toList();
		return error(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", concat(detalles, globales));
	}

	/** Parámetros de ruta inválidos, por ejemplo DELETE /queues/amq.x */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleParametroInvalido(HandlerMethodValidationException ex) {
		List<String> detalles = ex.getAllErrors().stream()
				.map(MessageSourceResolvable::getDefaultMessage)
				.toList();
		return error(HttpStatus.BAD_REQUEST, "Parámetro inválido", detalles);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
		List<String> detalles = ex.getConstraintViolations().stream()
				.map(v -> v.getPropertyPath() + ": " + v.getMessage())
				.toList();
		return error(HttpStatus.BAD_REQUEST, "Parámetro inválido", detalles);
	}

	/** JSON mal formado o un valor que no corresponde (ej. tipo de exchange inexistente). */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleJsonIlegible(HttpMessageNotReadableException ex) {
		return error(HttpStatus.BAD_REQUEST,
				"El cuerpo de la petición no es un JSON válido o tiene un valor no permitido "
						+ "(tipo de exchange: DIRECT, TOPIC o FANOUT)",
				List.of());
	}

	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<ErrorResponse> handleNoEncontrado(RecursoNoEncontradoException ex) {
		return error(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
	}

	@ExceptionHandler(RecursoYaExisteException.class)
	public ResponseEntity<ErrorResponse> handleYaExiste(RecursoYaExisteException ex) {
		return error(HttpStatus.CONFLICT, ex.getMessage(), List.of());
	}

	@ExceptionHandler(AmqpConnectException.class)
	public ResponseEntity<ErrorResponse> handleBrokerCaido(AmqpConnectException ex) {
		log.error("[ADMIN] No se pudo conectar a RabbitMQ: {}", ex.getMessage());
		return error(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ no está disponible en este momento", List.of());
	}

	@ExceptionHandler(AmqpException.class)
	public ResponseEntity<ErrorResponse> handleAmqp(AmqpException ex) {
		log.error("[ADMIN] RabbitMQ rechazó la operación: {}", ex.getMessage());
		return error(HttpStatus.BAD_GATEWAY, "RabbitMQ rechazó la operación: " + ex.getMessage(), List.of());
	}

	private ResponseEntity<ErrorResponse> error(HttpStatus status, String mensaje, List<String> detalles) {
		return ResponseEntity.status(status)
				.body(ErrorResponse.of(status.value(), status.getReasonPhrase(), mensaje, detalles));
	}

	private List<String> concat(List<String> a, List<String> b) {
		return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
	}
}
