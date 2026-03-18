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

### 3. 向量数据库配置

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

## 配置优先级

1. 环境变量（最高优先级）
2. application.yml中的默认值
3. 代码中的硬编码值（最低优先级）

## 启动应用

```bash
# 设置环境变量后启动
export EMBEDDING_API_URL=https://your-api.com/v1/embeddings
export EMBEDDING_API_KEY=your-key
java -jar ascend-agent.jar
```
