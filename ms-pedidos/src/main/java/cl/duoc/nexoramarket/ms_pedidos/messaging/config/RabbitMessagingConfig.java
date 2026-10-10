package cl.duoc.nexoramarket.ms_pedidos.messaging.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración técnica de mensajería: formato JSON y registro en log de los
 * mensajes publicados que el broker no confirma o no puede enrutar a ninguna cola.
 */
@Configuration
public class RabbitMessagingConfig {

	private static final Logger log = LoggerFactory.getLogger(RabbitMessagingConfig.class);

	@Bean
	public MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}

	@Bean
	public RabbitTemplateCustomizer registroDeMensajesNoEntregados() {
		return template -> {
			template.setConfirmCallback((correlacion, ack, causa) -> {
				if (!ack) {
					log.error("[PUBLICACION NO CONFIRMADA] El broker no aceptó el mensaje {}: {}",
							correlacion != null ? correlacion.getId() : "(sin id)", causa);
				}
			});
			template.setReturnsCallback(devuelto -> log.error(
					"[MENSAJE NO ENRUTADO] Ninguna cola recibió el mensaje. exchange={} routingKey={} motivo={} {}",
					devuelto.getExchange(), devuelto.getRoutingKey(), devuelto.getReplyCode(), devuelto.getReplyText()));
		};
	}
}
