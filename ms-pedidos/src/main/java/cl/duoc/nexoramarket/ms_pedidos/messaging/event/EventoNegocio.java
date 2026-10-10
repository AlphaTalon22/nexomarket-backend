package cl.duoc.nexoramarket.ms_pedidos.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

import cl.duoc.nexoramarket.ms_pedidos.model.Pedido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Evento de dominio que viaja como JSON por {@code nexora.eventos.topic}.
 * Mismo contrato que usan ms-pago y ms-notificaciones.
 */
public record EventoNegocio(
		@NotBlank String eventoId,
		@NotBlank String tipo,
		String origen,
		LocalDateTime ocurridoEn,
		@NotNull Long pedidoId,
		Long pagoId,
		Long clienteId,
		Double monto,
		String estado,
		String metodoPago) {

	/** Construye el evento que se publica cuando se crea un pedido. */
	public static EventoNegocio pedidoCreado(String tipo, Pedido pedido) {
		return new EventoNegocio(UUID.randomUUID().toString(), tipo, "ms-pedidos", LocalDateTime.now(),
				pedido.getId(), null, pedido.getClienteId(), pedido.getTotal(), pedido.getEstado(), null);
	}
}
