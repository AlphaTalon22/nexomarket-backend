package cl.duoc.nexoramarket.ms_rabbit_admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * La API no debe aceptar nombres vacíos, reservados o configuraciones incoherentes.
 */
class ValidacionDtoTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void init() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void close() {
		factory.close();
	}

	private Set<String> camposInvalidos(Object dto) {
		Set<ConstraintViolation<Object>> violaciones = validator.validate(dto);
		return violaciones.stream().map(v -> v.getPropertyPath().toString())
				.collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void colaValidaNoTieneErrores() {
		assertThat(camposInvalidos(new CrearColaRequest("nexora.demo.q", true, "nexora.dlx.direct", "demo.dead", 1000, 50L)))
				.isEmpty();
	}

	@Test
	void rechazaNombreVacio() {
		assertThat(camposInvalidos(new CrearColaRequest("  ", null, null, null, null, null))).contains("nombre");
	}

	@Test
	void rechazaNombreReservadoAmq() {
		assertThat(camposInvalidos(new CrearColaRequest("amq.prueba", null, null, null, null, null))).contains("nombre");
	}

	@Test
	void rechazaCaracteresNoPermitidos() {
		assertThat(camposInvalidos(new CrearColaRequest("cola con espacios", null, null, null, null, null)))
				.contains("nombre");
	}

	@Test
	void rechazaTtlNegativo() {
		assertThat(camposInvalidos(new CrearColaRequest("nexora.demo.q", null, null, null, -5, null))).contains("ttlMs");
	}

	@Test
	void rechazaRoutingKeyDeDlqSinExchange() {
		assertThat(camposInvalidos(new CrearColaRequest("nexora.demo.q", null, null, "demo.dead", null, null)))
				.contains("deadLetterConsistente");
	}

	@Test
	void exchangeSinTipoEsInvalido() {
		assertThat(camposInvalidos(new CrearExchangeRequest("nexora.demo.x", null, null))).contains("tipo");
	}

	@Test
	void bindingAceptaComodinesDeTopic() {
		assertThat(camposInvalidos(new BindingRequest("nexora.demo.q", "nexora.eventos.topic", "pedido.#"))).isEmpty();
	}
}
