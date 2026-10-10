package cl.duoc.nexoramarket.ms_pedidos.messaging.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Nombres de exchanges, routing keys y colas leídos desde {@code nexora.messaging} en application.yml.
 * Es la única fuente de nombres de mensajería del microservicio.
 */
@Validated
@ConfigurationProperties(prefix = "nexora.messaging")
public record MessagingProperties(
		@Valid @NotNull Exchanges exchanges,
		@Valid @NotNull RoutingKeys routingKeys,
		@Positive int retencionDlqMs,
		@Positive long maxMensajesCola,
		@Valid @NotNull Ruta pagos) {

	public record Exchanges(@NotBlank String eventos, @NotBlank String deadLetter) {
	}

	/** Routing keys que este microservicio PUBLICA. */
	public record RoutingKeys(@NotBlank String pedidoCreado) {
	}

	/** Cola que este microservicio CONSUME, con su DLQ y sus bindings. */
	public record Ruta(
			@NotBlank String queue,
			@NotBlank String dlq,
			@NotBlank String deadLetterRoutingKey,
			@NotEmpty List<@NotBlank String> bindingKeys) {
	}
}
