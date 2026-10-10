package cl.duoc.nexoramarket.ms_rabbit_admin.exception;

/** Se intentó crear una cola o exchange que ya existe (HTTP 409). */
public class RecursoYaExisteException extends RuntimeException {

	public RecursoYaExisteException(String message) {
		super(message);
	}
}
