package cl.duoc.nexoramarket.ms_notificaciones.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Nombres de exchanges, colas y bindings leídos desde {@code nexora.messaging} en application.yml.
 * <p>
 * Es la única fuente de nombres de mensajería del microservicio: ninguna clase de negocio
 * escribe nombres de colas o exchanges a mano. Si falta un valor, la aplicación no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "nexora.messaging")
public record MessagingProperties(
		@Valid @NotNull Exchanges exchanges,
		@Positive int retencionDlqMs,
		@Positive long maxMensajesCola,
		@Valid @NotNull Ruta notificaciones,
		@Valid @NotNull Ruta boletas) {

	/** Exchanges usados por este microservicio. */
	public record Exchanges(
			@NotBlank String eventos,
			@NotBlank String documentos,
			@NotBlank String deadLetter) {
	}

	/**
	 * Una ruta de mensajería completa: cola principal, su DLQ y las routing keys
	 * con las que la cola se enlaza a su exchange.
	 */
	public record Ruta(
			@NotBlank String queue,
			@NotBlank String dlq,
			@NotBlank String deadLetterRoutingKey,
			@NotEmpty List<@NotBlank String> bindingKeys) {
	}
}
