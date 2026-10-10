package cl.duoc.nexoramarket.ms_pedidos.exception;

/**
 * Error NO recuperable al procesar un mensaje (datos inválidos o inconsistentes).
 * Reintentar daría el mismo resultado: el consumidor lo envía directo a la DLQ.
 */
public class MensajeInvalidoException extends RuntimeException {

	public MensajeInvalidoException(String message) {
		super(message);
	}
}
