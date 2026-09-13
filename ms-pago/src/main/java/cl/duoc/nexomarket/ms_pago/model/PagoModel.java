package cl.duoc.nexoramarket.ms_pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El ID del pedido es obligatorio")
    @Column(nullable = false)
    private Long pedidoId;

    @NotNull(message = "El monto es obligatorio")
    @Min(value = 1, message = "El monto debe ser mayor a 0")
    @Column(nullable = false)
    private Double monto;

    @NotNull(message = "El método de pago es obligatorio")
    @Column(nullable = false)
    private String metodoPago; // Ejemplo: DEBITO, CREDITO, TRANSFERENCIA

    @Column(nullable = false)
    private String estado; // Ejemplo: PENDIENTE, APROBADO, RECHAZADO

    @Column(nullable = false)
    private LocalDateTime fechaPago;
}