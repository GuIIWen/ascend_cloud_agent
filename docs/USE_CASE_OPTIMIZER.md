# 用例优化器模块设计文档

## 1. 模块概述

### 1.1 职责
用例优化器（UseCaseOptimizer）负责将用户原始用例描述转化为结构化的测试用例，是用户与系统之间的"翻译层"。

### 1.2 核心能力
- 理解用户意图，提取关键信息
- 将自然语言转化为结构化步骤
- 补充参数信息和预期结果
- 识别验证点

### 1.3 设计原则
- **零交互优先**：尽可能自动化解析，减少用户补充
- **结构化输出**：输出标准化JSON/对象
- **可追溯**：保留原始输入和优化后的映射关系

---

## 2. 数据模型设计

### 2.1 输入/输出结构

#### UseCaseOptimizerInput
```java
public class UseCaseOptimizerInput {
    private String rawUserInput;              // 用户原始输入，必填
    private List<String> referenceLinks;     // 参考链接，可为空
    private Map<String, String> context;      // 额外上下文
}
```

#### OptimizedTestCase
```java
public class OptimizedTestCase {
    private String caseId;                    // 唯一标识，格式：case_UUID
    private String originalInput;             // 原始用户输入
    private String scenarioName;              // 优化后的场景名称
    private String scenarioDescription;      // 场景详细描述
    private List<OptimizedStep> steps;        // 优化后的步骤列表
    private List<String> expectedResults;      // 预期结果列表
    private Map<String, ParameterValue> parameters;
    private List<String> validationHints;
    private ConfidenceScore confidence;
}
```

#### OptimizedStep
```java
public class OptimizedStep {
    private int stepOrder;
    private String action;                    // 动作（登录/查询/创建等）
    private String target;                    // 操作目标
    private Map<String, ParameterValue> inputParams;
    private String expectedOutcome;
    private String validationHint;
}
```

#### ParameterValue
```java
public class ParameterValue {
    private String name;
    private String value;
    private ParameterType type;
    private String source;                    // USER_INPUT / AUTO_GENERATED / EXTRACTED
    private String extractionPath;

    public enum ParameterType {
        STRING, NUMBER, BOOLEAN, OBJECT, ARRAY
    }
}
```

#### ConfidenceScore
```java
public class ConfidenceScore {
    private double overall;                  // 总体置信度 0.0-1.0
    private double intentUnderstanding;
    private double parameterExtraction;
    private double stepDivision;
    private List<String> lowConfidenceReasons;

    public boolean isAcceptable() {
        return overall >= 0.7;
    }
}
```

---

## 3. 核心接口设计

### 3.1 UseCaseOptimizer接口

```java
public interface UseCaseOptimizer {

    OptimizedTestCase optimize(UseCaseOptimizerInput input);

    List<OptimizedTestCase> optimizeBatch(List<UseCaseOptimizerInput> inputs);

    QualityAssessment assessQuality(OptimizedTestCase optimizedCase);
}
```

### 3.2 异常定义

```java
public class UseCaseOptimizationException extends RuntimeException {

    public enum ErrorCode {
        INVALID_INPUT,
        LLM_CONNECTION_FAILED,
        PARSING_FAILED,
        LOW_CONFIDENCE,
        TIMEOUT
    }
}
```

---

## 4. Prompt策略设计

### 4.1 系统级Prompt

```markdown
你是一个专业的测试用例分析师。你的任务是将用户输入的自然语言测试用例描述转化为结构化的测试用例JSON。

## 输出格式要求
```json
{
  "scenarioName": "场景名称（简洁，20字以内）",
  "scenarioDescription": "场景详细描述（50-100字）",
  "steps": [
    {
      "stepOrder": 1,
      "action": "动作（登录/查询/创建/删除等）",
      "target": "操作目标（用户/订单/商品等）",
      "inputParams": {"参数名": "参数值"},
      "expectedOutcome": "步骤预期结果"
    }
  ],
  "expectedResults": ["预期结果1", "预期结果2"],
  "validationHints": ["验证点提示1", "验证点提示2"],
  "confidence": {
    "overall": 0.9,
    "intentUnderstanding": 0.95,
    "parameterExtraction": 0.85,
    "stepDivision": 0.9
  }
}
```

## 注意事项
1. steps数组中的stepOrder从1开始
2. action必须是动词，target必须是名词
3. 参数值尽量使用占位符或示例值
4. expectedOutcome描述的是执行后的状态变化
5. 如果无法理解输入，confidence设为0.3以下
```

### 4.2 场景化Prompt模板

根据不同场景类型，使用不同的Prompt模板：

#### CRUD操作模板
```markdown
## 场景类型：CRUD操作

用户通常这样描述：
- "测试创建用户功能"
- "查询用户列表，验证分页"
- "更新订单状态"
- "删除商品"

你的任务是：
1. 识别是哪种操作（Create/Read/Update/Delete）
2. 提取操作对象（用户、订单、商品）
3. 提取操作参数
4. 生成验证点
```

#### 业务流程模板
```markdown
## 场景类型：业务流程

用户通常这样描述：
- "用户注册后登录"
- "下单支付流程"
- "用户找回密码"

你的任务是：
1. 识别流程中的关键步骤
2. 识别步骤间的数据传递
3. 提取每个步骤的输入输出
4. 生成端到端的验证点
```

---

## 5. 实现流程

### 5.1 优化主流程

```java
public class UseCaseOptimizerImpl implements UseCaseOptimizer {

    @Override
    public OptimizedTestCase optimize(UseCaseOptimizerInput input) {
        // 1. 输入验证
        validateInput(input);

        // 2. 处理参考链接（如果有）
        String enhancedInput = input.getRawUserInput();
        if (input.getReferenceLinks() != null && !input.getReferenceLinks().isEmpty()) {
            String processedReferences = linkProcessor.process(input.getReferenceLinks());
            enhancedInput = enhanceInputWithReferences(enhancedInput, processedReferences);
        }

        // 3. 构建Prompt
        String prompt = templateEngine.buildPrompt("structured_optimization",
            Map.of("userInput", enhancedInput));

        // 4. 调用LLM
        String llmResponse = llmService.generate(prompt);

        // 5. 解析响应
        OptimizedTestCase result = parseAndValidateResponse(llmResponse);

        // 6. 后处理
        postProcess(result);

        return result;
    }
}
```

### 5.2 参考链接处理器

```java
public class ReferenceLinkProcessor {

    public String process(List<String> links) {
        List<String> extractedContents = new ArrayList<>();
        for (String link : links) {
            try {
                String content = fetchContent(link);
                extractedContents.add(content);
            } catch (Exception e) {
                log.warn("Failed to fetch reference link: {}", link, e);
            }
        }
        return String.join("\n\n", extractedContents);
    }
}
```

---

## 6. 与其他模块的交互

### 6.1 数据流图

```
┌─────────────────────────────────────────────────────────────────┐
                    用例优化器数据流
├─────────────────────────────────────────────────────────────────┤
  ┌──────────────────────────────────────┐
  │       UseCaseOptimizerInput          │
  │  - rawUserInput: "测试用户登录"       │
  │  - referenceLinks: [...]             │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │      参考链接处理 ReferenceLinkProcessor│
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │      Prompt构建 PromptTemplateEngine  │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │           LLMService                  │
  │      qwen-coder-plus                 │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │      响应解析 ResponseParser          │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │       OptimizedTestCase              │
  │  - scenarioName                      │
  │  - steps                             │
  │  - confidence                        │
  └──────────────────────────────────────┘
                      │
                      ↓ 输出到ApiTestGenerator
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. 错误处理策略

### 7.1 错误分类与处理

| 错误类型 | 处理策略 | 是否抛异常 |
|----------|----------|-----------|
| 输入为空 | 返回错误信息 | 是 |
| LLM连接失败 | 重试3次，指数退避 | 是 |
| 解析失败 | 返回原始LLM响应 | 是 |
| 置信度过低 | 记录警告，继续流程 | 否 |
| 超时 | 返回错误 | 是 |

### 7.2 降级策略

当LLM服务不可用时：

```java
public OptimizedTestCase optimizeFallback(UseCaseOptimizerInput input) {
    OptimizedTestCase fallback = new OptimizedTestCase();
    fallback.setCaseId("fallback_" + UUID.randomUUID());
    fallback.setScenarioName(input.getRawUserInput());
    fallback.setConfidence(createLowConfidenceScore("LLM unavailable"));

    // 简单的步骤提取
    List<String> sentences = splitIntoSentences(input.getRawUserInput());
    List<OptimizedStep> steps = new ArrayList<>();
    for (int i = 0; i < sentences.size(); i++) {
        OptimizedStep step = new OptimizedStep();
        step.setStepOrder(i + 1);
        step.setAction(extractAction(sentences.get(i)));
        step.setTarget(extractTarget(sentences.get(i)));
        steps.add(step);
    }
    fallback.setSteps(steps);
    return fallback;
}
```

---

## 8. 实现步骤

### Phase 1: 核心接口（1天）
- [ ] 定义数据模型
- [ ] 定义UseCaseOptimizer接口
- [ ] 实现Prompt模板引擎

### Phase 2: LLM集成（1天）
- [ ] 集成LLM服务
- [ ] 实现响应解析
- [ ] 置信度评估

### Phase 3: 参考链接处理（1天）
- [ ] 实现ReferenceLinkProcessor
- [ ] 网页内容抓取
- [ ] OpenAPI解析

### Phase 4: 优化与测试（1天）
- [ ] Prompt优化
- [ ] 单元测试
- [ ] 端到端测试

---

## 9. Task Prompt（供P8执行）

```markdown
## Task: 实现用例优化器模块

### WHY
用例优化器是用户与系统的"翻译层"，将自然语言输入转化为结构化测试用例。P10规格要求"零交互"，必须在输入阶段就完成结构化。

### WHAT
实现用例优化器模块，包括：
1. 数据模型：UseCaseOptimizerInput, OptimizedTestCase, OptimizedStep, ParameterValue, ConfidenceScore
2. 核心接口：UseCaseOptimizer
3. Prompt模板引擎
4. LLM服务集成
5. 参考链接处理器

### WHERE
- 数据模型：`src/main/java/com/agent/optimizer/model/`
- 核心接口：`src/main/java/com/agent/optimizer/service/`
- Prompt引擎：`src/main/java/com/agent/optimizer/prompt/`
- 参考链接处理：`src/main/java/com/agent/optimizer/reference/`
- 异常定义：`src/main/java/com/agent/optimizer/exception/`

### HOW MUCH
- 数据模型类：5个
- Prompt模板：3个场景化模板
- LLM调用：支持重试和降级
- 参考链接：支持网页+OpenAPI

### DONE（验收标准）
1. 跑通单元测试：`mvn test -Dtest=UseCaseOptimizer*Test`
2. LLM调用成功率 > 95%
3. 置信度评估与人工评估一致率 > 80%
4. 参考链接处理成功率 > 90%
5. 端到端测试通过

### DON'T
- 不在优化器中包含测试代码生成逻辑
- 不直接返回ApiMetadata
- 不在降级策略中丢失用户原始输入信息
```

---

## 10. 配置示例

```yaml
use-case-optimizer:
  llm:
    provider: custom
    api-url: ${LLM_API_URL}
    api-key: ${LLM_API_KEY}
    model: qwen-coder-plus
    temperature: 0.3
    max-tokens: 2048
    timeout: 30000

  templates:
    default: "classpath:prompts/use_case_optimization.md"
    crud: "classpath:prompts/use_case_optimization_crud.md"
    business-flow: "classpath:prompts/use_case_optimization_flow.md"

  confidence:
    acceptable-threshold: 0.7
    low-confidence-action: WARN

  reference:
    timeout: 10000
    max-links: 5
```
