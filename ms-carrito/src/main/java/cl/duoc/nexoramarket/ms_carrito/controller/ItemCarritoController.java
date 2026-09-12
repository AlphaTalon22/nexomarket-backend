package cl.duoc.nexoramarket.ms_carrito.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.nexoramarket.ms_carrito.model.ItemCarrito;
import cl.duoc.nexoramarket.ms_carrito.repository.ItemCarritoRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/carrito")
public class ItemCarritoController {

    @Autowired
    private ItemCarritoRepository itemCarritoRepository;

    @GetMapping
    public List<ItemCarrito> listarTodos() {
        return itemCarritoRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemCarrito> buscarPorId(@PathVariable Long id) {
        return itemCarritoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ItemCarrito> agregar(@Valid @RequestBody ItemCarrito item) {
        ItemCarrito guardado = itemCarritoRepository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemCarrito> actualizar(@PathVariable Long id, @Valid @RequestBody ItemCarrito datos) {
        return itemCarritoRepository.findById(id)
                .map(item -> {
                    item.setProductoId(datos.getProductoId());
                    item.setNombreProducto(datos.getNombreProducto());
                    item.setPrecioUnitario(datos.getPrecioUnitario());
                    item.setCantidad(datos.getCantidad());
                    return ResponseEntity.ok(itemCarritoRepository.save(item));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!itemCarritoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        itemCarritoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}