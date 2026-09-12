package cl.duoc.nexoramarket.ms_pedidos.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pedidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El ID del cliente es obligatorio")
    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @NotNull(message = "El total es obligatorio")
    @Min(value = 0, message = "El total no puede ser negativo")
    @Column(nullable = false)
    private Double total;

    @NotBlank(message = "El estado es obligatorio")
    @Column(nullable = false)
    private String estado;
}