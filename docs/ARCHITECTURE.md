# 系统架构图

> 文档定位：本文件描述“目标架构（P10规格）”，用于对齐方向与模块边界；不等价于当前已实现能力。  
> 运行与配置基线以 [CONFIG_GUIDE.md](/root/ascend_agent/docs/CONFIG_GUIDE.md) 为准；本文件仅负责说明架构目标与当前实现状态。

## 0. 当前实现状态与后续治理（截至 2026-03-20）

### 0.1 实现状态（收口口径）

状态标签：
- Implemented：可在仓库中定位实现，且具备可用闭环或至少具备自洽的最小链路
- Partial：存在实现但关键契约/闭环/幂等性不成立，或存在明确的 P0/P1 风险
- Stub：有接口/骨架/文档承诺，但默认路径不可用或关键依赖未装配
- Draft：仅设计与占位，代码中尚未出现

实现映射（仅列本文件图中核心构件）：

| 模块 | 目标（图中定位） | 当前状态 | 备注（来自评审结论） |
|---|---|---|---|
| KnowledgeBaseService v1 | 索引/检索基础设施 | Partial | 构建基线冲突、索引生命周期/幂等性与元数据 round-trip 不成立 |
| 知识库 v2（TestScenario/Builder/Retriever/Service） | 场景化检索 | Partial | YAML 加载器卡死风险；默认检索装配不稳定；索引更新与检索实体回源不闭环 |
| Spring Boot 接入层（Config/Controller） | 运行外壳/对外 API | Partial | 已具备预构建 JAR 运行外壳；服务可在 `8080` 暴露 `/actuator/health`，但不代表业务主链路已闭环 |
| Chroma 开发态向量存储 | 向量检索基础设施 | Implemented | Sprint-1 基线锁定为 Chroma `0.5.20`，脚本入口为 `scripts/install_chroma_0520.sh` / `scripts/start_chroma_22333.sh`，默认地址 `127.0.0.1:22333`，长期目录合同根为 `ASCEND_AGENT_HOME=./.ascend_agent/` |
| UseCaseOptimizer | 用例优化层 | Draft | 仅有文档与架构占位，当前仓库未实现 |
| ApiTestGenerator / CodeValidator 等 | 代码生成层 | Draft | 仅有文档与架构占位，当前仓库未实现 |
| 零交互引擎 / 主 Agent | 应用层主入口 | Draft | 仅有文档与架构占位，当前仓库未实现 |

### 0.2 当前基线/后续治理（收口说明）

当前基线结论：
- 本仓库当前主要是“知识库原型 + v2 骨架”，尚未形成架构图中的端到端零交互闭环。
- 当前 Java 基线已统一为 21；应用默认 `8080`，Chroma 开发态默认 `127.0.0.1:22333`。
- 长期默认目录合同已收口为 `ASCEND_AGENT_HOME=./.ascend_agent/`；`tools/chroma-venv-0520`、`chroma`、`db`、`logs`、`pids` 都应从该根目录派生，不再把 `/tmp` 视为长期默认路径。
- “服务能启动”只说明运行壳存在，不等价于图中所有模块都已实现。

后续治理约定：
- 任何新增能力必须先标注状态（Implemented/Partial/Stub/Draft），禁止把规划能力写成已完成。
- 若图与实现发生漂移：优先更新 [CONFIG_GUIDE.md](/root/ascend_agent/docs/CONFIG_GUIDE.md) 的运行基线与本节状态映射，再讨论是否扩展图中模块。

## 1. 整体架构（新版 - P10规格）

```mermaid
graph TB
    subgraph "用户层"
        User[测试人员<br/>不懂开发]
    end

    subgraph "应用层"
        Agent[TestGenerationAgent<br/>主入口]
        ZeroInteraction[零交互引擎]
    end

    subgraph "用例优化层"
        Optimizer[UseCaseOptimizer<br/>用例优化器]
        PromptEngine[Prompt模板引擎]
        LinkProcessor[ReferenceLinkProcessor<br/>参考链接处理]
    end

    subgraph "知识库层 v2"
        KBv2[KnowledgeBaseService v2]
        ScenarioRetriever[TestScenarioRetriever<br/>场景检索]
        ScenarioBuilder[ScenarioBuilder<br/>场景构建器]
        subgraph "存储"
            VectorDB[(向量数据库<br/>Chroma/Milvus)]
            MetaDB[(元数据库<br/>SQLite)]
        end
    end

    subgraph "代码生成层"
        Generator[ApiTestGenerator<br/>API测试生成器]
        TemplateEngine[TestCodeTemplateEngine<br/>模板引擎]
        AssertionGen[AssertionGenerator<br/>断言生成]
        SyntaxValidator[CodeValidator<br/>语法验证]
    end

    subgraph "外部服务"
        LLM[LLM服务<br/>qwen-coder-plus]
        EmbedAPI[Embedding API<br/>bge系列]
        RerankAPI[Rerank API<br/>bge-reranker]
    end

    User --> Agent
    Agent --> ZeroInteraction
    ZeroInteraction --> Optimizer
    Optimizer --> PromptEngine
    Optimizer --> LinkProcessor
    Optimizer --> LLM

    Optimizer --> KBv2
    KBv2 --> ScenarioRetriever
    KBv2 --> ScenarioBuilder
    ScenarioRetriever --> VectorDB
    ScenarioRetriever --> RerankAPI
    ScenarioBuilder --> VectorDB

    KBv2 --> Generator
    Optimizer --> Generator
    Generator --> TemplateEngine
    Generator --> AssertionGen
    Generator --> SyntaxValidator
    Generator --> LLM

    style User fill:#e1f5ff
    style Agent fill:#fff4e1
    style Optimizer fill:#e8f5e9
    style Generator fill:#f3e5f5
    style VectorDB fill:#c8e6c9
    style LLM fill:#ffe0b2
```

## 2. 核心流程（新版 - P10规格）

```mermaid
flowchart TB
    subgraph "Step 1: 输入"
        A1[用户输入用例描述]
        A2[可选：参考链接]
    end

    subgraph "Step 2: 用例优化"
        B1[UseCaseOptimizer]
        B2[Prompt模板引擎]
        B3[LLM调用]
        B4[OptimizedTestCase]
    end

    subgraph "Step 3: 场景检索"
        C1[TestScenarioRetriever]
        C2[Embedding向量化]
        C3[向量检索<br/>召回Top20]
        C4[Rerank重排<br/>精排Top5]
        C5[TestScenario]
    end

    subgraph "Step 4: 代码生成"
        D1[ApiTestGenerator]
        D2[模板渲染]
        D3[断言生成]
        D4[LLM增强]
        D5[GeneratedTestCode]
    end

    subgraph "Step 5: 输出"
        E1[优化用例]
        E2[API测试代码]
    end

    A1 --> B1
    A2 --> B1
    B1 --> B2
    B2 --> B3
    B3 --> B4

    B4 --> C1
    C1 --> C2
    C2 --> C3
    C3 --> C4
    C4 --> C5

    B4 --> D1
    C5 --> D1
    D1 --> D2
    D2 --> D3
    D3 --> D4
    D4 --> D5

    D5 --> E1
    D5 --> E2

    style A1 fill:#e1f5ff
    style B4 fill:#e8f5e9
    style C5 fill:#c8e6c9
    style D5 fill:#f3e5f5
```

## 3. 知识库v2架构

```mermaid
graph TB
    subgraph "输入源"
        API1[ApiMetadata<br/>单个API]
        YAML1[Yaml配置<br/>手动场景]
    end

    subgraph "场景构建"
        Builder[ScenarioBuilder]
        AutoBuild[自动构建<br/>调用图分析]
        ManualBuild[手动定义<br/>YAML解析]
    end

    subgraph "存储层"
        VDB[(向量数据库<br/>TestScenario)]
        Meta[(元数据库<br/>场景元数据)]
    end

    subgraph "检索层"
        Retriever[TestScenarioRetriever]
        Embed[Embedding]
        Rerank[Rerank]
    end

    API1 --> Builder
    YAML1 --> Builder
    Builder --> AutoBuild
    Builder --> ManualBuild
    AutoBuild --> VDB
    ManualBuild --> VDB
    Builder --> Meta

    Retriever --> Embed
    Embed --> VDB
    Retriever --> Rerank
    Rerank --> VDB

    style Builder fill:#e8f5e9
    style VDB fill:#c8e6c9
```

## 4. 数据模型关系

```mermaid
classDiagram
    class OptimizedTestCase {
        +String caseId
        +String scenarioName
        +List~OptimizedStep~ steps
        +List~String~ expectedResults
        +ConfidenceScore confidence
    }

    class OptimizedStep {
        +int stepOrder
        +String action
        +String target
        +Map~String, ParameterValue~ inputParams
        +String expectedOutcome
    }

    class TestScenario {
        +String scenarioId
        +String name
        +List~ScenarioStep~ steps
        +List~Validation~ validations
        +ScenarioMetadata metadata
    }

    class ScenarioStep {
        +int stepOrder
        +String apiId
        +String description
        +Map~String, Object~ inputParams
        +Map~String, String~ outputMapping
    }

    class Validation {
        +ValidationType type
        +String target
        +String expectedValue
    }

    class ApiMetadata {
        +String apiId
        +String className
        +String methodName
        +String httpMethod
        +String endpoint
    }

    class GeneratedTestCode {
        +String testClassName
        +String fullCode
        +List~GeneratedTestStep~ stepCodes
        +List~GeneratedAssertion~ assertions
    }

    OptimizedTestCase --> OptimizedStep
    OptimizedTestCase --> TestScenario
    TestScenario --> ScenarioStep
    TestScenario --> Validation
    ScenarioStep --> ApiMetadata
    GeneratedTestCode --> GeneratedTestStep
    GeneratedTestCode --> GeneratedAssertion

    style OptimizedTestCase fill:#e8f5e9
    style TestScenario fill:#c8e6c9
    style GeneratedTestCode fill:#f3e5f5
```

## 5. 错误处理策略（零交互）

```mermaid
flowchart TB
    A[用户输入] --> B{用例优化}
    B -->|成功| C{场景检索}
    B -->|失败| D[抛出异常<br/>退出]
    C -->|找到| E{代码生成}
    C -->|未找到| D
    E -->|成功| F[输出代码]
    E -->|失败| D

    style D fill:#ffcdd2
    style F fill:#c8e6c9
```

## 6. 技术栈

```mermaid
graph TB
    subgraph "核心框架"
        Spring[Spring Boot 3.x]
        Maven[Maven]
    end

    subgraph "RAG/检索"
        LC4J[LangChain4j 0.35.0]
        Chroma[Chroma]
        Milvus[Milvus]
    end

    subgraph "Embedding/Rerank"
        BGE[bge系列模型]
        Rerank[bge-reranker]
    end

    subgraph "LLM"
        Qwen[qwen-coder-plus]
    end

    subgraph "代码生成"
        FreeMarker[FreeMarker]
        JavaParser[JavaParser]
    end

    Spring --> LC4J
    Spring --> Maven
    LC4J --> Chroma
    LC4J --> Milvus
    LC4J --> BGE
    LC4J --> Rerank
    LC4J --> Qwen
    Spring --> FreeMarker
    Spring --> JavaParser

    style Spring fill:#6db33f
    style LC4J fill:#ff6b6b
    style Qwen fill:#ffa726
```

---

## 附录：原有知识库架构（v1）

> 以下为原有设计，保持兼容。

### A.1 知识库v1架构

```mermaid
graph TB
    subgraph "文档采集层"
        JavaScanner[Java代码扫描<br/>JavaParser]
        WebCrawler[网页抓取<br/>Jsoup]
        OpenAPILoader[OpenAPI解析<br/>Swagger Parser]
    end

    subgraph "文档处理层"
        Splitter[文档分割]
        Cleaner[内容清洗]
        Extractor[结构化提取]
    end

    subgraph "存储层"
        VectorDB[(向量数据库)]
        MetaDB[(元数据库)]
    end

    subgraph "检索层"
        Retriever[语义检索]
        Filter[结果过滤]
    end

    JavaScanner --> Splitter
    WebCrawler --> Splitter
    OpenAPILoader --> Splitter
    Splitter --> Cleaner
    Cleaner --> Extractor
    Extractor --> VectorDB
    Extractor --> MetaDB
    Retriever --> VectorDB
    Retriever --> Filter

    style VectorDB fill:#c8e6c9
```

### A.2 ApiMetadata结构

```java
public class ApiMetadata {
    private String apiId;              // 唯一标识
    private String className;          // 类名
    private String methodName;          // 方法名
    private String signature;          // 方法签名
    private String description;         // 描述文本
    private DocumentSourceType sourceType;
    private String sourceLocation;
    private List<Parameter> parameters;
    private String returnType;
    private List<String> exceptions;
    private String httpMethod;
    private String endpoint;
    private String requestBody;
    private String responseBody;
    private float[] vector;
}
```

### A.3 知识库v1服务接口

```java
public interface KnowledgeBaseService {
    IndexStats indexJavaProject(String projectPath);
    IndexStats indexExternalDocs(List<DocumentSource> sources);
    List<ApiMetadata> search(String query, int topK, SearchOptions options);
    void updateIndex(List<String> changedFiles);
}
```
