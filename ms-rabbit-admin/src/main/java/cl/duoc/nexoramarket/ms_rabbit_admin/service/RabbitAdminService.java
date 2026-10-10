package cl.duoc.nexoramarket.ms_rabbit_admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import cl.duoc.nexoramarket.ms_rabbit_admin.dto.BindingRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.CrearColaRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.CrearExchangeRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.Respuestas.BindingResponse;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.Respuestas.ColaResponse;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.Respuestas.ExchangeResponse;
import cl.duoc.nexoramarket.ms_rabbit_admin.exception.RecursoNoEncontradoException;
import cl.duoc.nexoramarket.ms_rabbit_admin.exception.RecursoYaExisteException;

/**
 * Única clase que habla con la API de administración de RabbitMQ (AmqpAdmin / RabbitAdmin).
 * <p>
 * Los controladores solo llaman a estos métodos de alto nivel y no conocen ninguna
 * clase de Spring AMQP. Aquí se concentran las reglas: no duplicar recursos, no borrar
 * lo que no existe y no enlazar colas o exchanges inexistentes.
 */
@Service
public class RabbitAdminService {

	private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

	private final AmqpAdmin amqpAdmin;
	private final RabbitTemplate rabbitTemplate;

	public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate) {
		this.amqpAdmin = amqpAdmin;
		this.rabbitTemplate = rabbitTemplate;
	}

	// ============================ Colas ============================

	public ColaResponse crearCola(CrearColaRequest request) {
		if (existeCola(request.nombre())) {
			throw new RecursoYaExisteException("La cola '" + request.nombre() + "' ya existe");
		}

		QueueBuilder builder = request.durable()
				? QueueBuilder.durable(request.nombre())
				: QueueBuilder.nonDurable(request.nombre());
		if (request.deadLetterExchange() != null) {
			builder.deadLetterExchange(request.deadLetterExchange());
		}
		if (request.deadLetterRoutingKey() != null) {
			builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
		}
		if (request.ttlMs() != null) {
			builder.ttl(request.ttlMs());
		}
		if (request.maxMensajes() != null) {
			builder.maxLength(request.maxMensajes());
		}

		amqpAdmin.declareQueue(builder.build());
		log.info("[ADMIN] Cola creada: {} (durable={}, dlx={}, ttlMs={}, max={})", request.nombre(),
				request.durable(), request.deadLetterExchange(), request.ttlMs(), request.maxMensajes());
		return new ColaResponse(request.nombre(), 0, 0);
	}

	public ColaResponse obtenerCola(String nombre) {
		QueueInformation info = amqpAdmin.getQueueInfo(nombre);
		if (info == null) {
			throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
		}
		return new ColaResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
	}

	public void eliminarCola(String nombre) {
		if (!existeCola(nombre)) {
			throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
		}
		amqpAdmin.deleteQueue(nombre);
		log.info("[ADMIN] Cola eliminada: {}", nombre);
	}

	// =========================== Exchanges ===========================

	public ExchangeResponse crearExchange(CrearExchangeRequest request) {
		if (existeExchange(request.nombre())) {
			throw new RecursoYaExisteException("El exchange '" + request.nombre() + "' ya existe");
		}

		Exchange exchange = switch (request.tipo()) {
			case DIRECT -> new DirectExchange(request.nombre(), request.durable(), false);
			case TOPIC -> new TopicExchange(request.nombre(), request.durable(), false);
			case FANOUT -> new FanoutExchange(request.nombre(), request.durable(), false);
		};

		amqpAdmin.declareExchange(exchange);
		log.info("[ADMIN] Exchange creado: {} (tipo={}, durable={})", request.nombre(), request.tipo(),
				request.durable());
		return new ExchangeResponse(request.nombre(), request.tipo(), request.durable());
	}

	public void eliminarExchange(String nombre) {
		if (!existeExchange(nombre)) {
			throw new RecursoNoEncontradoException("El exchange '" + nombre + "' no existe");
		}
		amqpAdmin.deleteExchange(nombre);
		log.info("[ADMIN] Exchange eliminado: {}", nombre);
	}

	// =========================== Bindings ===========================

	public BindingResponse crearBinding(BindingRequest request) {
		validarQueExistenColaYExchange(request);
		amqpAdmin.declareBinding(aBinding(request));
		log.info("[ADMIN] Binding creado: {} --[{}]--> {}", request.exchange(), request.routingKey(),
				request.cola());
		return new BindingResponse(request.cola(), request.exchange(), request.routingKey());
	}

	public void eliminarBinding(BindingRequest request) {
		validarQueExistenColaYExchange(request);
		amqpAdmin.removeBinding(aBinding(request));
		log.info("[ADMIN] Binding eliminado: {} --[{}]--> {}", request.exchange(), request.routingKey(),
				request.cola());
	}

	// =========================== Helpers ===========================

	private void validarQueExistenColaYExchange(BindingRequest request) {
		if (!existeCola(request.cola())) {
			throw new RecursoNoEncontradoException("La cola '" + request.cola() + "' no existe");
		}
		if (!existeExchange(request.exchange())) {
			throw new RecursoNoEncontradoException("El exchange '" + request.exchange() + "' no existe");
		}
	}

	private Binding aBinding(BindingRequest request) {
		return new Binding(request.cola(), Binding.DestinationType.QUEUE, request.exchange(),
				request.routingKey(), null);
	}

	boolean existeCola(String nombre) {
		return amqpAdmin.getQueueInfo(nombre) != null;
	}

	/**
	 * RabbitMQ no tiene un "get exchange": se usa una declaración pasiva, que falla
	 * si el exchange no existe y no modifica nada si existe.
	 */
	boolean existeExchange(String nombre) {
		try {
			rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(nombre));
			return true;
		}
		catch (AmqpConnectException ex) {
			throw ex; // el broker no responde: no es "no existe", es un error de conexión
		}
		catch (AmqpException ex) {
			return false;
		}
	}
}
