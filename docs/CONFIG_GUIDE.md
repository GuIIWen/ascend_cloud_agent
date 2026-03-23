# 配置指南

> 文档定位：本文件收口“当前已验证基线”与“已批准的目标口径”。目标设计请参考其他设计文档；若与本文件冲突，以本文件为准。

## 0. Sprint-1 当前有效基线

### 0.1 版本矩阵

| 项目 | 当前基线 | 说明 |
|------|----------|------|
| Java 编译目标（目标口径） | 17 | Sprint-1 目标口径；“是否已对齐”以实际构建/CI 结果为准 |
| Java 编译目标（当前仓库配置） | 17 | 见 `pom.xml` 中 `maven.compiler.source` / `maven.compiler.target` |
| Java 运行时（已验证口径） | JDK 21 | 当前运行口径统一为 JDK 21 |
| Spring Boot | 2.7.18 | 见 `pom.xml` |
| 开发态向量库 | Chroma 0.5.20 | 以仓库脚本入口为准 |
| Chroma 地址 | `http://127.0.0.1:22333` | 已替换历史旧端口口径 |
| 应用服务端口 | `8080` | 健康检查路径 `/actuator/health` |
| Chroma 安装入口 | `scripts/install_chroma_0520.sh` | 创建独立 venv 并安装 `chromadb==0.5.20` |
| Chroma 启动入口 | `scripts/start_chroma_22333.sh` | 默认监听 `127.0.0.1:22333` |
| 应用配置模板 | `src/main/resources/application.yml.template` | 基线模板，不会自动生效 |
| 应用运行入口 | `target/ascend-agent-1.0.0.jar` | 运行时建议显式设置 `JAVA_HOME`/`PATH` |
| 长期默认运行根（目录合同） | `./.ascend_agent/` | 以 `ASCEND_AGENT_HOME` 为根管理本地持久化目录 |

### 0.2 配置优先级与生效入口

当前仓库的配置优先级收口如下：

1. 启动命令显式注入的环境变量
2. 运行时实际加载的 `application.yml`（由 Spring Boot 默认规则加载，或显式指定）
3. `src/main/resources/application.yml.template` 中记录的默认基线
4. 代码中的兜底默认值

说明：
- `application.yml.template` 是模板，不是自动生效文件。
- Chroma 的安装/启动以脚本入口为准，不再以历史文档中的手敲命令为准。
- 环境变量与配置文件冲突时，以环境变量为准。
- 若需要明确指定配置文件路径，使用 Spring Boot 原生参数：`--spring.config.location=/abs/path/application.yml`（不属于仓库自研能力，仅为运行方式建议）。

### 0.3 目录合同与覆盖优先级

长期默认目录策略：
- `ASCEND_AGENT_HOME=./.ascend_agent/`
- 不再把 `/tmp` 作为默认长期运行目录

目录优先级：
1. `ascend.agent.data-dir`
2. `ascend.agent.home` / `ASCEND_AGENT_HOME`
3. 默认 `./.ascend_agent/`

默认目录布局：

| 相对路径 | 用途 |
|----------|------|
| `tools/chroma-venv-0520` | Chroma Python venv 与 CLI |
| `chroma` | Chroma 持久化数据目录 |
| `db` | 服务本地数据库目录 |
| `logs` | Chroma 与应用日志 |
| `pids` | 后台进程 PID 文件 |

当前落地方式：
- Chroma 脚本通过显式环境变量覆盖：`CHROMA_VENV_DIR`、`CHROMA_DATA_DIR`、`CHROMA_LOG_FILE`、`CHROMA_PID_FILE`
- 应用当前显式消费并覆盖的是：`ascend.agent.data-dir`、`ascend.agent.home`、`ASCEND_AGENT_HOME`
- 应用本地数据库目录优先级：`ascend.agent.data-dir` > `ascend.agent.home` > `ASCEND_AGENT_HOME` > `./.ascend_agent/db`
- 若要让 Chroma 也严格落到该合同，需要同时显式传入 `CHROMA_*` 覆盖变量，或直接使用仓库脚本默认派生路径
- 若同时传了 `ascend.agent.data-dir` 与 `ascend.agent.home`，以前者为准
- 若某些历史脚本内部仍保留 `/tmp` 回退值，应视为历史兼容或临时覆盖，不是长期默认路径

## 1. 应用配置结构

### 1.1 当前基线模板

当前模板与字段口径以 `src/main/resources/application.yml.template` 为准：

```yaml
server:
  port: ${SERVER_PORT:8080}

knowledge-base:
  vector-store:
    type: chroma
    url: http://127.0.0.1:22333
    collection: api-knowledge-base

  embedding:
    provider: ${EMBEDDING_PROVIDER:local}
    api-url: ${EMBEDDING_API_URL:http://localhost:8080/v1/embeddings}
    api-key: ${EMBEDDING_API_KEY:}
    model: ${EMBEDDING_MODEL:bge-large-zh-v1.5}
    dimension: 1024
    timeout-seconds: ${EMBEDDING_TIMEOUT_SECONDS:30}

  rerank:
    provider: ${RERANK_PROVIDER:none}
    api-url: ${RERANK_API_URL:http://localhost:8080/v1/rerank}
    api-key: ${RERANK_API_KEY:}
    model: ${RERANK_MODEL:bge-reranker-large}
    top-k: 5
    timeout-seconds: ${RERANK_TIMEOUT_SECONDS:30}

  llm:
    provider: ${LLM_PROVIDER:none}
    api-url: ${LLM_API_URL:http://localhost:8080/v1/chat/completions}
    api-key: ${LLM_API_KEY:}
    model: ${LLM_MODEL:qwen-coder-plus}
    temperature: ${LLM_TEMPERATURE:0.2}
    max-tokens: ${LLM_MAX_TOKENS:4096}
    timeout-seconds: ${LLM_TIMEOUT_SECONDS:30}
```

### 1.2 模型配置说明

| 配置项 | 说明 | 当前默认值 |
|--------|------|------------|
| `knowledge-base.embedding.api-url` | Embedding 服务地址 | `http://localhost:8080/v1/embeddings` |
| `knowledge-base.embedding.model` | Embedding 模型名 | `bge-large-zh-v1.5` |
| `knowledge-base.rerank.api-url` | Rerank 服务地址 | `http://localhost:8080/v1/rerank` |
| `knowledge-base.rerank.model` | Rerank 模型名 | `bge-reranker-large` |
| `knowledge-base.llm.api-url` | LLM Chat Completions 地址 | `http://localhost:8080/v1/chat/completions` |
| `knowledge-base.llm.model` | LLM 模型名 | `qwen-coder-plus` |
| `knowledge-base.vector-store.url` | 向量库地址 | `http://127.0.0.1:22333` |
| `knowledge-base.vector-store.collection` | Chroma Collection | `api-knowledge-base` |

### 1.3 环境变量示例

```bash
export EMBEDDING_API_URL=https://your-embedding-api.com/v1/embeddings
export EMBEDDING_API_KEY=your-embedding-key

export RERANK_API_URL=https://your-rerank-api.com/v1/rerank
export RERANK_API_KEY=your-rerank-key

export LLM_API_URL=https://your-llm-api.com/v1/chat/completions
export LLM_API_KEY=your-llm-key
```

## 2. 向量数据库配置

### 2.1 开发环境基线

```yaml
knowledge-base:
  vector-store:
    type: chroma
    url: http://127.0.0.1:22333
    collection: api-knowledge-base
```

### 2.2 生产环境目标

> 目标设计，非当前已验证实现。

```yaml
knowledge-base:
  vector-store:
    type: milvus
    url: http://milvus-host:19530
    collection: api-knowledge-base
```

## 3. Chroma 本地安装与启动

### 3.1 当前推荐路径

```bash
export ASCEND_AGENT_HOME="$(pwd)/.ascend_agent"
export CHROMA_VENV_DIR="$ASCEND_AGENT_HOME/tools/chroma-venv-0520"
export CHROMA_DATA_DIR="$ASCEND_AGENT_HOME/chroma"
export CHROMA_LOG_FILE="$ASCEND_AGENT_HOME/logs/chroma-22333.log"
export CHROMA_PID_FILE="$ASCEND_AGENT_HOME/pids/chroma-22333.pid"

scripts/install_chroma_0520.sh
scripts/start_chroma_22333.sh
```

按目录合同派生后的推荐路径：
- venv：`./.ascend_agent/tools/chroma-venv-0520`
- 数据目录：`./.ascend_agent/chroma`
- 日志：`./.ascend_agent/logs/chroma-22333.log`
- PID：`./.ascend_agent/pids/chroma-22333.pid`
- 地址：`127.0.0.1:22333`

### 3.2 验证命令

```bash
curl http://127.0.0.1:22333/api/v1/heartbeat
```

## 4. Prompt 模板配置

> 目标设计能力，是否实际生效取决于运行时是否已接入对应配置消费逻辑。

```yaml
prompt:
  system-template: |
    你是一个专业的Java单元测试工程师。
    擅长为各种Java方法生成高质量的JUnit测试用例。

  user-template: |
    请为以下Java代码生成单元测试：
    ${code}
```

## 5. 模型切换指南

1. 修改运行时 `application.yml` 或对应环境变量。
2. 优先确认目标模型接口兼容 OpenAI 风格的请求结构。
3. 启动后通过日志核对实际加载的模型与地址。

示例：

```yaml
knowledge-base:
  llm:
    model: qwen-plus
    temperature: 0.3
    max-tokens: 2048
```

## 6. 启动应用

当前已收口运行口径：
- 编译目标：Java 17
- 运行时：JDK 21
- 服务端口：`8080`
- 健康检查：`/actuator/health`
- 长期默认运行根：`./.ascend_agent/`

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export ASCEND_AGENT_HOME="$(pwd)/.ascend_agent"

export EMBEDDING_API_URL=https://your-api.com/v1/embeddings
export EMBEDDING_API_KEY=your-key
export LLM_API_URL=https://your-llm-api.com/v1/chat/completions
export LLM_API_KEY=your-llm-key

java \
  -Dascend.agent.home="$ASCEND_AGENT_HOME" \
  -Dascend.agent.data-dir="$ASCEND_AGENT_HOME/db" \
  -jar target/ascend-agent-1.0.0.jar
```

## 7. 常见问题

| 问题 | 现象 | 处理建议 |
|------|------|----------|
| Chroma 连不上 | 请求仍指向历史旧端口 | 检查是否还有旧配置覆盖 `knowledge-base.vector-store.url` |
| 本地文件散落在 `/tmp` | 启动后日志/数据目录不在仓库下 | 显式设置 `ASCEND_AGENT_HOME`，并同步导出 `CHROMA_*` 覆盖变量 |
| 配置不生效 | 修改了模板但运行结果未变 | 确认修改的是运行时 `application.yml`，不是仅修改模板 |
| Java 启动失败 | `java -version` 不符合预期 | 显式设置 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| 模型调用异常 | 401/超时/空响应 | 优先检查对应 `*_API_URL` / `*_API_KEY` 环境变量 |
