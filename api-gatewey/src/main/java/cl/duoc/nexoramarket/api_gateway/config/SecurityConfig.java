package cl.duoc.nexoramarket.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * El Gateway (BFF) valida el JWT ANTES de enrutar hacia los microservicios.
 * <p>
 * Con issuer-uri y audiences (application.yml) se verifica: firma contra las claves públicas
 * del IDaaS, emisor (tenant), audiencia (Client ID de la API) y vigencia. Sin token o con
 * un token inválido responde 401 y la petición nunca llega al backend.
 * Cada microservicio vuelve a validar el token (defensa en profundidad).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
		http.csrf(ServerHttpSecurity.CsrfSpec::disable)
			.authorizeExchange(exchanges -> exchanges
				.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // preflight CORS del navegador
				.pathMatchers("/actuator/health").permitAll()          // health check
				.anyExchange().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return http.build();
	}
}
