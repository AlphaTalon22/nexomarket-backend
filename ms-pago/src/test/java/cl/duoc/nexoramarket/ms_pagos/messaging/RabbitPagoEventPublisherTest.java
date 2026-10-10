package cl.duoc.nexoramarket.ms_pagos.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import cl.duoc.nexoramarket.ms_pagos.messaging.config.MessagingProperties;
import cl.duoc.nexoramarket.ms_pagos.messaging.event.EventoNegocio;
import cl.duoc.nexoramarket.ms_pagos.messaging.event.GenerarBoletaCommand;
import cl.duoc.nexoramarket.ms_pagos.messaging.publisher.RabbitPagoEventPublisher;
import cl.duoc.nexoramarket.ms_pagos.model.Pago;

class RabbitPagoEventPublisherTest {

	private RabbitTemplate rabbitTemplate;
	private RabbitPagoEventPublisher publisher;

	@BeforeEach
	void setUp() {
		rabbitTemplate = mock(RabbitTemplate.class);
		MessagingProperties props = new MessagingProperties(
				new MessagingProperties.Exchanges("nexora.eventos.topic", "nexora.documentos.direct"),
				new MessagingProperties.RoutingKeys("pago.aprobado", "pago.rechazado", "boleta.generar"));
		publisher = new RabbitPagoEventPublisher(rabbitTemplate, props);
	}

	private Pago pago(String estado) {
		return new Pago(3L, 10L, 450000.0, "DEBITO", estado, LocalDateTime.now());
	}

	@Test
	void pagoAprobadoSePublicaConRoutingKeyPagoAprobado() {
		publisher.publicarResultadoPago(pago("APROBADO"));

		ArgumentCaptor<Object> mensaje = ArgumentCaptor.forClass(Object.class);
		verify(rabbitTemplate).convertAndSend(eq("nexora.eventos.topic"), eq("pago.aprobado"), mensaje.capture(),
				any(CorrelationData.class));
		EventoNegocio evento = (EventoNegocio) mensaje.getValue();
		assertThat(evento.pedidoId()).isEqualTo(10L);
		assertThat(evento.pagoId()).isEqualTo(3L);
	}

	@Test
	void pagoRechazadoSePublicaConRoutingKeyPagoRechazado() {
		publisher.publicarResultadoPago(pago("RECHAZADO"));

		verify(rabbitTemplate).convertAndSend(eq("nexora.eventos.topic"), eq("pago.rechazado"), any(Object.class),
				any(CorrelationData.class));
	}

	@Test
	void laBoletaSePideAlExchangeDirectDeDocumentos() {
		publisher.solicitarBoleta(pago("APROBADO"));

		ArgumentCaptor<Object> mensaje = ArgumentCaptor.forClass(Object.class);
		verify(rabbitTemplate).convertAndSend(eq("nexora.documentos.direct"), eq("boleta.generar"), mensaje.capture(),
				any(CorrelationData.class));
		assertThat(((GenerarBoletaCommand) mensaje.getValue()).pagoId()).isEqualTo(3L);
	}

	@Test
	void siRabbitNoEstaDisponibleNoSePropagaElError() {
		doThrow(new AmqpConnectException(new RuntimeException("connection refused")))
				.when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

		// No debe lanzar excepción: el pago ya quedó guardado
		publisher.publicarResultadoPago(pago("APROBADO"));
	}
}
