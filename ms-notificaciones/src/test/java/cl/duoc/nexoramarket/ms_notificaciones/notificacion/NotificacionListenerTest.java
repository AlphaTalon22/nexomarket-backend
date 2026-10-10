package cl.duoc.nexoramarket.ms_notificaciones.notificacion;

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

import com.rabbitmq.client.Channel;

import cl.duoc.nexoramarket.ms_notificaciones.common.ErrorTransitorioException;
import cl.duoc.nexoramarket.ms_notificaciones.common.MensajeInvalidoException;
import cl.duoc.nexoramarket.ms_notificaciones.messaging.contract.EventoNegocio;

/**
 * Verifica las decisiones de ACK / NACK del consumidor sin necesitar un RabbitMQ real.
 */
class NotificacionListenerTest {

	private static final long TAG = 7L;

	private NotificacionService service;
	private Channel channel;
	private NotificacionListener listener;
	private EventoNegocio evento;

	@BeforeEach
	void setUp() {
		service = mock(NotificacionService.class);
		channel = mock(Channel.class);
		listener = new NotificacionListener(service);
		evento = new EventoNegocio("ev-1", "pedido.creado", "ms-pedidos", LocalDateTime.now(),
				10L, null, 1L, 450000.0, "PENDIENTE", null);
	}

	@Test
	void procesadoOkHaceAck() throws Exception {
		listener.onEvento(evento, channel, TAG, false);

		verify(channel).basicAck(TAG, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void mensajeInvalidoVaDirectoADlq() throws Exception {
		when(service.notificar(any())).thenThrow(new MensajeInvalidoException("pedidoId must not be null"));

		listener.onEvento(evento, channel, TAG, false);

		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void errorTransitorioPrimeraVezSeReencola() throws Exception {
		when(service.notificar(any())).thenThrow(new ErrorTransitorioException("correo caído"));

		listener.onEvento(evento, channel, TAG, false);

		verify(channel).basicNack(TAG, false, true);
	}

	@Test
	void errorTransitorioYaReintentadoVaADlq() throws Exception {
		when(service.notificar(any())).thenThrow(new ErrorTransitorioException("correo caído"));

		listener.onEvento(evento, channel, TAG, true);

		verify(channel).basicNack(TAG, false, false);
	}
}
