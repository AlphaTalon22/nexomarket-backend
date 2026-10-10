package cl.duoc.nexoramarket.ms_rabbit_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /rabbit-admin/bindings}: enlaza una cola a un exchange.
 *
 * @param cola       nombre de la cola destino
 * @param exchange   nombre del exchange origen
 * @param routingKey clave de enrutamiento (puede usar '*' y '#' en exchanges topic; vacía en fanout)
 */
public record BindingRequest(
		@NotBlank(message = "es obligatorio")
		@Size(min = ReglasNombres.LARGO_MIN, max = ReglasNombres.LARGO_MAX)
		@Pattern(regexp = ReglasNombres.NOMBRE_RECURSO, message = ReglasNombres.NOMBRE_RECURSO_MENSAJE)
		String cola,

		@NotBlank(message = "es obligatorio")
		@Size(min = ReglasNombres.LARGO_MIN, max = ReglasNombres.LARGO_MAX)
		@Pattern(regexp = ReglasNombres.NOMBRE_RECURSO, message = ReglasNombres.NOMBRE_RECURSO_MENSAJE)
		String exchange,

		@NotNull(message = "es obligatorio (puede ir vacío solo para exchanges fanout)")
		@Size(max = 255)
		@Pattern(regexp = ReglasNombres.ROUTING_KEY, message = ReglasNombres.ROUTING_KEY_MENSAJE)
		String routingKey) {
}
