# Nexora Market — Backend

Backend de microservicios para **Nexora Market**, proyecto desarrollado para el ramo **Desarrollo Cloud Native I (DSY1107)** de DUOC UC.

El backend está compuesto por 4 microservicios de negocio y un API Gateway, desplegados en instancias EC2 independientes dentro de una misma VPC de AWS, con autenticación centralizada mediante **Azure Entra ID (OAuth2 / JWT)**.

## Arquitectura

```
                         ┌─────────────────────────┐
                         │   Azure Entra ID (AAD)   │
                         │  Emisor y validador JWT  │
                         └────────────┬─────────────┘
                                      │ valida token
                                      ▼
        Cliente / Frontend ──────► API Gateway (8080)
     (Angular + MSAL, JWT)         Spring Cloud Gateway
                                      │
        ┌───────────────┬────────────┼────────────┬───────────────┐
        ▼               ▼            ▼            ▼               │
  ms-productos     ms-carrito   ms-pedidos     ms-pago             │
    (8081)           (8082)       (8083)        (8084)             │
        │               │            │            │                │
        └───────────────┴────────────┴────────────┘                │
                         │                                         │
                         ▼                                         │
              Amazon RDS (MySQL) — base de datos compartida ───────┘
```

Todas las instancias (gateway + 4 microservicios) viven en la misma VPC y comparten un único Security Group, lo que les permite comunicarse entre sí por IP privada sin exponer los microservicios directamente a internet. Solo el API Gateway está pensado para ser consumido públicamente.

## Servicios

| Servicio | Puerto | Ruta expuesta en el Gateway | Descripción |
|---|---|---|---|
| `api-gatewey` | 8080 | — (es el punto de entrada) | Spring Cloud Gateway. Enruta las peticiones a cada microservicio por IP privada. |
| `ms-productos` | 8081 | `/productos/**` | Catálogo de productos. |
| `ms-carrito` | 8082 | `/carrito/**` | Carrito de compras. |
| `ms-pedidos` | 8083 | `/pedidos/**` | Gestión de pedidos. |
| `ms-pago` | 8084 | `/pagos/**` | Procesamiento de pagos. |

Cada microservicio corre en su propia instancia EC2 (Amazon Linux 2023, t3.micro), todas dentro de la misma VPC y Security Group (`nexora-microservicios-sg`), lo que permite que el Gateway les llegue por IP privada.

> Las IPs privadas de las instancias son estables mientras no se eliminen (se mantienen entre apagados/encendidos dentro de la misma VPC), pero las IPs públicas pueden cambiar si no se asignó una Elastic IP. Antes de reconfigurar el Gateway tras un reinicio de instancias, confirma las IPs privadas actuales desde la consola de AWS.

## Stack tecnológico

- **Java 17** (Amazon Corretto)
- **Spring Boot** con Spring Web, Spring Data JPA, Spring Security
- **Spring Cloud Gateway** (WebFlux/Netty) para el enrutamiento
- **Spring Security OAuth2 Resource Server** para validar JWT de Azure Entra ID en cada microservicio
- **Amazon RDS (MySQL)** como base de datos compartida
- **AWS EC2** (Amazon Linux 2023) para el despliegue de cada servicio
- Maven Wrapper (`./mvnw`) para build y ejecución

## Seguridad

Todos los microservicios (incluido `ms-pago`) validan el token JWT emitido por Azure Entra ID mediante `spring-boot-starter-oauth2-resource-server`. La configuración base en cada servicio es:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/<TENANT_ID>/v2.0
```

Y el filtro de seguridad exige autenticación en cualquier endpoint:

```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

La API está registrada en Azure Entra ID como `NexoraMarket-API`, con el scope delegado `access_as_user` que el frontend (o cualquier cliente, como Postman) debe solicitar para obtener un token válido.

## Configuración de cada instancia

Cada microservicio requiere un archivo `application-secrets.properties` (no versionado, fuera del control de Git) con las credenciales de conexión a la base de datos compartida:

```properties
spring.datasource.url=jdbc:mysql://<host-rds>:3306/<nombre_bd>
spring.datasource.username=<usuario>
spring.datasource.password=<clave>
```

Y en `application.properties`, la referencia al issuer de Azure para validar los JWT (ver sección Seguridad).

## Despliegue (resumen)

Cada instancia EC2 se levanta de forma independiente:

```bash
# Instalar Java 17 y git
sudo dnf install -y java-17-amazon-corretto git

# Clonar el repositorio
git clone https://github.com/AlphaTalon22/nexomarket-backend.git

# Ir a la carpeta del microservicio correspondiente
cd nexomarket-backend/<ms-nombre>

# Crear application-secrets.properties con las credenciales de la BD

# Ejecutar en segundo plano (persiste tras cerrar la sesión SSH)
nohup ./mvnw spring-boot:run > app.log 2>&1 &
```

> Importante: los procesos levantados con `nohup` **no sobreviven a un reinicio de la instancia**. Si una EC2 se detiene y se vuelve a iniciar, hay que volver a ejecutar el comando de arranque manualmente en cada microservicio afectado.

El API Gateway (`api-gatewey`) se despliega igual, pero su `application.yml` debe apuntar a las **IPs privadas** de cada microservicio (no `localhost`):

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: ms-productos
          uri: http://<ip-privada-productos>:8081
          predicates:
            - Path=/productos/**
        # ... resto de las rutas
```

## Pruebas

Todas las rutas se probaron end-to-end con Postman contra la IP pública del Gateway:

- Sin token → `401 Unauthorized`
- Con token válido (obtenido vía OAuth2 Authorization Code contra Azure AD, scope `access_as_user`) → `200 OK` con datos reales de cada microservicio.

Configuración OAuth2 usada en Postman (pestaña Authorization → OAuth 2.0):

- **Auth URL:** `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/authorize`
- **Token URL:** `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/token`
- **Client ID:** el de `NexoraMarket-API`
- **Scope:** `api://<CLIENT_ID_API>/access_as_user openid profile offline_access`

## Estructura del repositorio

```
nexomarket-backend/
├── api-gatewey/       # API Gateway (Spring Cloud Gateway)
├── ms-productos/      # Microservicio de productos
├── ms-carrito/        # Microservicio de carrito
├── ms-pedidos/        # Microservicio de pedidos
└── ms-pago/           # Microservicio de pagos
```

## Estado del proyecto

- [x] 4 microservicios desarrollados y desplegados en EC2
- [x] API Gateway desplegado, enrutando por IP privada dentro de la VPC
- [x] Seguridad JWT/Azure Entra ID en los 4 microservicios
- [x] Pruebas end-to-end vía Postman (401 sin token / 200 con token) en las 4 rutas
- [ ] Frontend Angular (en desarrollo, repositorio separado `nexomarket-frontend`)
- [ ] Documentación EP2 y presentación final
