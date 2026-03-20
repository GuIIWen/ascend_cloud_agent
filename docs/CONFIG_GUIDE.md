# 配置指南

## 模型API配置

### 1. Embedding模型配置

在 `application.yml` 中配置Embedding模型的API地址：

```yaml
embedding:
  api-url: ${EMBEDDING_API_URL:http://localhost:8080/v1/embeddings}
  api-key: ${EMBEDDING_API_KEY:}
  model: bge-large-zh-v1.5
  dimension: 1024
```

**环境变量设置：**

```bash
export EMBEDDING_API_URL=https://your-embedding-api.com/v1/embeddings
export EMBEDDING_API_KEY=your-api-key-here
```

### 2. Rerank模型配置

```yaml
rerank:
  api-url: ${RERANK_API_URL:http://localhost:8080/v1/rerank}
  api-key: ${RERANK_API_KEY:}
  top-k: 5
```

**环境变量设置：**

```bash
export RERANK_API_URL=https://your-rerank-api.com/v1/rerank
export RERANK_API_KEY=your-rerank-key-here
```

### 3. LLM模型配置

```yaml
llm:
  api-url: ${LLM_API_URL:http://localhost:8080/v1/chat/completions}
  api-key: ${LLM_API_KEY:}
  model: qwen-coder-plus
  temperature: 0.2
  max-tokens: 4096
```

**配置说明：**

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| api-url | LLM API地址 | http://localhost:8080/v1/chat/completions |
| api-key | API密钥 | （空） |
| model | 模型名称 | qwen-coder-plus |
| temperature | 生成随机性（0-1） | 0.2 |
| max-tokens | 最大生成token数 | 4096 |

**环境变量设置：**

```bash
export LLM_API_URL=https://your-llm-api.com/v1/chat/completions
export LLM_API_KEY=your-llm-key-here
```

**支持的模型列表：**

| 模型 | 适用场景 | 说明 |
|------|----------|------|
| qwen-coder-plus | 代码生成（推荐） | 阿里通义编码专用模型 |
| qwen-plus | 通用对话 | 通用能力强 |
| qwen-max | 高质量生成 | 效果最佳，速度较慢 |
| gpt-4 | 代码生成 | OpenAI模型 |
| gpt-3.5-turbo | 快速测试 | 成本低，速度快 |

### 4. 向量数据库配置

**开发环境（Chroma）：**

```yaml
vector-store:
  type: chroma
  url: http://localhost:8000
  collection: api-knowledge-base
```

**生产环境（Milvus）：**

```yaml
vector-store:
  type: milvus
  url: http://milvus-host:19530
  collection: api-knowledge-base
```

## API接口规范

### Embedding API

**请求格式：**

```json
POST /v1/embeddings
{
  "input": ["文本内容"],
  "model": "bge-large-zh-v1.5"
}
```

**响应格式：**

```json
{
  "data": [
    {
      "embedding": [0.1, 0.2, ...],
      "index": 0
    }
  ]
}
```

### Rerank API

**请求格式：**

```json
POST /v1/rerank
{
  "query": "查询文本",
  "documents": ["文档1", "文档2"],
  "top_n": 5
}
```

**响应格式：**

```json
{
  "results": [
    {
      "index": 0,
      "relevance_score": 0.95
    }
  ]
}
```

### LLM API（Chat Completions）

**请求格式：**

```json
POST /v1/chat/completions
{
  "model": "qwen-coder-plus",
  "messages": [
    {
      "role": "system",
      "content": "你是一个专业的Java测试工程师..."
    },
    {
      "role": "user", 
      "content": "为以下方法生成单元测试：\npublic int add(int a, int b) {\n    return a + b;\n}"
    }
  ],
  "temperature": 0.2,
  "max_tokens": 4096
}
```

**响应格式：**

```json
{
  "id": "chatcmpl-xxx",
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "生成的测试代码..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 100,
    "completion_tokens": 200,
    "total_tokens": 300
  }
}
```

**错误响应格式：**

```json
{
  "error": {
    "message": "Invalid API key",
    "type": "authentication_error",
    "code": 401
  }
}
```

## Prompt模板配置

### 系统Prompt模板

系统Prompt定义AI角色和行为规则，支持变量注入：

```yaml
prompt:
  system-template: |
    你是一个专业的Java单元测试工程师。
    擅长为各种Java方法生成高质量的JUnit测试用例。
    
    遵循以下原则：
    - 使用JUnit 5框架
    - 包含有意义的测试用例命名
    - 覆盖正常路径和边界条件
    - 添加必要的注释说明
    
  user-template: |
    请为以下Java代码生成单元测试：
    
    ```java
    ${code}
    ```
    
    要求：
    - 测试类名：{ClassName}Test
    - 测试方法使用@DisplayName注解
    - 包含正向测试和边界测试
```

**变量注入说明：**

| 变量 | 说明 | 示例 |
|------|------|------|
| `${code}` | 待测试的Java代码 | `public int add(int a, int b){...}` |
| `${className}` | 类名 | `CalculatorService` |
| `${methodName}` | 方法名 | `add` |
| `${packageName}` | 包名 | `com.example.service` |

### 自定义Prompt

在 `application.yml` 中覆盖默认模板：

```yaml
prompt:
  system-template: |
    你是一个专业的Java测试工程师，专注于...
  user-template: |
    为以下代码生成测试：${code}
```

## 模型切换指南

### 切换步骤

1. **修改模型配置**
   
   编辑 `application.yml`：
   ```yaml
   llm:
     model: qwen-plus  # 切换为通用模型
   ```

2. **调整生成参数**
   
   不同模型建议使用不同参数：
   
   | 模型 | temperature | max-tokens | 说明 |
   |------|-------------|-------------|------|
   | qwen-coder-plus | 0.2 | 4096 | 代码生成专用 |
   | qwen-plus | 0.3 | 2048 | 通用对话 |
   | qwen-max | 0.5 | 4096 | 高质量生成 |
   | gpt-4 | 0.2 | 4096 | OpenAI代码 |

3. **验证配置**
   
   启动应用后查看日志：
   ```bash
   java -jar ascend-agent.jar 2>&1 | grep -i "llm"
   ```

### 注意事项

1. **API兼容性**
   - 确保API支持 OpenAI Chat Completions 格式
   - 不同提供商的API端点可能不同

2. **模型特性**
   - 代码专用模型（qwen-coder-plus）在代码生成任务上效果更好
   - 通用模型适合需要创意的场景

3. **成本控制**
   - 设置合理的 `max-tokens` 避免过量消耗
   - 高并发场景建议使用支持batch的模型

4. **错误处理**
   - 实现重试机制处理临时性API错误
   - 记录完整的错误响应便于排查

## 实际操作示例

### 完整配置示例

```yaml
# application.yml
embedding:
  api-url: ${EMBEDDING_API_URL:http://localhost:8080/v1/embeddings}
  api-key: ${EMBEDDING_API_KEY:}
  model: bge-large-zh-v1.5
  dimension: 1024

rerank:
  api-url: ${RERANK_API_URL:http://localhost:8080/v1/rerank}
  api-key: ${RERANK_API_KEY:}
  top-k: 5

llm:
  api-url: ${LLM_API_URL:http://localhost:8080/v1/chat/completions}
  api-key: ${LLM_API_KEY:}
  model: qwen-coder-plus
  temperature: 0.2
  max-tokens: 4096

vector-store:
  type: chroma
  url: http://localhost:8000
  collection: api-knowledge-base
```

### 环境变量配置脚本

创建 `set-env.sh` 脚本：

```bash
#!/bin/bash
# Embedding配置
export EMBEDDING_API_URL=https://api.example.com/v1/embeddings
export EMBEDDING_API_KEY=your-embedding-key

# Rerank配置
export RERANK_API_URL=https://api.example.com/v1/rerank
export RERANK_API_KEY=your-rerank-key

# LLM配置
export LLM_API_URL=https://api.example.com/v1/chat/completions
export LLM_API_KEY=your-llm-key

# 启动应用
java -jar ascend-agent.jar
```

### 验证配置

```bash
# 1. 检查环境变量
echo $LLM_API_URL
echo $LLM_API_KEY

# 2. 启动并观察日志
java -jar ascend-agent.jar

# 3. 查看LLM服务是否正常初始化
grep -i "LLM service initialized" logs/app.log
```

### 常见问题排查

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| API连接超时 | URL错误或网络不通 | 检查 `llm.api-url` 配置 |
| 401认证错误 | API Key无效 | 确认 `llm.api-key` 正确 |
| 生成内容为空 | temperature过高 | 降低 `temperature` 至0.1-0.3 |
| Token溢出 | max-tokens过小 | 增大 `max-tokens` 或精简prompt |
| 模型不支持 | 使用了未知模型 | 参考支持的模型列表 |

## 配置优先级

1. 环境变量（最高优先级）
2. application.yml中的默认值
3. 代码中的硬编码值（最低优先级）

## 启动应用

```bash
# 设置环境变量后启动
export EMBEDDING_API_URL=https://your-api.com/v1/embeddings
export EMBEDDING_API_KEY=your-key
export LLM_API_URL=https://your-llm-api.com/v1/chat/completions
export LLM_API_KEY=your-llm-key
java -jar ascend-agent.jar
```
