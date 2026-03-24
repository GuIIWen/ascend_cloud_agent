# Meeting Record

## Usage
- 对架构评审、代码审查、缺陷分析、技术决策类任务，统一记录到本文件。
- 每次新增记录时保留历史内容，不覆盖旧结论。
- 建议把最新记录放在文件最上方，便于事后快速查看。
- 记录至少包含：时间、主题、范围、统一结论、问题分级、行动项、关键证据。

## 2026-03-24 09:35:11 +0800

### 主题
Sprint-1 第五批交付验收：服务生命周期脚本、CI/package 门禁与 shutdown 风险缓解

### 参与角色
- P10 主线程：定义“剩余收口项”并完成验收

### 评审范围
- `scripts/start_service.sh`
- `scripts/stop_service.sh`
- `scripts/verify_baseline.sh`
- `.github/workflows/ci.yml`
- `src/main/resources/application.yml.template`
- `README.md`
- `docs/CONFIG_GUIDE.md`
- `meeting.md`

### 统一结论
- 服务生命周期的标准入口已补齐：启动使用 `scripts/start_service.sh`，停止使用 `scripts/stop_service.sh`，不再把手工 `kill` 作为标准停服务路径。
- 本地基线验证与 CI 门禁都已升级到 `compile + 定向测试 + package`，主分支不再只保证“能编译”，也要保证“能产出可运行 jar”。
- 启动脚本显式传入 `--logging.register-shutdown-hook=false`，作为 packaged jar 退出路径日志竞态的缓解措施。
- 主服务已在 `8080` 上以最新 wrapper 重启，`/actuator/health` 与 `/actuator/info` 均通过。

### 验收结果
- `bash -n scripts/start_service.sh`
  - 结果：通过
- `bash -n scripts/stop_service.sh`
  - 结果：通过
- `bash -n scripts/verify_baseline.sh`
  - 结果：通过
- `timeout 180 mvn -q -DskipTests compile`
  - 结果：通过
- `timeout 180 mvn -q -Dtest=AgentConfigBindingTest,AgentInfoContributorTest,AppConfigRuntimePathTest,KnowledgeBaseConfigBindingTest,AppConfigModelSelectionTest,AppConfigVectorStoreSelectionTest,HttpModelServiceTest,HuaweiCloudApiCrawlerServiceTest test`
  - 结果：通过
- `timeout 180 mvn -q -DskipTests package`
  - 结果：通过
- 隔离实例停服务验证
  - `env ASCEND_AGENT_HOME=/root/ascend_agent/.ascend_agent-test ASCEND_AGENT_PORT=18081 JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash scripts/start_service.sh`
  - `env ASCEND_AGENT_HOME=/root/ascend_agent/.ascend_agent-test bash scripts/stop_service.sh`
  - 结果：通过；日志中未再出现 `ThrowableProxy` / `SpringApplicationShutdownHook` 异常
- 主服务回切验证
  - `env ASCEND_AGENT_HOME=/root/ascend_agent/.ascend_agent bash scripts/stop_service.sh`
  - `env ASCEND_AGENT_HOME=/root/ascend_agent/.ascend_agent JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash scripts/start_service.sh`
  - `curl -sS -i -m 10 http://127.0.0.1:8080/actuator/health`
  - `curl -sS -i -m 10 http://127.0.0.1:8080/actuator/info`
  - 结果：通过；当前常驻进程 PID `2748596`

### 核心问题

#### P1
- shutdown 异常在当前基线下已经无法稳定复现，因此本轮更准确的处理是“标准化停服务路径 + 关闭日志 shutdown hook 竞态源”，而不是宣称已定位到唯一代码根因。

#### P2
- 当前剩余阻塞不在代码和运行态，而在远端写入链路：`git push` 仍有超时现象，需要继续治理 GitHub 写操作认证/传输链路。

### 决策
- 以后服务停机优先使用 `scripts/stop_service.sh`，不再把裸 `kill` 作为标准文档路径。
- CI 与本地 `verify_baseline.sh` 都以 `package` 作为最低发布门禁之一。

### 行动项
- P10：把本轮剩余收口项提交并推送远端。
- 后续执行层：继续排查 `git push` 写链路，直到主分支最新提交成功上远端。

### 关键证据
- `scripts/start_service.sh`
- `scripts/stop_service.sh`
- `scripts/verify_baseline.sh`
- `.github/workflows/ci.yml`
- `.ascend_agent/logs/service.log`

## 2026-03-23 17:33:00 +0800

### 主题
Sprint-1 第四批交付验收：Agent 对齐第一批落地（配置入口与运行态状态口径）

### 参与角色
- P10 主线程：定义“只对齐不收编”的第一批落地范围并验收
- P9-Architecture：评估 Agent 先做范围与停止线

### 评审范围
- `src/main/java/com/agent/config/AgentConfig.java`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/main/resources/application.yml.template`
- `scripts/start_service.sh`
- `README.md`
- `docs/CONFIG_GUIDE.md`
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `meeting.md`

### 统一结论
- Agent 对齐第一批已达到放行条件。
- 当前主 Agent 仍未实现，但“当前阶段是什么”不再只靠文档描述，而是由配置合同 `agent.*` 和 `/actuator/info` 共同暴露。
- 当前统一运行态事实为：`agent.stage=alignment`、`agent.enabled=false`、`agent.mode=knowledge-base-only`、`agent.entrypoint=knowledge-base-controller`。
- 本轮交付只做配置入口与状态口径收口，不引入新的主链路、调度层或编排层。

### 验收结果
- `bash -n scripts/start_service.sh`
  - 结果：通过
- `timeout 180 mvn -q -DskipTests compile`
  - 结果：通过
- `timeout 180 mvn -q -Dtest=AgentConfigBindingTest,AgentInfoContributorTest,AppConfigRuntimePathTest,KnowledgeBaseConfigBindingTest,AppConfigModelSelectionTest,AppConfigVectorStoreSelectionTest,HttpModelServiceTest,HuaweiCloudApiCrawlerServiceTest test`
  - 结果：通过
- `timeout 180 mvn -q -DskipTests package`
  - 结果：通过
- 标准端口运行验证
  - `env ASCEND_AGENT_HOME=/root/ascend_agent/.ascend_agent JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash scripts/start_service.sh`
  - 结果：通过，常驻进程 PID `2371693`
  - `curl -sS -i -m 10 http://127.0.0.1:8080/actuator/health`
  - 结果：`HTTP/1.1 200`，返回 `{"status":"UP",...}`
  - `curl -sS -i -m 10 http://127.0.0.1:8080/actuator/info`
  - 结果：返回 `agent.enabled=false`、`agent.stage=alignment`、`agent.mode=knowledge-base-only`

### 核心问题

#### P1
- 当前 Agent 运行态口径已经可观测，但这仍不代表零交互主 Agent 已上线；若后续文档和对外说明不继续引用 `/actuator/info`，仍可能回到“目标设计冒充当前实现”。

#### P2
- 在手工 kill 旧进程的退出路径上，日志曾出现 `NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy`；当前不阻塞启动与健康检查，但说明关闭路径的日志栈还有残余风险，需要后续单独治理。

### 决策
- Agent 下一阶段继续坚持“先对齐，后收编”，不因为已有 `agent.*` 配置就提前接入主链路。
- `/actuator/info` 从本条纪要开始成为 Agent 当前阶段的正式运行态事实来源之一。

### 行动项
- P10：提交并推送本轮 Agent 对齐第一批改动。
- 后续执行层：单独评估关闭路径日志异常，不与 Agent 主链路建设混在同一批次。

### 关键证据
- `src/main/java/com/agent/config/AgentConfig.java`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/test/java/com/agent/config/AgentConfigBindingTest.java`
- `src/test/java/com/agent/config/AgentInfoContributorTest.java`
- `.ascend_agent/logs/service.log`

## 2026-03-23 16:55:00 +0800

### 主题
Sprint-1 第三批决策：Java 基线统一升到 21，Agent 先对齐后收编

### 参与角色
- P10 主线程：拍板 Java 基线与 Agent 组织策略
- P9-Architecture：提供 Agent 先做范围与验收建议

### 评审范围
- `pom.xml`
- `README.md`
- `docs/CONFIG_GUIDE.md`
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/KNOWLEDGE_BASE.md`
- `meeting.md`

### 统一结论
- Java 基线从“双口径”收口为单一 `21`，不再保留“编译 17、运行 21”的对外表述。
- Agent 这条线当前不做“大一统收编”，先做边界对齐：统一运行时、配置入口、启动方式、目录合同和状态口径，确认没有偏差后再进入统一纳管。
- 本轮工作不扩主功能，不做验收脚本，不引入新的 Agent 主链路实现。

### 决策
- `pom.xml`、README、配置与设计文档全部切换到 Java 21 单一基线。
- Agent 当前阶段只做“对齐”，不做“收编”：
  - 先对齐：配置入口、运行目录、启动语义、依赖基线、状态口径
  - 暂不做：零交互主 Agent、新调度层、新流程编排

### 验收结果
- `timeout 180 mvn -q -DskipTests compile`
  - 结果：通过
- `timeout 180 mvn -q -Dtest=AppConfigRuntimePathTest,KnowledgeBaseConfigBindingTest,AppConfigModelSelectionTest,AppConfigVectorStoreSelectionTest,HttpModelServiceTest,HuaweiCloudApiCrawlerServiceTest test`
  - 结果：通过
- `timeout 180 mvn -q -DskipTests package`
  - 结果：通过
- 标准端口运行验证
  - `env ASCEND_AGENT_HOME=/root/ascend_agent/.ascend_agent JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash scripts/start_service.sh`
  - 结果：通过，常驻进程 PID `2359104`
  - `curl -sS -i -m 10 http://127.0.0.1:8080/actuator/health`
  - 结果：`HTTP/1.1 200`，返回 `{"status":"UP",...}`

### 核心问题

#### P1
- 继续保留 Java 17/21 双口径会让 `pom.xml`、README、CI、运行脚本出现长期漂移，后续依赖升级会反复返工。

#### P2
- Agent 若在当前阶段直接收编到主链路，会把尚未收口的运行/配置问题一并放大，导致架构边界和责任边界一起失真。

### 行动项
- 执行层：完成 Java 21 基线代码与文档收口，并通过 `compile + test + package` 验证。
- P10：在 Java 21 收口通过后，决定 Agent“对齐”阶段的具体验收列表。

### 关键证据
- `pom.xml`
- `README.md`
- `docs/CONFIG_GUIDE.md`
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/KNOWLEDGE_BASE.md`

## 2026-03-23 16:41:11 +0800

### 主题
Sprint-1 第二批交付验收：`ASCEND_AGENT_HOME` 目录合同、运行时路径优先级与服务常驻启动闭环

### 参与角色
- P10 主线程：定验收口径、并行调度 P9、完成最终放行判断
- P9-Runtime：审查运行根合同是否形成可执行闭环
- P9-Docs：审查 README、docs、meeting 口径是否一致

### 评审范围
- `src/main/java/com/agent/config/AppConfig.java`
- `src/test/java/com/agent/config/AppConfigRuntimePathTest.java`
- `scripts/install_chroma_0520.sh`
- `scripts/start_chroma_22333.sh`
- `scripts/start_service.sh`
- `README.md`
- `docs/CONFIG_GUIDE.md`
- `meeting.md`

### 统一结论
- 第二批“目录合同与运行根”已达到放行条件。
- 长期默认运行根统一为 `ASCEND_AGENT_HOME=./.ascend_agent/`；`tools/chroma-venv-0520`、`chroma`、`db`、`logs`、`pids` 均从该根派生。
- 应用本地数据库目录优先级已固化为：`ascend.agent.data-dir` > `ascend.agent.home` > `ASCEND_AGENT_HOME` > `./.ascend_agent/db`。
- `meeting.md` 中早期出现的 `/tmp/*`、`/root/.ascend_agent/*` 记录属于历史验证路径，不再代表当前默认目录策略；对外口径以 `README.md` 与 `docs/CONFIG_GUIDE.md` 为准。
- 本轮验收额外暴露出一个本地环境问题：被 `.gitignore` 忽略的 `src/main/resources/application.yml` 曾以 GBK 编码保存，导致旧打包产物启动时报 `YAMLException -> MalformedInputException`；该问题已在本地转为 UTF-8 后重打包验证，不属于仓库模板口径缺陷。

### 验收结果
- `bash -n scripts/install_chroma_0520.sh`
  - 结果：通过
- `bash -n scripts/start_chroma_22333.sh`
  - 结果：通过
- `bash -n scripts/start_service.sh`
  - 结果：通过
- `timeout 180 mvn -q -DskipTests compile`
  - 结果：通过
- `timeout 180 mvn -q -Dtest=AppConfigRuntimePathTest,KnowledgeBaseConfigBindingTest,AppConfigModelSelectionTest,AppConfigVectorStoreSelectionTest test`
  - 结果：通过
- 运行态验证
  - `env ASCEND_AGENT_HOME=/root/ascend_agent/.ascend_agent ASCEND_AGENT_PORT=18080 JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash scripts/start_service.sh`
  - 结果：通过，常驻进程 PID `2348345`
  - `curl -sS -i -m 10 http://127.0.0.1:18080/actuator/health`
  - 结果：`HTTP/1.1 200`，返回 `{"status":"UP",...}`
- 目录合同验证
  - `ls -la .ascend_agent .ascend_agent/db .ascend_agent/logs .ascend_agent/pids`
  - 结果：`db/logs/pids` 已从 `ASCEND_AGENT_HOME` 正确派生

### 核心问题

#### P1
- 目录合同虽然已闭环，但仍缺少“一键集成验收脚本”，当前运行态验收还依赖一组手工命令串联。

#### P2
- `meeting.md` 历史记录保留了早期 `/tmp` 证据，若不在新纪要中明确声明“已被目录合同取代”，外部读者容易误判默认目录策略。

### 决策
- 放行第二批交付，不回退到 `/tmp` 作为长期默认运行目录。
- 从本条纪要开始，目录合同的唯一事实来源定义为：`ASCEND_AGENT_HOME=./.ascend_agent/`，`/tmp` 仅允许作为显式覆盖或历史兼容路径。
- README 的服务启动主入口正式切换为 `scripts/start_service.sh`；手工 `java -jar` 只保留为故障排查路径，不再作为主文档入口。

### 行动项
- P10：提交本轮 `meeting.md`、README、CONFIG_GUIDE 收口改动并推送远端。
- 后续执行层：补一个运行根合同的一键验收脚本，把 Chroma 启动、服务启动、健康检查、目录校验固化为单条命令。

### 关键证据
- `src/main/java/com/agent/config/AppConfig.java`
- `src/test/java/com/agent/config/AppConfigRuntimePathTest.java`
- `scripts/start_service.sh`
- `README.md`
- `docs/CONFIG_GUIDE.md`
- `.ascend_agent/logs/service.log`

## 2026-03-23 14:37:12 +0800

### 主题
Sprint-1 第一批交付验收：README、基线 CI、验证脚本、配置与设计文档口径收口

### 参与角色
- P10 主线程：按文档与会议纪要口径验收并放行
- P8-A：README、CI、验证脚本交付
- P8-B：配置与设计文档口径收口

### 评审范围
- `README.md`
- `.github/workflows/ci.yml`
- `scripts/verify_baseline.sh`
- `docs/CONFIG_GUIDE.md`
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/KNOWLEDGE_BASE.md`

### 统一结论
- Sprint-1 第一批交付已达到放行条件。
- 仓库已补齐 README、最小 CI 和本地基线验证脚本，公开仓不再依赖纯口头说明。
- `CONFIG_GUIDE` 与三份设计文档已完成当前基线口径收口，`8000` 旧地址已从本轮验收范围移除。
- 当前放行结论基于“compile + 关键单测 + 文档口径一致”成立；CI 仍未覆盖 Chroma 集成态，这属于已知后续项，不阻塞本批放行。

### 验收结果
- `bash scripts/verify_baseline.sh`
  - 结果：通过
- `timeout 120 mvn -q -DskipTests compile`
  - 结果：通过
- `timeout 120 mvn -q -Dtest=KnowledgeBaseConfigBindingTest,AppConfigModelSelectionTest,AppConfigVectorStoreSelectionTest,HttpModelServiceTest,HuaweiCloudApiCrawlerServiceTest test`
  - 结果：通过
- 文档一致性检查
  - `rg -n "8000" docs/CONFIG_GUIDE.md docs/ARCHITECTURE.md docs/DESIGN.md docs/KNOWLEDGE_BASE.md README.md .github/workflows/ci.yml scripts/verify_baseline.sh`
  - 结果：无命中

### 核心问题

#### P1
- CI 当前只覆盖快速基线门禁，尚未包含 Chroma 集成态与“重启后检索一致性”自动化验证。

#### P2
- README 当前提供的是本地标准路径，不等同于完整部署手册；后续仍需补 README 与 docs 的职责边界说明。

### 决策
- 放行本批交付，进入 Sprint-1 下一阶段。
- 下一阶段优先处理“集成验收自动化”和“数据目录长期默认策略”，不回头重做本批已通过项。

### 行动项
- P10：把本轮交付结果提交并推送到远端主分支。
- 后续执行层：继续推进 Chroma 集成验收和持久化目录策略收口。

### 关键证据
- `README.md`
- `.github/workflows/ci.yml`
- `scripts/verify_baseline.sh`
- `docs/CONFIG_GUIDE.md`
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/KNOWLEDGE_BASE.md`

## 2026-03-23 14:22:49 +0800

### 主题
Sprint-1 战略启动：工程化基线固化、P9 分工与文档验收口径

### 参与角色
- P10 主线程：定战略、拍板基线合同、定义文档验收与放行口径
- P9：拆职责边界、交付节奏、风险点与依赖顺序

### 评审范围
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/KNOWLEDGE_BASE.md`
- `docs/CONFIG_GUIDE.md`
- `meeting.md`
- `scripts/install_chroma_0520.sh`
- `scripts/start_chroma_22333.sh`
- `src/main/resources/application.yml.template`
- `pom.xml`

### 统一结论
- Sprint-1 主线不是扩功能，而是把当前已跑通的闭环固化成“可重复、可验收、可公开协作”的工程基线。
- 当前对外可宣称的基线只包括：`Java 17 编译目标 + JDK 21 运行时 + Spring Boot 2.7.18 + Chroma 0.5.20 + 22333 + Huawei Cloud 目录页抓取/搜索/重启后检索闭环`。
- `ARCHITECTURE / DESIGN / KNOWLEDGE_BASE` 三份文档继续保留“目标设计”价值，但 Sprint-1 期间的放行判断以“文档中的当前状态说明 + meeting.md 决议 + 可验证证据”共同成立为准。

### 战略输入

#### 方向
- 把知识库原型从“单机可跑”收口为“团队可重复运行、对外可自证”的基线版本。

#### 成功标准
- 干净环境下存在唯一标准路径，能够完成：安装依赖、启动 Chroma、启动服务、健康检查、抓取目录页、搜索结果校验、服务重启后再次搜索。
- 配置入口、默认端口、版本矩阵、脚本、文档、会议纪要之间不存在冲突口径。
- 对外声明能力时，只使用 `Implemented 且可验证` 的表述，不再混淆目标设计和当前实现。

#### 基线合同（P10 拍板）
- 编译目标：`Java 17`
- 运行时：`JDK 21`
- Spring Boot 主版本：`2.7.18`，本 Sprint 冻结，不做大版本迁移
- Chroma 版本：`0.5.20`
- 向量库端口：`22333`
- 向量库类型：`chroma`
- Sprint-1 不引入新的模型链路、数据源类型和端到端主功能

#### 不做什么
- 不推进 `UseCaseOptimizer`、`ApiTestGenerator`、零交互 Agent 主链路的新实现。
- 不接新的文档源能力，如 `OPENAPI_FILE` 全量补齐、GraphQL、gRPC。
- 不升级 `langchain4j-chroma`、Chroma 主版本、Spring Boot 主版本。

### P9 编制
- `P9-A（工程化基线 Owner）`
- 负责：构建与运行基线、脚本标准、CI 门禁、最小验收链路、失败可诊断性。
- 交付边界：不定义业务功能，不扩数据源，只收口“怎么稳定 build / run / verify”。
- `P9-B（配置与持久化治理 Owner）`
- 负责：配置入口收口、运行时/模板一致性、向量库与元数据库目录策略、重启一致性验证、文档状态治理。
- 交付边界：不做新功能扩张，只治理“配置生效、数据不丢、文档不漂移”。
- `P9 间接口`
- P9-A 输出标准化验收命令和门禁层级；P9-B 提供配置与持久化场景样本及放行证据；两者统一回写到文档与 `meeting.md`。

### Sprint-1 顺序
1. 先冻结基线合同，禁止边做边改版本口径。
2. 先做配置入口收口，再做运行脚本与启动链路固化。
3. 在运行链路稳定后，再做“索引 -> 搜索 -> 重启 -> 再搜索”的持久化一致性验收。
4. 最后落 CI/验收门禁和文档收口，避免 CI 与文档建立在漂移口径之上。

### 文档验收口径

#### `docs/ARCHITECTURE.md`
- 必须持续明确“目标架构”和“当前实现状态”是两层口径。
- `向量数据库 + 元数据库` 的双存储分层不能与当前实现结论冲突。
- 任何未实现模块必须保持 `Draft/Partial/Stub` 标识，禁止冒充已完成。

#### `docs/DESIGN.md`
- 必须明确“交互式候选 API 确认”仍是目标流程，不等价于当前已实现。
- 配置管理描述必须和当前可用入口一致，不能再保留与实际运行无关的旧口径。
- 不能把“写入测试文件”等未落地能力写成当前可验收项。

#### `docs/KNOWLEDGE_BASE.md`
- 当前实现状态表必须继续反映真实实现边界。
- 存储层描述必须与当前基线一致：`Chroma 0.5.20 + SQLite`，且重启一致性由实测证据支撑。
- 对 `OPENAPI_FILE` 等未补齐能力，必须继续标注 `Stub`，不能借文档放大承诺。

#### `docs/CONFIG_GUIDE.md`
- 必须纠正过期示例，尤其是 `vector-store.url` 仍写 `8000` 的旧口径。
- 需要和 `application.yml.template`、脚本、环境变量入口保持一致。
- 不允许出现与当前版本矩阵冲突的安装/启动说明。

### 放行口径
- 合并门禁最低要求：`compile + 关键单测`。
- Sprint-1 发布放行要求：`Chroma 启动 -> 服务启动 -> /actuator/health -> Huawei Cloud 目录抓取 -> ListWorkflows 搜索 -> 服务重启后再次搜索` 全链路通过。
- 文档放行要求：上述四份文档与 `meeting.md` 在版本、端口、状态标记、能力边界上无冲突。

### 核心问题

#### P1
- 当前仓库缺少 README 和 `.github/workflows`，导致公开仓状态下的“如何跑起来、如何验收”仍主要依赖会议纪要和人工经验。
- `docs/CONFIG_GUIDE.md` 仍保留 `http://localhost:8000` 等过期口径，和当前 `22333` 基线冲突。

#### P2
- 当前有效运行态依然依赖 `/tmp` 目录，适合当前闭环验证，但还不是长期默认策略。
- 编译目标为 `17`、运行时使用 `21` 的事实已经稳定，但文档口径尚未完全收口。

### 决策
- Sprint-1 先做基线固化，不再并行推进新功能。
- P10 按文档与会议纪要联合放行，不接受“代码看起来像支持”但文档和验证证据不闭环的交付。
- 所有后续任务必须围绕本条会议纪要的基线合同和验收口径展开。

### 行动项
- P9-A：产出工程化基线任务树，覆盖脚本、CI、运行探测、回归口径。
- P9-B：产出配置与持久化治理任务树，覆盖配置入口、目录策略、文档状态收口。
- P10：以本条会议纪要为 Sprint-1 放行标准，后续按文档和证据验收。

### 关键证据
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/KNOWLEDGE_BASE.md`
- `docs/CONFIG_GUIDE.md`
- `src/main/resources/application.yml.template`
- `scripts/install_chroma_0520.sh`
- `scripts/start_chroma_22333.sh`
- `meeting.md`

## 2026-03-23 11:40:47 +0800

### 主题
本地 Chroma 0.5.20 安装脚本、启动脚本固化与仓库交付

### 参与角色
- P8：脚本落地、环境验证、提交
- 主线程：验收与后续 push

### 评审范围
- `scripts/install_chroma_0520.sh`
- `scripts/start_chroma_22333.sh`
- `meeting.md`

### 统一结论
- 本地 Chroma 不再依赖手工命令，已沉淀为仓库脚本。
- 安装脚本固定使用独立 venv，并显式锁定 `chromadb==0.5.20`。
- 启动脚本固定以 `127.0.0.1:22333` 启动本地 Chroma，并校验端口占用、输出 PID/日志/健康检查提示。
- 本次交付不改业务代码，不改用户本地密钥配置。

### 核心问题

#### P1
- 之前本地可用的 Chroma 是手工安装的 `chromadb 1.5.5`，`/api/v1/heartbeat` 已弃用，和项目当前 v1 接口预期不兼容。
- 仓库缺少可复用脚本，环境重建依赖临场命令，重复成本高且容易漂移。

### 决策
- 统一使用独立虚拟环境 `/tmp/chroma-venv-0520` 安装 `chromadb==0.5.20`。
- 统一使用本地持久化目录 `/tmp/chroma-data-22333`。
- 启动脚本默认写日志到 `/tmp/chroma-22333.log`，写 PID 到 `/tmp/chroma-22333.pid`。

### 行动项
- P8：提交安装脚本、启动脚本和本次 meeting 记录。
- 主线程：后续根据需要把项目默认 Chroma 端口从 `8000` 调整到 `22333` 并做统一验收。

### 关键证据
- `scripts/install_chroma_0520.sh`
- `scripts/start_chroma_22333.sh`
- `/tmp/chroma-venv-0520`
- `/tmp/chroma-data-22333`
- `/tmp/chroma-22333.log`
- `/tmp/chroma-22333.pid`
- 运行时验证：
  - `chromadb` 版本：`0.5.20`
  - `GET http://127.0.0.1:22333/api/v1/heartbeat` 返回 `200 OK`

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

## 2026-03-20 17:11:20 +0800

### 主题
模型配置模板核对与运行时生效路径确认

### 参与角色
- P10 主线程：结论确认与记录
- P8：未介入修改
- P9：未介入修改

### 评审范围
- `src/main/resources/application.yml.template`
- `src/main/resources/application.yml`
- `src/main/java/com/agent/config/AppConfig.java`
- `.gitignore`

### 统一结论
- `application.yml.template` 是仓库当前版本原样文件，本轮未修改。
- `application.yml` 是本地忽略文件，用于运行时本地配置，不参与 git 跟踪。
- 当前代码并未把 `application.yml.template` 中的模型配置真正绑定到运行时 Bean，直接修改 template 不会让服务实际切换 embedding 模型。

### 核心问题

#### P1
- `AppConfig` 中 `knowledgeBaseConfig()` 直接 `new KnowledgeBaseConfig()` 返回默认值，未从 Spring 配置加载。
- `AppConfig` 中 `embeddingModel()` 固定返回 `AllMiniLmL6V2EmbeddingModel`，未读取 template 中的 `knowledge-base.embedding.*`。

#### P2
- `application.yml.template` 的配置样例与当前运行路径存在脱节，容易误导使用者以为改模板即可生效。

### 决策
- 短期结论：将 template 视为样例文件，不视为当前运行态真实配置入口。
- 后续若要支持模型切换，应由 P8 将 YAML 配置绑定到 Spring Bean，并补最小回归验证后提交。

### 行动项
- 负责人：P10 向用户明确当前 template 状态与生效边界。
- 负责人：P8 后续如接到需求，修正配置绑定并单独 commit。

### 关键证据
- `src/main/resources/application.yml.template`
- `src/main/resources/application.yml`
- `src/main/java/com/agent/config/AppConfig.java`
- `.gitignore`

## 2026-03-20 17:46:59 +0800

### 主题
模型配置链路闭环修复与 P10 验收

### 参与角色
- P10 主线程：拍板范围、替换低效 P9、最终验收与复盘
- P9：两轮组织执行与提交
- P8：模型配置链路实现、测试、提交

### 评审范围
- `src/main/java/com/agent/config/AppConfig.java`
- `src/main/java/com/agent/config/KnowledgeBaseConfig.java`
- `src/main/java/com/agent/service/impl/DisabledLLMService.java`
- `src/main/java/com/agent/service/impl/HttpChatCompletionsLLMService.java`
- `src/main/java/com/agent/service/impl/HttpEmbeddingModel.java`
- `src/main/java/com/agent/service/impl/DisabledRerankService.java`
- `src/main/java/com/agent/service/impl/HttpRerankService.java`
- `src/main/resources/application.yml.template`
- `src/test/java/com/agent/config/AppConfigModelSelectionTest.java`
- `src/test/java/com/agent/config/KnowledgeBaseConfigBindingTest.java`
- `src/test/java/com/agent/service/impl/HttpModelServiceTest.java`
- `src/main/resources/application.yml`

### 统一结论
- 第一轮提交已打通 `embedding` 与 `llm` 的配置绑定、Bean 选择和最小 HTTP 实现，但遗漏了 `rerank` 运行时链路。
- 第二轮提交补齐 `rerank` 后，`knowledge-base.embedding.*`、`knowledge-base.llm.*`、`knowledge-base.rerank.*` 三类模型配置均已打通到 Spring Bean 选择层。
- 代码层闭环已完成并提交到 `master`，当前 `HEAD` 为 `a2f48c26537103f72ffa840d01e868686c162b16`。
- 仍有一个本地环境级问题未纳入提交：忽略文件 `src/main/resources/application.yml` 为非 UTF-8 编码，直接用它启动会先在 YAML 解析阶段失败。

### 核心问题

#### P1
- 第一轮执行未一次收口，`rerank` 虽有配置项但没有运行时实现与 Bean，P10 追加第二轮任务后才补齐。
- 本地 `src/main/resources/application.yml` 编码异常，`java -jar target/ascend-agent-1.0.0.jar` 直接启动时报 `YAMLException -> MalformedInputException`，这会阻塞用户实际配置和启动。

#### P2
- `LLMService` 与 `RerankService` 目前已“可配置、可注入、可调用”，但仓库里的上层业务入口尚未全面消费它们，当前完成的是基础设施接线，不是完整业务能力交付。

### 决策
- 接受两次代码提交结果，作为“模型配置基础设施接通”的当前基线。
- 不修改用户本地忽略配置文件中的真实密钥内容，编码问题由主线程在交付说明中单独提示。
- 以后凡是“配了就通”类需求，验收必须覆盖：模板、配置绑定、Bean 选择、最小调用测试、提交，以及用户本地运行配置可用性检查。

### 行动项
- 负责人：P10 向用户明确两次提交结果、当前 `HEAD`、已打通范围和本地 `application.yml` 的编码阻塞。
- 负责人：后续执行层若继续推进业务能力，要把 `LLMService` / `RerankService` 接到真实业务入口，不得只停留在基础设施层。

### 关键证据
- `75d997d3823c2223be0c1fb4716312c5b9f8464e`
- `a2f48c26537103f72ffa840d01e868686c162b16`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/main/resources/application.yml.template`
- `src/main/resources/application.yml`

## 2026-03-23 09:36:51 +0800

### 主题
`application.yml.template` 缺少端口等基础配置的根因核查

### 参与角色
- P10 主线程：核查历史、命名边界与交付口径
- P8：未介入
- P9：未介入

### 评审范围
- `src/main/resources/application.yml.template`
- `src/main/resources/application.yml`
- `git log -- src/main/resources/application.yml.template`

### 统一结论
- `application.yml.template` 缺少 `server.port`、`management`、`logging` 等基础配置，不是最近一次漏更新，而是这个文件从 2026-03-18 初版开始就只承载 `knowledge-base` 配置。
- 2026-03-19 在提交 `afdf1df` 中，该文件被改名为 `application.yml.template`，但内容边界没有同步升级为“完整应用模板”，因此命名与内容职责不一致。
- 当前真正包含端口等基础配置的是本地忽略文件 `src/main/resources/application.yml`，而不是仓库模板。

### 核心问题

#### P1
- 模板命名误导。文件名叫 `application.yml.template`，但内容只覆盖 `knowledge-base`，会让使用者自然预期其中包含完整的应用启动参数。

#### P2
- 文档口径缺失。仓库内没有明确说明该 template 只是知识库配置片段，导致用户需要自行对照本地 `application.yml` 才能发现 `server.port` 等配置不在模板内。

### 决策
- 将此问题定义为“模板职责边界不清”，不是单纯字段漏配。
- 后续若修复，应二选一：
- 方案 A：把 `application.yml.template` 扩成完整应用模板，补齐 `server`、`spring.application`、`management`、`logging` 等基础段。
- 方案 B：保留当前内容，但改名并补文档，明确它只是 `knowledge-base` 配置样例。

### 行动项
- 负责人：P10 先向用户明确这不是最近没更新，而是历史上一直如此。
- 负责人：若用户确认要修，后续由执行层按 A/B 之一落地并提交。

### 关键证据
- `src/main/resources/application.yml.template`
- `src/main/resources/application.yml`
- `b80ef81`
- `afdf1df`
- `75d997d`

## 2026-03-23 09:40:26 +0800

### 主题
完整化 `application.yml.template` 落地结果验收

### 参与角色
- P10 主线程：方案拍板、验收、记录
- P9：组织执行与提交
- P8：模板修改与提交

### 评审范围
- `src/main/resources/application.yml.template`
- `0211614d2b160ac5b6d44b87d5154804b911537c`

### 统一结论
- 方案 A 已落地完成。
- `application.yml.template` 已补齐完整应用基础段：`server`、`spring.application`、`management`、`logging`。
- 新模板同时保留原有 `knowledge-base` 配置，已不再是“文件名像完整模板、内容却只是知识库片段”的状态。
- 本次提交已落在 `master`，当前 `HEAD` 为 `0211614d2b160ac5b6d44b87d5154804b911537c`。

### 核心问题

#### P2
- 本次未处理本地 `application.yml` 编码问题；该问题仍是用户本地运行态风险，但不属于本次模板完整化提交范围。

### 决策
- 接受提交 `0211614d2b160ac5b6d44b87d5154804b911537c` 作为模板职责修正基线。
- 后续若继续增强模板，应基于完整模板演进，而不是再回退为局部片段。

### 行动项
- 负责人：P10 向用户确认模板已补齐，并说明当前剩余风险仅在本地忽略配置文件编码。
- 负责人：后续执行层如再改配置模板，必须保持“文件名、内容职责、文档口径”一致。

### 关键证据
- `0211614d2b160ac5b6d44b87d5154804b911537c`
- `src/main/resources/application.yml.template`

## 2026-03-23 09:51:48 +0800

### 主题
按用户当前 embedding/rerank 配置重启服务并验活

### 参与角色
- P10 主线程：范围拍板、结果验收、记录
- P9：组织执行与结果回传
- P8：重启服务、构造临时运行配置、健康检查

### 评审范围
- `/tmp/ascend-agent-runtime.yml`
- `/tmp/ascend-agent-runtime.log`
- `src/main/resources/application.yml`
- 运行中进程 `2168393`

### 统一结论
- 旧进程 `633561` 已停掉，避免误把旧配置当结果。
- 因本地 `src/main/resources/application.yml` 仍为非 UTF-8，不能直接拿它启动。
- 执行层基于用户当前本地配置提取出 `embedding/rerank` 实际值，生成一次性临时文件 `/tmp/ascend-agent-runtime.yml` 后成功启动了新进程。
- 新进程 `2168393` 已监听 `8080`，`/actuator/health` 返回 `200 UP`。
- 启动日志未显示 `embedding/rerank` 配置导致的启动期异常。

### 核心问题

#### P2
- 当前验证证明“用户已配置的 embedding/rerank 不阻塞服务启动，并已被带入运行配置”，但不等于已经证明外部 API 在真实业务调用阶段一定可达。
- 本地 `application.yml` 编码问题仍未修复，后续若不处理，直接按默认配置文件启动仍会失败。

### 决策
- 接受本次运行态验证结果：服务已按用户当前 `embedding/rerank` 配置启动成功。
- 后续若要验证外部模型链路，应补一条真实触发 embedding/rerank 的请求级验证，而不是只看健康检查。

### 行动项
- 负责人：P10 向用户明确服务已启动成功、当前 PID、日志路径与边界条件。
- 负责人：如用户继续要求外部模型链路验证，执行层补做请求级联调。

### 关键证据
- `/tmp/ascend-agent-runtime.yml`
- `/tmp/ascend-agent-runtime.log`
- `2168393`
- `633561`

## 2026-03-23 09:55:54 +0800

### 主题
抓取指定华为云 ModelArts API 页面并录入知识库

### 参与角色
- P10 主线程：范围拍板、结果验收、记录
- P9：组织执行与验证
- P8：接口调用、日志核验、检索验证

### 评审范围
- `https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html`
- 本地服务 `http://127.0.0.1:8080`
- 运行日志 `/tmp/ascend-agent-runtime.log`

### 统一结论
- 指定页面已经录入知识库，并可通过知识库搜索接口检索到。
- 专用 `crawl/huawei-cloud` 解析链路未能把该页面识别为结构化 API，结果是 `0 APIs`。
- 通用 `index` 网页入库链路成功，将该页面作为 1 个外部文档写入知识库，并切分为 `95 segments`。

### 核心问题

#### P1
- 华为云专用解析器对该页面未生效。抓取接口调用成功，但解析结果为 `0 APIs`，说明“页面可抓”不等于“结构化 API 可抽取”。

#### P2
- 当前录入成功依赖通用网页入库兜底，因此检索面可用，但结构化 API 元数据抽取能力仍未覆盖该页面类型。

### 决策
- 接受本次“页面已录入知识库”的结果。
- 不把本次结果表述为“华为云 API 结构化解析已打通”，避免误导。
- 后续若用户要求更高质量的 API 知识库，应单独修华为云专用解析器。

### 行动项
- 负责人：P10 向用户明确当前是“网页正文入库成功”，不是“专用 API parser 解析成功”。
- 负责人：如后续要提升结构化能力，执行层单独排查 `HuaweiCloudApiParser` 对该页面的抽取规则。

### 关键证据
- `POST /api/knowledge/crawl/huawei-cloud`
- `POST /api/knowledge/index`
- `POST /api/knowledge/search`
- `https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html`

## 2026-03-23 10:30:07 +0800

### 主题
修复华为云目录页下钻抓取与结构化 API 入库

### 参与角色
- P10 主线程：问题定性、验收门禁、记录
- P9：组织执行、要求补测试与运行闭环
- P8：实现 crawler/parser/service 修复、测试、提交、实测

### 评审范围
- `src/main/java/com/agent/crawler/WebDocumentCrawler.java`
- `src/main/java/com/agent/parser/HuaweiCloudApiParser.java`
- `src/main/java/com/agent/service/HuaweiCloudApiCrawlerService.java`
- `src/test/java/com/agent/parser/HuaweiCloudApiParserTest.java`
- `src/test/java/com/agent/service/HuaweiCloudApiCrawlerServiceTest.java`
- 提交 `573d8bbd114c7795b33e102dc984f22346c0b9ea`
- 运行日志 `/tmp/ascend-agent-runtime.log`

### 统一结论
- 专用华为云抓取链路已经从“只处理单页正文”修复为“目录页发现子详情页 -> 详情页结构化解析 -> API 入库”。
- 针对 `https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html`，新链路可稳定发现 `257` 个子详情页，并完成 `257 APIs indexed, 0 errors`。
- 检索 `ListWorkflows` 已命中结构化结果，`sourceLocation` 指向子详情页 `ListWorkflows.html`，说明不再只是目录页正文入库。
- 修复已提交到 `master`，当前 `HEAD` 为 `573d8bbd114c7795b33e102dc984f22346c0b9ea`。

### 核心问题

#### P1
- 原始根因一：`HuaweiCloudApiCrawlerService` 过去把网页正文文本当 HTML 交给 parser，导致 parser 实际拿不到 DOM。
- 原始根因二：过去只处理当前页，没有目录页到详情页的一层下钻能力。

#### P2
- 重复抓取同一目录页会产生重复结构化结果，当前检索中已观察到 `ListWorkflows` 等结果重复出现，说明后续仍需做去重或幂等处理。
- 当前目录页抓取是同步长耗时接口，请求一次约 `58s~172s`，后续若扩大范围需要考虑异步化或任务化。

### 决策
- 接受本次“目录页下钻结构化入库”修复结果。
- 不把当前状态包装成完全收口：重复入库与同步长耗时仍是后续优化项。

### 行动项
- 负责人：P10 向用户确认该目录页的子 API 已结构化入库，并说明当前残余风险。
- 负责人：后续执行层若继续演进知识库抓取能力，应补幂等去重与异步任务化。

### 关键证据
- `573d8bbd114c7795b33e102dc984f22346c0b9ea`
- `/tmp/ascend-agent-runtime.log`
- `https://support.huaweicloud.com/api-modelarts/ListWorkflows.html`
- `POST /api/knowledge/crawl/huawei-cloud`
- `POST /api/knowledge/search`

## 2026-03-23 10:55:43 +0800

### 主题
设计文档与当前向量存储实现偏差核查

### 参与角色
- P10 主线程：设计对照、偏差定性、记录
- P8：未介入
- P9：未介入

### 评审范围
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/KNOWLEDGE_BASE.md`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/main/java/com/agent/config/KnowledgeBaseConfig.java`
- `src/main/java/com/agent/service/KnowledgeBaseServiceImpl.java`

### 统一结论
- 设计文档不是按“向量只存在内存、服务重启即丢”来设计的。
- 文档的目标架构明确是“向量数据库（Chroma/Milvus/FAISS 等） + 元数据库（SQLite）”的双存储分层。
- 当前实现把 `EmbeddingStore` 硬编码成 `InMemoryEmbeddingStore`，而 `knowledge-base.vector-store.*` 配置没有真正接到运行时，属于实现偏离设计，不是设计本身如此。

### 核心问题

#### P1
- `AppConfig` 当前直接返回内存向量存储，导致向量索引不具备重启后持久化能力。
- `KnowledgeBaseConfig` 中已经有 `vector-store.type/url/collection`，但当前实现没有消费这组配置，形成“文档和配置都像支持、运行态其实没接上”的偏差。

#### P2
- 设计文档里向量库承担的是“语义检索层”，元数据库承担的是“结构化真值层”；当前实现只把真值层落到了 SQLite，向量层仍停留在演示态。

### 决策
- 将该问题定义为“实现偏离设计”，不是“文档设计如此”。
- 后续如果要做稳定性收口，应优先把持久化向量库真正接入运行时，替换内存实现。

### 行动项
- 负责人：P10 向用户明确设计意图与当前实现偏差。
- 负责人：后续执行层如继续改造知识库基础设施，应把 `vector-store` 配置接到 Chroma/Milvus/FAISS 等真实持久化实现，并补重启后可检索验证。

### 关键证据
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/KNOWLEDGE_BASE.md`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/main/java/com/agent/config/KnowledgeBaseConfig.java`

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

## 2026-03-23 11:20:49 +0800

### 主题
本地安装 Chroma 替代 Docker 拉镜像，并校准向量库默认端口配置

### 参与角色
- P10 主线程：决策安装路径，核对配置偏差，监督闭环验收
- P8：执行本地安装、启动进程、验证 heartbeat、必要时提交配置修复
- P9：未单独实例化，由主线程代行任务编排

### 评审范围
- `src/main/resources/application.yml.template`
- `src/main/java/com/agent/config/KnowledgeBaseConfig.java`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/test/java/com/agent/config/AppConfigVectorStoreSelectionTest.java`
- `src/test/java/com/agent/config/KnowledgeBaseConfigBindingTest.java`

### 统一结论
- Docker 拉取 Chroma 受网络影响，不应继续阻塞主线，先采用本地直接安装方案。
- 当前代码虽已开始接入 `ChromaEmbeddingStore`，但默认 URL 仍残留 `8000`，没有与用户指定端口 `22333` 对齐。
- 如果只安装 Chroma 而不改默认端口/模板，服务仍可能连接错误地址，导致“基础设施已就绪、应用仍不可用”的假象。

### 核心问题

#### P1
- `KnowledgeBaseConfig` 默认 `vector-store.url` 仍为 `http://localhost:8000`，与当前决策端口不一致。
- `application.yml.template` 仍引导用户配置 `http://localhost:8000`，会把后续部署继续带偏。

#### P2
- 新增测试样例仍使用 `8000`，与目标运行口径不一致，后续容易让维护者误判真实期望端口。
- P8 本轮安装任务如果不绑定“heartbeat + 端口 + 应用接入”三项验收，容易出现只验证进程存在、不验证可用性的假完成。

### 决策
- 先让 P8 走本地安装 Chroma 路径，不再卡 Docker。
- 安装完成后必须用 `22333` 做 heartbeat 校验。
- 如需仓库改动，P8 需把默认端口相关配置和测试一并修正，并单独 commit。

### 行动项
- 负责人：P8 执行本地安装并反馈启动、端口、heartbeat 结果。
- 负责人：P8 如改仓库，统一把 `8000` 校准为 `22333`，提交独立 commit。
- 负责人：P10 在 P8 返回后做结果复核，并决定是否继续推进持久化回归验证。

### 关键证据
- `src/main/resources/application.yml.template`
- `src/main/java/com/agent/config/KnowledgeBaseConfig.java`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/test/java/com/agent/config/AppConfigVectorStoreSelectionTest.java`
- `src/test/java/com/agent/config/KnowledgeBaseConfigBindingTest.java`

## 2026-03-23 11:36:22 +0800

### 主题
本地安装 Chroma、回退兼容版本并完成知识库服务重启后持久化验证

### 参与角色
- P10 主线程：做版本决策、收口配置、验收服务重启前后行为
- P8：执行本地安装路径探索，确认非 Docker 可行
- P9：未单独实例化，由主线程代行任务编排

### 评审范围
- `/tmp/chroma-venv`
- `/tmp/chroma-venv-0520`
- `/tmp/chroma-22333.log`
- `/tmp/ascend-agent-runtime.yml`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/main/java/com/agent/config/KnowledgeBaseConfig.java`
- `src/main/resources/application.yml.template`
- `src/test/java/com/agent/config/AppConfigVectorStoreSelectionTest.java`
- `src/test/java/com/agent/config/KnowledgeBaseConfigBindingTest.java`

### 统一结论
- 非 Docker 本地安装 Chroma 可行，机器具备 `Python 3.12 + pip + venv` 条件。
- `chromadb 1.5.5` 虽能启动，但已废弃 `api/v1`，与当前 `langchain4j-chroma 0.35.0` 不兼容。
- `chromadb 0.5.20` 仍支持 `api/v1`，可与当前 Java 客户端直接对接。
- 服务已在 `JDK 21 + Chroma 0.5.20 + 22333` 组合下重启成功，并完成“抓取 -> 搜索 -> 重启 -> 再搜索”闭环，证明向量不再随服务重启丢失。

### 核心问题

#### P1
- 初始本地安装默认拿到 `chromadb 1.5.5`，其 `api/v1/heartbeat` 返回 `410 Gone`，与当前 Java 客户端期望不一致。
- 运行时配置 `/tmp/ascend-agent-runtime.yml` 仍指向 `http://localhost:8000`，若不改会导致服务继续连接错误端口。

#### P2
- 仓库默认值和模板原先仍残留 `8000`，与实际目标端口 `22333` 不一致。
- 默认终端 `JAVA_HOME` 仍指向 JDK8，如不显式切到 JDK21，会导致构建和测试结论失真。

### 决策
- Chroma 运行版本固定为兼容当前客户端的 `0.5.20`，不采用 `1.5.5`。
- 向量库运行端口统一为 `22333`。
- 应用构建与运行统一使用 JDK21。

### 行动项
- 负责人：P10 已完成仓库默认端口收口，并重启服务验证。
- 负责人：后续如升级 `langchain4j-chroma` 或 Chroma 主版本，必须先做 API 版本兼容性评审，不能只验证端口存活。

### 关键证据
- `/tmp/chroma-22333.log`
- `/tmp/ascend-agent-runtime.log`
- `/tmp/ascend-agent-runtime.yml`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/main/java/com/agent/config/KnowledgeBaseConfig.java`

## 2026-03-24 09:48:23 +0800

### 主题
P10 对第二批 Agent 对齐工作做架构拍板，并把当前运行态异常拆出独立缺陷卡

### 参与角色
- P10 主线程：核对代码事实、审核 P9 产出、决定放行顺序与红线
- P9：提交架构评审与三张任务卡，负责继续向执行层分发
- P8：待领取 `WP3` 与“运行态异常”两张执行卡

### 评审范围
- `src/main/java/com/agent/controller/KnowledgeBaseController.java`
- `src/main/java/com/agent/service/KnowledgeBaseServiceImpl.java`
- `src/main/java/com/agent/storage/MetadataStore.java`
- `src/main/java/com/agent/config/AgentConfig.java`
- `src/main/java/com/agent/config/AppConfig.java`
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/CONFIG_GUIDE.md`
- `/tmp/ascend-agent-runtime.log`

### 统一结论
- P9 给出的三张任务卡方向正确，但执行顺序必须固定为 `WP3 -> WP1 -> WP2`，不允许并行放大 scope。
- 当前仓库的第二批对齐缺口是三类：运行态停止线还没把“允许入口”做成硬事实；`/api/knowledge/*` 返回与错误契约仍是 ad hoc；检索链路对元数据缺失仍会静默丢结果。
- 当前运行态还存在独立缺陷：`/tmp/ascend-agent-runtime.log` 出现 `SpringApplicationShutdownHook` 的 `NoClassDefFoundError`；同轮本机探活出现 `curl` 失败而端口监听信息不稳定，需单独排障，不得借机扩成“主 Agent 重构”。

### 核心问题

#### P1
- `KnowledgeBaseController` 四个 `/api/knowledge/*` 接口仍直接返回 `Map<String, Object>`，没有统一错误体、参数校验和兼容性声明；这是 `WP1` 需要收口的事实基础。
- `KnowledgeBaseServiceImpl.search()` 在 `apiId` 存在但 `metadataStore.findByApiId()` 为空或抛错时，会直接丢掉该向量命中，只留下日志或返回更少结果；这会把弱一致问题放大成检索不稳定。

#### P2
- `indexJavaProject()` 当前先写 SQLite 元数据再批量写向量，没有事务协调；本轮只能按“弱一致 + 显式降级”收口，不能误做成强一致重构。
- `/actuator/info` 当前暴露了 `enabled/stage/mode/entrypoint`，但还没有把“当前允许的唯一对外入口是 /api/knowledge/*”写成运行态事实，这就是 `WP3` 必须先发的原因。
- 当前运行态存在独立生命周期异常，排障任务必须与对齐主线分离，否则执行层会借“修服务”扩大改动面。

### 决策
- P10 正式批准 P9 三张任务卡，但放行顺序固定为 `WP3 -> WP1 -> WP2`。
- 第一张立即放行的是 `WP3`，目标是把“只对齐不收编”固化到配置、运行态和验收门禁中。
- 运行态异常单独建缺陷卡处理，边界仅限服务生命周期和探活异常，不得新增任何主 Agent 风格 API、`workflow/planner/executor` 路径或新存储。
- 当前批次继续执行红线：不得新增 `/api/agent/*`、`/api/plan/*`、`/api/execute/*`、`/api/workflow/*`；不得引入 `orchestration/planner/executor/workflow` 真实实现；不得把知识库链路升级成“检索 + 生成”主链路；不得新增存储或中间件。

### 行动项
- 负责人：P9 立即向执行层下发 `WP3` 六要素任务卡，并把提交要求固定为“先 commit，再验证，再汇报”。
- 负责人：P9 额外下发“运行态异常”缺陷卡，要求在不扩 scope 的前提下定位 `SpringApplicationShutdownHook` 和探活异常。
- 负责人：P10 仅按 `meeting.md`、`/actuator/info`、`scripts/verify_baseline.sh` 和红线扫描做验收，不直接代做执行层工作。

### 关键证据
- `src/main/java/com/agent/controller/KnowledgeBaseController.java`
- `src/main/java/com/agent/service/KnowledgeBaseServiceImpl.java`
- `src/main/java/com/agent/storage/MetadataStore.java`
- `src/main/java/com/agent/config/AgentConfig.java`
- `src/main/java/com/agent/config/AppConfig.java`
- `/tmp/ascend-agent-runtime.log`

## 2026-03-24 10:32:03 +0800

### 主题
P10 验收 `WP3` 停止线固化与运行态异常修复，并确认可进入 `WP1`

### 参与角色
- P10 主线程：做最终验收、复核运行态、记录结论
- P9：前序已完成任务卡下发与红线约束
- P8：完成 `WP3` 与运行态异常两条执行线并分别提交

### 评审范围
- `src/main/java/com/agent/config/AgentConfig.java`
- `src/main/java/com/agent/config/AppConfig.java`
- `src/main/resources/application.yml.template`
- `src/test/java/com/agent/config/AgentInfoContributorTest.java`
- `src/test/java/com/agent/config/AgentStoplineHardeningTest.java`
- `src/test/java/com/agent/runtime/StartupRuntimeGuardrailsTest.java`
- `scripts/start_service.sh`
- `scripts/stop_service.sh`
- `/root/ascend_agent/.ascend_agent/logs/service.log`
- `/tmp/ascend-agent-runtime.log`

### 统一结论
- `WP3` 已验收通过：启动期对越线配置执行 fail-fast，`/actuator/info` 在兼容原有字段的同时新增 `allowedEndpoints` 与 `stopline`，明确当前唯一允许入口是 `/api/knowledge/*`。
- 运行态异常已验收通过：启动脚本不再误继承宿主机 Java 8，服务成功链路从“进程活着”收紧为“`/actuator/health` 可达”，停机后 `8080` 正常释放。
- 历史 `SpringApplicationShutdownHook -> ThrowableProxy` 异常只存在于旧验证路径 `/tmp/ascend-agent-runtime.log`；本轮标准日志 `/root/ascend_agent/.ascend_agent/logs/service.log` 未复现该异常。
- 第二批对齐顺序不变：`WP3` 完成后，下一张允许放行的是 `WP1`；`WP2` 继续排在其后。

### 核心问题

#### P1
- `WP3` 初次提交只做到代码与 compile 级别验证，未重新 `package` 即启动旧 jar，导致 `/actuator/info` 一度看不到新字段；P10 本轮要求补齐“重打包 + 真实起停”后才通过验收。
- 运行态问题根因不是单点，而是两个缺陷叠加：旧启动链路会误用 Java 8，旧成功判定又只看进程存活，不看健康探活。

#### P2
- 旧停机链路过度依赖 pid 文件，遇到 pid 漂移会降低可控性；本轮已补为 pid 文件与端口监听双重判定。
- 标准运行日志已迁移到 `ASCEND_AGENT_HOME`，后续排障不得再把 `/tmp/ascend-agent-runtime.log` 当成当前基线日志。

### 决策
- 接受提交 `5f0c1f9` `feat: harden alignment stopline` 作为 `WP3` 完成交付。
- 接受提交 `da00b33` `fix(runtime): harden service startup and health checks` 作为运行态异常修复交付。
- P10 正式放行下一阶段 `WP1`，继续禁止提前启动 `WP2` 或任何主 Agent 收编动作。

### 行动项
- 负责人：P9 依据本条纪要，向执行层放行 `WP1`，范围仅限 `/api/knowledge/*` 契约与错误模型收口。
- 负责人：P10 在 `WP1` 合入前继续使用红线扫描，阻断任何 `/api/agent/*`、`/plan`、`/execute`、`/workflow` 扩张。
- 负责人：后续所有运行态验收统一使用 `scripts/start_service.sh` / `scripts/stop_service.sh` 和 `ASCEND_AGENT_HOME` 下日志，不再引用旧 `/tmp` 路径作为当前结论依据。

### 关键证据
- `git show --stat 5f0c1f9`
- `git show --stat da00b33`
- `curl -i http://127.0.0.1:8080/actuator/health`
- `curl -i http://127.0.0.1:8080/actuator/info`
- `scripts/start_service.sh`
- `scripts/stop_service.sh`
- `/root/ascend_agent/.ascend_agent/logs/service.log`

## 2026-03-24 11:08:00 +0800

### 主题
P10 验收 `WP1` 契约收口与 `WP2` 弱一致降级，确认第二批对齐主线完成

### 参与角色
- P10 主线程：核主仓落地、做集成验收、记录最终结论
- P9：负责 `WP1`、`WP2` 任务拆分与执行组织
- P8：分别完成 `WP1` 两张卡与 `WP2` 两张卡并提交

### 评审范围
- `src/main/java/com/agent/controller/KnowledgeBaseController.java`
- `src/main/java/com/agent/controller/KnowledgeBaseControllerAdvice.java`
- `src/main/java/com/agent/model/error/ApiErrorResponse.java`
- `src/main/java/com/agent/service/KnowledgeBaseServiceImpl.java`
- `src/main/java/com/agent/storage/MetadataStore.java`
- `src/main/java/com/agent/storage/VectorStoreAdapter.java`
- `src/main/java/com/agent/parser/HuaweiCloudApiParser.java`
- `src/main/java/com/agent/service/HuaweiCloudApiCrawlerService.java`
- `src/test/java/com/agent/controller/KnowledgeBaseControllerTest.java`
- `src/test/java/com/agent/controller/KnowledgeBaseControllerAdviceTest.java`
- `src/test/java/com/agent/controller/KnowledgeBaseControllerRuntimeFailureTest.java`
- `src/test/java/com/agent/service/KnowledgeBaseServiceImplWeakConsistencyTest.java`
- `src/test/java/com/agent/parser/HuaweiCloudApiParserTest.java`
- `src/test/java/com/agent/service/HuaweiCloudApiCrawlerServiceTest.java`

### 统一结论
- `WP1` 已验收通过：`/api/knowledge/*` 在不破坏成功响应字段语义的前提下，补上了统一错误体和最小参数校验；业务字段校验错误保持 `200 + 结构化 error body`，框架/传输层错误允许 `400/415 + 结构化 error body`。
- 华为云目录页抓取已验收通过：仍严格保持一层下钻，只增强了链接规范化、去重、锚点/查询串清洗、无关链接过滤和空页容错，没有扩成多层爬取。
- `WP2` 已验收通过：搜索链路在 metadata 缺失、metadata 查询异常、向量检索异常等场景下不再静默丢结果或直接抛到 controller；索引链路在单文件失败场景下保留准确 `failureCount` 并继续处理其余文件。
- 第二批对齐主线已按 `WP3 -> WP1 -> WP2` 顺序完成，本轮没有越过红线引入主 Agent API、workflow/planner/executor 真实路径或新存储。

### 核心问题

#### P1
- `WP2` 执行期间主线程在 agent 生命周期管理上再次出现失误：把“消息已发出”误判成“worker 已开始执行”，导致进度判断失真；此次验收以主仓 commit 和主仓验证结果为准，修正了这个口径问题。
- `KnowledgeBaseServiceImpl` 原实现对 metadata 缺失/查询失败的向量命中会直接丢弃结果，本轮通过降级结果和日志把该风险收口为“弱一致可诊断”，而不是“静默缺失”。

#### P2
- 目标测试在当前环境需要使用 `-DforkCount=0`，否则 surefire 默认 fork 在本机环境不稳定；这属于测试环境约束，不是断言失败。
- 工作区仍存在未跟踪目录 `task_cards/`，不属于本轮源码与验收提交范围，后续是否保留需单独决定。

### 决策
- 接受提交 `8559b40` `feat: harden knowledge api contract` 作为 `WP1` Card#1 完成交付。
- 接受提交 `07af413` `fix(huawei-cloud): harden one-level directory crawl` 作为 `WP1` Card#2 完成交付。
- 接受提交 `136e5bc` `feat: harden knowledge base weak consistency` 作为 `WP2` 产线改造交付。
- 接受提交 `80dda8d` `test(wp2): cover weak consistency degradation paths` 作为 `WP2` 测试交付。
- 第二批“对齐而非收编”任务到此完成；后续若继续推进，必须先由 P10 重新定义下一批目标，不能直接滑向主 Agent 收编。

### 行动项
- 负责人：P10 如需对外说明当前能力边界，统一引用 `/actuator/info` 与 `meeting.md`，不得把“第二批对齐完成”表述成“主 Agent 已上线”。
- 负责人：后续若要继续做下一批工作，P9 需先提出新的任务包与边界，不得默认顺延到主 Agent 收编。
- 负责人：`task_cards/` 是否纳入仓库管理单独决策，本轮先不计入验收结论。

### 关键证据
- `git show --stat 8559b40`
- `git show --stat 07af413`
- `git show --stat 136e5bc`
- `git show --stat 80dda8d`
- `mvn -q -DskipTests compile`
- `mvn -q -DforkCount=0 -Dtest=KnowledgeBaseServiceImplWeakConsistencyTest,KnowledgeBaseControllerRuntimeFailureTest,KnowledgeBaseControllerTest,KnowledgeBaseControllerAdviceTest,HuaweiCloudApiParserTest,HuaweiCloudApiCrawlerServiceTest test`
