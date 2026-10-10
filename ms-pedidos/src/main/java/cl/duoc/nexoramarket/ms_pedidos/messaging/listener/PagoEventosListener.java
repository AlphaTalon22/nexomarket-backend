package cl.duoc.nexoramarket.ms_pedidos.messaging.listener;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.nexoramarket.ms_pedidos.exception.MensajeInvalidoException;
import cl.duoc.nexoramarket.ms_pedidos.messaging.event.EventoNegocio;
import cl.duoc.nexoramarket.ms_pedidos.service.PedidoEstadoService;

/**
 * Consumidor del dominio PEDIDOS: escucha {@code nexora.pedidos.pagos.q} (pago.aprobado / pago.rechazado)
 * y actualiza el estado del pedido.
 * <ol>
 *   <li>OK                                        → basicAck</li>
 *   <li>Evento inválido o pedido inexistente      → basicNack(requeue=false) → DLQ</li>
 *   <li>Error transitorio (ej. BD caída), 1ª vez  → basicNack(requeue=true)  → reintento</li>
 *   <li>Error transitorio ya reintentado          → basicNack(requeue=false) → DLQ</li>
 * </ol>
 */
@Component
public class PagoEventosListener {

	private static final Logger log = LoggerFactory.getLogger(PagoEventosListener.class);

	private final PedidoEstadoService pedidoEstadoService;

	public PagoEventosListener(PedidoEstadoService pedidoEstadoService) {
		this.pedidoEstadoService = pedidoEstadoService;
	}

	@RabbitListener(id = "pedidos-pagos-listener", queues = "${nexora.messaging.pagos.queue}")
	public void onResultadoPago(EventoNegocio evento,
			Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
			@Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) throws IOException {

		String id = (evento != null) ? evento.eventoId() : "(vacío)";
		try {
			pedidoEstadoService.aplicarResultadoPago(evento);
		}
		catch (MensajeInvalidoException ex) {
			channel.basicNack(deliveryTag, false, false);
			log.error("[NACK -> DLQ] Evento {} no aplicable (no se reintenta): {}", id, ex.getMessage());
			return;
		}
		catch (RuntimeException ex) {
			if (Boolean.TRUE.equals(redelivered)) {
				channel.basicNack(deliveryTag, false, false);
				log.error("[NACK -> DLQ] El evento {} volvió a fallar tras el reintento: {}", id, ex.getMessage());
			}
			else {
				channel.basicNack(deliveryTag, false, true);
				log.warn("[NACK -> REINTENTO] Error transitorio con el evento {}, se reencola una vez: {}",
						id, ex.getMessage());
			}
			return;
		}

		channel.basicAck(deliveryTag, false);
		log.info("[ACK] Resultado de pago aplicado. evento={} tipo={} pedidoId={}", id, evento.tipo(),
				evento.pedidoId());
	}
}
