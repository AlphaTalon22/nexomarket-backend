package cl.duoc.nexoramarket.ms_pagos.messaging.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchanges donde publica ms-pago.
 * <pre>
 *  pago.aprobado / pago.rechazado ──► [nexora.eventos.topic]      (lo consumen ms-pedidos y ms-notificaciones)
 *  boleta.generar                 ──► [nexora.documentos.direct]  (lo consume ms-notificaciones)
 * </pre>
 * Las colas las declara cada consumidor; el productor no necesita conocerlas.
 */
@Configuration
public class RabbitTopologyConfig {

	private final MessagingProperties props;

	public RabbitTopologyConfig(MessagingProperties props) {
		this.props = props;
	}

	@Bean
	public TopicExchange eventosExchange() {
		return new TopicExchange(props.exchanges().eventos(), true, false);
	}

	@Bean
	public DirectExchange documentosExchange() {
		return new DirectExchange(props.exchanges().documentos(), true, false);
	}
}
