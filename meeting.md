# Meeting Record

## Usage
- 对架构评审、代码审查、缺陷分析、技术决策类任务，统一记录到本文件。
- 每次新增记录时保留历史内容，不覆盖旧结论。
- 建议把最新记录放在文件最上方，便于事后快速查看。
- 记录至少包含：时间、主题、范围、统一结论、问题分级、行动项、关键证据。

## 2026-03-20 16:00:13 +0800

### 主题
知识库基础设施代码审查、项目架构设计审视、统一问题清单与后续治理决议

### 参与角色
- P10 主线程：统一评审、交叉验证、收敛结论
- P8 代码审查：知识库基础设施 bug 与缺陷审查
- P9 架构审查：项目架构、分层与演进路径审视

### 评审范围
- 知识库 v1：`src/main/java/com/agent/service/*`、`storage/*`、`processor/*`、`crawler/*`、`parser/*`
- 知识库 v2：`src/main/java/com/agent/knowledge/v2/**`
- Spring 接入层：`src/main/java/com/agent/config/*`、`controller/*`、`AscendAgentApplication.java`
- 设计文档：`docs/ARCHITECTURE.md`、`docs/DESIGN.md`、`docs/KNOWLEDGE_BASE.md`、`docs/KNOWLEDGE_BASE_V2.md`
- 构建基线：`pom.xml`

### 统一结论
当前问题不是单点 bug，而是构建基线、核心契约、检索闭环、架构边界同时失配。继续堆功能会放大返工成本，必须先做基线对齐和核心链路止血。

### 核心问题

#### P0
- 构建基线冲突：`pom.xml` 同时使用 Java 8 编译目标和 Spring Boot 3.2.3，当前基线不可稳定交付。
  - 证据：`pom.xml`
- YAML 场景加载器存在确定性卡死风险。
  - 证据：`src/main/java/com/agent/knowledge/v2/builder/YamlScenarioLoader.java`
  - 现象：`steps:`、`validations:` 等 block 节点会导致重复处理同一行。
- `apiId` 不稳定，导致重复索引、脏数据、旧结果残留。
  - 证据：`src/main/java/com/agent/parser/JavaCodeParser.java`
  - 现象：每次解析生成新 UUID，破坏增量更新和场景关联。
- v2 检索默认不可用。
  - 证据：`src/main/java/com/agent/knowledge/v2/service/TestScenarioServiceImpl.java`
  - 现象：默认构造路径把 `null` rerank 服务传入检索器，运行时可能 NPE。
- v2 检索结果不是已索引场景实体，更新/删除也不会同步向量索引。
  - 证据：`src/main/java/com/agent/knowledge/v2/retriever/TestScenarioRetriever.java`

#### P1
- `MetadataStore` 落库字段不完整，检索回来的 `ApiMetadata` 被截断。
  - 证据：`src/main/java/com/agent/storage/MetadataStore.java`
- `OPENAPI_FILE` 在文档和枚举中声明支持，但实现未处理。
  - 证据：`src/main/java/com/agent/service/KnowledgeBaseServiceImpl.java`、`docs/KNOWLEDGE_BASE.md`
- 索引构建不是原子流程，metadata、vector、cache 之间可能不一致。
  - 证据：`src/main/java/com/agent/service/KnowledgeBaseServiceImpl.java`、`src/main/java/com/agent/processor/DocumentProcessor.java`

#### P2
- 项目形态不清晰：核心库设计与 Spring Boot 服务形态混在同一产物中。
  - 证据：`src/main/java/com/agent/config/AppConfig.java`、`src/main/java/com/agent/controller/KnowledgeBaseController.java`
- 文档与实现长期漂移，存在“文档承诺大于代码能力”的问题。
  - 证据：`docs/ARCHITECTURE.md`、`docs/DESIGN.md`、`docs/KNOWLEDGE_BASE.md`、`docs/KNOWLEDGE_BASE_V2.md`

### 架构分析结论
- 文档目标、当前代码、当前构建基线三者不一致。
- `docs/ARCHITECTURE.md` 走零交互全链路方向，但 `docs/DESIGN.md` 仍描述用户确认候选 API 的交互式流程。
- 当前代码主要还是知识库原型与 v2 骨架，尚未形成稳定的端到端闭环。
- Spring Boot 接入层已经出现，但没有带来完整的配置绑定、运行时治理和可验证的依赖装配。
- 若继续不区分“核心库”和“运行外壳”，后续扩展会持续放大耦合。

### 代码审查结论
- `YamlScenarioLoader` 不应继续依赖手写递归解析。
- `apiId` 必须改为稳定可复现的业务键。
- `indexJavaProject()`、`updateIndex()`、`updateScenario()`、`deleteScenario()` 需要统一为幂等的索引生命周期。
- 检索结果必须能回源到真实场景对象，而不是返回丢字段的临时对象。
- `MetadataStore` 需要补齐 round-trip 能力，否则后续测试代码生成会基于残缺元数据。

### 决策
- 暂停继续堆 v2 新能力，先做基线与闭环修复。
- 先处理 P0：构建基线、YAML 解析、稳定主键、检索可用性。
- 文档要么收缩承诺，要么补齐实现，不再允许长期占位式漂移。

### 行动项
- P10：拍板项目交付形态，是“核心库 + Boot 外壳”还是“单体 Boot 服务”。
- P8：优先修复 `YamlScenarioLoader`、稳定 `apiId`、索引更新生命周期，并补回归测试。
- P9：收敛文档，明确 `Implemented / Stub / Draft` 状态，统一架构叙事。

### 测试与验证备注
- 本轮结论主要基于源码静态审查与交叉验证。
- 当前工作区为 dirty 状态，且构建基线存在冲突，`mvn` 结果不适合作为唯一判断依据。
- `YamlScenarioLoader` 相关测试存在卡死风险，说明当前测试体系对“挂起型缺陷”覆盖不足。

### 测试执行结果
- 执行时间：2026-03-20
- 执行角色：P8 测试执行
- 已执行命令：`timeout 900 mvn -DskipTests package`
- 本地补充探测：`timeout 25 mvn -q -Dtest=com.agent.knowledge.v2.builder.YamlScenarioLoaderTest test`
- 结果：均失败于主代码编译阶段，测试未真正开始执行
- 当前编译阻塞：
  - `src/main/java/com/agent/config/AppConfig.java`：`EmbeddingModel` 是抽象类，不能直接实例化
  - `src/main/java/com/agent/parser/HuaweiCloudApiParser.java`：`ApiMetadataBuilder` 符号不存在
  - `src/main/java/com/agent/parser/HuaweiCloudApiParser.java`：`Parameter(String,String,String)` 构造函数不匹配
  - `src/main/java/com/agent/controller/KnowledgeBaseController.java`：`DocumentSource.builder()` 不存在
- 影响：包括 `YamlScenarioLoaderTest` 在内的知识库 / v2 测试当前都无法通过 Maven 正式执行
- 结论：测试执行已证明“当前仓库先修编译基线，再谈测试结果”是正确优先级
- 基线修复后的建议验证顺序：
  - `timeout 120 mvn -Dtest=YamlScenarioLoaderTest test`
  - `timeout 300 mvn -Dtest=ScenarioBuilderTest,TestScenarioServiceImplTest,ScenarioMetadataTest,ScenarioStepTest,ValidationTest,TestScenarioTest test`

### 后续修复与复核结果
- 执行时间：2026-03-20
- 执行角色：P8 修复 / P10 复核
- 已处理的编译阻塞：
  - `src/main/java/com/agent/config/AppConfig.java`
  - `src/main/java/com/agent/controller/KnowledgeBaseController.java`
  - `src/main/java/com/agent/parser/HuaweiCloudApiParser.java`
- 复核命令与结果：
  - `timeout 120 mvn -q -DskipTests compile`
    - 结果：通过
  - `timeout 120 mvn -q -Dtest=com.agent.knowledge.v2.builder.YamlScenarioLoaderTest test`
    - 结果：超时，退出码 `124`
  - `timeout 120 mvn -q -Dtest=com.agent.knowledge.v2.builder.ScenarioBuilderTest,com.agent.knowledge.v2.model.ScenarioMetadataTest,com.agent.knowledge.v2.model.ScenarioStepTest,com.agent.knowledge.v2.model.TestScenarioTest,com.agent.knowledge.v2.model.ValidationTest,com.agent.knowledge.v2.service.TestScenarioServiceImplTest test`
    - 结果：通过
- 最新结论：
  - 编译基线已从“无法进入测试阶段”提升到“可以编译并运行大部分 v2 相关单测”。
  - `YamlScenarioLoaderTest` 的超时现象被再次验证，当前仍是最明确的挂起/死循环风险点。
  - 问题边界已收敛：当前优先级最高的剩余执行项是修复 `YamlScenarioLoader`，而不是继续排查构建阻塞。

### 最终放行结论
- 执行时间：2026-03-20
- 执行角色：P8 owner 验证 / P10 放行
- P8 最终执行命令：
  - `timeout 120 mvn -q -DskipTests compile`
  - `timeout 120 mvn -q -Dtest=com.agent.knowledge.v2.builder.YamlScenarioLoaderTest test`
  - `timeout 120 mvn -q -Dtest=com.agent.knowledge.v2.builder.ScenarioBuilderTest,com.agent.knowledge.v2.builder.YamlScenarioLoaderTest,com.agent.knowledge.v2.model.ScenarioMetadataTest,com.agent.knowledge.v2.model.ScenarioStepTest,com.agent.knowledge.v2.model.TestScenarioTest,com.agent.knowledge.v2.model.ValidationTest,com.agent.knowledge.v2.service.TestScenarioServiceImplTest test`
- 执行结果：
  - 三条命令全部退出码 `0`
  - `YamlScenarioLoader` 挂起问题已在当前指定验证范围内解除
- P10 放行意见：
  - 可以进入下一阶段
  - 本次放行仅覆盖 `compile + knowledge/v2` 定向测试，不等同于全量回归
  - `LoaderOptions` 防御、`pom.xml` 并行改动影响面、知识库其他高优问题仍需在后续阶段继续治理

### 技术决策追加
- 执行时间：2026-03-20
- 决策：构建基线从 Java 8 升级到 Java 17
- 原因：
  - 当前服务启动失败的直接根因是 Java 8 运行时无法加载较高 class version 的依赖
  - 仓库已经引入 Spring Boot、SLF4J 2.x、Logback 1.4.x 等较新的运行栈，继续维持 Java 8 治理成本更高
- 实施方式：
  - `pom.xml` 统一将 `maven.compiler.source/target` 提升到 `17`
  - 本机没有 JDK 17，但存在 JDK 21，因此验证时使用 JDK 21 作为 17+ 运行时
- 影响说明：
  - 后续所有编译、打包、启动验证都应在 JDK 17+ 环境下进行
  - 这项决策只解决“8 不兼容较新运行栈”的问题，不自动代表全量功能已回归

### 启动阻塞补充
- 执行时间：2026-03-20
- 在 JDK 17+ 运行时下，`java -jar target/ascend-agent-1.0.0.jar` 首次验证仍失败
- 根因：`pom.xml` 中显式声明的 `slf4j-api 2.0.9` 与 `logback-classic 1.4.14` 和 Spring Boot 2.7.x 的默认日志栈不兼容
- 处理：移除显式 `slf4j/logback` 依赖，交还给 Spring Boot 依赖管理统一约束

### 启动路径修正
- 执行时间：2026-03-20
- 在日志栈问题修复后，服务启动进入 Spring 上下文阶段，但再次阻塞在 `MetadataStore` 初始化
- 根因：默认 SQLite 路径写到用户目录 `/root/.ascend_agent/api_metadata.db`，在当前运行环境下不可写
- 处理：`AppConfig` 改为支持 `-Dascend.agent.data-dir=...` 配置数据目录，默认落在项目内 `data/`

### 服务启动验证
- 执行时间：2026-03-20
- 执行角色：P8 后台启动 / P10 验收
- 启动命令：
  - `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH; setsid nohup java -jar /root/ascend_agent/target/ascend-agent-1.0.0.jar >/tmp/ascend-agent.log 2>&1 < /dev/null & echo $! >/tmp/ascend-agent.pid`
- 启动结果：
  - PID：`633561`
  - 8080 端口：已监听
  - 健康检查：`/actuator/health` 返回 `UP`
- 日志摘要：
  - Spring Boot `2.7.18`
  - Java `21.0.10`
  - Tomcat 绑定 `8080`
  - 应用启动完成，耗时约 `3.404s`

### 后续约定
- 以后每次做架构评审、代码审查、技术决策或重大问题分析，主Agent必须更新本文件。
- 更新时必须写入新的时间戳，并记录统一结论、问题分级、行动项和关键证据文件。

## Template

```md
## YYYY-MM-DD HH:MM:SS +0800

### 主题
一句话描述本次会议/评审主题

### 参与角色
- P10 主线程：
- P8：
- P9：

### 评审范围
- 文件/模块/文档范围

### 统一结论
- 最终统一结论

### 核心问题

#### P0
- 

#### P1
- 

#### P2
- 

### 决策
- 

### 行动项
- 负责人：
- 负责人：

### 关键证据
- 文件路径
- 文件路径
```
