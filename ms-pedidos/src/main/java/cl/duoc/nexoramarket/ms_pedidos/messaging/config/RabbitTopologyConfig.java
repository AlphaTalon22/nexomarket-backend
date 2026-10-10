package cl.duoc.nexoramarket.ms_pedidos.messaging.config;

import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ de ms-pedidos.
 * <pre>
 *  PUBLICA:  pedido.creado ──► [nexora.eventos.topic]
 *  CONSUME:  [nexora.eventos.topic] ──pago.*──► nexora.pedidos.pagos.q ──(falla)──► [nexora.dlx.direct] ──pedidos-pagos.dead──► nexora.pedidos.pagos.dlq
 * </pre>
 */
@Configuration
public class RabbitTopologyConfig {

	private final MessagingProperties props;

	public RabbitTopologyConfig(MessagingProperties props) {
		this.props = props;
	}

	/** Exchange TOPIC de eventos de dominio (compartido por todo Nexora Market). */
	@Bean
	public TopicExchange eventosExchange() {
		return new TopicExchange(props.exchanges().eventos(), true, false);
	}

	/** Dead Letter Exchange compartido. */
	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(props.exchanges().deadLetter(), true, false);
	}

	/** Cola donde ms-pedidos recibe el resultado de cada pago. */
	@Bean
	public Queue pagosQueue() {
		return QueueBuilder.durable(props.pagos().queue())
				.deadLetterExchange(props.exchanges().deadLetter())
				.deadLetterRoutingKey(props.pagos().deadLetterRoutingKey())
				.maxLength(props.maxMensajesCola())
				.build();
	}

	/** DLQ de la cola de pagos: retiene los mensajes fallidos 24 h para revisión. */
	@Bean
	public Queue pagosDlq() {
		return QueueBuilder.durable(props.pagos().dlq())
				.ttl(props.retencionDlqMs())
				.build();
	}

	/** nexora.pedidos.pagos.q recibe pago.aprobado y pago.rechazado (comodín pago.*). */
	@Bean
	public Declarables pagosBindings() {
		List<Binding> bindings = props.pagos().bindingKeys().stream()
				.map(key -> BindingBuilder.bind(pagosQueue()).to(eventosExchange()).with(key))
				.toList();
		return new Declarables(bindings);
	}

	@Bean
	public Binding pagosDlqBinding() {
		return BindingBuilder.bind(pagosDlq())
				.to(deadLetterExchange())
				.with(props.pagos().deadLetterRoutingKey());
	}
}
