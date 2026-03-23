# 配置指南

> 文档定位：本文件收口“当前已验证基线”与“已批准的目标口径”。目标设计请参考其他设计文档；若与本文件冲突，以本文件为准。

## 0. Sprint-1 当前有效基线

### 0.1 版本矩阵

| 项目 | 当前基线 | 说明 |
|------|----------|------|
| Java 编译目标（目标口径） | 17 | Sprint-1 目标口径；“是否已对齐”以实际构建/CI 结果为准 |
| Java 编译目标（当前仓库配置） | 1.8 | 见 `pom.xml` 中 `maven.compiler.source` / `maven.compiler.target`；待升级对齐到 17 |
| Java 运行时（已验证口径） | JDK 21 | 当前运行口径统一为 JDK 21 |
| Spring Boot | 2.7.18 | 见 `pom.xml` |
| 开发态向量库 | Chroma 0.5.20 | 以仓库脚本入口为准 |
| Chroma 地址 | `http://127.0.0.1:22333` | 已替换历史旧端口口径 |
| 应用服务端口 | `8080` | 健康检查路径 `/actuator/health` |
| Chroma 安装入口 | `scripts/install_chroma_0520.sh` | 创建独立 venv 并安装 `chromadb==0.5.20` |
| Chroma 启动入口 | `scripts/start_chroma_22333.sh` | 默认监听 `127.0.0.1:22333` |
| 应用配置模板 | `src/main/resources/application.yml.template` | 基线模板，不会自动生效 |
| 应用运行入口 | `target/ascend-agent-1.0.0.jar` | 运行时建议显式设置 `JAVA_HOME`/`PATH` |

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
scripts/install_chroma_0520.sh
scripts/start_chroma_22333.sh
```

默认参数：
- venv：`/tmp/chroma-venv-0520`
- 数据目录：`/tmp/chroma-data-22333`
- 日志：`/tmp/chroma-22333.log`
- PID：`/tmp/chroma-22333.pid`
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

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

export EMBEDDING_API_URL=https://your-api.com/v1/embeddings
export EMBEDDING_API_KEY=your-key
export LLM_API_URL=https://your-llm-api.com/v1/chat/completions
export LLM_API_KEY=your-llm-key

java -jar target/ascend-agent-1.0.0.jar
```

## 7. 常见问题

| 问题 | 现象 | 处理建议 |
|------|------|----------|
| Chroma 连不上 | 请求仍指向历史旧端口 | 检查是否还有旧配置覆盖 `knowledge-base.vector-store.url` |
| 配置不生效 | 修改了模板但运行结果未变 | 确认修改的是运行时 `application.yml`，不是仅修改模板 |
| Java 启动失败 | `java -version` 不符合预期 | 显式设置 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| 模型调用异常 | 401/超时/空响应 | 优先检查对应 `*_API_URL` / `*_API_KEY` 环境变量 |
