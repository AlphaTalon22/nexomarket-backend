package cl.duoc.nexoramarket.ms_notificaciones.common;

/**
 * Error NO recuperable: el mensaje está mal formado o le faltan datos.
 * Reintentarlo daría el mismo resultado, así que el consumidor lo envía directo a la DLQ.
 */
public class MensajeInvalidoException extends RuntimeException {

	public MensajeInvalidoException(String message) {
		super(message);
	}
}
