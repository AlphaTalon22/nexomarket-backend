package cl.duoc.nexoramarket.ms_notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Microservicio consumidor de Nexora Market.
 * <p>
 * No expone endpoints HTTP: solo escucha colas de RabbitMQ y procesa tareas
 * asíncronas (notificaciones al cliente y generación de boletas).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MsNotificacionesApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsNotificacionesApplication.class, args);
	}

}
