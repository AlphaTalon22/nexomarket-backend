package cl.duoc.nexoramarket.ms_notificaciones.boleta;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.nexoramarket.ms_notificaciones.common.MensajeInvalidoException;
import cl.duoc.nexoramarket.ms_notificaciones.messaging.contract.GenerarBoletaCommand;

/**
 * Consumidor del dominio BOLETAS: escucha {@code nexora.boletas.q}.
 * <p>
 * Mismo criterio de confirmación que el resto de consumidores:
 * ACK si se generó, NACK sin reencolar (DLQ) si el comando es inválido,
 * un reintento si el error es transitorio y DLQ si vuelve a fallar.
 */
@Component
public class BoletaListener {

	private static final Logger log = LoggerFactory.getLogger(BoletaListener.class);

	private final BoletaService boletaService;

	public BoletaListener(BoletaService boletaService) {
		this.boletaService = boletaService;
	}

	@RabbitListener(id = "boletas-listener", queues = "${nexora.messaging.boletas.queue}")
	public void onGenerarBoleta(GenerarBoletaCommand comando,
			Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
			@Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) throws IOException {

		Object pagoId = (comando != null) ? comando.pagoId() : "(vacío)";
		try {
			boletaService.generar(comando);
		}
		catch (MensajeInvalidoException ex) {
			channel.basicNack(deliveryTag, false, false);
			log.error("[NACK -> DLQ] Comando de boleta inválido (no se reintenta): {}", ex.getMessage());
			return;
		}
		catch (RuntimeException ex) {
			if (Boolean.TRUE.equals(redelivered)) {
				channel.basicNack(deliveryTag, false, false);
				log.error("[NACK -> DLQ] La boleta del pago {} volvió a fallar tras el reintento: {}",
						pagoId, ex.getMessage());
			}
			else {
				channel.basicNack(deliveryTag, false, true);
				log.warn("[NACK -> REINTENTO] Error transitorio al generar la boleta del pago {}, se reencola: {}",
						pagoId, ex.getMessage());
			}
			return;
		}

		channel.basicAck(deliveryTag, false);
		log.info("[ACK] Boleta procesada. pagoId={}", pagoId);
	}
}
