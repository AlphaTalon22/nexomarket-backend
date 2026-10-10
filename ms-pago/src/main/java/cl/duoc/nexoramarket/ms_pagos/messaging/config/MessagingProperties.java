package cl.duoc.nexoramarket.ms_pagos.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Nombres de exchanges y routing keys leídos desde {@code nexora.messaging} en application.yml.
 * ms-pago solo publica, así que no declara colas.
 */
@Validated
@ConfigurationProperties(prefix = "nexora.messaging")
public record MessagingProperties(
		@Valid @NotNull Exchanges exchanges,
		@Valid @NotNull RoutingKeys routingKeys) {

	public record Exchanges(@NotBlank String eventos, @NotBlank String documentos) {
	}

	public record RoutingKeys(
			@NotBlank String pagoAprobado,
			@NotBlank String pagoRechazado,
			@NotBlank String boletaGenerar) {
	}
}
