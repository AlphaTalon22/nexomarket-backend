package cl.duoc.nexoramarket.ms_rabbit_admin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /rabbit-admin/queues}.
 *
 * @param nombre               nombre de la cola (obligatorio)
 * @param durable              si sobrevive a un reinicio del broker (por defecto true)
 * @param deadLetterExchange   exchange al que se envían los mensajes rechazados (opcional)
 * @param deadLetterRoutingKey routing key para la DLQ (opcional, requiere deadLetterExchange)
 * @param ttlMs                tiempo de vida de los mensajes en milisegundos (opcional)
 * @param maxMensajes          máximo de mensajes en la cola (opcional)
 */
public record CrearColaRequest(
		@NotBlank(message = "es obligatorio")
		@Size(min = ReglasNombres.LARGO_MIN, max = ReglasNombres.LARGO_MAX)
		@Pattern(regexp = ReglasNombres.NOMBRE_RECURSO, message = ReglasNombres.NOMBRE_RECURSO_MENSAJE)
		String nombre,

		Boolean durable,

		@Size(min = ReglasNombres.LARGO_MIN, max = ReglasNombres.LARGO_MAX)
		@Pattern(regexp = ReglasNombres.NOMBRE_RECURSO, message = ReglasNombres.NOMBRE_RECURSO_MENSAJE)
		String deadLetterExchange,

		@Size(max = 255)
		@Pattern(regexp = ReglasNombres.ROUTING_KEY, message = ReglasNombres.ROUTING_KEY_MENSAJE)
		String deadLetterRoutingKey,

		@Positive(message = "debe ser mayor que 0")
		Integer ttlMs,

		@Positive(message = "debe ser mayor que 0")
		Long maxMensajes) {

	public CrearColaRequest {
		durable = (durable == null) ? Boolean.TRUE : durable;
	}

	/** No tiene sentido indicar una routing key de DLQ sin decir a qué exchange van los mensajes muertos. */
	@AssertTrue(message = "deadLetterRoutingKey requiere indicar también deadLetterExchange")
	public boolean isDeadLetterConsistente() {
		return deadLetterRoutingKey == null || deadLetterExchange != null;
	}
}
