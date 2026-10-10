package cl.duoc.nexoramarket.ms_rabbit_admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio administrador de RabbitMQ: permite crear y eliminar colas,
 * exchanges y bindings mediante una API REST protegida con JWT.
 */
@SpringBootApplication
public class MsRabbitAdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsRabbitAdminApplication.class, args);
	}

}
