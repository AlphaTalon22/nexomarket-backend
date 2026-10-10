package cl.duoc.nexoramarket.ms_pagos.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import cl.duoc.nexoramarket.ms_pagos.messaging.publisher.PagoEventPublisher;
import cl.duoc.nexoramarket.ms_pagos.model.Pago;
import cl.duoc.nexoramarket.ms_pagos.repository.PagoRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/pagos")
public class PagoController {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private PagoEventPublisher pagoEventPublisher;

    @GetMapping
    public List<Pago> listarTodos() {
        return pagoRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pago> buscarPorId(@PathVariable Long id) {
        return pagoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<Pago> buscarPorPedidoId(@PathVariable Long pedidoId) {
        return pagoRepository.findByPedidoId(pedidoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/procesar")
    public ResponseEntity<Pago> procesarPago(@Valid @RequestBody Pago pago) {
        // Aquí iría la lógica de integración con una pasarela real.
        // Por ahora simularemos que el pago siempre es aprobado.
        pago.setEstado("APROBADO");
        pago.setFechaPago(LocalDateTime.now());
        
        Pago guardado = pagoRepository.save(pago);

        // Tareas asíncronas (no bloquean ni afectan la respuesta):
        // 1) avisar el resultado del pago (actualiza el pedido y notifica al cliente)
        pagoEventPublisher.publicarResultadoPago(guardado);
        // 2) si fue aprobado, pedir la generación de la boleta
        if ("APROBADO".equals(guardado.getEstado())) {
            pagoEventPublisher.solicitarBoleta(guardado);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }
}