# ascend_agent

`ascend_agent` is a Spring Boot service for API knowledge indexing and retrieval. The current baseline focuses on a reproducible local developer workflow: compile with Java 17 target bytecode, run on JDK 21, and use a locally managed Chroma `0.5.20` instance for vector persistence.

## Baseline

- Spring Boot: `2.7.18`
- Maven compiler target: Java `17`
- Runtime JDK: `21`
- Chroma: `0.5.20`
- Local Chroma port: `22333`
- Default local Chroma paths:
  - venv: `/tmp/chroma-venv-0520`
  - data: `/tmp/chroma-data-22333`
  - log: `/tmp/chroma-22333.log`
  - pid: `/tmp/chroma-22333.pid`

## Dependency Matrix

| Component | Version / Baseline |
| --- | --- |
| Java compile target | 17 |
| Recommended runtime JDK | 21 |
| Spring Boot | 2.7.18 |
| Maven | 3.9+ recommended |
| Chroma | 0.5.20 |

## Quick Start

### 1. Prepare Java

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Expected: JDK `21.x`.

### 2. Install local Chroma 0.5.20

```bash
bash scripts/install_chroma_0520.sh
```

Expected output includes:

```text
chroma venv: /tmp/chroma-venv-0520
chromadb version: 0.5.20
binary: /tmp/chroma-venv-0520/bin/chroma
```

### 3. Start local Chroma on 22333

```bash
bash scripts/start_chroma_22333.sh
```

Expected output includes the pid, log path, and heartbeat hints.

Health check:

```bash
curl -i http://127.0.0.1:22333/api/v1/heartbeat
```

Expected: `HTTP/1.1 200 OK`.

### 4. Build the runnable jar

```bash
timeout 180 mvn -q -DskipTests package
```

This produces `target/ascend-agent-1.0.0.jar` for the next step.

### 5. Start the service

The repository baseline is validated with Chroma on `22333`. Pass the vector store URL explicitly at startup so the service points at the local Chroma instance:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
setsid nohup java \
  -Dascend.agent.data-dir=/root/ascend_agent/data \
  -jar target/ascend-agent-1.0.0.jar \
  --knowledge-base.vector-store.type=chroma \
  --knowledge-base.vector-store.url=http://127.0.0.1:22333 \
  --knowledge-base.vector-store.collection=api-knowledge-base \
  >/tmp/ascend-agent.log 2>&1 < /dev/null &
```

Health check:

```bash
curl -i http://127.0.0.1:8080/actuator/health
```

Expected: `HTTP/1.1 200` with `{"status":"UP",...}`.

## Minimal Verification Chain

Run the shared baseline verification script:

```bash
bash scripts/verify_baseline.sh
```

That script runs:

```bash
timeout 120 mvn -q -DskipTests compile
timeout 120 mvn -q -Dtest=KnowledgeBaseConfigBindingTest,AppConfigModelSelectionTest,AppConfigVectorStoreSelectionTest,HttpModelServiceTest,HuaweiCloudApiCrawlerServiceTest test
```

The script forces `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` so it does not inherit a stale Java 8 shell environment.

## CI

The repository includes a minimal GitHub Actions workflow at `.github/workflows/ci.yml`.

It currently enforces:

- Maven compile on JDK 21
- Configuration and model selection tests
- HTTP model client tests
- Huawei Cloud crawler service unit test

CI intentionally does not provision Chroma or boot the full application. It is a fast baseline gate, not a full integration environment.

## Known Boundaries

- Chroma is not provisioned inside CI. Local persistence validation still requires a running Chroma instance.
- Local runtime should use Chroma `0.5.20` on `22333`. Do not mix this README baseline with ad hoc `1.5.x` commands.
- The project contains additional local-only files and environment-specific workflows not covered by this README.
- For local service startup, pass the vector store URL explicitly if your local config still points somewhere else.

## File Map

- Chroma install script: [scripts/install_chroma_0520.sh](/root/ascend_agent/scripts/install_chroma_0520.sh)
- Chroma start script: [scripts/start_chroma_22333.sh](/root/ascend_agent/scripts/start_chroma_22333.sh)
- Baseline verification script: [scripts/verify_baseline.sh](/root/ascend_agent/scripts/verify_baseline.sh)
- CI workflow: [.github/workflows/ci.yml](/root/ascend_agent/.github/workflows/ci.yml)
