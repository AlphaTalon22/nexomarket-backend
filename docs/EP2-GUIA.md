# Nexora Market — Guía de implementación EP2 (RabbitMQ + ajustes EP1)

Esta guía acompaña los cambios de la rama `ep2-rabbitmq`. Sigue el mismo orden del plan de 10 pasos.
Leyenda: ✅ ya está en el código · 🔧 lo hacen ustedes en la consola (Azure / AWS) · 📸 sacar captura para la entrega.

---

## 0. Arquitectura final

```
            HTTPS                        HTTPS + JWT                      HTTP + JWT
Angular (S3 + CloudFront) ──► API Manager (AWS API Gateway, JWT authorizer) ──► api-gatewey / BFF (EC2 :8080, valida JWT)
        │                                                                              │
        │ OIDC Authorization Code + PKCE                                              ├─► ms-productos :8081 ─┐
        ▼                                                                              ├─► ms-carrito   :8082 ─┤
Microsoft Entra External ID (tenant + user flow de registro)                          ├─► ms-pedidos   :8083 ─┼─► Amazon RDS (MySQL)
                                                                                       ├─► ms-pago      :8084 ─┘
                                                                                       └─► ms-rabbit-admin :8085
                                                                                                │
   ms-pedidos ─ pedido.creado ─────────┐                                                        ▼
   ms-pago ──── pago.aprobado/rechazado ┼─► RabbitMQ (clúster 2 nodos, docker compose en EC2) ◄── administra
   ms-pago ──── boleta.generar ─────────┘        │
                                                  ├─► ms-pedidos        (actualiza estado del pedido)
                                                  └─► ms-notificaciones (avisa al cliente + genera boleta)
```

### Topología RabbitMQ (3 colas + 3 DLQ, exchanges direct y topic)

| Exchange | Tipo | Routing key | Cola destino | Consumidor | DLQ |
|---|---|---|---|---|---|
| `nexora.eventos.topic` | **topic** | `pedido.*`, `pago.*` | `nexora.notificaciones.q` | ms-notificaciones | `nexora.notificaciones.dlq` |
| `nexora.eventos.topic` | **topic** | `pago.*` | `nexora.pedidos.pagos.q` | ms-pedidos | `nexora.pedidos.pagos.dlq` |
| `nexora.documentos.direct` | **direct** | `boleta.generar` | `nexora.boletas.q` | ms-notificaciones | `nexora.boletas.dlq` |
| `nexora.dlx.direct` | **direct** (DLX) | `*.dead` por cola | las 3 DLQ | — (se revisan en el dashboard) | — |

| Productor | Cuándo | Mensaje |
|---|---|---|
| ms-pedidos | `POST /pedidos` | `pedido.creado` → topic |
| ms-pago | `POST /pagos/procesar` | `pago.aprobado` o `pago.rechazado` → topic |
| ms-pago | pago APROBADO | `boleta.generar` → direct |

Políticas: colas durables, máximo 10.000 mensajes por cola principal, DLQ con retención de 24 h (TTL).
Si se publica algo que no llega a ninguna cola, el productor lo registra en el log (`[MENSAJE NO ENRUTADO]`).

### Estrategia de ACK (pauta: 20 %)

Todos los consumidores usan `acknowledge-mode: manual` y deciden explícitamente:

| Situación | Acción | Resultado |
|---|---|---|
| Procesado OK | `basicAck` | mensaje eliminado |
| Mensaje inválido / pedido inexistente (no recuperable) | `basicNack(requeue=false)` | va a la DLQ + log `[NACK -> DLQ]` |
| Error transitorio (BD, correo), primer intento | `basicNack(requeue=true)` | 1 reintento + log `[NACK -> REINTENTO]` |
| Error transitorio, ya reintentado | `basicNack(requeue=false)` | va a la DLQ + log `[NACK -> DLQ]` |
| JSON mal formado | lo rechaza el contenedor de Spring | va a la DLQ |

No se usa `listener.simple.retry` de Spring junto con ACK manual (son dos mecanismos que se pisan); el reintento
se decide en el consumidor con el flag `redelivered` del mensaje. Consecuencia conocida: si un consumidor se
desconecta con un mensaje sin confirmar, ese mensaje vuelve con `redelivered=true`, y si su siguiente intento
tiene un error transitorio va directo a la DLQ (no se pierde: queda ahí para revisión).

---

## 1. Ajustes según EP1

### 1.a "Flujo de usuario para que los usuarios creen su cuenta" 🔧

En un tenant normal de Entra ID los usuarios no se pueden registrar solos. Microsoft resuelve esto con
**Entra External ID (tenant externo) + un *user flow* "Sign up and sign in"** (es el reemplazo de Azure AD B2C).

1. Portal de Entra (entra.microsoft.com) → *Manage tenants* → *Create* → **External** (usar la prueba gratuita
   de 30 días si no tienen suscripción). Anotar **Tenant ID** y el **subdominio** (`<subdominio>.ciamlogin.com`). 📸
2. En el tenant externo, *App registrations*:
   - **NexoraMarket-API**: *Expose an API* → Application ID URI `api://<CLIENT_ID_API>` → scope `access_as_user`.
     En *Manifest* poner `"requestedAccessTokenVersion": 2` dentro de `"api"` (en el formato antiguo se llama
     `accessTokenAcceptedVersion`), para que los tokens salgan en versión 2.
   - **NexoraMarket-Frontend** (SPA): redirect URIs `http://localhost:4200` y `https://<dominio-cloudfront>`.
     *API permissions* → agregar `NexoraMarket-API / access_as_user` → **Grant admin consent**. 📸
   - (Postman) en la app que use Postman, plataforma *Mobile and desktop* con `https://oauth.pstmn.io/v1/callback`.
3. *External Identities → User flows → New user flow* → "SignUpSignIn", proveedor **Email with password**,
   atributos: *Display Name*. 📸
4. En el user flow → *Applications* → **Add application** → NexoraMarket-Frontend. 📸
5. Probar: en el frontend, botón **Crear cuenta** (ver `frontend-referencia/`), registrar un usuario y mostrar
   que aparece en *Users* del tenant. 📸
6. Backend: el emisor cambia. En el `application-secrets.properties` de **cada** microservicio y del gateway:
   ```properties
   NEXORA_JWT_ISSUER_URI=https://<TENANT_ID>.ciamlogin.com/<TENANT_ID>/v2.0
   NEXORA_JWT_AUDIENCE=<CLIENT_ID_API>
   ```
   Verificación: abrir `https://<TENANT_ID>.ciamlogin.com/<TENANT_ID>/v2.0/.well-known/openid-configuration`
   en el navegador; el campo `issuer` debe ser exactamente el valor de `NEXORA_JWT_ISSUER_URI`. Decodificar un
   token en https://jwt.ms y confirmar `iss` y `aud`.

> Plan B si no pueden crear el tenant externo: en el tenant actual, *External Identities → External collaboration
> settings* → habilitar *guest self-service sign up via user flows*, crear un user flow de registro y asociarle la app
> SPA. Los usuarios quedan como invitados del tenant y el backend no cambia de emisor.

### 1.b "Evidencia de cada ruta devolviendo el JSON esperado" ✅ + 📸

- Colección Postman: `docs/NexoraMarket-EP2.postman_collection.json` (carpetas 1 y 2) con tests
  automáticos de código HTTP y formato JSON. Ejecutar con *Run collection* y capturar *Test Results*. 📸
- Frontend: una pantalla por ruta que muestre el JSON (ver `frontend-referencia/`). 📸

### 1.c "El BFF debe validar el token igual que el API Manager" ✅

- `api-gatewey` ahora es *resource server*: valida firma, emisor, **audiencia** y vigencia antes de enrutar
  (`config/SecurityConfig.java`). Sin token → 401 sin llegar al backend.
- Todos los microservicios validan también la **audiencia** (antes solo emisor).
- Se corrigió el paquete del gateway y de ms-pago (carpeta y `package` no coincidían; los tests no compilaban).

### 1.d API Manager en la nube + frontend desplegado (pauta de presentación) 🔧

**API Manager = AWS API Gateway (HTTP API)**, delante del BFF:

1. Asignar una **Elastic IP** a la EC2 del gateway (en AWS Academy la IP pública cambia en cada sesión).
2. API Gateway → *Create API* → **HTTP API** → integración **HTTP**, método `ANY`,
   URL `http://<ELASTIC-IP>:8080/{proxy}` → ruta `ANY /{proxy+}` → stage `$default` (auto-deploy). 📸
3. *Authorization* → *Create JWT authorizer*: identity source `$request.header.Authorization`,
   issuer = valor de `NEXORA_JWT_ISSUER_URI`, audience = `<CLIENT_ID_API>` → adjuntarlo a `ANY /{proxy+}`. 📸
4. *CORS*: origins `https://<dominio-cloudfront>` y `http://localhost:4200`; headers `authorization,content-type`;
   methods `GET,POST,PUT,DELETE,OPTIONS`.
5. La *Invoke URL* (`https://xxxx.execute-api.us-east-1.amazonaws.com`) es la que usa el frontend.

**Frontend en la nube (HTTPS es obligatorio: Entra no acepta redirect URIs `http://` salvo localhost):**

1. `ng build` → subir el contenido de `dist/<proyecto>/browser/` a un bucket S3 privado.
2. CloudFront → *Create distribution* → origen el bucket con **Origin access control** (aceptar la política
   sugerida para el bucket) → *Default root object* `index.html` → *Error pages*: 403 y 404 → `/index.html`, 200.
3. Agregar `https://<dominio>.cloudfront.net` como redirect URI de la SPA (1.a) y en el gateway:
   `NEXORA_FRONTEND_URL=https://<dominio>.cloudfront.net` en su `application-secrets.properties`. 📸

---

## 2. Levantar RabbitMQ en EC2 🔧

Ver `infra/rabbitmq-cluster/README.md`. Resumen: EC2 nueva **t3.medium** "rabbitmq-server" en la misma VPC,
Docker + Compose, `docker compose up -d`, `rabbitmqctl cluster_status` muestra 2 nodos. 📸

> La guía del curso trae dos errores en `rabbitmq.conf` que impiden arrancar: carga un `definitions.json` que no
> existe y usa `queue_master_location`, que no es una opción válida. El archivo de este repo ya está corregido.
>
> Con 2 nodos se desactiva Khepri (`RABBITMQ_FEATURE_FLAGS=-khepri_db`, nuevo almacén de metadatos de RabbitMQ 4
> basado en Raft): con Khepri, apagar cualquiera de los dos nodos bloquearía la creación de colas. Detalle y demo de
> tolerancia a fallos en `infra/rabbitmq-cluster/README.md`.

## 3. Colas y exchanges a implementar ✅

Tabla de la sección 0. Los nombres viven **solo** en `application.yml` (`nexora.messaging.*`) y se leen con un
`@ConfigurationProperties` validado (`MessagingProperties`) o con `${...}` en los `@RabbitListener`.

## 4-5. Productores, consumidores, exchanges, bindings y colas ✅

| Microservicio | Configuración | Productor | Consumidor | Negocio |
|---|---|---|---|---|
| ms-pedidos | `messaging/config/*` | `RabbitPedidoEventPublisher` | `PagoEventosListener` | `PedidoEstadoService` |
| ms-pago | `messaging/config/*` | `RabbitPagoEventPublisher` | — | (controlador existente) |
| ms-notificaciones (nuevo) | `config/*` | — | `NotificacionListener`, `BoletaListener` | `NotificacionService`, `BoletaService` |

- Los controladores solo llaman a una **interfaz** (`PedidoEventPublisher`, `PagoEventPublisher`); no conocen RabbitMQ.
- Si RabbitMQ está caído, el pedido/pago **igual se guarda** y el error queda en el log: la mensajería no rompe
  la lógica existente.
- Consumidores agrupados por dominio: `notificacion/`, `boleta/`, `dlq/` en ms-notificaciones.

## 6. DLX, DLQ y ACK ✅

Sección 0. Además, ms-notificaciones trae `DeadLetterAuditListener` (apagado por defecto): con
`nexora.messaging.dlq-monitor.enabled=true` consume las DLQ y registra en el log la cola de origen y el motivo
(`x-death`). Se deja apagado para que en la demo los mensajes se vean acumulados en las DLQ.

## 7. Clúster y Rabbit Admin ✅ + 🔧

`ms-rabbit-admin` (puerto 8085, protegido con JWT, expuesto en el gateway como `/rabbit-admin/**`):

| Método | Ruta | Respuesta |
|---|---|---|
| POST | `/rabbit-admin/queues` | 201 · 400 si el nombre es vacío/inválido/`amq.*` · 409 si existe |
| GET | `/rabbit-admin/queues/{nombre}` | 200 (mensajes, consumidores) · 404 |
| DELETE | `/rabbit-admin/queues/{nombre}` | 204 · 404 |
| POST | `/rabbit-admin/exchanges` | 201 · 400 (tipo DIRECT/TOPIC/FANOUT) · 409 |
| DELETE | `/rabbit-admin/exchanges/{nombre}` | 204 · 404 |
| POST | `/rabbit-admin/bindings` | 201 · 404 si la cola o el exchange no existen |
| DELETE | `/rabbit-admin/bindings?cola=&exchange=&routingKey=` | 204 · 404 |

Toda la lógica está en `RabbitAdminService`; el controlador no importa ninguna clase de Spring AMQP. Los DTO
(`CrearColaRequest`, `CrearExchangeRequest`, `BindingRequest`) validan con Bean Validation y los errores salen
con un formato JSON uniforme (`GlobalExceptionHandler`).

## 8. Pruebas funcionales 📸

**Unitarias** (no necesitan RabbitMQ ni BD):
```bash
cd ms-notificaciones && ./mvnw test
cd ms-rabbit-admin   && ./mvnw test
cd ms-pedidos && ./mvnw test -Dtest='PagoEventosListenerTest,PedidoEstadoServiceTest'
cd ms-pago    && ./mvnw test -Dtest='RabbitPagoEventPublisherTest'
```
(Los `contextLoads` que ya existían en ms-pedidos/ms-pago levantan la aplicación completa y necesitan la BD.)

**End-to-end** con la colección Postman, en orden:

| # | Prueba | Evidencia esperada |
|---|---|---|
| 1 | Sin token / token inválido / token válido | 401 / 401 / 200 (gateway y API Manager) |
| 2 | GET de las 4 rutas | 200 + arreglo JSON |
| 3 | Crear pedido | log ms-pedidos `[PUBLICADO] pedido.creado` · log ms-notificaciones `[NOTIFICACION]` |
| 4 | Pagar pedido | pedido pasa a `PAGADO` · log `[BOLETA] Boleta generada` · archivo `boletas/boleta-pago-N.txt` |
| 5 | Pago de pedido 999999 | log ms-pedidos `[NACK -> DLQ]` · 1 mensaje en `nexora.pedidos.pagos.dlq` |
| 6 | Reintento | iniciar ms-notificaciones con `--nexora.notificaciones.simular-fallo-correo=true`, crear pedido → `[NACK -> REINTENTO]` y luego `[NACK -> DLQ]` |
| 7 | Mensaje malformado | Dashboard → Exchanges → `nexora.documentos.direct` → *Publish message* con routing key `boleta.generar` y cuerpo `esto no es json` → aparece en `nexora.boletas.dlq` |
| 8 | Admin API | carpeta 5 de Postman: 201/409/400/204/404/401 |
| 9 | Clúster | `rabbitmqctl cluster_status` + *Overview → Nodes* con 2 nodos |

## 9. Preparar la presentación (pauta EP2 presentación)

| La pauta pide mostrar… | Qué mostrar |
|---|---|
| Instancia de API Manager en la nube | API Gateway (HTTP API) en la consola AWS |
| Configuración del API Manager hacia el backend | ruta `ANY /{proxy+}` + integración HTTP al gateway |
| Frontend consume a través del API Manager | DevTools → Network: llamadas a `execute-api...` con `Authorization: Bearer` |
| API Manager valida JWT | Postman carpeta 1 contra la Invoke URL (401/200) + el authorizer en consola |
| Tenant IDaaS y usuarios registrados | tenant externo + *Users* con el usuario creado desde "Crear cuenta" |
| Login OAuth 2.0/OIDC con JWT válido | login en vivo + token en jwt.ms |
| Backend y frontend desplegados e integrados | URL de CloudFront funcionando + EC2 en ejecución |
| Dos nodos RabbitMQ en clúster | `cluster_status` + pestaña Nodes |
| Tres colas con sus DLQ funcionando | pestaña Queues + pruebas 5, 6 y 7 |
| Exchanges direct y topic | pestaña Exchanges (`nexora.eventos.topic`, `nexora.documentos.direct`, `nexora.dlx.direct`) |
| docker-compose en la nube | SSH a la EC2: `docker compose ps` |

## 10. Entrega

Encargo y presentación: subir **los mismos** enlaces de GitHub (backend y frontend) + carpeta de evidencias
(capturas de Postman *Test Results*, dashboard RabbitMQ, consola AWS/Azure y frontend).

---

## Anexo A — `application-secrets.properties` de cada servicio (no se sube a GitHub)

```properties
# ms-pedidos, ms-pago (además de las credenciales de la BD que ya tienen)
spring.rabbitmq.addresses=<ip-privada-rabbit>:5672,<ip-privada-rabbit>:5673
spring.rabbitmq.username=nexora
spring.rabbitmq.password=<clave del .env>
NEXORA_JWT_ISSUER_URI=<emisor>          # solo si cambian de tenant (1.a)
NEXORA_JWT_AUDIENCE=<CLIENT_ID_API>

# ms-notificaciones, ms-rabbit-admin (corren en la EC2 de RabbitMQ)
spring.rabbitmq.addresses=localhost:5672,localhost:5673
spring.rabbitmq.username=nexora
spring.rabbitmq.password=<clave del .env>

# api-gatewey
RABBIT_ADMIN_URI=http://<ip-privada-rabbit>:8085
NEXORA_FRONTEND_URL=https://<dominio>.cloudfront.net
```

El archivo va en `src/main/resources/` de cada microservicio (igual que hoy).

## Anexo B — Arranque en EC2

```bash
git pull && git checkout ep2-rabbitmq
cd <microservicio>
chmod +x mvnw                                        # el repo guarda mvnw sin permiso de ejecución
nohup ./mvnw spring-boot:run > app.log 2>&1 &
tail -f app.log
```

Orden: RabbitMQ (docker) → ms-notificaciones → ms-pedidos, ms-pago, ms-productos, ms-carrito →
ms-rabbit-admin → api-gatewey. ms-notificaciones primero para que sus colas existan antes de publicar.

## Anexo C — Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange'` | la cola ya existía creada con otros argumentos | borrarla (dashboard o `DELETE /rabbit-admin/queues/{nombre}`) y reiniciar el consumidor |
| `ACCESS_REFUSED ... user 'guest'` | `guest` solo funciona desde localhost | usar el usuario del `.env` |
| El nodo 2 no aparece en el clúster | cookie distinta o arrancó antes que el nodo 1 | misma `RABBITMQ_ERLANG_COOKIE` y unión manual (infra README) |
| 401 con token válido después de cambiar de tenant | emisor o audiencia no coinciden | revisar `iss`/`aud` en jwt.ms vs `NEXORA_JWT_*` |
| El navegador muestra error CORS en el frontend desplegado | origen no permitido | `NEXORA_FRONTEND_URL` en el gateway y CORS del API Gateway |
| En local (frontend → gateway directo) un token vencido aparece como "error CORS" y no como 401 | el 401 lo responde Spring Security antes de que el gateway agregue los headers CORS | es esperado; en producción el API Manager agrega CORS a todas sus respuestas. Cerrar sesión y volver a entrar |
| `./mvnw: Permission denied` | mvnw sin permiso de ejecución | `chmod +x mvnw` |
