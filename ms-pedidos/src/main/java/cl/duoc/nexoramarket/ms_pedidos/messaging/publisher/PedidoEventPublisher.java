package cl.duoc.nexoramarket.ms_pedidos.messaging.publisher;

import cl.duoc.nexoramarket.ms_pedidos.model.Pedido;

/**
 * Lo único que el controlador sabe de la mensajería: "avisa que se creó este pedido".
 * Cómo y dónde se publica queda encapsulado en la implementación.
 */
public interface PedidoEventPublisher {

	void publicarPedidoCreado(Pedido pedido);
}
