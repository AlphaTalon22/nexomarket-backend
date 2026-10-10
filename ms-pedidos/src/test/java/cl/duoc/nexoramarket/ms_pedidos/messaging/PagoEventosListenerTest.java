package cl.duoc.nexoramarket.ms_pedidos.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.rabbitmq.client.Channel;

import cl.duoc.nexoramarket.ms_pedidos.exception.PedidoNoEncontradoException;
import cl.duoc.nexoramarket.ms_pedidos.messaging.event.EventoNegocio;
import cl.duoc.nexoramarket.ms_pedidos.messaging.listener.PagoEventosListener;
import cl.duoc.nexoramarket.ms_pedidos.service.PedidoEstadoService;

/** Decisiones de ACK / NACK del consumidor de ms-pedidos, sin RabbitMQ real. */
class PagoEventosListenerTest {

	private static final long TAG = 3L;

	private PedidoEstadoService service;
	private Channel channel;
	private PagoEventosListener listener;
	private EventoNegocio evento;

	@BeforeEach
	void setUp() {
		service = mock(PedidoEstadoService.class);
		channel = mock(Channel.class);
		listener = new PagoEventosListener(service);
		evento = new EventoNegocio("ev-9", "pago.aprobado", "ms-pago", LocalDateTime.now(), 10L, 3L, null,
				450000.0, "APROBADO", "DEBITO");
	}

	@Test
	void okHaceAck() throws Exception {
		listener.onResultadoPago(evento, channel, TAG, false);

		verify(channel).basicAck(TAG, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void pedidoInexistenteVaADlqSinReintento() throws Exception {
		when(service.aplicarResultadoPago(any())).thenThrow(new PedidoNoEncontradoException(10L));

		listener.onResultadoPago(evento, channel, TAG, false);

		verify(channel).basicNack(TAG, false, false);
	}

	@Test
	void baseDeDatosCaidaSeReintentaUnaVez() throws Exception {
		when(service.aplicarResultadoPago(any())).thenThrow(new DataAccessResourceFailureException("BD caída"));

		listener.onResultadoPago(evento, channel, TAG, false);

		verify(channel).basicNack(TAG, false, true);
	}

	@Test
	void baseDeDatosCaidaEnElReintentoVaADlq() throws Exception {
		when(service.aplicarResultadoPago(any())).thenThrow(new DataAccessResourceFailureException("BD caída"));

		listener.onResultadoPago(evento, channel, TAG, true);

		verify(channel).basicNack(TAG, false, false);
	}
}
