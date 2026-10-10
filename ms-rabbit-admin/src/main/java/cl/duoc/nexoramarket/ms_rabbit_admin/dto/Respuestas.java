package cl.duoc.nexoramarket.ms_rabbit_admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuestas JSON de la API de administración.
 */
public final class Respuestas {

	private Respuestas() {
	}

	/** Estado actual de una cola en el broker. */
	public record ColaResponse(String nombre, long mensajes, int consumidores) {
	}

	public record ExchangeResponse(String nombre, TipoExchange tipo, boolean durable) {
	}

	public record BindingResponse(String cola, String exchange, String routingKey) {
	}

	/** Error con formato uniforme para toda la API. */
	public record ErrorResponse(LocalDateTime timestamp, int status, String error, String mensaje,
			List<String> detalles) {

		public static ErrorResponse of(int status, String error, String mensaje, List<String> detalles) {
			return new ErrorResponse(LocalDateTime.now(), status, error, mensaje, detalles);
		}
	}
}
