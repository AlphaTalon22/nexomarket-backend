package cl.duoc.nexoramarket.ms_notificaciones.boleta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cl.duoc.nexoramarket.ms_notificaciones.common.MensajeInvalidoException;
import cl.duoc.nexoramarket.ms_notificaciones.common.ValidadorMensajes;
import cl.duoc.nexoramarket.ms_notificaciones.messaging.contract.GenerarBoletaCommand;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class BoletaServiceTest {

	private static ValidatorFactory factory;
	private static ValidadorMensajes validador;

	@TempDir
	Path tempDir;

	@BeforeAll
	static void initValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validador = new ValidadorMensajes(factory.getValidator());
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	private GenerarBoletaCommand comando(Double monto) {
		return new GenerarBoletaCommand("cmd-1", 3L, 10L, monto, "DEBITO", LocalDateTime.of(2026, 10, 2, 12, 0));
	}

	@Test
	void generaArchivoDeBoleta() throws Exception {
		BoletaService service = new BoletaService(validador, tempDir.toString());

		Path archivo = service.generar(comando(450000.0));

		assertThat(archivo).exists();
		assertThat(Files.readString(archivo)).contains("N° pago     : 3").contains("N° pedido   : 10");
	}

	@Test
	void mensajeDuplicadoNoSobrescribeLaBoleta() throws Exception {
		BoletaService service = new BoletaService(validador, tempDir.toString());
		Path archivo = service.generar(comando(450000.0));
		Files.writeString(archivo, "contenido original");

		service.generar(comando(450000.0));

		assertThat(Files.readString(archivo)).isEqualTo("contenido original");
	}

	@Test
	void montoNegativoEsInvalido() {
		BoletaService service = new BoletaService(validador, tempDir.toString());

		assertThatThrownBy(() -> service.generar(comando(-1.0)))
				.isInstanceOf(MensajeInvalidoException.class)
				.hasMessageContaining("monto");
	}
}
