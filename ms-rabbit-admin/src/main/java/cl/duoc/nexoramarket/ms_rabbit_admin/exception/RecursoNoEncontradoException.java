package cl.duoc.nexoramarket.ms_rabbit_admin.exception;

/** La cola o el exchange indicado no existe en RabbitMQ (HTTP 404). */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String message) {
		super(message);
	}
}
