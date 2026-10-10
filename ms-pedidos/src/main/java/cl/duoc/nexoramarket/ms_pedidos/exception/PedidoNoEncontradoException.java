package cl.duoc.nexoramarket.ms_pedidos.exception;

/** Llegó un pago de un pedido que no existe: no se puede aplicar, va a la DLQ para revisión. */
public class PedidoNoEncontradoException extends MensajeInvalidoException {

	public PedidoNoEncontradoException(Long pedidoId) {
		super("No existe el pedido " + pedidoId);
	}
}
