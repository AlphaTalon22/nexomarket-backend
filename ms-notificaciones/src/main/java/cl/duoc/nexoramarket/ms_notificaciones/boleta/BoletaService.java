package cl.duoc.nexoramarket.ms_notificaciones.boleta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.duoc.nexoramarket.ms_notificaciones.common.ErrorTransitorioException;
import cl.duoc.nexoramarket.ms_notificaciones.common.ValidadorMensajes;
import cl.duoc.nexoramarket.ms_notificaciones.messaging.contract.GenerarBoletaCommand;

/**
 * Lógica de negocio de las boletas: genera un documento de texto por cada pago aprobado.
 * <p>
 * Es idempotente: si la boleta del pago ya existe (mensaje duplicado), no la vuelve a escribir.
 */
@Service
public class BoletaService {

	private static final Logger log = LoggerFactory.getLogger(BoletaService.class);
	private static final NumberFormat CLP = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CL"));
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

	private final ValidadorMensajes validador;
	private final Path directorio;

	public BoletaService(ValidadorMensajes validador,
			@Value("${nexora.boletas.directorio:./boletas}") String directorio) {
		this.validador = validador;
		this.directorio = Path.of(directorio);
	}

	/**
	 * Genera la boleta del pago.
	 *
	 * @return ruta del archivo de la boleta
	 * @throws cl.duoc.nexoramarket.ms_notificaciones.common.MensajeInvalidoException si el comando viene incompleto
	 * @throws ErrorTransitorioException si no se pudo escribir el archivo
	 */
	public Path generar(GenerarBoletaCommand comando) {
		validador.validar(comando);

		Path archivo = directorio.resolve("boleta-pago-" + comando.pagoId() + ".txt");
		if (Files.exists(archivo)) {
			log.info("[BOLETA] La boleta del pago {} ya existía (mensaje duplicado), no se regenera: {}",
					comando.pagoId(), archivo.toAbsolutePath());
			return archivo;
		}

		try {
			Files.createDirectories(directorio);
			Files.writeString(archivo, contenido(comando), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new ErrorTransitorioException("No se pudo escribir la boleta del pago "
					+ comando.pagoId() + ": " + ex.getMessage(), ex);
		}

		log.info("[BOLETA] Boleta generada para el pago {} (pedido {}): {}",
				comando.pagoId(), comando.pedidoId(), archivo.toAbsolutePath());
		return archivo;
	}

	String contenido(GenerarBoletaCommand comando) {
		LocalDateTime fecha = comando.fechaPago() != null ? comando.fechaPago() : LocalDateTime.now();
		return String.join(System.lineSeparator(),
				"==============================",
				"        NEXORA MARKET",
				"     BOLETA ELECTRÓNICA",
				"==============================",
				"N° pago     : " + comando.pagoId(),
				"N° pedido   : " + comando.pedidoId(),
				"Fecha       : " + FECHA.format(fecha),
				"Medio pago  : " + comando.metodoPago(),
				"TOTAL       : " + CLP.format(comando.monto()),
				"==============================",
				"Comando     : " + comando.comandoId(),
				"");
	}
}
