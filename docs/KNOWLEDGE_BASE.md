# 知识库模块设计文档

> 文档状态：Partial（设计为主，含部分实现）。本文件同时包含目标方案与实现设想，其中部分能力在当前仓库仍处于 Stub/Draft 状态。  
> 当前实现基线与已知偏差以 [meeting.md](/root/ascend_agent/meeting.md) 中 `2026-03-20 16:00:13 +0800` 评审结论为准。

## 0. 当前实现状态与后续治理（截至 2026-03-20）

### 0.1 实现状态（收口口径）

状态标签：Implemented / Partial / Stub / Draft（含义参见 [ARCHITECTURE.md](/root/ascend_agent/docs/ARCHITECTURE.md) 的“0.1 实现状态”）

实现映射（按本文件核心承诺收口）：

| 能力 | 文档承诺 | 当前状态 | 备注（来自评审结论） |
|---|---|---|---|
| 扫描 Java 项目并提取 API 元数据 | v1 核心能力 | Partial | `apiId` 不稳定导致重复索引与脏数据；增量更新不可依赖 |
| 导入网页文档（HTML） | MVP 优先级 1 | Partial | 有抓取/解析相关实现与依赖，但仓库存在编译阻塞，未形成可验证闭环 |
| 导入 OpenAPI/Swagger 文件 | 文档声明支持 | Stub | 枚举/文档中声明支持，但当前实现未处理该类型 |
| 向量化 + 向量存储（Chroma/Milvus） | 可切换存储 | Partial | 有适配与依赖选型，但索引流程原子性与一致性存在风险 |
| 元数据落库 + round-trip | 结果可回源 | Partial | `MetadataStore` 落库字段不完整，检索回来的元数据可能被截断 |

### 0.2 当前基线/后续治理（收口说明）

当前基线结论：
- 本仓库以“知识库基础设施原型”为主，当前编译/测试基线未稳定，本文中的性能指标与端到端指标未被仓库自证。
- 本文后续所有“实现代码示例”均视作示意，不应被解读为当前实现的真实等价物。

后续治理约定：
- 每次合并涉及知识库能力的变更，必须同步更新 [meeting.md](/root/ascend_agent/meeting.md) 的评审记录，并在本文“0.1 实现状态”表中更新状态与备注。
- 对外声明能力以“Implemented 且可验证”为准；Partial/Stub/Draft 禁止在对外材料中描述为已完成。

## 1. 模块概述

### 1.1 职责
知识库模块负责索引Java项目的API信息和外部API文档，提供基于语义的检索能力，是测试用例生成系统的核心基础设施。

### 1.2 核心功能
- 扫描Java项目，提取API元数据
- 导入外部API文档（网页、OpenAPI、Swagger、Markdown）
- 生成API的向量表示
- 构建和维护向量索引
- 提供高效的语义检索接口
- 统一检索内部代码和外部服务API

### 1.3 技术目标
- **检索精度**：Top 5召回率 > 90%
- **检索速度**：单次查询 < 100ms
- **索引构建**：1000个API < 30秒
- **开发效率**：使用成熟框架，最小化自研代码

### 1.4 技术选型

**核心框架**：LangChain4j（Java原生RAG框架）
- 内置文档处理pipeline
- 支持多种向量数据库
- 开箱即用的RAG能力

**向量数据库**：
- 开发阶段：Chroma（轻量、易部署）
- 生产环境：Milvus（高性能、企业级）

**文档解析**：
- HTML解析：Jsoup
- OpenAPI解析：Swagger Parser
- 内容提取：LangChain4j内置能力

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────┐
│         知识库服务                       │
├─────────────────────────────────────────┤
│  1. 文档采集层                           │
│     - Java代码扫描（JavaParser）         │
│     - 网页抓取（Jsoup）                  │
│     - OpenAPI解析（Swagger Parser）      │
├─────────────────────────────────────────┤
│  2. 文档处理层（LangChain4j）            │
│     - 文档分割                           │
│     - 内容清洗                           │
│     - 结构化提取                         │
├─────────────────────────────────────────┤
│  3. 向量化层（LangChain4j）              │
│     - Embedding模型集成                  │
│     - 批量向量化                         │
├─────────────────────────────────────────┤
│  4. 存储层                               │
│     - 向量存储（Chroma/Milvus）          │
│     - 元数据存储（SQLite）               │
├─────────────────────────────────────────┤
│  5. 检索层（LangChain4j）                │
│     - 语义检索                           │
│     - 重排序                             │
│     - 结果过滤                           │
└─────────────────────────────────────────┘
```

### 2.2 模块结构（简化）

```
knowledge-base/
├── crawler/          # 文档采集
│   ├── JavaCodeScanner.java
│   ├── WebDocumentCrawler.java
│   └── OpenApiLoader.java
├── processor/        # 文档处理（基于LangChain4j）
│   ├── DocumentProcessor.java
│   └── ApiMetadataExtractor.java
├── storage/          # 存储层
│   ├── VectorStoreAdapter.java
│   └── MetadataStore.java
└── retriever/        # 检索层（基于LangChain4j）
    └── ApiRetriever.java
```

### 2.3 核心接口定义

```java
/**
 * 知识库服务接口
 */
public interface KnowledgeBaseService {

    /**
     * 索引Java项目
     */
    IndexStats indexJavaProject(String projectPath);

    /**
     * 索引外部文档
     */
    IndexStats indexExternalDocs(List<DocumentSource> sources);

    /**
     * 语义检索
     */
    List<ApiMetadata> search(String query, int topK, SearchOptions options);

    /**
     * 增量更新
     */
    void updateIndex(List<String> changedFiles);
}
```

## 3. 数据模型设计

### 3.1 API元数据

```java
public class ApiMetadata {
    // 基本信息
    private String apiId;              // 唯一标识
    private String className;          // 类名
    private String methodName;         // 方法名
    private String signature;          // 方法签名
    private String description;        // 描述文本
    
    // 来源信息
    private DocumentSourceType sourceType;  // INTERNAL_CODE / EXTERNAL_DOC
    private String sourceLocation;          // 文件路径或URL
    
    // 参数信息
    private List<Parameter> parameters;
    private String returnType;
    private List<String> exceptions;
    
    // 外部API特有字段
    private String httpMethod;         // GET, POST等
    private String endpoint;           // /api/users/{id}
    private String requestBody;        // 请求体示例
    private String responseBody;       // 响应体示例
    
    // 向量信息
    private float[] vector;            // embedding向量
}

public enum DocumentSourceType {
    INTERNAL_CODE,    // Java源代码
    WEB_PAGE,         // 网页文档
    OPENAPI_FILE,     // OpenAPI/Swagger文件
    MARKDOWN_FILE     // Markdown文档
}
```

### 3.2 文档源配置

```java
public class DocumentSource {
    private String id;                 // 唯一标识
    private String name;               // 显示名称
    private DocumentSourceType type;   // 来源类型
    private String location;           // URL或文件路径
    private boolean enabled;           // 是否启用
    private int refreshInterval;       // 刷新间隔（秒）
    private Map<String, String> metadata;
}
```

## 4. 外部文档支持

### 4.1 支持的文档类型

**优先级1（MVP）**：
- 网页文档（最重要）- 如华为云API文档
- OpenAPI/Swagger文件
- Markdown文档

**优先级2（未来）**：
- GraphQL schema
- gRPC proto文件

### 4.2 网页文档处理流程

```
URL → Jsoup抓取 → 内容提取 → LangChain4j处理 → 向量化 → 存储
```

**关键点**：
- 使用Jsoup抓取HTML
- 提取主要内容（去除导航、广告）
- 识别API端点（正则匹配）
- 转换为LangChain4j Document格式

### 4.3 OpenAPI文档处理

```java
// 使用Swagger Parser解析
OpenAPI openAPI = new OpenAPIV3Parser().read(filePath);

// 遍历所有API端点
openAPI.getPaths().forEach((path, pathItem) -> {
    pathItem.readOperationsMap().forEach((method, operation) -> {
        // 提取API信息
        ApiMetadata api = extractFromOperation(path, method, operation);
    });
});
```

## 5. 基于LangChain4j的实现方案

### 5.1 为什么选择LangChain4j

- **纯Java实现**：无需跨语言调用
- **开箱即用**：内置文档处理、向量化、RAG
- **开发量最小**：约300行核心代码
- **易于集成**：与现有Java项目无缝集成

### 5.2 核心依赖

```xml
<dependencies>
    <!-- LangChain4j核心 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.35.0</version>
    </dependency>
    
    <!-- 向量存储 - Chroma -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-chroma</artifactId>
        <version>0.35.0</version>
    </dependency>
    
    <!-- 本地嵌入模型 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
        <version>0.35.0</version>
    </dependency>
    
    <!-- HTML解析 -->
    <dependency>
        <groupId>org.jsoup</groupId>
        <artifactId>jsoup</artifactId>
        <version>1.17.2</version>
    </dependency>
</dependencies>
```

### 5.3 文档处理实现

```java
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class DocumentProcessor {
    
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    
    public void processAndStore(Document document) {
        // 1. 文档分割（框架自动处理）
        var splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> segments = splitter.split(document);
        
        // 2. 向量化并存储（一行代码）
        embeddingStore.addAll(
            embeddingModel.embedAll(segments).content(),
            segments
        );
    }
}
```

### 5.4 网页抓取实现

```java
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class WebDocumentCrawler {
    
    public Document crawl(String url) throws IOException {
        org.jsoup.nodes.Document doc = Jsoup.connect(url).get();
        
        // 提取主要内容
        String title = doc.title();
        String mainContent = extractMainContent(doc);
        
        // 转换为LangChain4j Document
        return Document.from(mainContent);
    }
    
    private String extractMainContent(org.jsoup.nodes.Document doc) {
        // 移除噪声元素
        doc.select("nav, header, footer, aside, script, style").remove();
        
        // 提取主体内容
        Element main = doc.selectFirst("main, article, .content");
        return main != null ? main.text() : doc.body().text();
    }
}
```

## 6. 向量数据库选型

### 6.1 对比分析

| 特性 | Chroma | Milvus | 推荐场景 |
|------|--------|--------|----------|
| 部署复杂度 | ⭐ 简单 | ⭐⭐⭐ 复杂 | Chroma适合开发 |
| 性能 | ⭐⭐⭐ 中等 | ⭐⭐⭐⭐⭐ 极快 | Milvus适合生产 |
| 可扩展性 | ⭐⭐ 单机 | ⭐⭐⭐⭐⭐ 分布式 | Milvus支持大规模 |
| 功能丰富度 | ⭐⭐⭐ 基础 | ⭐⭐⭐⭐⭐ 完善 | Milvus功能更强 |

### 6.2 推荐方案

- **开发/测试**：Chroma（Docker一键启动）
- **生产环境**：Milvus（高性能、企业级）

**切换成本**：只需修改配置，无需改代码

### 6.3 Chroma启动

```bash
docker run -p 8000:8000 chromadb/chroma
```

### 6.4 Milvus启动

```bash
# 使用docker-compose
wget https://github.com/milvus-io/milvus/releases/download/v2.3.0/milvus-standalone-docker-compose.yml -O docker-compose.yml
docker-compose up -d
```

## 7. 配置设计

### 7.1 应用配置（application.yml）

```yaml
knowledge-base:
  # 向量存储配置
  vector-store:
    type: chroma  # chroma 或 milvus
    url: http://localhost:8000
    collection: api-knowledge-base
  
  # 嵌入模型配置
  embedding:
    provider: custom  # custom/openai/local
    api-url: ${EMBEDDING_API_URL}
    api-key: ${EMBEDDING_API_KEY}
    model: bge-large-zh-v1.5
    dimension: 1024
  
  # 重排序模型配置
  rerank:
    provider: custom
    api-url: ${RERANK_API_URL}
    api-key: ${RERANK_API_KEY}
    top-k: 5
  
  # 文档源配置
  sources:
    # Java项目
    - id: internal-code
      type: INTERNAL_CODE
      location: ./src/main/java
      enabled: true
    
    # 网页文档
    - id: huawei-api-docs
      type: WEB_PAGE
      location: https://support.huaweicloud.com/api-modelarts/
      enabled: true
      refresh-interval: 86400  # 24小时
      metadata:
        crawl-depth: 2
        rate-limit: 1
    
    # OpenAPI文档
    - id: user-service-api
      type: OPENAPI_FILE
      location: ./docs/external/openapi.yaml
      enabled: true
```

### 7.2 环境变量

```bash
# Embedding服务
export EMBEDDING_API_URL=https://your-embedding-api.com/v1/embeddings
export EMBEDDING_API_KEY=your-key

# Rerank服务
export RERANK_API_URL=https://your-rerank-api.com/v1/rerank
export RERANK_API_KEY=your-key
```

## 8. 实施步骤

### 8.1 环境准备（1小时）

1. 安装Docker
2. 启动Chroma：`docker run -p 8000:8000 chromadb/chroma`
3. 配置Maven依赖

### 8.2 核心开发（1-2天）

**需要自己开发的部分（约300行代码）**：

1. **URL收集器**（50行）
   - 爬取文档站点地图
   - 提取API文档链接

2. **文档解析器**（100行）
   - 使用Jsoup提取结构化信息
   - 转换为LangChain4j Document

3. **批量处理脚本**（50行）
   - 遍历URL列表
   - 调用框架API处理

4. **REST API封装**（100行，可选）
   - Spring Boot暴露检索接口

**框架自动处理的部分**：
- 文档分割
- 向量化
- 向量存储
- 语义检索
- RAG生成

### 8.3 数据采集（自动化）

运行爬虫脚本，处理所有API文档

### 8.4 优化调试（1天）

- 调整检索参数
- 优化Prompt
- 性能测试

**总计：3-4天完成MVP**

## 9. 关键优势

### 9.1 开发效率

- **传统方案**：从零实现需要2-3周
- **LangChain4j方案**：3-4天完成MVP
- **代码量减少**：90%（从3000行到300行）

### 9.2 维护成本

- 使用成熟框架，bug少
- 社区活跃，持续更新
- 文档完善，易于上手

### 9.3 扩展性

- 轻松切换向量数据库
- 支持多种文档格式
- 易于添加新的数据源

## 10. 后续优化方向

- 支持更多文档格式（GraphQL、gRPC）
- 优化检索精度（混合检索、重排序）
- 支持多语言（Kotlin、Scala）
- 分布式部署（Milvus集群）
