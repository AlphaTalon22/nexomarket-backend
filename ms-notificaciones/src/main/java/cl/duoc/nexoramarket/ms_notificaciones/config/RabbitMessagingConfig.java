package cl.duoc.nexoramarket.ms_notificaciones.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conversión de mensajes: los mensajes viajan como JSON.
 * <p>
 * Spring Boot usa este bean automáticamente en los @RabbitListener. El tipo de destino
 * se infiere del parámetro del listener (EventoNegocio, GenerarBoletaCommand), así que
 * el productor y el consumidor no necesitan compartir las mismas clases Java.
 * Si llega un mensaje que no es JSON válido, se rechaza sin reencolar y termina en la DLQ.
 */
@Configuration
public class RabbitMessagingConfig {

	@Bean
	public MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}
}
