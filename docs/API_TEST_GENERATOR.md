# API测试代码生成器模块设计文档

## 1. 模块概述

### 1.1 职责
API测试代码生成器（ApiTestGenerator）负责根据优化后的用例（OptimizedTestCase）和匹配的测试场景（TestScenario），生成可执行的API测试代码。

### 1.2 输入/输出

**输入**：
- OptimizedTestCase：用例优化器输出的结构化用例
- TestScenario：从知识库检索到的匹配场景
- List<ApiMetadata>：场景中涉及的API元数据

**输出**：
- GeneratedTestCode：包含测试类名、方法名、代码内容、断言

---

## 2. 数据模型设计

### 2.1 输入/输出结构

#### ApiTestGeneratorInput
```java
public class ApiTestGeneratorInput {
    private OptimizedTestCase optimizedCase;
    private TestScenario scenario;
    private List<ApiMetadata> apiMetadataList;
    private GeneratorOptions options;
}
```

#### GeneratorOptions
```java
public class GeneratorOptions {
    private String testFramework;                // JUNIT5 / TESTNG
    private String mockFramework;                // MOCKITO / EASY_MOCK
    private boolean generateAssertions;
    private boolean generateSetup;
    private String namingPattern;
    private Map<String, String> customTemplates;

    public static GeneratorOptions defaults() {
        return GeneratorOptions.builder()
            .testFramework("JUNIT5")
            .mockFramework("MOCKITO")
            .generateAssertions(true)
            .generateSetup(true)
            .namingPattern("test_{scenario}_{step}")
            .build();
    }
}
```

#### GeneratedTestCode
```java
public class GeneratedTestCode {
    private String testClassName;
    private String testMethodName;
    private String fullCode;
    private List<GeneratedTestStep> stepCodes;
    private List<GeneratedAssertion> assertions;
    private Map<String, Object> metadata;
}
```

#### GeneratedTestStep
```java
public class GeneratedTestStep {
    private int stepOrder;
    private String httpMethod;
    private String endpoint;
    private String requestBody;
    private String codeSnippet;
    private List<GeneratedAssertion> assertions;
}
```

#### GeneratedAssertion
```java
public class GeneratedAssertion {
    private String assertionType;
    private String target;
    private String expectedValue;
    private String actualValuePath;
    private String codeSnippet;
}
```

---

## 3. 核心接口设计

### 3.1 ApiTestGenerator接口

```java
public interface ApiTestGenerator {

    GeneratedTestCode generate(ApiTestGeneratorInput input);

    List<GeneratedTestCode> generateBatch(List<ApiTestGeneratorInput> inputs);

    GeneratedTestStep generateStep(
        OptimizedTestCase.OptimizedStep step,
        ApiMetadata api
    );

    ValidationResult validateSyntax(GeneratedTestCode code);
}
```

### 3.2 异常定义

```java
public class TestGenerationException extends RuntimeException {

    public enum ErrorCode {
        INVALID_INPUT,
        MISSING_API_METADATA,
        TEMPLATE_ERROR,
        LLM_GENERATION_FAILED,
        SYNTAX_VALIDATION_FAILED
    }

    @Data
    public static class GenerationIssue {
        private IssueSeverity severity;
        private String message;
        private String location;

        public enum IssueSeverity {
            ERROR, WARNING, INFO
        }
    }
}
```

---

## 4. 代码生成策略

### 4.1 模板引擎设计

使用FreeMarker模板引擎生成测试代码：

```java
public class TestCodeTemplateEngine {

    public String render(String templateName, Map<String, Object> context) {
        Template template = templateLoader.getTemplate(templateName);
        return template.render(context);
    }

    public Map<String, Object> buildContext(
        GeneratedTestStep step,
        ApiMetadata api,
        OptimizedTestCase.OptimizedStep originalStep
    ) {
        Map<String, Object> context = new HashMap<>(defaultContext);
        context.put("stepOrder", step.getStepOrder());
        context.put("httpMethod", api.getHttpMethod());
        context.put("endpoint", api.getEndpoint());
        context.put("requestBody", buildRequestBody(step, api));
        context.put("assertions", step.getAssertions());
        context.put("description", originalStep.getExpectedOutcome());
        return context;
    }
}
```

### 4.2 测试代码模板

#### 测试类模板
```java
// templates/ApiTestClass.java.ftl
package ${packageName};

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ${testClassName} {

    @Autowired
    private TestRestTemplate restTemplate;

    private static String authToken;

    <#list stepCodes as step>
    @Test
    @Order(${step.stepOrder})
    void ${step.methodName}() {
        // ${step.description}
        ${step.code}
    }
    </#list>
}
```

#### 单步测试模板
```java
// templates/ApiTestStep.java.ftl
HttpEntity<String> request${stepOrder} = new HttpEntity<>(
    <#if requestBody??>"${requestBody?jackson}"<#else>null</#if>,
    new HttpHeaders()
);

ResponseEntity<String> response${stepOrder} = restTemplate.exchange(
    "${endpoint}",
    HttpMethod.${httpMethod},
    request${stepOrder},
    String.class
);

assertEquals(HttpStatus.OK, response${stepOrder}.getStatusCode());

<#list assertions as assertion>
assert${assertion.method}(
    ${assertion.expected},
    ${assertion.actual}
);
</#list>
```

### 4.3 断言生成策略

```java
public class AssertionGenerator {

    public String generate(GeneratedAssertion assertion) {
        return switch (assertion.getAssertionType()) {
            case "ASSERT_EQUAL" ->
                String.format("assertEquals(\"%s\", jsonPath(\"%s\"))",
                    assertion.getExpectedValue(),
                    assertion.getActualValuePath());

            case "ASSERT_STATUS" ->
                String.format("assertEquals(HttpStatus.%s, response.getStatusCode())",
                    assertion.getExpectedValue());

            case "ASSERT_CONTAINS" ->
                String.format("assertTrue(response.getBody().contains(\"%s\"))",
                    assertion.getExpectedValue());

            case "ASSERT_NOT_NULL" ->
                String.format("assertNotNull(jsonPath(\"%s\"))",
                    assertion.getActualValuePath());

            default -> "// Unknown assertion type: " + assertion.getAssertionType();
        };
    }
}
```

---

## 5. 生成流程

### 5.1 主生成流程

```java
public class ApiTestGeneratorImpl implements ApiTestGenerator {

    @Override
    public GeneratedTestCode generate(ApiTestGeneratorInput input) {
        // 1. 输入验证
        validateInput(input);

        // 2. 构建生成上下文
        GenerationContext context = buildContext(input);

        // 3. 生成各步骤代码
        List<GeneratedTestStep> stepCodes = generateStepCodes(context);

        // 4. 生成断言
        List<GeneratedAssertion> assertions = generateAssertions(context);

        // 5. 组装完整代码
        GeneratedTestCode result = assembleCode(context, stepCodes, assertions);

        // 6. 语法验证
        ValidationResult validation = codeValidator.validate(result.getFullCode());
        if (!validation.isValid()) {
            throw new TestGenerationException(
                TestGenerationException.ErrorCode.SYNTAX_VALIDATION_FAILED,
                validation.getIssues()
            );
        }

        return result;
    }
}
```

### 5.2 LLM增强生成

```java
public class LLMCodeEnhancer {

    public String enhance(String generatedCode, GenerationContext context) {
        String prompt = String.format("""
            你是一个Java测试代码专家。请优化以下生成的测试代码：

            原始需求：%s

            已生成的代码：
            ```java
            %s
            ```

            请优化：
            1. 确保HTTP请求参数正确
            2. 添加必要的异常处理
            3. 改进断言使其更准确
            4. 保持代码风格一致

            只返回优化后的Java代码，不要解释。
            """,
            context.getOptimizedCase().getScenarioDescription(),
            code
        );

        return postProcess(llmService.generate(prompt));
    }
}
```

---

## 6. 与其他模块的交互

### 6.1 数据流图

```
┌─────────────────────────────────────────────────────────────────┐
                  API测试代码生成器数据流
├─────────────────────────────────────────────────────────────────┤
  ┌──────────────────────────────────────┐
  │         输入数据                       │
  │  - OptimizedTestCase                 │
  │  - TestScenario                      │
  │  - List<ApiMetadata>                  │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │         输入验证 InputValidation       │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │         上下文构建 ContextBuilding    │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │         代码生成 CodeGeneration       │
  │  - 模板渲染                           │
  │  - 断言生成                           │
  │  - LLM增强（可选）                    │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │         语法验证 SyntaxValidation     │
  └──────────────────────────────────────┘
                      │
                      ↓
  ┌──────────────────────────────────────┐
  │       GeneratedTestCode               │
  │  - testClassName                     │
  │  - fullCode                           │
  │  - assertions                         │
  └──────────────────────────────────────┘
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. 错误处理与回退

### 7.1 错误处理策略

| 错误类型 | 处理策略 | 是否可继续 |
|----------|----------|-----------|
| 输入为空 | 抛异常 | 否 |
| API元数据缺失 | 尝试从场景构建 | 取决于缺失数量 |
| 模板渲染失败 | 使用简化模板 | 是 |
| LLM生成失败 | 使用规则生成 | 是 |
| 语法验证失败 | 尝试修复 | 最多3次 |

### 7.2 简化回退模板

```java
// templates/SimpleApiTest.java.ftl
package com.agent.generated.tests;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;

public class ${testClassName} {

    @Test
    void ${testMethodName}() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "${baseUrl}${endpoint}",
            String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

---

## 8. 实现步骤

### Phase 1: 核心接口（1天）
- [ ] 定义数据模型
- [ ] 定义ApiTestGenerator接口
- [ ] 实现模板引擎

### Phase 2: 代码生成（2天）
- [ ] 实现模板渲染
- [ ] 实现断言生成器
- [ ] 实现HTTP调用代码生成

### Phase 3: LLM增强（1天）
- [ ] 集成LLM服务
- [ ] 实现代码增强逻辑

### Phase 4: 验证与优化（1天）
- [ ] 实现语法验证器
- [ ] 端到端测试

---

## 9. Task Prompt（供P8执行）

```markdown
## Task: 实现API测试代码生成器模块

### WHY
ApiTestGenerator是生成可执行测试代码的核心模块。它接收优化后的用例和知识库中的场景，生成符合项目规范的API测试代码。P10规格要求"零交互"，生成的代码必须可以直接运行。

### WHAT
实现API测试代码生成器模块，包括：
1. 数据模型：ApiTestGeneratorInput, GeneratedTestCode, GeneratedTestStep, GeneratedAssertion
2. 核心接口：ApiTestGenerator
3. 模板引擎：TestCodeTemplateEngine
4. 断言生成器：AssertionGenerator
5. 语法验证器：CodeValidator
6. LLM增强：LLMCodeEnhancer

### WHERE
- 数据模型：`src/main/java/com/agent/generator/model/`
- 核心接口：`src/main/java/com/agent/generator/service/`
- 模板引擎：`src/main/java/com/agent/generator/template/`
- 断言生成：`src/main/java/com/agent/generator/assertion/`
- 语法验证：`src/main/java/com/agent/generator/validation/`
- 异常定义：`src/main/java/com/agent/generator/exception/`

### HOW MUCH
- 数据模型类：5个
- 模板文件：3个
- 断言类型：5种
- 语法验证：Java语法+编译测试

### DONE（验收标准）
1. 跑通单元测试：`mvn test -Dtest=ApiTestGenerator*Test`
2. 生成的代码能通过Java编译
3. 端到端测试：输入用例 → 输出可运行代码
4. 模板渲染成功率 > 95%

### DON'T
- 不包含JUnit测试执行逻辑
- 不生成非Java语言的测试代码
- 不在生成器中包含性能测试逻辑
```

---

## 10. 配置示例

```yaml
api-test-generator:
  options:
    test-framework: JUNIT5
    mock-framework: MOCKITO
    generate-assertions: true
    generate-setup: true

  templates:
    class-template: classpath:templates/ApiTestClass.java.ftl
    step-template: classpath:templates/ApiTestStep.java.ftl
    fallback-template: classpath:templates/SimpleApiTest.java.ftl

  llm-enhancement:
    enabled: true
    trigger-threshold: 0.5
    max-retries: 2

  validation:
    enabled: true
    compile-test: true
    timeout: 5000

  output:
    package-name: com.agent.generated.tests
    base-path: ./src/test/java
```
