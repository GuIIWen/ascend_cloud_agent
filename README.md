# ascend_agent

`ascend_agent` is a Spring Boot service for API knowledge indexing/retrieval and testcase generation. The current baseline focuses on a reproducible local developer workflow: compile and run on Java 21, and use a locally managed Chroma `0.5.20` instance for vector persistence.

## Baseline

- Spring Boot: `2.7.18`
- Java baseline: `21`
- Chroma: `0.5.20`
- Local Chroma port: `22333`
- Agent runtime stage: `alignment` via `/actuator/info`
- Default runtime root: `./.ascend_agent/`
- Default directory layout:
  - `./.ascend_agent/tools/chroma-venv-0520`
  - `./.ascend_agent/chroma`
  - `./.ascend_agent/db`
  - `./.ascend_agent/logs`
  - `./.ascend_agent/pids`

## Dependency Matrix

| Component | Version / Baseline |
| --- | --- |
| Java baseline | 21 |
| Spring Boot | 2.7.18 |
| Maven | 3.9+ recommended |
| Chroma | 0.5.20 |

## Quick Start

### 1. Prepare Java and runtime root

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
export ASCEND_AGENT_HOME="$(pwd)/.ascend_agent"

mkdir -p \
  "$ASCEND_AGENT_HOME/tools" \
  "$ASCEND_AGENT_HOME/chroma" \
  "$ASCEND_AGENT_HOME/db" \
  "$ASCEND_AGENT_HOME/logs" \
  "$ASCEND_AGENT_HOME/pids"

java -version
```

Expected: JDK `21.x`.

### 2. Install local Chroma 0.5.20

```bash
export CHROMA_VENV_DIR="$ASCEND_AGENT_HOME/tools/chroma-venv-0520"
bash scripts/install_chroma_0520.sh
```

Expected output includes:

```text
chroma venv: <ASCEND_AGENT_HOME>/tools/chroma-venv-0520
chromadb version: 0.5.20
binary: <ASCEND_AGENT_HOME>/tools/chroma-venv-0520/bin/chroma
```

### 3. Start local Chroma on 22333

```bash
export CHROMA_VENV_DIR="$ASCEND_AGENT_HOME/tools/chroma-venv-0520"
export CHROMA_DATA_DIR="$ASCEND_AGENT_HOME/chroma"
export CHROMA_LOG_FILE="$ASCEND_AGENT_HOME/logs/chroma-22333.log"
export CHROMA_PID_FILE="$ASCEND_AGENT_HOME/pids/chroma-22333.pid"
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

The repository baseline is validated with Chroma on `22333`. The preferred service entrypoint is [scripts/start_service.sh](/root/ascend_agent/scripts/start_service.sh), which derives logs, pid files, and `-Dascend.agent.*` runtime flags from `ASCEND_AGENT_HOME`:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
export ASCEND_AGENT_HOME="$(pwd)/.ascend_agent"
export ASCEND_AGENT_PORT=8080
bash scripts/start_service.sh
```

Health check:

```bash
curl -i http://127.0.0.1:8080/actuator/health
```

Expected: `HTTP/1.1 200` with `{"status":"UP",...}`.

Runtime status check:

```bash
curl -i http://127.0.0.1:8080/actuator/info
```

Expected: the response includes `agent.stage=alignment`, `agent.enabled=false`, and `agent.mode=knowledge-base-only`.

Stop the service:

```bash
bash scripts/stop_service.sh
```

## Directory Contract

The approved long-lived local directory contract is rooted at `ASCEND_AGENT_HOME=./.ascend_agent/`.

Default layout:

- `tools/chroma-venv-0520`: Chroma CLI and Python virtualenv
- `chroma`: Chroma persistent data directory
- `db`: service-owned local database files such as `api_metadata.db`
- `logs`: Chroma and Spring Boot log files
- `pids`: background process pid files

Override strategy:

1. `-Dascend.agent.data-dir=...` has the highest priority for service-owned local database files.
2. `-Dascend.agent.home=...` or `ASCEND_AGENT_HOME=...` defines the approved contract root used to derive runtime paths in the application and wrapper scripts.
3. If you need to force current scripts onto the contract today, export `CHROMA_VENV_DIR`, `CHROMA_DATA_DIR`, `CHROMA_LOG_FILE`, and `CHROMA_PID_FILE` explicitly from `ASCEND_AGENT_HOME`.

The repository should no longer be documented as defaulting to `/tmp` for long-lived local state. If a script still has an internal `/tmp` fallback, treat it as compatibility-only and override it explicitly.

## Minimal Verification Chain

Run the shared baseline verification script:

```bash
bash scripts/verify_baseline.sh
```

That script runs:

```bash
timeout 120 mvn -q -DskipTests compile
timeout 120 mvn -q -Dtest=AgentConfigBindingTest,AgentInfoContributorTest,AppConfigRuntimePathTest,KnowledgeBaseConfigBindingTest,AppConfigModelSelectionTest,AppConfigVectorStoreSelectionTest,HttpModelServiceTest,HuaweiCloudApiCrawlerServiceTest test
timeout 180 mvn -q -DskipTests package
```

The script forces `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` so it does not inherit a stale Java 8 shell environment.

## Testcase Generation API Baseline

Public testcase endpoint:

- `POST /api/testcase/generate`

Current baseline today:

- If `execution` is absent, or `execution.enabled` is not `true`, the request stays on the existing generate-only path.
- Generate mode only returns generation artifacts such as `javaTestCode`, `citations`, `refinedRequirement`, and `degraded`.
- Repository helper scripts are local validation tooling around the generated code. They are not a second public route, and they do not mean the service already owns cloud resource lifecycle.

Approved next-step design:

- The same endpoint adds execute capability through a nested `execution` object; there is no separate public `/api/testcase/execute` route.
- `execution.enabled=true` requires a whitelisted `execution.resourceProfile`.
- Resource provision/release belongs to a stable service-side execution layer, not to the LLM-generated Java code.
- Generated Java code remains limited to API invocation and assertions.
- `execution.enabled=true` with missing or invalid `execution.resourceProfile` must return `400`.
- Execute responses must expose `provision`, `compile`, `test`, and `release` stage states, and `release` must still run on failure.

## Repository Verification Scripts

The repository keeps two local verification paths for generated Java testcases. They are implementation and acceptance helpers around `/api/testcase/generate`; they are not the public service-side execute contract.

### 1. Generate and compile only locally

Use the existing compile-only verifier when you want a fast gate for `/api/testcase/generate` output shape and Java 21 compilation:

```bash
bash scripts/verify_testcase_generation.sh \
  --requirement "验证卸载 Lite Server 系统盘在 BMS 场景下返回 400" \
  --expected-http-status 400 \
  --expected-error-code ModelArts.7000 \
  --expected-error-description "does not support detach volume device"
```

This path verifies:

- response fields: `javaTestCode`, `citations`, `degraded`, `refinedRequirement`
- no `TODO` / `placeholder`
- exactly one public class
- Java 21 + JUnit 5 compilation
- explicit expectation fragments when they are provided

### 2. Generate, compile, and optionally execute locally

Prepare the shared JUnit runner once:

```bash
bash scripts/install_generated_test_runner.sh
```

Then run the generated testcase in compile-only mode:

```bash
bash scripts/run_generated_testcase.sh \
  --requirement "验证卸载 Lite Server 系统盘在 BMS 场景下返回 400" \
  --expected-http-status 400 \
  --expected-error-code ModelArts.7000 \
  --expected-error-description "does not support detach volume device"
```

To execute the generated JUnit test against a real environment, export the runtime config expected by the generated code and add `--execute`:

```bash
export HUAWEICLOUD_BASE_URL="https://modelarts.cn-north-9.myhuaweicloud.com"
export HUAWEICLOUD_PROJECT_ID="<project_id>"
export HUAWEICLOUD_DEV_SERVER_ID="<dev_server_id>"
export HUAWEICLOUD_VOLUME_ID="<volume_id>"
export HUAWEICLOUD_AUTH_TOKEN="$(cat /root/auth_token.txt)"

bash scripts/run_generated_testcase.sh \
  --execute \
  --requirement "验证卸载 Lite Server 系统盘在 BMS 场景下返回 400" \
  --expected-http-status 400 \
  --expected-error-code ModelArts.7000 \
  --expected-error-description "does not support detach volume device"
```

Notes:

- `scripts/run_generated_testcase.sh` stores artifacts under `ASCEND_AGENT_HOME/generated-testcase-runs/`.
- The script prepares the shared `GeneratedJUnitRunner` automatically through `scripts/install_generated_test_runner.sh`.
- Execute mode uses the current shell environment or system properties consumed by the generated testcase itself; the script does not inject cloud credentials on its own.
- This local script is repository-side validation tooling. It does not replace the future service-side `execution.enabled=true` contract.
- The default API endpoint is `http://127.0.0.1:8080/api/testcase/generate`; override it with `--api` if your service is exposed elsewhere.

## CI

The repository includes a minimal GitHub Actions workflow at `.github/workflows/ci.yml`.

It currently enforces:

- Maven compile on JDK 21
- Maven package on JDK 21
- Agent/runtime status contract tests
- Configuration and model selection tests
- HTTP model client tests
- Huawei Cloud crawler service unit test

CI intentionally does not provision Chroma or boot the full application. It is a fast baseline gate, not a full integration environment.

## Known Boundaries

- Chroma is not provisioned inside CI. Local persistence validation still requires a running Chroma instance.
- Local runtime should use Chroma `0.5.20` on `22333`. Do not mix this README baseline with ad hoc `1.5.x` commands.
- The repository does not currently provide a single command that provisions Chroma and the Spring Boot service together. Use the documented Chroma scripts plus [scripts/start_service.sh](/root/ascend_agent/scripts/start_service.sh).
- The current public testcase API baseline is still generate-only unless `execution.enabled=true`. Approved execute capability is same-route design work, not a historical already-shipped capability.
- Existing generated-testcase scripts are local verification helpers; automatic resource provision/release belongs to the future stable execution layer, not to generated code or shell scripts.
- The project contains additional local-only files and environment-specific workflows not covered by this README.
- For local service startup, pass the vector store URL and data directory explicitly if your local config still points somewhere else.
- Service startup disables Boot's logging shutdown hook in the wrapper script to reduce shutdown-time logging races in packaged runs.

## File Map

- Chroma install script: [scripts/install_chroma_0520.sh](/root/ascend_agent/scripts/install_chroma_0520.sh)
- Chroma start script: [scripts/start_chroma_22333.sh](/root/ascend_agent/scripts/start_chroma_22333.sh)
- Service start script: [scripts/start_service.sh](/root/ascend_agent/scripts/start_service.sh)
- Service stop script: [scripts/stop_service.sh](/root/ascend_agent/scripts/stop_service.sh)
- Baseline verification script: [scripts/verify_baseline.sh](/root/ascend_agent/scripts/verify_baseline.sh)
- Generated testcase runner install script: [scripts/install_generated_test_runner.sh](/root/ascend_agent/scripts/install_generated_test_runner.sh)
- Generated testcase local compile/execute script: [scripts/run_generated_testcase.sh](/root/ascend_agent/scripts/run_generated_testcase.sh)
- Generated testcase compile-only verifier: [scripts/verify_testcase_generation.sh](/root/ascend_agent/scripts/verify_testcase_generation.sh)
- CI workflow: [.github/workflows/ci.yml](/root/ascend_agent/.github/workflows/ci.yml)
- Batch 3 testcase generation execution baseline: [docs/TESTCASE_GENERATION_V3_CURRENT.md](/root/ascend_agent/docs/TESTCASE_GENERATION_V3_CURRENT.md) + latest Batch 3 decisions in `meeting.md`
