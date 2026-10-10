package cl.duoc.nexoramarket.ms_rabbit_admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.nexoramarket.ms_rabbit_admin.dto.BindingRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.CrearColaRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.CrearExchangeRequest;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.ReglasNombres;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.Respuestas.BindingResponse;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.Respuestas.ColaResponse;
import cl.duoc.nexoramarket.ms_rabbit_admin.dto.Respuestas.ExchangeResponse;
import cl.duoc.nexoramarket.ms_rabbit_admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * API REST de administración de RabbitMQ (expuesta en el Gateway como /rabbit-admin/**).
 *
 * <pre>
 * POST   /rabbit-admin/queues                 crear cola           201 / 400 / 409
 * GET    /rabbit-admin/queues/{nombre}        ver estado de cola   200 / 404
 * DELETE /rabbit-admin/queues/{nombre}        eliminar cola        204 / 404
 * POST   /rabbit-admin/exchanges              crear exchange       201 / 400 / 409
 * DELETE /rabbit-admin/exchanges/{nombre}     eliminar exchange    204 / 404
 * POST   /rabbit-admin/bindings               crear binding        201 / 400 / 404
 * DELETE /rabbit-admin/bindings?cola=&exchange=&routingKey=   eliminar binding  204 / 400 / 404
 * </pre>
 */
@RestController
@RequestMapping("/rabbit-admin")
public class RabbitAdminController {

	private final RabbitAdminService adminService;

	public RabbitAdminController(RabbitAdminService adminService) {
		this.adminService = adminService;
	}

	// ----------------------------- Colas -----------------------------

	@PostMapping("/queues")
	public ResponseEntity<ColaResponse> crearCola(@Valid @RequestBody CrearColaRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminService.crearCola(request));
	}

	@GetMapping("/queues/{nombre}")
	public ColaResponse obtenerCola(@PathVariable
			@Pattern(regexp = ReglasNombres.NOMBRE_RECURSO, message = ReglasNombres.NOMBRE_RECURSO_MENSAJE)
			String nombre) {
		return adminService.obtenerCola(nombre);
	}

	@DeleteMapping("/queues/{nombre}")
	public ResponseEntity<Void> eliminarCola(@PathVariable
			@Pattern(regexp = ReglasNombres.NOMBRE_RECURSO, message = ReglasNombres.NOMBRE_RECURSO_MENSAJE)
			String nombre) {
		adminService.eliminarCola(nombre);
		return ResponseEntity.noContent().build();
	}

	// --------------------------- Exchanges ---------------------------

	@PostMapping("/exchanges")
	public ResponseEntity<ExchangeResponse> crearExchange(@Valid @RequestBody CrearExchangeRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminService.crearExchange(request));
	}

	@DeleteMapping("/exchanges/{nombre}")
	public ResponseEntity<Void> eliminarExchange(@PathVariable
			@Pattern(regexp = ReglasNombres.NOMBRE_RECURSO, message = ReglasNombres.NOMBRE_RECURSO_MENSAJE)
			String nombre) {
		adminService.eliminarExchange(nombre);
		return ResponseEntity.noContent().build();
	}

	// ---------------------------- Bindings ----------------------------

	@PostMapping("/bindings")
	public ResponseEntity<BindingResponse> crearBinding(@Valid @RequestBody BindingRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminService.crearBinding(request));
	}

	/**
	 * DELETE sin cuerpo: el binding se identifica por parámetros de consulta
	 * (?cola=...&amp;exchange=...&amp;routingKey=...), que se validan con las mismas reglas del POST.
	 */
	@DeleteMapping("/bindings")
	public ResponseEntity<Void> eliminarBinding(@Valid @ModelAttribute BindingRequest request) {
		adminService.eliminarBinding(request);
		return ResponseEntity.noContent().build();
	}
}
