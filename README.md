# Gateway Task

This project is a simple **Vert.x 5.0.4**–based HTTP → JSON-RPC gateway.  
It forwards Ethereum JSON-RPC requests to a configured upstream Ethereum node, applies basic request counting (metrics), and enforces authentication via JWT on all endpoints except `/health`.

---

## License

This project is submitted as part of a hiring process.  
It is intended for review and evaluation only.  
All rights reserved.

---

## Features

- **Vert.x 5.0.4** reactive stack
- **Forwarding Gateway**: Accepts JSON-RPC requests on `/rpc` and proxies them to a configured upstream (Ethereum RPC endpoint).
- **JWT Authentication**: Required on `/rpc` and `/metrics`. Only `/health` is public.
- **Metrics**: Exposes simple method counters on `/metrics`.
- **TLS Support**: Configurable via PKCS#12 keystore.
- **Dockerized**: Ready to build and run with Docker.

---

## Requirements

- Java 17+
- Maven 3.9+
- Docker (for container build & run)
- (Optional) [mkcert](https://github.com/FiloSottile/mkcert) or OpenSSL if generating local TLS certs

---

## Configuration

The application reads configuration from environment variables (or JSON config when embedding).

| Key                | Description                                | Default           |
|--------------------|--------------------------------------------|-------------------|
| `TLS_ENABLED`      | Enable TLS (`true`/`false`)                | `false`           |
| `PORT`             | Port to listen on (8443 if TLS, else 8080) | `8080`            |
| `RPC_URL`          | Upstream Ethereum node URL                 | *(required)*      |
| `RPC_TIMEOUT_MS`   | Timeout when calling upstream              | `10000`           |
| `MAX_BODY_BYTES`   | Maximum request body size in bytes         | `10485760` (10 MB)|
| `JWT_SECRET`       | Shared secret for HMAC (HS256) JWT signing | *(required)*      |
| `JWT_ISS`          | Expected `iss` claim in JWT                | *(required)*      |
| `JWT_AUD`          | Expected `aud` claim in JWT                | *(required)*      |
| `TLS_P12_PATH`     | Path to PKCS#12 keystore file              | *(if TLS=true)*   |
| `TLS_P12_PASSWORD` | Password for keystore                      | *(if TLS=true)*   |
| `TOKEN_API_KEY`  | API key required to mint tokens at `/auth/token` | *(optional; if set, enables /auth/token)* |

Example .env for testing:
```bash
; TLS_ENABLED=false
; PORT=8080
RPC_URL=https://ethereum-rpc.publicnode.com
RPC_TIMEOUT_MS=10000
MAX_BODY_BYTES=10485760
JWT_SECRET='fGKomYJ+lP8qX3DbrzBLG0Ox1n8iXVa0mmQe8dynoiA='
JWT_ISS=test-gateway
JWT_AUD=test-clients
TOKEN_API_KEY=test-apikey

TLS_ENABLED=true
PORT=8443
TLS_P12_PATH=src/main/resources/tls/server.p12
TLS_P12_PASSWORD=test-pass
```

---

## Build & Run

### 1. Build JAR locally
```bash
mvn clean package -DskipTests
```

Run directly:
```bash
java -jar target/gateway-1.0.0-fat.jar
```

### 2. Build Docker image
```bash
docker build -t gateway:latest .
```

### 3. Run with Docker

**HTTP example (no TLS):**
```bash
docker run --rm -p 8080:8080   -e TLS_ENABLED=false   -e PORT=8080   -e RPC_URL=https://ethereum-rpc.publicnode.com   -e JWT_SECRET=super-secret-value   -e JWT_ISS=my-gateway   -e JWT_AUD=my-clients   gateway:latest
```

**TLS example:**
```bash
docker run --rm -p 8443:8443   -e TLS_ENABLED=true   -e PORT=8443   -e RPC_URL=https://ethereum-rpc.publicnode.com   -e JWT_SECRET=super-secret-value   -e JWT_ISS=my-gateway   -e JWT_AUD=my-clients   -e TLS_P12_PATH=/certs/server.p12   -e TLS_P12_PASSWORD=test-pass   -v $(pwd)/src/main/resources/tls:/certs:ro   gateway:latest
```

---

## Endpoints

- `GET /health` → returns `200 OK` (no auth)
- `POST /rpc` → forward JSON-RPC request to upstream (JWT required)
- `GET /metrics` → return method call counters (JWT required)
- `POST /auth/token` → mint a short-lived JWT (protected by `X-API-Key`)

---

## Generating Tokens for Testing

### Obtaining a JWT via `/auth/token` (recommended)

If you don't want to generate tokens yourself, the gateway can issue them for you via a **private minting endpoint**.

**Security model:** the endpoint is protected by an API key passed in the `X-API-Key` header. The **JWT secret is never exposed**.  
Enable it by setting `TOKEN_API_KEY` in the environment. If `TOKEN_API_KEY` is missing, the endpoint is disabled.

### Request
```
POST /auth/token
X-API-Key: <your-api-key>
Content-Type: application/json

{
  "sub": "alice@example.com"   // optional; defaults to "client"
}
```

### Response
```json
{ "token": "<jwt-here>" }
```

### Example (curl)
```bash
# Assuming the server was started with: -e TOKEN_API_KEY=dev-mint-key
curl -s http://localhost:8080/auth/token   -H "X-API-Key: dev-mint-key"   -H "Content-Type: application/json"   -d '{"sub":"alice@example.com"}' | jq -r .token
```

You can then use the token:
```bash
TOKEN="$(curl -s http://localhost:8080/auth/token   -H "X-API-Key: dev-mint-key" -H "Content-Type: application/json"   -d '{"sub":"alice@example.com"}' | jq -r .token)"

curl -s http://localhost:8080/metrics -H "Authorization: Bearer $TOKEN"
```

> **Note:** `/auth/token` exists to help local/dev testers or small trusted client lists. In production with many users, prefer issuing tokens from an external IdP (Auth0/Keycloak) and configure the gateway to verify RS256 tokens.
---
## Notes & Design Decisions

- **Vert.x chosen** for async I/O and lightweight reactive programming.
- **JWT Auth** is applied at route level. Only `/health` bypasses auth.
- **Metrics** are kept in-memory (`ConcurrentHashMap<String, LongAdder>`). No persistence. Simplified for the exercise.
- **Error Handling**: Maps upstream errors into JSON-RPC–style error responses with appropriate gateway HTTP status codes.
- **TLS Certificate**:  
  A PKCS#12 keystore (`server.p12`) is included under `src/main/resources/tls` with password `test-pass`. It is **trusted locally** and **pushed to the repo only for this test purpose**.  
  ⚠️ Do **not** use this in production; certificates must be issued and managed properly (e.g., via Let’s Encrypt, Vault, or cloud provider tools).
- Only **HS256 JWT** is supported for simplicity.
- Upstream Ethereum node is assumed to be **JSON-RPC 2.0–compatible**.



