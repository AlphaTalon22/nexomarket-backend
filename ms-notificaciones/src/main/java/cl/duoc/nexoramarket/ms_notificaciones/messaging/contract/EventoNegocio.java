package cl.duoc.nexoramarket.ms_notificaciones.messaging.contract;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Evento de dominio publicado por ms-pedidos y ms-pago en el exchange TOPIC
 * {@code nexora.eventos.topic}.
 * <p>
 * Es un contrato JSON: ms-pedidos y ms-pago tienen su propio record con los mismos campos.
 * Tipos posibles: {@code pedido.creado}, {@code pago.aprobado}, {@code pago.rechazado}.
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
}
