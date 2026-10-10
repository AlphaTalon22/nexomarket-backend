package cl.duoc.nexoramarket.ms_notificaciones.config;

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

import cl.duoc.nexoramarket.ms_notificaciones.config.MessagingProperties.Ruta;

/**
 * Topología RabbitMQ que este microservicio necesita para consumir.
 * <p>
 * Rutas de mensajería declaradas aquí:
 * <pre>
 *  [nexora.eventos.topic]   --pedido.* / pago.*-->  nexora.notificaciones.q  --(falla)-->  nexora.dlx.direct --notificaciones.dead--> nexora.notificaciones.dlq
 *  [nexora.documentos.direct] --boleta.generar-->   nexora.boletas.q         --(falla)-->  nexora.dlx.direct --boletas.dead-->        nexora.boletas.dlq
 * </pre>
 * Spring AMQP declara estos beans en el broker al conectarse (es idempotente:
 * si ya existen con la misma configuración, los reutiliza).
 */
@Configuration
public class RabbitTopologyConfig {

	private final MessagingProperties props;

	public RabbitTopologyConfig(MessagingProperties props) {
		this.props = props;
	}

	// ===================== Exchanges =====================

	/** Exchange TOPIC de eventos de dominio: permite comodines en las routing keys (pedido.*, pago.*). */
	@Bean
	public TopicExchange eventosExchange() {
		return new TopicExchange(props.exchanges().eventos(), true, false);
	}

	/** Exchange DIRECT de comandos de documentos: la routing key debe coincidir exacta (boleta.generar). */
	@Bean
	public DirectExchange documentosExchange() {
		return new DirectExchange(props.exchanges().documentos(), true, false);
	}

	/** Dead Letter Exchange: recibe los mensajes rechazados (NACK sin reencolar), expirados o desbordados. */
	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(props.exchanges().deadLetter(), true, false);
	}

	// ============ Caso de uso 1: notificaciones al cliente ============

	@Bean
	public Queue notificacionesQueue() {
		return colaPrincipal(props.notificaciones());
	}

	@Bean
	public Queue notificacionesDlq() {
		return colaMuerta(props.notificaciones());
	}

	/** nexora.notificaciones.q recibe todos los eventos de pedidos y de pagos. */
	@Bean
	public Declarables notificacionesBindings() {
		List<Binding> bindings = props.notificaciones().bindingKeys().stream()
				.map(key -> BindingBuilder.bind(notificacionesQueue()).to(eventosExchange()).with(key))
				.toList();
		return new Declarables(bindings);
	}

	@Bean
	public Binding notificacionesDlqBinding() {
		return BindingBuilder.bind(notificacionesDlq())
				.to(deadLetterExchange())
				.with(props.notificaciones().deadLetterRoutingKey());
	}

	// ============ Caso de uso 2: generación de boletas ============

	@Bean
	public Queue boletasQueue() {
		return colaPrincipal(props.boletas());
	}

	@Bean
	public Queue boletasDlq() {
		return colaMuerta(props.boletas());
	}

	/** nexora.boletas.q recibe solo el comando boleta.generar (match exacto del exchange DIRECT). */
	@Bean
	public Declarables boletasBindings() {
		List<Binding> bindings = props.boletas().bindingKeys().stream()
				.map(key -> BindingBuilder.bind(boletasQueue()).to(documentosExchange()).with(key))
				.toList();
		return new Declarables(bindings);
	}

	@Bean
	public Binding boletasDlqBinding() {
		return BindingBuilder.bind(boletasDlq())
				.to(deadLetterExchange())
				.with(props.boletas().deadLetterRoutingKey());
	}

	// ===================== Helpers =====================

	/**
	 * Cola principal durable con su Dead Letter Exchange configurado.
	 * Un mensaje llega a la DLQ cuando el consumidor hace NACK sin reencolar,
	 * o cuando la cola supera el máximo de mensajes.
	 */
	private Queue colaPrincipal(Ruta ruta) {
		return QueueBuilder.durable(ruta.queue())
				.deadLetterExchange(props.exchanges().deadLetter())
				.deadLetterRoutingKey(ruta.deadLetterRoutingKey())
				.maxLength(props.maxMensajesCola())
				.build();
	}

	/** DLQ durable con política de retención: los mensajes muertos se guardan 24 h para análisis. */
	private Queue colaMuerta(Ruta ruta) {
		return QueueBuilder.durable(ruta.dlq())
				.ttl(props.retencionDlqMs())
				.build();
	}
}
