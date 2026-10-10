package cl.duoc.nexoramarket.ms_notificaciones.notificacion;

import java.text.NumberFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.duoc.nexoramarket.ms_notificaciones.common.ErrorTransitorioException;
import cl.duoc.nexoramarket.ms_notificaciones.common.ValidadorMensajes;
import cl.duoc.nexoramarket.ms_notificaciones.messaging.contract.EventoNegocio;

/**
 * Lógica de negocio de las notificaciones al cliente.
 * <p>
 * No sabe nada de RabbitMQ: recibe un evento ya convertido y "envía" la notificación
 * (en este proyecto se simula registrando el correo en el log).
 */
@Service
public class NotificacionService {

	private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
	private static final NumberFormat CLP = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CL"));

	private final ValidadorMensajes validador;
	private final boolean simularFalloCorreo;

	public NotificacionService(ValidadorMensajes validador,
			@Value("${nexora.notificaciones.simular-fallo-correo:false}") boolean simularFalloCorreo) {
		this.validador = validador;
		this.simularFalloCorreo = simularFalloCorreo;
	}

	/**
	 * Valida el evento y envía la notificación correspondiente.
	 *
	 * @return el texto enviado al cliente
	 * @throws cl.duoc.nexoramarket.ms_notificaciones.common.MensajeInvalidoException si el evento no trae los datos mínimos
	 * @throws ErrorTransitorioException si el servidor de correo no está disponible
	 */
	public String notificar(EventoNegocio evento) {
		validador.validar(evento);

		if (simularFalloCorreo) {
			throw new ErrorTransitorioException("Servidor de correo no disponible (fallo simulado)");
		}

		String texto = construirMensaje(evento);
		log.info("[NOTIFICACION] Correo enviado al cliente {} -> {}",
				evento.clienteId() != null ? evento.clienteId() : "(asociado al pedido " + evento.pedidoId() + ")",
				texto);
		return texto;
	}

	String construirMensaje(EventoNegocio evento) {
		return switch (evento.tipo()) {
			case "pedido.creado" -> "Recibimos tu pedido #" + evento.pedidoId()
					+ " por " + formatear(evento.monto()) + ". Estado: " + evento.estado() + ".";
			case "pago.aprobado" -> "Tu pago #" + evento.pagoId() + " del pedido #" + evento.pedidoId()
					+ " fue APROBADO (" + formatear(evento.monto()) + "). Pronto recibirás tu boleta.";
			case "pago.rechazado" -> "Tu pago del pedido #" + evento.pedidoId()
					+ " fue RECHAZADO. Puedes intentarlo con otro medio de pago.";
			default -> "Hay novedades en tu pedido #" + evento.pedidoId() + " (" + evento.tipo() + ").";
		};
	}

	private String formatear(Double monto) {
		return monto == null ? "monto no informado" : CLP.format(monto);
	}
}
