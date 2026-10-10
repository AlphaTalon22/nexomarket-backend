package cl.duoc.nexoramarket.ms_rabbit_admin.dto;

/**
 * Reglas de validación compartidas por los DTO de la API de administración.
 */
public final class ReglasNombres {

	/**
	 * Nombre de cola o exchange: letras, números, punto, guion, guion bajo y dos puntos.
	 * No puede empezar con "amq." (prefijo reservado por RabbitMQ).
	 */
	public static final String NOMBRE_RECURSO = "^(?!amq\\.)[A-Za-z0-9._:-]+$";

	public static final String NOMBRE_RECURSO_MENSAJE =
			"solo puede tener letras, números, '.', '_', '-', ':' y no puede empezar con 'amq.'";

	/** Routing key: puede incluir los comodines de topic '*' y '#'. Vacía se permite (exchanges fanout). */
	public static final String ROUTING_KEY = "^[A-Za-z0-9._*#-]*$";

	public static final String ROUTING_KEY_MENSAJE =
			"solo puede tener letras, números, '.', '_', '-' y los comodines '*' o '#'";

	public static final int LARGO_MIN = 3;
	public static final int LARGO_MAX = 100;

	private ReglasNombres() {
	}
}
