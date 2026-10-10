package cl.duoc.nexoramarket.ms_notificaciones.notificacion;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.nexoramarket.ms_notificaciones.common.MensajeInvalidoException;
import cl.duoc.nexoramarket.ms_notificaciones.messaging.contract.EventoNegocio;

/**
 * Consumidor del dominio NOTIFICACIONES: escucha {@code nexora.notificaciones.q}.
 * <p>
 * Confirmación manual (acknowledge-mode: manual). Decisiones:
 * <ol>
 *   <li>Procesado OK                        → {@code basicAck}: RabbitMQ borra el mensaje.</li>
 *   <li>Mensaje inválido (no recuperable)   → {@code basicNack(requeue=false)}: va directo a la DLQ.</li>
 *   <li>Error transitorio, primer intento   → {@code basicNack(requeue=true)}: se reintenta una vez.</li>
 *   <li>Error transitorio, ya reintentado   → {@code basicNack(requeue=false)}: va a la DLQ.</li>
 * </ol>
 */
@Component
public class NotificacionListener {

	private static final Logger log = LoggerFactory.getLogger(NotificacionListener.class);

	private final NotificacionService notificacionService;

	public NotificacionListener(NotificacionService notificacionService) {
		this.notificacionService = notificacionService;
	}

	@RabbitListener(id = "notificaciones-listener", queues = "${nexora.messaging.notificaciones.queue}")
	public void onEvento(EventoNegocio evento,
			Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
			@Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) throws IOException {

		String id = (evento != null) ? evento.eventoId() : "(vacío)";
		try {
			notificacionService.notificar(evento);
		}
		catch (MensajeInvalidoException ex) {
			// No recuperable: reintentar daría el mismo error.
			channel.basicNack(deliveryTag, false, false);
			log.error("[NACK -> DLQ] Evento {} descartado por inválido (no se reintenta): {}", id, ex.getMessage());
			return;
		}
		catch (RuntimeException ex) {
			if (Boolean.TRUE.equals(redelivered)) {
				// Ya tuvo su reintento: se aparta a la DLQ para análisis.
				channel.basicNack(deliveryTag, false, false);
				log.error("[NACK -> DLQ] El evento {} volvió a fallar tras el reintento: {}", id, ex.getMessage());
			}
			else {
				// Primer fallo transitorio: se devuelve a la cola para un segundo intento.
				channel.basicNack(deliveryTag, false, true);
				log.warn("[NACK -> REINTENTO] Error transitorio con el evento {}, se reencola una vez: {}",
						id, ex.getMessage());
			}
			return;
		}

		channel.basicAck(deliveryTag, false);
		log.info("[ACK] Notificación procesada. evento={} tipo={} pedidoId={}", id, evento.tipo(), evento.pedidoId());
	}
}
