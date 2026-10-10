package cl.duoc.nexoramarket.ms_notificaciones.dlq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

/**
 * Auditoría de las DLQ (desactivada por defecto).
 * <p>
 * Con {@code nexora.messaging.dlq-monitor.enabled=true} consume lo que llega a las DLQ de
 * notificaciones y boletas y registra en el log el motivo (header x-death de RabbitMQ):
 * cola de origen, razón (rejected / expired / maxlen) y cantidad de veces.
 * <p>
 * Se deja apagada para que durante la demo los mensajes muertos se vean acumulados
 * en las DLQ desde el dashboard de RabbitMQ.
 */
@Component
public class DeadLetterAuditListener {

	private static final Logger log = LoggerFactory.getLogger(DeadLetterAuditListener.class);

	@RabbitListener(id = "dlq-audit-listener",
			queues = { "${nexora.messaging.notificaciones.dlq}", "${nexora.messaging.boletas.dlq}" },
			autoStartup = "${nexora.messaging.dlq-monitor.enabled:false}")
	public void onMensajeMuerto(Message mensaje, Channel channel) throws IOException {
		long deliveryTag = mensaje.getMessageProperties().getDeliveryTag();
		List<Map<String, ?>> xDeath = mensaje.getMessageProperties().getXDeathHeader();
		Object colaOrigen = null;
		Object motivo = null;
		Object veces = null;
		if (xDeath != null && !xDeath.isEmpty()) {
			Map<String, ?> ultimaMuerte = xDeath.get(0);
			colaOrigen = ultimaMuerte.get("queue");
			motivo = ultimaMuerte.get("reason");
			veces = ultimaMuerte.get("count");
		}

		log.error("[DLQ] Mensaje muerto en {} | cola origen={} | motivo={} | veces={} | contenido={}",
				mensaje.getMessageProperties().getConsumerQueue(), colaOrigen, motivo, veces,
				new String(mensaje.getBody(), StandardCharsets.UTF_8));

		// Ya quedó registrado: se confirma para sacarlo de la DLQ.
		channel.basicAck(deliveryTag, false);
	}
}
