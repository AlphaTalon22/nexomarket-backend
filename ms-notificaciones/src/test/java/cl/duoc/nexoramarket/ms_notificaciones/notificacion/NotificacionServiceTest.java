package cl.duoc.nexoramarket.ms_notificaciones.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cl.duoc.nexoramarket.ms_notificaciones.common.ErrorTransitorioException;
import cl.duoc.nexoramarket.ms_notificaciones.common.MensajeInvalidoException;
import cl.duoc.nexoramarket.ms_notificaciones.common.ValidadorMensajes;
import cl.duoc.nexoramarket.ms_notificaciones.messaging.contract.EventoNegocio;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class NotificacionServiceTest {

	private static ValidatorFactory factory;
	private static ValidadorMensajes validador;

	@BeforeAll
	static void initValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validador = new ValidadorMensajes(factory.getValidator());
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	private EventoNegocio evento(String tipo, Long pedidoId) {
		return new EventoNegocio("ev-1", tipo, "test", LocalDateTime.now(),
				pedidoId, 5L, 1L, 450000.0, "APROBADO", "DEBITO");
	}

	@Test
	void construyeMensajeSegunTipoDeEvento() {
		NotificacionService service = new NotificacionService(validador, false);

		assertThat(service.notificar(evento("pedido.creado", 10L))).contains("Recibimos tu pedido #10");
		assertThat(service.notificar(evento("pago.aprobado", 10L))).contains("APROBADO");
		assertThat(service.notificar(evento("pago.rechazado", 10L))).contains("RECHAZADO");
	}

	@Test
	void eventoSinPedidoEsInvalido() {
		NotificacionService service = new NotificacionService(validador, false);

		assertThatThrownBy(() -> service.notificar(evento("pedido.creado", null)))
				.isInstanceOf(MensajeInvalidoException.class)
				.hasMessageContaining("pedidoId");
	}

	@Test
	void falloSimuladoDeCorreoEsTransitorio() {
		NotificacionService service = new NotificacionService(validador, true);

		assertThatThrownBy(() -> service.notificar(evento("pedido.creado", 10L)))
				.isInstanceOf(ErrorTransitorioException.class);
	}
}
