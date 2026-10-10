package cl.duoc.nexoramarket.ms_pedidos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.duoc.nexoramarket.ms_pedidos.exception.MensajeInvalidoException;
import cl.duoc.nexoramarket.ms_pedidos.exception.PedidoNoEncontradoException;
import cl.duoc.nexoramarket.ms_pedidos.messaging.event.EventoNegocio;
import cl.duoc.nexoramarket.ms_pedidos.model.Pedido;
import cl.duoc.nexoramarket.ms_pedidos.repository.PedidoRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class PedidoEstadoServiceTest {

	private static ValidatorFactory factory;

	private PedidoRepository repository;
	private PedidoEstadoService service;

	@BeforeAll
	static void initValidator() {
		factory = Validation.buildDefaultValidatorFactory();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	@BeforeEach
	void setUp() {
		repository = mock(PedidoRepository.class);
		service = new PedidoEstadoService(repository, factory.getValidator());
		when(repository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private EventoNegocio pago(String tipo, Long pedidoId) {
		return new EventoNegocio("ev-1", tipo, "ms-pago", LocalDateTime.now(), pedidoId, 3L, null, 450000.0,
				"APROBADO", "DEBITO");
	}

	private Pedido pedido(Long id, String estado) {
		return new Pedido(id, 1L, LocalDateTime.now(), 450000.0, estado);
	}

	@Test
	void pagoAprobadoDejaElPedidoPagado() {
		when(repository.findById(10L)).thenReturn(Optional.of(pedido(10L, "PENDIENTE")));

		Pedido actualizado = service.aplicarResultadoPago(pago("pago.aprobado", 10L));

		assertThat(actualizado.getEstado()).isEqualTo("PAGADO");
	}

	@Test
	void pagoRechazadoDejaElPedidoRechazado() {
		when(repository.findById(10L)).thenReturn(Optional.of(pedido(10L, "PENDIENTE")));

		assertThat(service.aplicarResultadoPago(pago("pago.rechazado", 10L)).getEstado()).isEqualTo("PAGO_RECHAZADO");
	}

	@Test
	void pedidoInexistenteEsNoRecuperable() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.aplicarResultadoPago(pago("pago.aprobado", 99L)))
				.isInstanceOf(PedidoNoEncontradoException.class);
	}

	@Test
	void eventoSinPedidoEsInvalido() {
		assertThatThrownBy(() -> service.aplicarResultadoPago(pago("pago.aprobado", null)))
				.isInstanceOf(MensajeInvalidoException.class)
				.hasMessageContaining("pedidoId");
	}

	@Test
	void eventoRepetidoNoVuelveAGuardar() {
		when(repository.findById(10L)).thenReturn(Optional.of(pedido(10L, "PAGADO")));

		service.aplicarResultadoPago(pago("pago.aprobado", 10L));

		verify(repository, never()).save(any(Pedido.class));
	}
}
