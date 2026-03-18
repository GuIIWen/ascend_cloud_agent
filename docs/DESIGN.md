# Java测试用例生成Agent - 技术设计文档

## 1. 系统概述

### 1.1 目标
构建一个智能Agent，根据用户自然语言输入，自动生成Java单元测试代码。

### 1.2 核心能力
- 理解用户意图，匹配项目中的Java API
- 利用embedding模型进行语义检索
- 使用rerank模型精确排序候选API
- 调用LLM生成高质量测试代码
- 支持自定义模型配置

## 2. 系统架构

### 2.1 整体流程
```
用户输入
  ↓
意图理解 & 关键词提取
  ↓
Embedding向量化
  ↓
向量数据库检索（召回Top 20-50）
  ↓
Rerank模型重排序（精排Top 5）
  ↓
展示候选API供用户确认
  ↓
构建Prompt（API信息 + 用户需求）
  ↓
LLM生成测试代码
  ↓
代码格式化 & 验证
  ↓
写入测试文件
```

### 2.2 核心模块

#### 2.2.1 项目索引模块（ProjectIndexer）
**职责**：扫描Java项目，提取API元数据，构建向量索引

**功能**：
- 扫描src/main/java目录
- 解析Java文件（使用JavaParser）
- 提取类、方法、字段、注解信息
- 生成API描述文本
- 调用Embedding服务生成向量
- 存储到向量数据库

**输出数据结构**：
```json
{
  "apiId": "com.example.UserService.login",
  "className": "UserService",
  "methodName": "login",
  "signature": "User login(String username, String password)",
  "description": "用户登录认证，验证用户名和密码",
  "parameters": [...],
  "returnType": "User",
  "dependencies": ["UserRepository", "PasswordEncoder"],
  "vector": [0.123, 0.456, ...]
}
```

#### 2.2.2 API匹配模块（ApiMatcher）
**职责**：根据用户输入，检索和排序最相关的API

**流程**：
1. 用户输入向量化（Embedding）
2. 向量数据库检索（FAISS/Milvus）
3. 召回Top 20-50候选
4. Rerank模型重排序
5. 返回Top 5结果

#### 2.2.3 测试代码生成模块（TestGenerator）
**职责**：调用LLM生成测试代码

**Prompt构建策略**：
```
你是一个Java测试专家。请为以下API生成JUnit 5单元测试：

【API信息】
类名：UserService
方法签名：User login(String username, String password)
方法描述：用户登录认证
依赖：UserRepository, PasswordEncoder
异常：AuthenticationException

【测试要求】
- 使用JUnit 5和Mockito
- 包含正常场景和异常场景
- 使用AAA模式（Arrange-Act-Assert）
- 添加@DisplayName注解

【用户需求】
{用户的具体输入}

请生成完整的测试类代码。
```

#### 2.2.4 配置管理模块（ConfigManager）
**职责**：加载和管理模型配置

**支持的配置项**：
- Embedding模型配置（API地址、密钥、模型名）
- Rerank模型配置
- LLM配置
- 向量数据库配置
- 项目路径配置

#### 2.2.5 服务抽象层
**接口定义**：
- `EmbeddingService`：文本向量化
- `RerankService`：候选结果重排序
- `LLMService`：代码生成
- `VectorDBService`：向量存储和检索

## 3. 数据流设计

### 3.1 索引构建阶段（离线）
```
Java源码文件
  ↓ [JavaParser解析]
API元数据（类、方法、签名、Javadoc）
  ↓ [生成描述文本]
"UserService.login方法：用户登录认证，接收用户名和密码参数，返回User对象"
  ↓ [Embedding服务]
向量 [0.123, 0.456, ..., 0.789]
  ↓ [存储]
向量数据库（FAISS索引）
```

### 3.2 查询匹配阶段（在线）
```
用户输入："测试用户登录功能"
  ↓ [Embedding服务]
查询向量 [0.234, 0.567, ..., 0.890]
  ↓ [向量检索]
候选API列表（Top 50，基于余弦相似度）
  ↓ [Rerank服务]
精排后的Top 5 API
  ↓ [用户确认]
选中的API：UserService.login
```

### 3.3 代码生成阶段
```
选中的API元数据 + 用户需求
  ↓ [构建Prompt]
完整的生成指令
  ↓ [LLM服务]
生成的测试代码（String）
  ↓ [代码格式化]
格式化后的代码
  ↓ [语法验证]
验证通过
  ↓ [文件写入]
src/test/java/com/example/UserServiceTest.java
```

## 4. 关键技术决策

### 4.1 为什么需要Embedding + Rerank两阶段？

**Embedding阶段（召回）**：
- 优点：速度快，可以从海量API中快速筛选
- 缺点：精度有限，可能召回不够精确

**Rerank阶段（精排）**：
- 优点：精度高，考虑query和候选的交互关系
- 缺点：速度慢，不适合大规模数据

**两阶段结合**：先用Embedding快速召回，再用Rerank精确排序，平衡速度和精度。

### 4.2 向量数据库选型

**FAISS（推荐用于MVP）**：
- 优点：轻量、本地部署、速度快
- 缺点：功能相对简单

**Milvus（推荐用于生产）**：
- 优点：功能强大、支持分布式、易扩展
- 缺点：部署复杂

### 4.3 API描述文本生成策略

**方案**：类名 + 方法名 + Javadoc + 参数信息

**示例**：
```
UserService类的login方法：用户登录认证功能。
接收参数：username(String)用户名, password(String)密码。
返回：User对象。
可能抛出：AuthenticationException认证失败异常。
依赖：UserRepository用户仓库, PasswordEncoder密码编码器。
```

**优点**：
- 包含足够的语义信息
- 支持中英文混合检索
- 便于向量化

## 5. 配置设计

### 5.1 配置文件结构（config.yaml）
```yaml
embedding:
  api_url: "https://api.example.com/v1/embeddings"
  api_key: "${EMBEDDING_API_KEY}"
  model_name: "bge-large-zh-v1.5"
  dimension: 1024

rerank:
  api_url: "https://api.example.com/v1/rerank"
  api_key: "${RERANK_API_KEY}"
  model_name: "bge-reranker-v2-m3"
  top_k: 5

llm:
  api_url: "https://api.example.com/v1/chat/completions"
  api_key: "${LLM_API_KEY}"
  model_name: "qwen-coder-plus"
  temperature: 0.2
  max_tokens: 4096

vector_db:
  type: "faiss"
  index_path: "./data/api_index"

project:
  source_path: "./src/main/java"
  test_path: "./src/test/java"
```

### 5.2 环境变量支持
- 支持`${VAR_NAME}`语法引用环境变量
- 敏感信息（API Key）不写入配置文件

## 6. 接口设计

### 6.1 核心接口

```java
// Embedding服务
interface EmbeddingService {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}

// Rerank服务
interface RerankService {
    List<RerankResult> rerank(String query, List<String> candidates);
}

// LLM服务
interface LLMService {
    String generateTestCode(String prompt);
}

// 向量数据库服务
interface VectorDBService {
    void addVector(String id, float[] vector, Map<String, Object> metadata);
    List<SearchResult> search(float[] queryVector, int topK);
}
```

### 6.2 主流程接口

```java
// Agent主入口
class TestGenerationAgent {
    public String generateTest(String userInput) {
        // 1. 向量化用户输入
        float[] queryVector = embeddingService.embed(userInput);

        // 2. 向量检索
        List<ApiMetadata> candidates = vectorDB.search(queryVector, 50);

        // 3. Rerank重排序
        List<ApiMetadata> topApis = rerankAndFilter(userInput, candidates, 5);

        // 4. 用户确认
        ApiMetadata selectedApi = userConfirm(topApis);

        // 5. 构建Prompt
        String prompt = buildPrompt(selectedApi, userInput);

        // 6. LLM生成
        String testCode = llmService.generateTestCode(prompt);

        // 7. 格式化和写入
        return formatAndWrite(testCode, selectedApi);
    }
}
```

## 7. 实现计划

### Phase 1: 基础框架（1-2周）
- [ ] 配置管理模块
- [ ] 服务抽象接口
- [ ] Embedding/Rerank/LLM服务实现
- [ ] 基础的HTTP客户端封装

### Phase 2: 索引构建（1-2周）
- [ ] JavaParser集成
- [ ] API元数据提取
- [ ] 描述文本生成
- [ ] FAISS向量索引构建

### Phase 3: 匹配和生成（1-2周）
- [ ] 向量检索实现
- [ ] Rerank集成
- [ ] Prompt工程优化
- [ ] 测试代码生成和写入

### Phase 4: 优化和完善（1周）
- [ ] 增量索引更新
- [ ] 用户交互优化
- [ ] 错误处理和日志
- [ ] 性能优化

## 8. 风险和挑战

### 8.1 技术风险
- **向量检索精度**：可能召回不相关的API
  - 缓解：优化描述文本生成，调整Rerank阈值

- **LLM生成质量**：生成的测试代码可能不符合预期
  - 缓解：Prompt工程优化，提供更多上下文

- **大型项目性能**：索引构建和检索可能较慢
  - 缓解：增量索引，异步处理

### 8.2 产品风险
- **用户意图理解偏差**：用户输入模糊
  - 缓解：提供候选列表，支持用户确认和修正

## 9. 后续扩展方向

- 支持集成测试生成
- 学习项目测试风格
- 支持测试数据自动生成
- 多语言支持（Kotlin, Scala）
- IDE插件集成
