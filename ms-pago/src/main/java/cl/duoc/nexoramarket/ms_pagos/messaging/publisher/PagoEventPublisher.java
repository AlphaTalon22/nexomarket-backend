package cl.duoc.nexoramarket.ms_pagos.messaging.publisher;

import cl.duoc.nexoramarket.ms_pagos.model.Pago;

/**
 * Lo único que el controlador sabe de la mensajería.
 * Cómo y dónde se publica queda encapsulado en la implementación.
 */
public interface PagoEventPublisher {

	/** Publica pago.aprobado o pago.rechazado según el estado del pago. */
	void publicarResultadoPago(Pago pago);

	/** Pide la generación asíncrona de la boleta del pago. */
	void solicitarBoleta(Pago pago);
}
