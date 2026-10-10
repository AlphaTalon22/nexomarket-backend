# Clúster RabbitMQ (2 nodos) en EC2

## 1. Preparar la EC2 (Amazon Linux 2023, recomendado t3.medium)

```bash
sudo dnf install -y docker git java-17-amazon-corretto
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user        # cerrar sesión SSH y volver a entrar

# Docker Compose v2 (no viene con el paquete docker de Amazon Linux)
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

Security Group de esta EC2:

| Puerto | Origen | Para qué |
|---|---|---|
| 5672, 5673 | el Security Group de los microservicios | AMQP (nodo 1 y nodo 2) |
| 15672, 15673 | Mi IP | Management UI de cada nodo |
| 8085 | el Security Group del api-gatewey | ms-rabbit-admin |
| 22 | Mi IP | SSH |

## 2. Levantar el clúster

```bash
cd nexomarket-backend/infra/rabbitmq-cluster
cp .env.example .env && nano .env       # cambiar usuario, clave y cookie
docker compose up -d
docker compose ps                       # ambos deben quedar "healthy"
```

## 3. Verificar que es UN clúster de 2 nodos

```bash
docker exec rabbitmq-1 rabbitmqctl cluster_status
```

En la salida, `Running Nodes` debe listar **rabbit@rabbitmq-1 y rabbit@rabbitmq-2**.
En la UI (`http://<ip-publica>:15672`, pestaña *Overview → Nodes*) se ven los dos nodos.

### Si el nodo 2 quedó como clúster separado (unión manual de respaldo)

```bash
docker exec rabbitmq-2 rabbitmqctl stop_app
docker exec rabbitmq-2 rabbitmqctl reset
docker exec rabbitmq-2 rabbitmqctl join_cluster rabbit@rabbitmq-1
docker exec rabbitmq-2 rabbitmqctl start_app
docker exec rabbitmq-1 rabbitmqctl cluster_status
```

## 4. Conexión desde los microservicios

En `application-secrets.properties` de cada microservicio que use RabbitMQ:

```properties
spring.rabbitmq.addresses=<ip-privada-rabbit>:5672,<ip-privada-rabbit>:5673
spring.rabbitmq.username=nexora
spring.rabbitmq.password=<la clave del .env>
```

Con dos direcciones, si se pierde la conexión con un nodo el cliente se reconecta al otro.

## Qué tolera (y qué no) un clúster de 2 nodos

- Exchanges, colas, bindings y usuarios se comparten en todo el clúster: se ven desde cualquiera de los dos nodos.
- El **contenido** de una cola clásica vive en un solo nodo (el nodo al que estaba conectado quien la declaró;
  con la lista de direcciones de arriba, normalmente el nodo 1). Si ese nodo cae, esas colas quedan no disponibles
  hasta que vuelva; los mensajes durables no se pierden porque están en disco.
- Si cae el **otro** nodo, todo sigue funcionando. Para eso se desactivó Khepri (`RABBITMQ_FEATURE_FLAGS=-khepri_db`):
  Khepri usa Raft y con 2 nodos necesitaría los dos para cualquier cambio de metadatos.
- Alta disponibilidad real: 3 nodos + *quorum queues* (replican el contenido y toleran la caída de 1 nodo).
  La pauta pide 2 nodos, por eso se usan colas clásicas durables.

Demo de tolerancia: `docker stop rabbitmq-2` → el sistema sigue procesando pedidos → `docker start rabbitmq-2`
→ el nodo vuelve a aparecer en `cluster_status`.
