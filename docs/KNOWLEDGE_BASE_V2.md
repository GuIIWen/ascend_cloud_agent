# 知识库v2模块设计文档

> 文档状态：Partial（实现骨架已出现，但与目标闭环存在差距）。  
> 当前实现基线与已知偏差以 [meeting.md](/root/ascend_agent/meeting.md) 中 `2026-03-20 16:00:13 +0800` 评审结论为准。

## 0. 当前实现状态与后续治理（截至 2026-03-20）

### 0.1 实现状态（收口口径）

状态标签：Implemented / Partial / Stub / Draft（含义参见 [ARCHITECTURE.md](/root/ascend_agent/docs/ARCHITECTURE.md) 的“0.1 实现状态”）

实现映射（按 v2 目标能力收口）：

| 能力 | 文档承诺 | 当前状态 | 备注（来自评审结论） |
|---|---|---|---|
| v2 数据模型（TestScenario/Step/Validation/Metadata） | 结构升级基础 | Implemented | 已存在模型类与单测文件，但受构建基线影响未形成可执行验证 |
| ScenarioBuilder 自动构建 | 从 ApiMetadata 组合场景 | Partial | 有实现与测试用例，但与“稳定主键/索引生命周期”未闭环 |
| YAML 场景定义加载 | 手动定义复杂场景 | Partial | 存在确定性卡死风险（需优先治理） |
| TestScenarioRetriever 检索（向量 + Rerank） | 召回 + 精排 | Partial | 默认装配与索引回源/更新删除一致性存在缺口 |
| update/delete 同步向量索引 | 索引生命周期 | Stub | 文档承诺需要成立，但当前实现未形成可证的幂等闭环 |

### 0.2 当前基线/后续治理（收口说明）

当前基线结论：
- v2 现阶段应被视为“可演进骨架”，优先级必须让位于 P0 基线修复（构建基线、YAML 解析、稳定主键、检索可用性与索引生命周期）。

后续治理约定：
- v2 每次演进必须同步更新本节“0.1 实现状态”表，并在 [meeting.md](/root/ascend_agent/meeting.md) 记录新的评审时间戳与证据文件。

## 1. 模块概述

### 1.1 职责
知识库v2是知识库模块的升级版本，从"单个API检索"升级为"操作序列(TestScenario)检索"，支持多步骤API调用流程的语义检索和匹配。

### 1.2 核心变化

| v1 (ApiMetadata) | v2 (TestScenario) |
|-------------------|-------------------|
| 单个API | 多步骤操作序列 |
| 独立检索 | 流程化检索 |
| 无状态 | 有状态（步骤间数据传递） |
| 无验证点 | 支持验证点（Validation） |

### 1.3 技术目标
- **检索精度**：Top 5场景召回率 > 85%
- **检索速度**：单次查询 < 200ms
- **支持步骤数**：单场景最多10步
- **验证点支持**：支持状态码、响应字段、响应内容验证

---

## 2. 数据模型设计

### 2.1 核心数据结构

#### TestScenario（测试场景）
```java
public class TestScenario {
    private String scenarioId;              // 唯一标识，格式：scen_UUID
    private String name;                    // 场景名称，如"用户登录并查询订单"
    private String description;             // 场景描述，中文详细说明
    private List<ScenarioStep> steps;       // 操作步骤列表
    private List<Validation> validations;   // 全局验证点
    private ScenarioMetadata metadata;      // 元数据
}
```

#### ScenarioStep（场景步骤）
```java
public class ScenarioStep {
    private int stepOrder;                  // 步骤序号，从1开始
    private String stepId;                  // 步骤唯一标识
    private String apiId;                   // 关联的ApiMetadata.apiId
    private String description;              // 步骤描述
    private Map<String, Object> inputParams; // 输入参数模板
    private String paramExtractFromPrev;    // 从上一步提取的参数
    private Map<String, String> outputMapping; // 输出参数映射
}
```

#### Validation（验证点）
```java
public class Validation {
    private String validationId;
    private ValidationType type;
    private String target;
    private String expectedValue;
    private String actualValuePath;
    private String description;

    public enum ValidationType {
        ASSERT_EQUAL,
        ASSERT_NOT_NULL,
        ASSERT_CONTAINS,
        ASSERT_STATUS,
        ASSERT_JSON_PATH
    }
}
```

---

## 3. 核心接口设计

### 3.1 TestScenarioService

```java
public interface TestScenarioService {

    IndexStats buildScenarioIndex(List<TestScenario> scenarios);

    List<TestScenario> searchScenarios(String query, int topK);

    List<TestScenario> findByApiId(String apiId);

    Optional<TestScenario> getScenarioById(String scenarioId);

    void updateScenario(TestScenario scenario);

    void deleteScenario(String scenarioId);
}
```

### 3.2 检索流程

```java
public class TestScenarioRetriever {

    public List<TestScenario> retrieve(String query, int topK) {
        // 1. 用户查询向量化
        float[] queryVector = embeddingService.embed(query);

        // 2. 向量检索（召回Top 20）
        List<SearchResult<TestScenario>> candidates =
            vectorDBService.search(queryVector, 20);

        // 3. Rerank重排序（精排Top 5）
        List<String> candidateTexts = candidates.stream()
            .map(this::scenarioToText)
            .toList();
        List<RerankResult> reranked = rerankService.rerank(query, candidateTexts);

        // 4. 返回结果
        return reranked.stream()
            .limit(topK)
            .map(r -> candidates.get(r.index()).getPayload())
            .toList();
    }
}
```

---

## 4. 场景构建策略

### 4.1 自动场景构建

从已索引的ApiMetadata自动组合构建TestScenario：

```java
public class ScenarioBuilder {

    public List<TestScenario> buildFromApiGraph(List<ApiMetadata> apis) {
        // 1. 构建API调用图
        Map<String, Set<String>> callGraph = buildCallGraph(apis);

        // 2. 查找高频路径
        List<List<String>> frequentPaths = findFrequentPaths(callGraph, 3);

        // 3. 转换为TestScenario
        return frequentPaths.stream()
            .map(this::pathToScenario)
            .toList();
    }
}
```

### 4.2 手动场景定义

支持通过YAML配置文件手动定义复杂场景：

```yaml
scenario:
  id: scen_user_login_order
  name: 用户登录并查询订单
  description: 用户完成登录认证后，查询本人的订单列表
  tags:
    - 用户
    - 订单
    - 登录
  steps:
    - order: 1
      apiId: com.example.UserService.login
      description: 用户登录获取Token
      outputMapping:
        token: "$.response.token"
        userId: "$.response.userId"
    - order: 2
      apiId: com.example.OrderService.listOrders
      description: 查询用户订单列表
      paramExtractFromPrev:
        userId: "$.steps[1].output.userId"
        token: "$.steps[1].output.token"
  validations:
    - type: ASSERT_STATUS
      target: "$.status"
      expectedValue: "200"
      description: 验证响应状态码
```

---

## 5. 与其他模块的交互

### 5.1 数据流图

```
┌─────────────────────────────────────────────────────────────────┐
                     知识库v2数据流
├─────────────────────────────────────────────────────────────────┤
  ┌──────────────┐     ┌──────────────────┐
  │ ApiMetadata  │     │ TestScenario     │
  │ (单个API)    │────>│ (操作序列)       │
  └──────────────┘     └──────────────────┘
         │                      │
         │ 自动组合              │ 手动定义
         ↓                      ↓
  ┌──────────────────────────────────────┐
  │         场景构建器 ScenarioBuilder    │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │      向量化 EmbeddingService          │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │      存储 VectorDBService             │
  │      (Chroma/Milvus)                 │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │      检索 TestScenarioRetriever      │
  │      1. 向量检索（召回Top20）         │
  │      2. Rerank重排（精排Top5）        │
  └──────────────────────────────────────┘
                      │
                      ↓ 输出
            List<TestScenario>
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. 实现步骤

### Phase 1: 数据模型重构（1天）
- [ ] 新增TestScenario、ScenarioStep、Validation类
- [ ] 保留ApiMetadata类（兼容v1）
- [ ] 更新数据访问层

### Phase 2: 场景构建器（2天）
- [ ] ScenarioBuilder自动构建逻辑
- [ ] YAML场景配置文件解析
- [ ] 场景导入导出功能

### Phase 3: 检索服务（2天）
- [ ] TestScenarioRetriever实现
- [ ] 向量存储适配器升级
- [ ] Rerank服务集成

### Phase 4: 集成测试（1天）
- [ ] 与UseCaseOptimizer对接
- [ ] 端到端测试
- [ ] 性能基准测试

---

## 7. Task Prompt（供P8执行）

```markdown
## Task: 实现知识库v2模块

### WHY
知识库v2从"单个API检索"升级为"操作序列(TestScenario)检索"，支持多步骤API调用流程的语义检索，是实现零交互测试生成的关键基础设施。

### WHAT
实现知识库v2模块，包括：
1. 数据模型：TestScenario、ScenarioStep、Validation
2. 场景构建器：ScenarioBuilder
3. 检索服务：TestScenarioRetriever
4. 与现有ApiMetadata兼容

### WHERE
- 数据模型：`src/main/java/com/agent/knowledge/v2/model/`
- 场景构建器：`src/main/java/com/agent/knowledge/v2/builder/`
- 检索服务：`src/main/java/com/agent/knowledge/v2/retriever/`
- 接口定义：`src/main/java/com/agent/knowledge/v2/service/`

### HOW MUCH
- 数据模型类：4个（TestScenario, ScenarioStep, Validation, ScenarioMetadata）
- 场景构建器：支持自动构建+配置文件解析
- 检索服务：支持向量检索+Rerank
- 兼容现有ApiMetadata

### 验收标准（目标）
1. 跑通单元测试：`mvn test -Dtest=TestScenario*Test`
2. 向量检索延迟 < 200ms
3. 场景检索召回率验证（人工评估Top5）
4. 与UseCaseOptimizer对接成功

### DON'T
- 不修改现有ApiMetadata数据结构（兼容v1）
- 不直接写死场景数据（通过Builder或配置生成）
- 不在检索服务中包含业务逻辑
```

---

## 8. 配置示例

```yaml
knowledge-base:
  v2:
    scenario-index:
      enabled: true
      collection: test_scenarios
      embedding-dimension: 1024

    builder:
      auto-build:
        enabled: true
        max-steps: 5
        min-frequency: 3
      config-path: ./config/scenarios/

    retrieval:
      recall-top-k: 20
      rerank-top-k: 5
```
