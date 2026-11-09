# LLM Gateway

A production-grade API gateway for LLM providers (Claude, OpenAI) built with Java 21 and Spring Boot 3. Routes requests through a unified API with rate limiting, response caching, circuit breaking, automatic failover, and usage tracking.

## Architecture

```
Client Request
    │
    ▼
┌─────────────────────────────────────────────┐
│              LLM Gateway                     │
│                                              │
│  ┌──────────┐  ┌─────────┐  ┌────────────┐ │
│  │   Rate    │→ │ Response│→ │  Provider  │ │
│  │  Limiter  │  │  Cache  │  │   Router   │ │
│  │(Token     │  │(Caffeine│  │            │ │
│  │ Bucket)   │  │ SHA-256)│  │ ┌────────┐ │ │
│  └──────────┘  └─────────┘  │ │ Claude │ │ │
│                              │ └────────┘ │ │
│  ┌──────────┐  ┌─────────┐  │ ┌────────┐ │ │
│  │  Usage   │  │ Circuit │  │ │ OpenAI │ │ │
│  │ Tracker  │  │ Breaker │  │ └────────┘ │ │
│  │(Micrometer│  │(R4j)   │  └────────────┘ │
│  └──────────┘  └─────────┘                  │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │     Prometheus + Grafana Metrics     │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

## Features

| Feature | Implementation |
|---------|---------------|
| **Unified API** | Single `/api/v1/chat` endpoint for Claude and OpenAI |
| **Rate Limiting** | Token bucket algorithm, per-client, configurable RPM |
| **Response Caching** | SHA-256 hash of request → cached response (Caffeine) |
| **Circuit Breaker** | Resilience4j — opens on 50% failure rate, half-open recovery |
| **Auto Failover** | If Claude fails, automatically routes to OpenAI (and vice versa) |
| **Usage Tracking** | Per-client token counts, cost estimation, request logging |
| **Observability** | Prometheus metrics + Grafana dashboards via Spring Actuator |
| **Cost Estimation** | Per-request USD cost based on token pricing |

## Tech Stack

- **Java 21** (records, virtual threads ready)
- **Spring Boot 3.3** (Web, WebFlux client, Validation, Actuator)
- **Resilience4j** (circuit breaker, retry)
- **Caffeine** (in-memory caching)
- **Micrometer + Prometheus** (metrics)
- **Docker + Docker Compose** (containerization)
- **JUnit 5** (testing)

## Quick Start

### Prerequisites
- Java 21+ ([Download](https://adoptium.net/temurin/releases/?version=21))
- Claude API key ([Get one](https://console.anthropic.com/)) and/or OpenAI API key ([Get one](https://platform.openai.com/api-keys))
- Maven 3.9+ (or use the included Maven Wrapper — no install needed)

### Option 1: Run locally (recommended for trying it out)

```bash
# 1. Clone
git clone https://github.com/basukinath/llm-gateway.git
cd llm-gateway

# 2. Set your API keys (you need at least one)
# On Linux/Mac:
export CLAUDE_API_KEY=sk-ant-your-key-here
export OPENAI_API_KEY=sk-your-key-here

# On Windows (PowerShell):
$env:CLAUDE_API_KEY="sk-ant-your-key-here"
$env:OPENAI_API_KEY="sk-your-key-here"

# 3. Build and run (uses Maven Wrapper — no Maven install required)
# Linux/Mac:
./mvnw spring-boot:run

# Windows:
mvnw.cmd spring-boot:run
```

The gateway starts on **http://localhost:8080**. Try it immediately:

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: test-user" \
  -d '{"messages": [{"role": "user", "content": "Say hello in one sentence"}], "maxTokens": 50}'
```

### Option 2: Run with Docker (includes monitoring stack)

```bash
cp .env.example .env
# Edit .env with your API keys

docker compose up --build
```

This starts:
- **Gateway**: http://localhost:8080
- **Prometheus**: http://localhost:9090 (metrics dashboard)
- **Grafana**: http://localhost:3000 (login: admin/admin)

### Run tests (no API keys needed)

```bash
./mvnw test          # Linux/Mac
mvnw.cmd test        # Windows
```

## API Usage

### Send a chat request

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: my-app" \
  -d '{
    "messages": [{"role": "user", "content": "What is a circuit breaker pattern?"}],
    "maxTokens": 256
  }'
```

### Choose a specific provider

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "openai",
    "model": "gpt-4o-mini",
    "messages": [{"role": "user", "content": "Hello"}],
    "maxTokens": 100
  }'
```

### Check usage

```bash
curl http://localhost:8080/api/v1/usage/my-app
```

### Response format

```json
{
  "id": "msg_abc123",
  "provider": "claude",
  "model": "claude-sonnet-4-20250514",
  "content": "A circuit breaker pattern is...",
  "usage": {
    "promptTokens": 15,
    "completionTokens": 120,
    "totalTokens": 135,
    "estimatedCostUsd": 0.001845
  },
  "latencyMs": 1523,
  "cached": false,
  "timestamp": "2026-11-10T12:00:00Z"
}
```

### Response headers

```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 58
X-RateLimit-Reset: 1699617600
X-Response-Cached: false
```

## Configuration

All settings in `application.yml` or via environment variables:

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `gateway.claude.api-key` | `CLAUDE_API_KEY` | — | Anthropic API key |
| `gateway.openai.api-key` | `OPENAI_API_KEY` | — | OpenAI API key |
| `gateway.rate-limit.requests-per-minute` | — | 60 | Max requests per client per minute |
| `gateway.cache.enabled` | — | true | Enable response caching |
| `gateway.cache.max-size` | — | 1000 | Max cached responses |
| `gateway.cache.ttl-minutes` | — | 30 | Cache TTL |
| `gateway.routing.fallback-order` | — | claude, openai | Provider priority |

## How It Works

### Request Flow

1. **Rate Limiter** checks if the client (identified by `X-Client-Id` header) has remaining quota
2. **Cache** checks if an identical request was seen before (SHA-256 hash of provider + model + messages + temperature)
3. **Provider Router** selects the right provider based on explicit choice, model name, or default fallback order
4. **Circuit Breaker** (Resilience4j) protects the provider call — if the provider has been failing, the circuit opens and immediately falls back
5. **Fallback** routes to the next provider in the fallback chain
6. **Usage Tracker** records tokens, cost, and latency per client via Micrometer

### Token Bucket Rate Limiter

Each client gets a bucket of N tokens (default: 60). Each request consumes one token. Tokens refill to max every 60 seconds. Simple, predictable, and fair.

### Circuit Breaker States

```
CLOSED (normal) → failures exceed 50% of last 10 calls → OPEN (reject all, return fallback)
                                                             │
                                                        wait 30s
                                                             │
                                                        HALF-OPEN (allow 3 test calls)
                                                             │
                                                    success? → CLOSED
                                                    failure? → OPEN
```

## Monitoring

With Docker Compose running:

- **Prometheus**: http://localhost:9090 — query `gateway_requests_total`, `gateway_tokens_total`, `gateway_cost_usd`, `gateway_latency`
- **Grafana**: http://localhost:3000 (admin/admin) — add Prometheus as data source, build dashboards

### Key Metrics

| Metric | Tags | Description |
|--------|------|-------------|
| `gateway.requests.total` | provider, model, cached | Total requests |
| `gateway.tokens.total` | provider, type (input/output) | Tokens consumed |
| `gateway.cost.usd` | provider | Estimated cost |
| `gateway.latency` | provider, cached | Response time |
| `gateway.cache.hits` | — | Cache hit count |
| `gateway.cache.misses` | — | Cache miss count |

## Project Structure

```
src/main/java/dev/basuki/llmgateway/
├── LlmGatewayApplication.java
├── config/
│   └── GatewayProperties.java          # Type-safe config with records
├── controller/
│   └── GatewayController.java          # REST API + fallback logic
├── model/
│   ├── ChatRequest.java                # Request DTO (record + validation)
│   ├── ChatResponse.java               # Response DTO with usage stats
│   ├── GatewayError.java               # Error response format
│   └── Provider.java                   # Provider enum
├── provider/
│   ├── LlmProvider.java                # Provider interface
│   ├── ClaudeProvider.java             # Anthropic API integration
│   ├── OpenAiProvider.java             # OpenAI API integration
│   └── ProviderRouter.java             # Routing + fallback logic
├── ratelimit/
│   ├── RateLimiter.java                # Rate limiter interface
│   └── TokenBucketRateLimiter.java     # Token bucket implementation
├── cache/
│   └── ResponseCache.java              # SHA-256 keyed Caffeine cache
├── metrics/
│   └── UsageTracker.java               # Per-client usage tracking
└── exception/
    ├── RateLimitExceededException.java
    └── GlobalExceptionHandler.java      # Unified error handling
```

## License

MIT
