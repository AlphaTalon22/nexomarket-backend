package cl.duoc.nexoramarket.ms_pagos.messaging.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.duoc.nexoramarket.ms_pagos.messaging.config.MessagingProperties;
import cl.duoc.nexoramarket.ms_pagos.messaging.event.EventoNegocio;
import cl.duoc.nexoramarket.ms_pagos.messaging.event.GenerarBoletaCommand;
import cl.duoc.nexoramarket.ms_pagos.model.Pago;

/**
 * Productor de ms-pago.
 * <ul>
 *   <li>{@code pago.aprobado} / {@code pago.rechazado} → exchange TOPIC de eventos.</li>
 *   <li>{@code boleta.generar} → exchange DIRECT de documentos.</li>
 * </ul>
 * Si RabbitMQ no está disponible, el pago igual queda guardado y el fallo queda en el log.
 */
@Component
public class RabbitPagoEventPublisher implements PagoEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(RabbitPagoEventPublisher.class);

	private final RabbitTemplate rabbitTemplate;
	private final MessagingProperties props;

	public RabbitPagoEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
		this.rabbitTemplate = rabbitTemplate;
		this.props = props;
	}

	@Override
	public void publicarResultadoPago(Pago pago) {
		String routingKey = "APROBADO".equalsIgnoreCase(pago.getEstado())
				? props.routingKeys().pagoAprobado()
				: props.routingKeys().pagoRechazado();
		EventoNegocio evento = EventoNegocio.desdePago(routingKey, pago);
		publicar(props.exchanges().eventos(), routingKey, evento, evento.eventoId(), pago);
	}

	@Override
	public void solicitarBoleta(Pago pago) {
		GenerarBoletaCommand comando = GenerarBoletaCommand.desdePago(pago);
		publicar(props.exchanges().documentos(), props.routingKeys().boletaGenerar(), comando, comando.comandoId(),
				pago);
	}

	private void publicar(String exchange, String routingKey, Object mensaje, String id, Pago pago) {
		try {
			rabbitTemplate.convertAndSend(exchange, routingKey, mensaje, new CorrelationData(id));
			log.info("[PUBLICADO] {} -> {} (pagoId={}, pedidoId={}, id={})", routingKey, exchange, pago.getId(),
					pago.getPedidoId(), id);
		}
		catch (AmqpException ex) {
			log.error("[PUBLICACION FALLIDA] No se pudo publicar {} del pago {}: {}. El pago sí quedó guardado.",
					routingKey, pago.getId(), ex.getMessage());
		}
	}
}
