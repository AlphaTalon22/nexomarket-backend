package cl.duoc.nexoramarket.ms_notificaciones.common;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Valida el contenido de un mensaje con las anotaciones de Bean Validation de su record.
 * Si algo no cumple, lanza {@link MensajeInvalidoException} (error no recuperable → DLQ).
 */
@Component
public class ValidadorMensajes {

	private final Validator validator;

	public ValidadorMensajes(Validator validator) {
		this.validator = validator;
	}

	public <T> void validar(T mensaje) {
		if (mensaje == null) {
			throw new MensajeInvalidoException("El mensaje llegó vacío");
		}
		Set<ConstraintViolation<T>> violaciones = validator.validate(mensaje);
		if (!violaciones.isEmpty()) {
			String detalle = violaciones.stream()
					.map(v -> v.getPropertyPath() + " " + v.getMessage())
					.sorted()
					.collect(Collectors.joining(", "));
			throw new MensajeInvalidoException(
					mensaje.getClass().getSimpleName() + " inválido: " + detalle);
		}
	}
}
