package cl.duoc.nexoramarket.ms_rabbit_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /rabbit-admin/exchanges}.
 *
 * @param nombre  nombre del exchange (obligatorio)
 * @param tipo    DIRECT, TOPIC o FANOUT (obligatorio)
 * @param durable si sobrevive a un reinicio del broker (por defecto true)
 */
public record CrearExchangeRequest(
		@NotBlank(message = "es obligatorio")
		@Size(min = ReglasNombres.LARGO_MIN, max = ReglasNombres.LARGO_MAX)
		@Pattern(regexp = ReglasNombres.NOMBRE_RECURSO, message = ReglasNombres.NOMBRE_RECURSO_MENSAJE)
		String nombre,

		@NotNull(message = "es obligatorio (DIRECT, TOPIC o FANOUT)")
		TipoExchange tipo,

		Boolean durable) {

	public CrearExchangeRequest {
		durable = (durable == null) ? Boolean.TRUE : durable;
	}
}
