package cl.duoc.nexoramarket.ms_pedidos.messaging.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.duoc.nexoramarket.ms_pedidos.messaging.config.MessagingProperties;
import cl.duoc.nexoramarket.ms_pedidos.messaging.event.EventoNegocio;
import cl.duoc.nexoramarket.ms_pedidos.model.Pedido;

/**
 * Productor: publica {@code pedido.creado} en el exchange TOPIC de eventos.
 * <p>
 * Si RabbitMQ no está disponible, el pedido igual queda guardado (la mensajería no rompe
 * la lógica existente) y el fallo queda registrado en el log.
 */
@Component
public class RabbitPedidoEventPublisher implements PedidoEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(RabbitPedidoEventPublisher.class);

	private final RabbitTemplate rabbitTemplate;
	private final MessagingProperties props;

	public RabbitPedidoEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
		this.rabbitTemplate = rabbitTemplate;
		this.props = props;
	}

	@Override
	public void publicarPedidoCreado(Pedido pedido) {
		EventoNegocio evento = EventoNegocio.pedidoCreado(props.routingKeys().pedidoCreado(), pedido);
		try {
			rabbitTemplate.convertAndSend(props.exchanges().eventos(), evento.tipo(), evento,
					new CorrelationData(evento.eventoId()));
			log.info("[PUBLICADO] {} -> {} (pedidoId={}, eventoId={})", evento.tipo(), props.exchanges().eventos(),
					pedido.getId(), evento.eventoId());
		}
		catch (AmqpException ex) {
			log.error("[PUBLICACION FALLIDA] No se pudo publicar {} del pedido {}: {}. El pedido sí quedó guardado.",
					evento.tipo(), pedido.getId(), ex.getMessage());
		}
	}
}
