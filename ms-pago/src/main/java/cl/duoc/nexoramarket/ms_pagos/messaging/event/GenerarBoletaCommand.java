package cl.duoc.nexoramarket.ms_pagos.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

import cl.duoc.nexoramarket.ms_pagos.model.Pago;

/**
 * Comando "genera la boleta de este pago", enviado por {@code nexora.documentos.direct}
 * con routing key {@code boleta.generar}. Lo consume ms-notificaciones.
 */
public record GenerarBoletaCommand(
		String comandoId,
		Long pagoId,
		Long pedidoId,
		Double monto,
		String metodoPago,
		LocalDateTime fechaPago) {

	public static GenerarBoletaCommand desdePago(Pago pago) {
		return new GenerarBoletaCommand(UUID.randomUUID().toString(), pago.getId(), pago.getPedidoId(),
				pago.getMonto(), pago.getMetodoPago(), pago.getFechaPago());
	}
}
