package cl.duoc.nexoramarket.ms_rabbit_admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import cl.duoc.nexoramarket.ms_rabbit_admin.dto.BindingRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.CrearColaRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.CrearExchangeRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.TipoExchange;
import cl.duoc.nexoramarket.ms_rabbit_admin.exception.RecursoNoEncontradoException;
import cl.duoc.nexoramarket.ms_rabbit_admin.exception.RecursoYaExisteException;

class RabbitAdminServiceTest {

	private AmqpAdmin amqpAdmin;
	private RabbitTemplate rabbitTemplate;
	private RabbitAdminService service;

	@BeforeEach
	void setUp() {
		amqpAdmin = mock(AmqpAdmin.class);
		rabbitTemplate = mock(RabbitTemplate.class);
		service = new RabbitAdminService(amqpAdmin, rabbitTemplate);
	}

	private void exchangeExiste(boolean existe) {
		if (existe) {
			when(rabbitTemplate.execute(any())).thenReturn(null);
		}
		else {
			when(rabbitTemplate.execute(any())).thenThrow(new AmqpIOException(new java.io.IOException("NOT_FOUND")));
		}
	}

	@Test
	void creaColaConDeadLetterExchange() {
		when(amqpAdmin.getQueueInfo("nueva.q")).thenReturn(null);

		service.crearCola(new CrearColaRequest("nueva.q", null, "nexora.dlx.direct", "nueva.dead", 60000, null));

		ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
		verify(amqpAdmin).declareQueue(captor.capture());
		Queue cola = captor.getValue();
		assertThat(cola.getName()).isEqualTo("nueva.q");
		assertThat(cola.isDurable()).isTrue();
		assertThat(cola.getArguments())
				.containsEntry("x-dead-letter-exchange", "nexora.dlx.direct")
				.containsEntry("x-dead-letter-routing-key", "nueva.dead")
				.containsEntry("x-message-ttl", 60000);
	}

	@Test
	void noPermiteCrearUnaColaQueYaExiste() {
		when(amqpAdmin.getQueueInfo("nexora.boletas.q")).thenReturn(new QueueInformation("nexora.boletas.q", 0, 1));

		assertThatThrownBy(() -> service.crearCola(new CrearColaRequest("nexora.boletas.q", true, null, null, null, null)))
				.isInstanceOf(RecursoYaExisteException.class);
		verify(amqpAdmin, never()).declareQueue(any(Queue.class));
	}

	@Test
	void eliminarColaInexistenteDa404() {
		when(amqpAdmin.getQueueInfo(anyString())).thenReturn(null);

		assertThatThrownBy(() -> service.eliminarCola("no.existe"))
				.isInstanceOf(RecursoNoEncontradoException.class);
		verify(amqpAdmin, never()).deleteQueue(anyString());
	}

	@Test
	void creaExchangeTopic() {
		exchangeExiste(false);

		service.crearExchange(new CrearExchangeRequest("demo.topic", TipoExchange.TOPIC, null));

		ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
		verify(amqpAdmin).declareExchange(captor.capture());
		assertThat(captor.getValue()).isInstanceOf(TopicExchange.class);
		assertThat(captor.getValue().isDurable()).isTrue();
	}

	@Test
	void noEnlazaUnExchangeInexistente() {
		when(amqpAdmin.getQueueInfo("demo.q")).thenReturn(new QueueInformation("demo.q", 0, 0));
		exchangeExiste(false);

		assertThatThrownBy(() -> service.crearBinding(new BindingRequest("demo.q", "no.existe", "demo.#")))
				.isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessageContaining("exchange");
		verify(amqpAdmin, never()).declareBinding(any(Binding.class));
	}

	@Test
	void creaBindingCuandoColaYExchangeExisten() {
		when(amqpAdmin.getQueueInfo("demo.q")).thenReturn(new QueueInformation("demo.q", 0, 0));
		exchangeExiste(true);

		service.crearBinding(new BindingRequest("demo.q", "demo.topic", "pedido.*"));

		ArgumentCaptor<Binding> captor = ArgumentCaptor.forClass(Binding.class);
		verify(amqpAdmin).declareBinding(captor.capture());
		assertThat(captor.getValue().getDestination()).isEqualTo("demo.q");
		assertThat(captor.getValue().getExchange()).isEqualTo("demo.topic");
		assertThat(captor.getValue().getRoutingKey()).isEqualTo("pedido.*");
	}
}
