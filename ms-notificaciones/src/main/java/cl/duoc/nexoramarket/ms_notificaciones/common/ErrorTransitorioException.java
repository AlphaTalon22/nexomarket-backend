package cl.duoc.nexoramarket.ms_notificaciones.common;

/**
 * Error recuperable: falló algo externo y temporal (servidor de correo caído, disco ocupado...).
 * El consumidor reencola el mensaje UNA vez; si vuelve a fallar, lo envía a la DLQ.
 */
public class ErrorTransitorioException extends RuntimeException {

	public ErrorTransitorioException(String message) {
		super(message);
	}

	public ErrorTransitorioException(String message, Throwable cause) {
		super(message, cause);
	}
}
