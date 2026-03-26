# Meeting Record

## Usage
- 对架构评审、代码审查、缺陷分析、技术决策类任务，统一记录到本文件。
- 每次新增记录时保留历史内容，不覆盖旧结论。
- 建议把最新记录放在文件最上方，便于事后快速查看。
- 记录至少包含：时间、主题、范围、统一结论、问题分级、行动项、关键证据。

## 2026-03-25 20:53:09 +0800

### 主题
Sprint-1 第二十八批文档/验收线落地：生成代码合同、验收脚本与 BMS 负向真值收口

### 参与角色
- P8 文档/验收 owner：补设计文档、skill、验收脚本与会议记录

### 统一结论
- “生成代码应该长什么样”不再只靠口头约定，已收口为文档合同与 skill 规则。
- 当前 BMS 场景的真实负向返回已经被提升为验收真值：
  - HTTP `400`
  - `error_code=ModelArts.7000`
  - `error_msg=Server f13a67fc-11c4-48f9-8f0f-b533a5bcea13 type is BMS, does not support detach volume device.`
- 已新增最小验收脚本 `scripts/verify_testcase_generation.sh`，用于统一执行：
  - `/api/testcase/generate` 调用
  - 响应字段校验
  - TODO/placeholder 校验
  - `public class` 校验
  - Java 21 编译校验

### 行动项
- 后续执行线按文档合同治理生成质量，重点清理资源 ID 硬编码与臆造断言。

### 关键证据
- `docs/TESTCASE_GENERATION_V3_CURRENT.md`
- `.codex/skills/huawei-testcase-generation/SKILL.md`
- `scripts/verify_testcase_generation.sh`
- `meeting.md`

## 2026-03-25 20:36:41 +0800

### 主题
Sprint-1 第二十七批真实接口验收：使用正确 Lite Server id 后的 DetachDevServerVolume 返回

### 参与角色
- P10 主线程：使用正确 Lite Server 资源 ID 直连真实接口复核

### 统一结论
- 已改用真实 Lite Server 资源 ID `f13a67fc-11c4-48f9-8f0f-b533a5bcea13` 重新调用 `DetachDevServerVolume`。
- 本次不再返回 `instance not found`，说明资源 ID 选型已正确。
- 真实返回为：
  - `HTTP 400`
  - `error_code = ModelArts.7000`
  - `error_msg = Server f13a67fc-11c4-48f9-8f0f-b533a5bcea13 type is BMS, does not support detach volume device.`
- 结论已经明确：当前这台 `liteserver-bm-a2-1203` 是 `BMS` 类型 Lite Server，该接口不支持对这类实例执行卸载磁盘设备操作。

### 关键证据
- 请求：
  - `DELETE https://modelarts.cn-north-9.myhuaweicloud.com/v1/2b5cf022801c4a1cac8ee90d431a8f20/dev-servers/f13a67fc-11c4-48f9-8f0f-b533a5bcea13/detachvolume/0ce45186-07a7-4139-98b9-2a00233b5ba5`
- 响应：
  - `HTTP 400`
  - `X-Request-Id: 71f2993bb44fa789d3a61b8b6201ba95`
  - `{"error_code":"ModelArts.7000","error_msg":"Server f13a67fc-11c4-48f9-8f0f-b533a5bcea13 type is BMS, does not support detach volume device."}`

## 2026-03-25 20:26:22 +0800

### 主题
Sprint-1 第二十六批真实资源映射核验：`cloud_server.id` 与 `Lite Server id` 不是同一个字段

### 参与角色
- P10 主线程：用真实 `ListAllDevServers` 接口核对实例映射关系

### 统一结论
- 用户给出的 `56048fb9-726e-403d-8044-26c1dbacbca2` 确实存在，但它不是 `DetachDevServerVolume` 接口所需的 Lite Server 资源 ID，而是底层 `cloud_server.id`。
- 在 `GET /v1/{project_id}/dev-servers/all` 返回中，这台机器的真实 Lite Server 资源为：
  - `id = f13a67fc-11c4-48f9-8f0f-b533a5bcea13`
  - `cloud_server.id = 56048fb9-726e-403d-8044-26c1dbacbca2`
  - `name = liteserver-bm-a2-1203`
  - `status = RUNNING`
  - `volumes[0].evs_id = 0ce45186-07a7-4139-98b9-2a00233b5ba5`
- 因此此前 `DELETE /dev-servers/56048.../detachvolume/0ce4...` 返回 `ModelArts.6404 not found` 是符合预期的，因为接口查的是 Lite Server 资源 ID，不是底层 BMS/ECS ID。

### 关键证据
- `GET https://modelarts.cn-north-9.myhuaweicloud.com/v1/2b5cf022801c4a1cac8ee90d431a8f20/dev-servers/all`
- 命中记录：
  - `id = f13a67fc-11c4-48f9-8f0f-b533a5bcea13`
  - `cloud_server.id = 56048fb9-726e-403d-8044-26c1dbacbca2`
  - `volumes[0].evs_id = 0ce45186-07a7-4139-98b9-2a00233b5ba5`

## 2026-03-25 20:14:27 +0800

### 主题
Sprint-1 第二十五批真实接口验收：DetachDevServerVolume 指定实例/磁盘调用返回

### 参与角色
- P10 主线程：读取用户更新后的 token，直连华为云真实接口验收

### 统一结论
- 已使用用户更新后的 token 调用真实接口。
- 该 token 当前绑定项目为 `cn-north-9`，`project_id=2b5cf022801c4a1cac8ee90d431a8f20`。
- 对以下资源发起卸载请求后，真实返回为 `HTTP 400`：
  - `dev-server id=56048fb9-726e-403d-8044-26c1dbacbca2`
  - `volume_id=0ce45186-07a7-4139-98b9-2a00233b5ba5`
- 真实错误为：
  - `error_code=ModelArts.6404`
  - `error_msg=LiteServer instance '56048fb9-726e-403d-8044-26c1dbacbca2' not found.`
- 这说明当前失败点发生在 `Lite Server 实例不存在/当前项目下找不到`，还没进入到磁盘合法性校验阶段。

### 关键证据
- 请求：
  - `DELETE https://modelarts.cn-north-9.myhuaweicloud.com/v1/2b5cf022801c4a1cac8ee90d431a8f20/dev-servers/56048fb9-726e-403d-8044-26c1dbacbca2/detachvolume/0ce45186-07a7-4139-98b9-2a00233b5ba5`
- 响应：
  - `HTTP 400`
  - `X-Request-Id: 8346d725662901265ce3d15846d2d69f`
  - `{"error_code":"ModelArts.6404","error_msg":"LiteServer instance '56048fb9-726e-403d-8044-26c1dbacbca2' not found."}`

## 2026-03-25 18:43:38 +0800

### 主题
Sprint-1 第二十四批运行态闭环：重新编译部署后对四个问题的真实验收

### 参与角色
- P10 主线程：重新编译、重启服务、重刷知识库、宿主机接口验收

### 统一结论
- 之前“为什么还没修好”的根因已经验证清楚：当时确实是我没有把工作区改动推进到运行态闭环。
- 现在已经完成 `mvn package -> stop/start service -> 全量重刷 -> 宿主机接口复测`。
- 用户指出的四个问题，在当前运行态已经全部修正：
  - `卸载系统盘` 不再漂到 `CancelObs`
  - `卸载开发服务器卷` 不再漂到 `DeleteService`
  - `DetachDevServerVolume` 的 `parameters/requestBody/responseBody` 已有真实结构化内容
  - `citations` 已收敛为单条正确引用，不再混入无关 API
- 但这轮同时暴露了一个新的更后置问题：生成接口虽已 `200`，生成的测试代码仍存在业务语义上的硬编码/臆造值，例如把 `volume_id` 写成 `"system"`，这不是前述四个问题，而是下一阶段的生成质量问题。

### 验收结果
- 构建与部署
  - `mvn -q -Dtest=TestcaseGenerationServiceImplTest,KnowledgeBaseServiceImplWeakConsistencyTest,HuaweiCloudApiCrawlerServiceTest test`：通过
  - `mvn -q -DskipTests package`：通过
  - 新服务 PID：`3582928`
  - `GET /actuator/health`：`HTTP 200`
- 全量重刷
  - `POST /api/knowledge/crawl/huawei-cloud`
  - 结果：`success=true`，`successCount=258`，`failureCount=0`，`durationMs=123780`
- 检索验收 1
  - query：`卸载系统盘`
  - top1：`DetachDevServerVolume`
  - 结构化字段：`parameters=3`，`requestBody=无`，`responseBody` 为真实响应示例
- 检索验收 2
  - query：`卸载Lite Server系统盘`
  - top1：`DetachDevServerVolume`
- 检索验收 3
  - query：`卸载开发服务器卷`
  - top1：`DetachDevServerVolume`
- 生成接口验收
  - `POST /api/testcase/generate`
  - 入参：`{"requirement":"卸载Lite Server系统盘"}`
  - 结果：`HTTP 200`
  - `citations`：仅 1 条，且为 `DetachDevServerVolume`
  - `refinedRequirement`：已返回

### 核心问题

#### P1
- 生成测试代码仍存在业务语义偏差：示例输出里把路径参数 `volume_id` 硬编码成 `"system"`，且对详情断言引入了未从上下文明确约束出的字段/状态。

### 决策
- 当前四个指定问题验收通过。
- 下一步从“知识库/召回问题”切到“代码生成质量问题”，重点治理：
  - 必填路径参数一律从配置读取，不得臆造
  - 断言只能使用上下文中已知字段和明确约束

### 关键证据
- `scripts/start_service.sh`
- `src/main/java/com/agent/parser/HuaweiCloudApiParser.java`
- `src/main/java/com/agent/service/KnowledgeBaseServiceImpl.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `POST /api/knowledge/search`
- `POST /api/testcase/generate`

## 2026-03-25 17:40:42 +0800

### 主题
Sprint-1 第二十三批结论澄清：四个问题为什么在运行态还没消失

### 参与角色
- P10 主线程：核对工作区改动、运行中服务状态、生成链路引用逻辑

### 统一结论
- 这四个问题没有在运行态消失，不是因为没人修，而是因为“代码已改，但还没完成主线程验收后的重启生效与重新索引”。
- 当前工作区里已经存在 parser 修复和 retrieval 修复代码，但运行中的 `3518618` 进程仍是旧版本服务。
- 其中 parser 类问题即使代码已改，也必须用新 parser 再次重刷网页，才能把 `requestBody/responseBody/parameters` 重新写入存储。
- `citations` 噪声问题本轮也没有被直接修，当前实现仍会把 `effectiveKbResults` 全量转成 citations，因此只要召回结果里混入无关 concrete hit，引用就会脏。

### 验收结果
- 工作区状态
  - `HuaweiCloudApiParser.java`、`HuaweiCloudApiParserTest.java` 已修改
  - `KnowledgeBaseServiceImpl.java`、`KnowledgeBaseServiceImplWeakConsistencyTest.java` 已修改
- 运行态状态
  - `service.log` 里当前搜索日志仍是旧口径：`Found 3 results for query`
  - 新 retrieval 代码的日志口径应为：`Found {} results for query after ranking {} unique candidates across {} variants`
  - 结论：运行中服务尚未切到新实现
- 生成链路引用逻辑
  - `TestcaseGenerationServiceImpl.buildKnowledgeBaseCitations()` 仍对 `effectiveKbResults` 全量生成 citations
  - 本轮没有新增 citations 过滤或主引用收敛逻辑

### 核心问题

#### P1
- parser 修复代码尚未经过“重启服务 + 重新爬取/重建索引 + 宿主机接口复测”的完整闭环，所以运行态仍看到结构化字段为空。

#### P1
- retrieval 修复代码尚未部署到当前运行进程，所以运行态仍可能继续返回 `CancelObs` / `DeleteService`。

#### P2
- citations 噪声不是 parser 问题，而是生成链路当前按 `effectiveKbResults` 全量出引用；这条本轮没有单独修。

### 决策
- 口径统一为：`已编码，未完成运行态闭环`
- 下一步必须按顺序执行：
  - 合入当前两条修复
  - 重新构建并重启服务
  - 用新 parser 全量重刷知识库
  - 再做 `/api/knowledge/search` 与 `/api/testcase/generate` 宿主机验收
  - 单独补 citations 收敛逻辑

### 关键证据
- `git status --short`
- `src/main/java/com/agent/parser/HuaweiCloudApiParser.java`
- `src/main/java/com/agent/service/KnowledgeBaseServiceImpl.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `.ascend_agent/logs/service.log`

## 2026-03-25 17:15:45 +0800

### 主题
Sprint-1 第二十二批并行验收：全量网页重刷与失败点复测

### 参与角色
- P10 主线程：组织刷新、复测、最终验收与记录沉淀
- P8 执行视角：执行全量网页重刷并回传结果
- P9 验收视角：复测失败点并给出通过性结论

### 统一结论
- 本轮全量网页重刷已完成，`258/258` 成功，`failureCount=0`，`durationMs=70590`。
- `/api/testcase/generate` 对 `requirement=卸载Lite Server系统盘` 本轮已真实返回 `HTTP 200`，并且响应中确实包含 `javaTestCode`、`citations`、`degraded`、`refinedRequirement`。
- 与上一轮相比，`DetachDevServerVolume` 相关 parser 脏数据已有改善：`httpMethod` 已稳定为 `DELETE`，`description` 已恢复为与 Lite Server 卸载磁盘相关的正确文案。
- 但结构化抽取仍未过关：`requestBody=null`、`responseBody=null`、`parameters=[]` 依旧为空。
- 召回漂移仍未解决：`卸载系统盘` 仍漂到 `CancelObs`，`卸载开发服务器卷` 仍漂到 `DeleteService`；只有 `卸载Lite Server系统盘` top1 命中正确 API。
- 本轮日志中未再出现上轮的 `429`；要求优化与代码生成两个 LLM 调用均为 `status=200`。

### 验收结果
- 全量网页重刷
  - 请求：`POST /api/knowledge/crawl/huawei-cloud`
  - 目录页：`https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html`
  - 结果：`success=true`，`successCount=258`，`failureCount=0`，`durationMs=70590`
- 召回复测 1
  - query：`卸载系统盘`
  - top1：`huawei-cancelobs--1027994440`
  - method：`CancelObs`
  - endpoint：`DELETE /v1/{project_id}/notebooks/{instance_id}/storage/{storage_id}`
  - 结论：不通过，仍为错误 API
- 召回复测 2
  - query：`卸载Lite Server系统盘`
  - top1：`huawei-detachdevservervolume-1288194079`
  - method：`DetachDevServerVolume`
  - endpoint：`DELETE /v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}`
  - 结论：通过，top1 命中正确 API
  - 结构化字段：`parameters=[]`，`requestBody=null`，`responseBody=null`
- 召回复测 3
  - query：`卸载开发服务器卷`
  - top1：`huawei-deleteservice--1488106924`
  - method：`DeleteService`
  - endpoint：`DELETE /v1/{project_id}/services/{service_id}`
  - 结论：不通过，仍发生召回漂移
- 生成链路复测
  - 请求：`POST /api/testcase/generate`
  - 入参：`{"requirement":"卸载Lite Server系统盘"}`
  - 结果：`HTTP 200`
  - 响应字段核验
    - `javaTestCode`：有
    - `citations`：有
    - `degraded`：`false`
    - `refinedRequirement`：有
  - refinedRequirement 真实返回：
    - `测试目标：验证卸载Lite Server系统盘功能。前置条件：存在一台已挂载系统盘的Lite Server实例。输入参数：服务器实例ID、系统盘ID。测试步骤：调用卸载磁盘接口，传入指定ID。预期结果：接口返回成功，系统盘挂载状态变更为“未挂载”或“可用”，服务器详情中系统盘ID字段为空。`
  - 额外观察
    - citations 中除正确的 `DetachDevServerVolume` 外，还混入了 `DeleteDevServer`，说明引用选择仍有噪声
    - 生成代码本轮仅完成接口级返回验收，尚未做编译/真实执行验收

### 核心问题

#### P1
- 召回漂移仍明显存在，`卸载系统盘` 与 `卸载开发服务器卷` 两个 query 仍无法稳定打到 `DetachDevServerVolume`。

#### P1
- `DetachDevServerVolume` 的结构化字段依然为空，说明 parser 抽取问题尚未真正修通，后续会继续拖累生成质量与精确断言。

#### P2
- 生成接口虽然已恢复 `200`，但 citations 仍有噪声，说明检索结果消费与引用筛选策略还不够干净。

### 决策
- 本轮判定为：`部分通过`
- 通过项
  - 全量网页重刷成功
  - `卸载Lite Server系统盘` top1 已稳定命中正确 API
  - `testcase/generate` 已恢复成功返回，且 `refinedRequirement` 已对外可见
- 未通过项
  - parser 结构化抽取未完成
  - 检索漂移未完成治理

### 行动项
- 下一优先级 1：修 `HuaweiCloudApiParser`，把 `requestBody/responseBody/parameters` 对 `DetachDevServerVolume` 这类页面真正抽出来。
- 下一优先级 2：修 `KnowledgeBaseServiceImpl` 的召回排序/消费逻辑，压制 `CancelObs`、`DeleteService` 这类 DELETE 类接口的误召回。
- 下一优先级 3：在 parser 和召回修正后，重新跑 `generate -> compile -> 真实调用/模拟调用` 的完整业务验收。

### 关键证据
- `POST /api/knowledge/crawl/huawei-cloud`
- `POST /api/knowledge/search`
- `POST /api/testcase/generate`
- `.ascend_agent/logs/service.log`

## 2026-03-25 16:11:50 +0800

### 主题
Sprint-1 第二十一批并行验收：refine 文本可见性核验与召回改造方案收口

### 参与角色
- P10 主线程：主线程复核、拍板后续召回治理顺序
- P8 执行代理：核验 refined requirement 当前是否可见、可追踪
- P9 方案视角：收口知识库召回改造方案

### 统一结论
- 当前系统中 `refined requirement` 确实会生成，但它只是运行时中间变量：没有进返回对象、没有进日志正文、没有单独接口、没有持久化，因此对调用方是不可见、不可追踪的。
- 这一点不是“本次没观测到”，而是当前实现本身没有暴露。
- 知识库召回的主问题已经明确：不是完全搜不到，而是“搜到了也只是弱结构结果”，导致生成主链路不能把 top1 命中当成 concrete hit 消费。
- 召回治理优先级已经拍板：先修结构化 metadata 打通，再治漂移，再做长期 hybrid 检索与评测体系。

### 验收结果
- refine 生成位置
  - `TestcaseGenerationServiceImpl.generate()` 中先调 `refineRequirement(requirement)`
  - `refineRequirement()` 内部通过 `TestcasePromptBuilder.buildRefinementPrompt()` + `llmService.generateTestCode()` 生成 refined 文本
- refine 使用位置
  - `knowledgeBaseService.search(refinedRequirement, topK)`
  - `promptBuilder.buildCodeGenerationPrompt(refinedRequirement, ...)`
- refine 暴露情况
  - `TestcaseGenerateResponse` 只有 `javaTestCode/citations/degraded`
  - `TestcaseGenerationResult` 只有 `javaTestCode/citations/degraded`
  - `TestcaseGenerationController` 也未返回 refined 文本
  - 结论：当前接口对调用方不可见
- refine 日志情况
  - `HttpChatCompletionsLLMService` 仅记录 `mode/status/maxTokens/elapsedMs`
  - `service.log` 可见 `TASK_MODE=REQUIREMENT_REFINEMENT` 调用发生，但看不到 refined 文本内容
  - 结论：当前运行时也不可直接追踪 refined 文本本身
- P9 改造方案拍板
  - 阶段 1：补齐爬虫/解析/metadata 落盘与 concrete 命中判定
  - 阶段 2：重建向量文本、引入重排、增加 query 漂移拦截
  - 阶段 3：升级为 hybrid retrieval + 更强知识模型 + 评测体系

### 核心问题

#### P1
- `refined requirement` 对调用方不可见，导致用户无法核验“描述优化”到底把原始需求改成了什么。

#### P1
- 当前 top1 命中的 `DetachDevServerVolume` 仍以弱结构 `ApiMetadata` 返回，导致生成主链路继续报 `TESTCASE_REFERENCE_URL_REQUIRED`。

#### P2
- 检索漂移仍然存在，例如“卸载开发服务器卷”会偏到 `DeleteService`，说明仅靠 description 文本相似度不够。

### 决策
- 当前接受“refine 已存在但不可见”的事实判断，后续若要给用户看，需要单独做接口或结果字段暴露。
- 知识库召回治理顺序固定为：
  - 先做阶段 1：结构化 metadata 打通
  - 再做阶段 2：召回稳定性与漂移治理
  - 阶段 3 暂不展开大改
- 后续子代理统一使用 `gpt-5.4`，不再修改模型。

### 行动项
- P10：输出本轮验收结论。
- 后续执行层：先修 metadata 结构化与 concrete hit 打通，再继续召回稳定性优化。

### 关键证据
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `src/main/java/com/agent/model/testcase/TestcaseGenerateResponse.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationResult.java`
- `src/main/java/com/agent/controller/TestcaseGenerationController.java`
- `src/main/java/com/agent/service/impl/HttpChatCompletionsLLMService.java`
- `.ascend_agent/logs/service.log`

## 2026-03-25 15:58:09 +0800

### 主题
Sprint-1 第二十批并行验收：skill 流程化改写与“卸载系统盘”召回核验

### 参与角色
- P10 主线程：并行分派、主线程复核、最终验收
- P8 执行代理 1：重写 skill 为通用流程资产
- P8 执行代理 2：核验当前向量检索召回结果

### 统一结论
- `huawei-testcase-generation` skill 的主流程已回归通用流程资产，显式补齐了“测试用例描述优化（refine）”步骤，不再把具体 API 场景当默认主路径。
- 当前“卸载系统盘”相关 query 的真实召回结果已核验：
  - `卸载系统盘`：top1 命中 `DetachDevServerVolume`
  - `卸载Lite Server系统盘`：top1 命中 `DetachDevServerVolume`
  - `卸载开发服务器卷`：top1 漂移到 `DeleteService`
- 更关键的问题是：虽然前两条 query 的 top1 已经命中了正确 API，但返回的 metadata 仍是弱结构，`httpMethod/endpoint/requestBody/responseBody/className/methodName/signature` 全为空；因此生成主链路仍会把它判定为 KB miss。
- 已做接口级复核：直接调用 `/api/testcase/generate` 且不带 `referenceUrl`，`requirement=卸载Lite Server系统盘` 时，真实返回仍是 `HTTP 400` + `TESTCASE_REFERENCE_URL_REQUIRED`。这说明当前阻塞点不是“语义完全搜不到”，而是“向量召回结果缺乏可被主链路接受的结构化 metadata”。

### 验收结果
- Skill 验收
  - 结果：通过
  - 主流程已收口为：`requirement -> refine -> retrieval -> referenceUrl fallback -> codegen -> validate -> compile`
  - 已去除把具体 API 场景作为默认主路径的写法
- 检索接口健康
  - `GET /actuator/health`
  - 结果：`HTTP 200`
- 检索结果 1
  - query：`卸载系统盘`
  - top1：`huawei-detachdevservervolume-1288194079`
  - source：`https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html`
  - 描述中可见端点：`DELETE /v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}`
- 检索结果 2
  - query：`卸载Lite Server系统盘`
  - top1：`huawei-detachdevservervolume-1288194079`
  - source：`https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html`
  - 描述中可见端点：`DELETE /v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}`
- 检索结果 3
  - query：`卸载开发服务器卷`
  - top1：`huawei-deleteservice--1488106924`
  - source：`https://support.huaweicloud.com/api-modelarts/DeleteService.html`
  - 结论：发生检索漂移
- 生成链路复核
  - 请求：`POST /api/testcase/generate`，仅传 `requirement=卸载Lite Server系统盘`
  - 结果：`HTTP 400`
  - 错误码：`TESTCASE_REFERENCE_URL_REQUIRED`

### 核心问题

#### P1
- 向量检索虽然已经能在部分 query 上把 `DetachDevServerVolume` 排到 top1，但返回给主链路的 `ApiMetadata` 结构字段为空，导致 `isConcreteKnowledgeHit()` 判定失败，主链路仍然必须依赖 `referenceUrl`。

#### P1
- Query 语义稍有变化就会发生漂移，例如 `卸载开发服务器卷` 被错误召回到 `DeleteService`，说明当前索引文本与召回判定还不够稳。

#### P2
- Skill 虽然已回归流程资产，但示例里仍引用了具体 API URL；它已明确标注为 example，不再阻塞本轮放行，但后续仍可继续去 API 化。

### 决策
- 本轮接受 skill 改写结果。
- 当前检索能力的准确口径为：“部分 query top1 可命中正确 API，但仍不是可被生成主链路直接消费的 concrete hit。”
- 后续要解决“无 `referenceUrl` 也能生成”，必须补齐知识库中 `ApiMetadata` 的结构化字段，而不是只依赖 description 文本相似度。

### 行动项
- P10：输出本轮验收结论。
- 后续执行层：治理知识库 metadata 结构化落盘与检索漂移问题。

### 关键证据
- `.codex/skills/huawei-testcase-generation/SKILL.md`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `POST /api/knowledge/search`
- `POST /api/testcase/generate`

## 2026-03-25 15:42:34 +0800

### 主题
Sprint-1 第十九批架构结论：测试用例生成 skill 应回归流程资产

### 参与角色
- P10 主线程：组织结论并拍板 skill 边界
- P9 方案视角：定义 skill 目录结构与流程边界
- P8 执行视角：评审当前 skill 对执行层的误导点

### 统一结论
- 当前 `huawei-testcase-generation` skill 写偏了，已经从“通用流程资产”滑成了“某个 API 场景的验收样例”。
- skill 的主目标应当是：指导“从测试用例描述到 Java 测试代码生成与验收”的通用流程，而不是固化某个 API 的 referenceUrl、endpoint、错误码或真值。
- `测试用例描述优化` 必须成为 skill 的显式步骤，而不是隐含在实现里。
- 具体 API 的真实返回、真值断言、场景纠偏记录，不应写在 skill 主流程里；这些内容应进入 `meeting.md` 或单独 example 文档。

### P9 结论
- skill 应按“Goal/Inputs/Pre-flight/Refine/Retrieval/Context Build/Codegen/Post-process/Acceptance/Troubleshooting”组织。
- skill 必须定义：KB 命中判定、弱命中处理、referenceUrl 何时强制介入、显式期望字段优先级。
- 验收阶段除编译外，还应记录最终召回并使用的 `apiId/endpoint/httpMethod/source`，防止检索漂移。

### P8 结论
- 当前 skill 最误导执行层的点，是把 `DetachDevServerVolume` 当成主路径，导致执行层会把流程 skill 误当成单 API 脚本。
- 当前 skill 缺少“测试用例描述优化”步骤，执行层很容易直接拿原始 requirement 去检索，造成 KB 命中不稳或命中错 API。
- 当前 skill 里的场景纠偏说明和真值回填，不应该出现在主流程里，否则执行层会误以为每次都要照抄。

### P10 拍板
- skill 立即回归“流程资产”，不再绑定 `DeleteWorkflow`、`DetachDevServerVolume` 等具体场景为默认主路径。
- skill 主流程必须新增“测试用例描述优化”步骤，并与当前实现链路一致：`requirement -> refine -> retrieval -> codegen -> validate -> compile`
- 具体 API 示例可以保留一则，但必须明确标注为 `Example`，不能放在主流程定义里。
- 具体 API 真值、真实错误码、真实响应体，只记录到 `meeting.md` 或独立示例文档，不进入 skill 主流程。

### 行动项
- P10：输出本次讨论结论。
- 后续执行层：按上述结构重写 skill，删除主流程里的单 API 绑定内容。

### 关键证据
- `.codex/skills/huawei-testcase-generation/SKILL.md`
- `docs/TESTCASE_GENERATION_V3_CURRENT.md`

## 2026-03-25 15:32:30 +0800

### 主题
Sprint-1 第十八批范围纠偏：切回 DetachDevServerVolume 正确 API

### 参与角色
- P10 主线程：按用户补充的正确文档页重新对齐 API

### 统一结论
- 用户给的正确页面是 `DetachDevServerVolume`，不是 `DeleteWorkflow`。
- 官方文档已确认正确 URI 为：
  - `DELETE /v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}`
- 我已用真实 token 调 `GET /v1/{project_id}/dev-servers/all` 验证当前项目下确实有 Lite Server 资源，且返回结构同时包含：
  - 顶层 Lite Server `id`
  - 底层 `cloud_server.hps_ecs_id`
  - `volumes[].evs_id`
- 这说明该接口需要的不是普通 ECS ID，而是 Lite Server 实例 ID + 磁盘 EVS ID。

### 关键证据
- `https://support.huaweicloud.com/intl/zh-cn/api-modelarts/DetachDevServerVolume.html`
- `https://support.huaweicloud.com/intl/zh-cn/api-modelarts/ListAllDevServers.html`
- `X-Request-Id: 22faa6d51ad537ba0ccb00817b1580b9`

## 2026-03-25 15:28:08 +0800

### 主题
Sprint-1 第十七批范围澄清：当前链路已偏到 DeleteWorkflow，与“卸载系统盘”不一致

### 参与角色
- P10 主线程：复盘本轮场景漂移原因并收口后续纠偏方向

### 统一结论
- 用户真实业务场景是“卸载系统盘”，不是“删除工作流”。
- 当前之所以跑到 `DELETE /v2/{project_id}/workflows/{workflow_id}`，是因为前序链路长期绑定了 `https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html` 及其 `DeleteWorkflow` 相关验收，导致 skill、设计文档、测试样例都围绕 ModelArts workflow 展开。
- 这属于场景漂移，不是用户需求本身变化。

### 决策
- 之前所有 `DeleteWorkflow` 的真实返回，只能作为一条独立的 ModelArts 场景样例保留，不能再当作“卸载系统盘”用例的验收依据。
- 后续若继续做用户真实需求，必须重新切换到“卸载系统盘”对应的华为云服务与 API 文档，再重新做真实请求验收。

### 关键证据
- `docs/TESTCASE_GENERATION_V3_CURRENT.md`
- `.codex/skills/huawei-testcase-generation/SKILL.md`
- `src/test/java/com/agent/service/testcase/TestcaseGenerationServiceImplTest.java`

## 2026-03-25 15:25:36 +0800

### 主题
Sprint-1 第十六批真实验收：ECS UUID 作为 workflow_id 的返回分支确认

### 参与角色
- P10 主线程：使用用户提供的 ECS UUID 直接验证删除工作流接口真实返回

### 评审范围
- 华为云 ModelArts `GET /v2/{project_id}/workflows`
- 华为云 ModelArts `DELETE /v2/{project_id}/workflows/{workflow_id}`
- `meeting.md`

### 统一结论
- `workflow` 不是 ECS 实例，而是 ModelArts 的工作流资源。
- 用户提供的 `b137d2be-77f0-4fc1-bef7-dc7931993344` 虽然是一个合法 UUID，但在当前项目下不是已存在的 ModelArts workflow。
- 真实接口返回已确认：
  - `GET /workflows?limit=5` 返回 `200` 且 `items=[]`
  - `DELETE /workflows/b137d2be-77f0-4fc1-bef7-dc7931993344` 返回 `400`
  - `error_code=ModelArts.7512`
  - `error_msg=Workflow b137d2be-77f0-4fc1-bef7-dc7931993344 not found`
- 因此当前可以明确区分两类真实分支：
  - 非 UUID 格式：`400 / ModelArts.0104 / uuid4 tag`
  - UUID 格式正确但资源不存在：`400 / ModelArts.7512 / Workflow ... not found`

### 关键证据
- `X-Request-Id: abdb086288832c3ec7289281515167da`
- `X-Request-Id: 0744b21e6cf54932229850bc6f83f4b0`

## 2026-03-25 15:11:06 +0800

### 主题
Sprint-1 第十五批收口：生成 skill 与设计文档回填真实 API 真值

### 参与角色
- P10 主线程：按真实接口验收结果回填 skill 与当前设计基线

### 评审范围
- `.codex/skills/huawei-testcase-generation/SKILL.md`
- `docs/TESTCASE_GENERATION_V3_CURRENT.md`
- `meeting.md`

### 统一结论
- 已将“删除工作流接口的非法 `workflow_id` 场景”从演示值收口为真实值。
- Skill 与当前设计文档已统一回填以下真值：
  - `expectedHttpStatus=400`
  - `expectedErrorCode=ModelArts.0104`
  - `expectedErrorDescription=Invalid parameter, error: Key: '' Error:Field validation for '' failed on the 'uuid4' tag.`
- 后续测试代码生成、skill 使用和验收纪要三处口径已对齐，不再存在“文档写演示值、真实接口回真值”的分叉。

### 行动项
- P10：已完成真实值回填。
- 后续执行层：基于该真值继续修正生成结果断言与验收脚本。

### 关键证据
- `.codex/skills/huawei-testcase-generation/SKILL.md`
- `docs/TESTCASE_GENERATION_V3_CURRENT.md`

## 2026-03-25 15:08:29 +0800

### 主题
Sprint-1 第十四批真实验收：从本机文件读取 token 直连 ModelArts 成功

### 参与角色
- P10 主线程：从 `/root/auth_token.txt` 无损读取 token，直连真实华为云 API

### 评审范围
- 华为云 ModelArts `GET /v2/{project_id}/workflows`
- 华为云 ModelArts `DELETE /v2/{project_id}/workflows/{workflow_id}`
- `meeting.md`

### 统一结论
- 这次从本机文件无损读取 token 后，鉴权已通过，真实 API 已经跑通。
- 真实返回值已经拿到，后续测试用例必须以这次真实结果为准，不再使用猜测值。
- 当前用户给定 `project_id=b1cada1c234f4571a89274cced4861e0` 时，实测结果为：
  - `GET /workflows?limit=1` 返回 `HTTP 200`
  - `DELETE /workflows/invalid-id-format` 返回 `HTTP 400`
  - 错误码：`ModelArts.0104`
  - 错误描述：`Invalid parameter, error: Key: '' Error:Field validation for '' failed on the 'uuid4' tag.`
- 经验事实是：本次在 `cn-southwest-2` 与 `cn-north-4` 两个端点上都得到了相同结果；对当前测试用例场景，`400 / ModelArts.0104 / uuid4 tag` 已可作为真实验收口径。

### 验收结果
- `GET https://modelarts.cn-southwest-2.myhuaweicloud.com/v2/{project_id}/workflows?limit=1`
  - 结果：`HTTP 200`
  - 响应体：`{"total":0,"count":0,"items":[],"default_order":"asc"}`
  - `X-Request-Id: 37a77143f7375708bdfad2c2554cc3d8`
- `DELETE https://modelarts.cn-southwest-2.myhuaweicloud.com/v2/{project_id}/workflows/invalid-id-format`
  - 结果：`HTTP 400`
  - `error_code=ModelArts.0104`
  - `error_msg=Invalid parameter, error: Key: '' Error:Field validation for '' failed on the 'uuid4' tag.`
  - `X-Request-Id: 2b87d8da6774da13e626178a8aff26fc`
- `GET https://modelarts.cn-north-4.myhuaweicloud.com/v2/{project_id}/workflows?limit=1`
  - 结果：`HTTP 200`
  - 响应体：`{"total":0,"count":0,"items":[],"default_order":"asc"}`
  - `X-Request-Id: 0863c6b98116a3f9c455bacb7ebfda7e`
- `DELETE https://modelarts.cn-north-4.myhuaweicloud.com/v2/{project_id}/workflows/invalid-id-format`
  - 结果：`HTTP 400`
  - `error_code=ModelArts.0104`
  - `error_msg=Invalid parameter, error: Key: '' Error:Field validation for '' failed on the 'uuid4' tag.`
  - `X-Request-Id: 506bdaed69fcf486c8dc7cfc0ce0330f`

### 核心问题

#### P1
- 之前通过聊天粘贴传 token 的方式不可靠，已经实锤会把排障方向带偏；敏感 token 必须走本机文件或环境变量注入。

#### P2
- 当前生成链路里历史使用过的演示值 `400 + 示例错误描述` 与真实上游返回不一致，需要全部以本次真实错误码和错误描述回填。

### 决策
- 立即以 `400 / ModelArts.0104 / uuid4 tag` 作为“无效 workflow_id”场景的真实用例期望。
- 后续涉及华为云真实接口验证时，统一使用本机文件或环境变量注入 token，不再经聊天明文传递。

### 行动项
- P10：已完成真实 API 直连验收并记录真值。
- 后续执行层：按本次真实返回修正生成 skill、提示词和用例断言。

### 关键证据
- `37a77143f7375708bdfad2c2554cc3d8`
- `2b87d8da6774da13e626178a8aff26fc`
- `0863c6b98116a3f9c455bacb7ebfda7e`
- `506bdaed69fcf486c8dc7cfc0ce0330f`

## 2026-03-25 14:32:56 +0800

### 主题
Sprint-1 第十三批真实验收：用户提供的 X-Auth-Token 直连 ModelArts

### 参与角色
- P10 主线程：使用用户提供的 token 和 `project_id` 直接打真实华为云 API

### 评审范围
- 华为云 ModelArts `GET /v2/{project_id}/workflows`
- 华为云 ModelArts `DELETE /v2/{project_id}/workflows/{workflow_id}`
- `meeting.md`

### 统一结论
- 已按用户要求直接使用提供的 token 和 `project_id=b1cada1c234f4571a89274cced4861e0` 调真实接口。
- 真实结果不是 `400`，而是统一返回未鉴权：
  - `HTTP 401 Unauthorized`
  - `error_code=APIGW.0301`
  - `error_msg=Incorrect IAM authentication information: decrypt token fail`
- 这说明当前提供的 token 没有通过网关解密校验，当前阻塞点是 token 本身不可用，而不是接口参数校验。
- 虽然用户提供的 `project_id` 对应区域信息显示为 `cn-southwest-2`，但无论请求 `cn-southwest-2` 还是 `cn-north-4` 端点，结果都一致为 `decrypt token fail`，因此当前首要问题仍是 token 有效性，而不是区域路由。

### 验收结果
- `GET https://modelarts.cn-southwest-2.myhuaweicloud.com/v2/{project_id}/workflows?limit=1`
  - 结果：`401 / APIGW.0301 / decrypt token fail`
  - `X-Request-Id: daed988881d0e142f570c98aad9ddf56`
- `DELETE https://modelarts.cn-southwest-2.myhuaweicloud.com/v2/{project_id}/workflows/invalid-id-format`
  - 结果：`401 / APIGW.0301 / decrypt token fail`
  - `X-Request-Id: 6309c90a5885a41a0d7e51a06f88b3b0`
- `GET https://modelarts.cn-north-4.myhuaweicloud.com/v2/{project_id}/workflows?limit=1`
  - 结果：`401 / APIGW.0301 / decrypt token fail`
  - `X-Request-Id: d6a62e33b7d345a40a6e4d3d808f75fa`
- `DELETE https://modelarts.cn-north-4.myhuaweicloud.com/v2/{project_id}/workflows/invalid-id-format`
  - 结果：`401 / APIGW.0301 / decrypt token fail`
  - `X-Request-Id: 9b12de738b88b61f7f38dc239e4a5932`

### 核心问题

#### P1
- 当前 token 通过不了华为云网关解密校验，后续所有业务接口都会先卡死在鉴权层，看不到真实业务错误码。

#### P1
- 用户通过聊天直接粘贴了敏感 token，存在复制截断、换行污染或转义损坏的风险；即使 token 本身原本有效，经过聊天链路后也可能已经不可直接用作请求头。

### 决策
- 下一轮真实验收不再复用这份聊天中传递的 token。
- 统一改为从本机 shell 或文件中无损读取新 token，再直接请求真实接口。

### 行动项
- P10：已完成用户 token 的真实接口验收并记录结果。
- 后续执行层：要求用户重新生成并以无损方式注入 token，再继续验证真实业务分支。

### 关键证据
- `daed988881d0e142f570c98aad9ddf56`
- `6309c90a5885a41a0d7e51a06f88b3b0`
- `d6a62e33b7d345a40a6e4d3d808f75fa`
- `9b12de738b88b61f7f38dc239e4a5932`

## 2026-03-25 14:23:34 +0800

### 主题
Sprint-1 第十二批调研记录：X-Auth-Token 与 AK/SK 鉴权关系澄清

### 参与角色
- P10 主线程：核对官方鉴权文档，澄清 token 链路与签名链路边界

### 评审范围
- 华为云 IAM 鉴权文档
- 华为云 ModelArts 认证鉴权文档
- 本项目当前测试用例生成与验收方式

### 统一结论
- `X-Auth-Token` 链路与 `AK/SK` 链路不是一回事。
- 如果目标是拿到请求头 `X-Auth-Token`，官方路径是：使用 IAM 用户名 + 密码 调 `POST /v3/auth/tokens`，再从响应头读取 `X-Subject-Token`。
- `AK/SK` 是另一套“签名认证”方式，通常用于直接对业务请求做签名；走这条链路时，不需要先去换 `X-Auth-Token`。
- 对当前项目现状，代码和生成 skill 都是围绕 `HUAWEICLOUD_AUTH_TOKEN` 展开的，所以当前最短路径仍然是“用户名密码取 token”，不是直接上 `AK/SK`。

### 决策
- 当前阶段继续使用 `X-Auth-Token` 路线完成真实 API 验收。
- 若后续决定统一改成 `AK/SK` 签名认证，需要同步调整测试代码模板、skill 和验收链路，不能只改配置名不改实现。

### 关键证据
- 华为云 IAM 文档：`POST /v3/auth/tokens` 返回 `X-Subject-Token`
- 华为云 ModelArts 文档：支持 `X-Auth-Token` 与签名认证两类鉴权方式
## 2026-03-25 11:50:49 +0800

### 主题
Sprint-1 第十一批调研记录：X-Auth-Token 获取路径收口

### 参与角色
- P10 主线程：核对官方 IAM 鉴权文档，收口可直接执行的 token 获取流程

### 评审范围
- 华为云 IAM Token 获取文档
- 华为云 ModelArts 认证鉴权文档
- 本项目测试用例生成链路的华为云配置约定

### 统一结论
- `X-Auth-Token` 不是静态配置项，而是先调用 IAM `POST /v3/auth/tokens` 获取，随后从响应头 `X-Subject-Token` 读取出来，再作为业务 API 请求头 `X-Auth-Token` 使用。
- 对当前 ModelArts `cn-north-4` 场景，推荐直接申请“项目级 token”，再把同一个 `project_id` 同时用于 URL 路径 `/v2/{project_id}/...`。
- 当前项目与该流程匹配的运行时配置键已经存在：
  - 环境变量：`HUAWEICLOUD_AUTH_TOKEN`、`HUAWEICLOUD_PROJECT_ID`、`HUAWEICLOUD_BASE_URL`
  - 系统属性：`hwcloud.auth.token`、`hwcloud.project.id`、`hwcloud.base.url`

### 验收结果
- 官方流程已收口为 3 步：
  - 第一步：准备 `domain name / IAM user / password / region(project scope) / project_id`
  - 第二步：调用 IAM `POST /v3/auth/tokens`
  - 第三步：从响应头取 `X-Subject-Token`，作为后续业务请求头 `X-Auth-Token`
- 对当前项目的最小落地方式已明确：
  - `export HUAWEICLOUD_AUTH_TOKEN=<X-Subject-Token>`
  - `export HUAWEICLOUD_PROJECT_ID=<你的项目ID>`
  - `export HUAWEICLOUD_BASE_URL=https://modelarts.cn-north-4.myhuaweicloud.com`

### 核心问题

#### P1
- 如果当前手里只有“华为云账号”登录态，而没有可编程访问的 IAM 用户、密码和区域项目信息，就无法稳定走自动化 token 获取链路。

#### P1
- `X-Auth-Token` 有有效期，不能写死在模板里长期复用；正式接入时要么做运行前刷新，要么在测试前人工更新一次。

### 决策
- 后续真实 API 验收统一按“先取 token，再调业务接口”执行，不再直接裸调业务 API。
- 下一次若要验证 `400` 业务分支，前置条件明确为：先拿到有效 `X-Auth-Token` 与正确 `project_id`。

### 行动项
- P10：已完成 token 获取路径调研并记录纪要。
- 后续执行层：拿到用户真实 IAM 信息后，先跑一次获取 token，再继续打 ModelArts 真实请求。

### 关键证据
- 华为云 IAM 文档：`POST /v3/auth/tokens` 响应头返回 `X-Subject-Token`
- 华为云 ModelArts 文档：业务请求头使用 `X-Auth-Token`

## 2026-03-25 11:45:00 +0800

### 主题
Sprint-1 第十批交付验收：华为云真实 API 直连核验

### 参与角色
- P10 主线程：按用户要求跳过演示假设，直接核验真实华为云 API 返回

### 评审范围
- 华为云 ModelArts API：`DELETE /v2/{project_id}/workflows/{workflow_id}`
- `meeting.md`

### 统一结论
- 已按要求直接调用真实华为云 API，而不是继续围绕本地生成演示值打转。
- 当前这次真实直连在“未携带 IAM 鉴权头”的前提下，返回的是未鉴权错误，不是业务参数校验错误。
- 真实返回已确认：
  - HTTP 状态码：`401 Unauthorized`
  - 错误码：`APIGW.0301`
  - 错误描述：`Incorrect IAM authentication information: x-auth-token not found`
- 因此，前面本地生成链路中使用的 `400 + 示例错误描述` 只能算演示输入，不代表华为云真实返回口径。

### 验收结果
- 真实 API 请求
  - 命令：
    - `curl -sS -i -m 20 -X DELETE 'https://modelarts.cn-north-4.myhuaweicloud.com/v2/00000000000000000000000000000000/workflows/invalid-id-format'`
  - 结果：通过，拿到真实响应
- 真实响应摘要
  - `HTTP/1.1 401 Unauthorized`
  - `X-Request-Id: 0b67bf0abe6d9a8844030ded87109c9f`
  - `error_code=APIGW.0301`
  - `error_msg=Incorrect IAM authentication information: x-auth-token not found`

### 核心问题

#### P1
- 当前项目之前围绕 `400 + 示例错误描述` 做了生成演示，但没有先验证真实上游返回，导致“演示合同”和“真实接口行为”发生偏差。

#### P1
- 这次请求没有带 `X-Auth-Token`，所以当前只验证到了“未鉴权失败”分支；如果目标是校验 `400` 或更细的业务错误描述，下一步必须补真实鉴权，再打一次已鉴权请求。

### 决策
- 从这一刻起，测试用例的默认真实口径以这次直连结果为准：未鉴权场景先落 `401 / APIGW.0301 / x-auth-token not found`。
- 只有在拿到有效 IAM 鉴权后，才继续定义“已鉴权但参数非法”场景对应的 `400` 或其他业务错误码断言。

### 行动项
- P10：已完成真实 API 直连核验并记录纪要。
- 后续执行层：基于这次真实返回修正测试用例场景拆分，避免再把演示值当成真实值。
- 若要继续打 `400` 业务分支：补齐真实 `X-Auth-Token` 和有效 `project_id` 后再次直连验收。

### 关键证据
- 真实响应 `X-Request-Id: 0b67bf0abe6d9a8844030ded87109c9f`
- 华为云真实响应体：`{"error_msg":"Incorrect IAM authentication information: x-auth-token not found","error_code":"APIGW.0301","request_id":"0b67bf0abe6d9a8844030ded87109c9f"}`

## 2026-03-25 10:46:42 +0800

### 主题
Sprint-1 第九批交付验收：华为云测试用例生成 skill 建设与一次模拟请求验收

### 参与角色
- P10 主线程：定义 skill 边界、验收 skill 内容并完成一次真实模拟请求
- P8 执行层：生成 skill 并提交

### 评审范围
- `.codex/skills/huawei-testcase-generation/SKILL.md`
- `meeting.md`

### 统一结论
- 本轮 skill 已达到放行条件。
- 新增 skill `huawei-testcase-generation` 已覆盖从测试用例描述、参考链接、显式期望、华为云鉴权变量，到本地生成接口调用和 Java 21 编译验收的完整最小流程。
- Skill 内容与当前项目实现合同一致，未沿用旧版输入结构。
- 按 skill 中的最小 curl 示例实际调用 `/api/testcase/generate` 后，服务返回 `HTTP 200`，并返回包含 `400` 断言和错误描述断言的 Java 测试代码。

### 验收结果
- P8 skill 提交
  - `ea3645e docs(skill): add huawei testcase generation skill`
- skill 内容核对
  - 结果：通过
  - 已覆盖：触发场景、输入合同、鉴权变量、标准流程、失败规则、最小 curl 示例、最小编译验收示例
- 服务健康检查
  - `curl -sS -i -m 5 http://127.0.0.1:8080/actuator/health`
  - 结果：`HTTP 200`
- 按 skill 模拟请求
  - `POST /api/testcase/generate`
  - 请求包含：
    - `requirement`
    - `referenceUrl=https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html`
    - `expectedHttpStatus=400`
    - `expectedErrorDescription=示例错误描述`
  - 结果：`HTTP 200`
- 返回结果摘要
  - `has_java_code=1`
  - `degraded=true`
  - `citations=1`
  - `has_status_400=1`
  - `has_desc=1`
  - `has_placeholder=0`
  - `has_todo=0`
- Java 21 实编译验证
  - 结果：按类名 `DeleteWorkflowTest` 落盘后 `javac_exit=0`

### 核心问题

#### P1
- 当前 skill 已把“如何生成和验收”文档化，但它依赖现有生成链路的运行时性能；本轮模拟请求虽然通过，仍然用了分钟级等待，后续要继续治理时延。

#### P2
- 当前模拟请求中的 `expectedErrorDescription=示例错误描述` 仍是演示值，不是最终业务值；skill 能力已备好，但正式验收仍依赖用户给出最终错误描述。

### 决策
- 以后当用户要“根据测试用例描述 + 华为云 API 文档生成 Java 测试代码”时，优先使用该 skill，不再每次临时口头拼流程。
- 该 skill 的标准演示路径保留为：`expectedHttpStatus=400` + 显式错误描述 + Java 21 编译验收。

### 行动项
- P10：输出本轮 skill 验收结论，并把纪要推远端。
- 后续执行层：在用户给出正式错误描述后，再按 skill 跑一次正式业务值演示。

### 关键证据
- `.codex/skills/huawei-testcase-generation/SKILL.md`
- `/tmp/testcase_skill_demo.out`
- `/tmp/DeleteWorkflowTest.java`

## 2026-03-25 10:27:51 +0800

### 主题
Sprint-1 第八批交付验收：测试用例生成支持显式错误描述并完成一次真实生成演示

### 参与角色
- P10 主线程：定义“400 + 错误描述”验收标准并完成最终验收
- P8 执行层：补齐错误描述合同、单测与本地提交

### 评审范围
- `src/main/java/com/agent/model/testcase/TestcaseGenerateRequest.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationRequest.java`
- `src/main/java/com/agent/controller/TestcaseGenerationController.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `src/main/java/com/agent/service/testcase/TestcasePromptBuilder.java`
- `src/test/java/com/agent/controller/TestcaseGenerationControllerTest.java`
- `src/test/java/com/agent/service/testcase/TestcaseGenerationServiceImplTest.java`
- `src/test/java/com/agent/service/testcase/TestcasePromptBuilderTest.java`
- `meeting.md`

### 统一结论
- 本轮交付达到放行条件。
- 测试用例生成请求已新增可选字段 `expectedErrorDescription`，并与既有 `expectedHttpStatus` / `expectedErrorCode` 一起贯通到 controller、service 与 prompt。
- Prompt 已新增明确约束：显式提供错误描述时必须优先使用；未提供且上下文不明确时，不允许臆造具体错误描述。
- 在真实运行态下，使用演示输入 `expectedHttpStatus=400`、`expectedErrorDescription=示例错误描述` 调用生成接口后，返回的 Java 代码已包含 `400` 断言和描述断言，并通过 Java 21 实编译。
- 当前这次真实运行是“合同打通演示通过”，其中 `示例错误描述` 只是演示值，不代表最终业务口径已经冻结。

### 验收结果
- P8 实现提交
  - `6fafdcc feat(testcase): add expected error description contract`
- `mvn -q -Dtest=TestcaseGenerationControllerTest,TestcaseGenerationServiceImplTest,TestcasePromptBuilderTest test`
  - 结果：通过
- `mvn -q -DskipTests package`
  - 结果：通过
- 服务重启
  - `bash scripts/stop_service.sh`
  - `bash scripts/start_service.sh`
  - 结果：通过；当前常驻进程 PID `3366070`
- 健康检查
  - `curl -sS -i -m 5 http://127.0.0.1:8080/actuator/health`
  - 结果：`HTTP 200`
- 真实生成演示
  - `POST /api/testcase/generate`
  - 请求包含：
    - `referenceUrl=https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html`
    - `expectedHttpStatus=400`
    - `expectedErrorDescription=示例错误描述`
  - 结果：`HTTP 200`
- 返回代码静态核验
  - 结果：`has_java_code=1`、`degraded=true`、`has_status_400=1`、`has_desc=1`
- Java 21 实编译验证
  - 结果：`javac_exit=0`
- LLM 真实耗时
  - 需求优化：`elapsedMs=39978`
  - 代码生成：`elapsedMs=153456`

### 核心问题

#### P1
- 当前真实生成仍然耗时较长，总链路约 193 秒；虽然本轮在放宽客户端超时后能跑通，但默认 180 秒超时下会被客户端判为失败，说明性能边界仍未收口。

#### P2
- 当前“错误描述”只完成了输入合同、prompt 约束和一次演示性实跑，还没有做生成后代码级强校验，因此最终是否严格使用用户给定描述，仍主要依赖模型遵守约束。

### 决策
- 在用户给出最终业务描述前，系统先以 `expectedErrorDescription` 作为可选显式输入保留能力，不提前把演示值固化为正式口径。
- 异常类测试用例以后优先以 `expectedHttpStatus + expectedErrorDescription` 驱动生成，不再只靠 requirement 文本隐式表达。

### 行动项
- P10：输出本轮验收结论，并把纪要纳入历史记录。
- 后续执行层：在用户给出正式错误描述后，补一次真实业务值验收；并单独治理生成链路超时边界。

### 关键证据
- `.ascend_agent/logs/service.log`
- `/tmp/testcase_400_desc.out`
- `/tmp/DeleteWorkflowValidationTest.java`

## 2026-03-24 20:31:43 +0800

### 主题
Sprint-1 第七批交付验收：测试用例生成显式期望合同收口

### 参与角色
- P10 主线程：定义“不依赖用户立即给错误码”的收口范围并完成最终验收
- P8 执行层：落地请求合同、入口校验、prompt 约束与定向单测

### 评审范围
- `src/main/java/com/agent/model/testcase/TestcaseGenerateRequest.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationRequest.java`
- `src/main/java/com/agent/controller/TestcaseGenerationController.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `src/main/java/com/agent/service/testcase/TestcasePromptBuilder.java`
- `src/test/java/com/agent/controller/TestcaseGenerationControllerTest.java`
- `src/test/java/com/agent/service/testcase/TestcaseGenerationServiceImplTest.java`
- `src/test/java/com/agent/service/testcase/TestcasePromptBuilderTest.java`
- `meeting.md`

### 统一结论
- 本轮交付已达到放行条件。
- 测试用例生成入口已补齐两项可选显式期望：`expectedHttpStatus`、`expectedErrorCode`，后续用户明确错误码时不需要再改链路合同。
- Controller 层已对 `expectedHttpStatus` 做 `100-599` 范围校验，并把空白 `expectedErrorCode` 归一化为 `null`。
- Service 层已把显式期望透传到生成 prompt；prompt 已明确要求“显式期望优先、未提供且上下文不明确时禁止臆造具体状态码/错误码”。
- 当前通过口径是“合同与约束收口通过”，不是“真实错误码执行验收通过”。

### 验收结果
- 代码级验收
  - 结果：通过；P8 提交已真实落到主仓，提交为 `afcc152`、`5e52ce1`
- `mvn -q -Dtest=TestcaseGenerationControllerTest,TestcaseGenerationServiceImplTest,TestcasePromptBuilderTest test`
  - 结果：通过
- `mvn -q -DskipTests package`
  - 结果：通过
- 验收点核对
  - request model 已支持 `expectedHttpStatus`、`expectedErrorCode`
  - controller 已覆盖非法 `expectedHttpStatus` 的 `400` 拒绝
  - blank `expectedErrorCode` 已归一化为 `null`
  - prompt 已包含显式期望，并包含“未提供时不要臆造”的约束
  - service 已把显式期望透传给生成 prompt

### 核心问题

#### P1
- 当前只是把“用户显式给值时如何约束模型”这层合同补齐，还没有在生成后代码检查层强制校验“生成结果是否真的使用了显式期望”；这一层仍依赖模型遵守 prompt。

#### P2
- 本轮没有做 HTTP 接口级 E2E 回归，因此不能把这次放行表述成“真实错误码场景已验证通过”；当前准确口径仍然是“合同层、单测层、打包层通过”。

### 决策
- 以后异常类测试用例生成优先接受用户显式提供的 `expectedHttpStatus` / `expectedErrorCode`，不再把具体错误码交给模型猜。
- 在用户明确错误码之前，当前链路先保持“合同已备好、生成约束已收口”的状态，不提前冻结具体断言值。

### 行动项
- P10：对本轮放行结果做结论输出，并把纪要纳入历史记录。
- 后续执行层：在用户给出明确错误码后，补做真实执行级验收，并视结果决定是否追加生成后代码级强校验。

### 关键证据
- `git log --oneline -2`
- `src/test/java/com/agent/controller/TestcaseGenerationControllerTest.java`
- `src/test/java/com/agent/service/testcase/TestcaseGenerationServiceImplTest.java`
- `src/test/java/com/agent/service/testcase/TestcasePromptBuilderTest.java`

## 2026-03-24 20:02:55 +0800

### 主题
Sprint-1 第六批最终验收：测试用例生成真实闭环跑通

### 参与角色
- P10 主线程：以“可回包、可编译、可追溯”为唯一放行标准完成最终验收

### 评审范围
- `scripts/start_service.sh`
- `src/main/java/com/agent/service/LLMPromptMarkers.java`
- `src/main/java/com/agent/service/impl/HttpChatCompletionsLLMService.java`
- `src/main/java/com/agent/service/testcase/GeneratedTestcasePostProcessor.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `src/main/java/com/agent/service/testcase/TestcasePromptBuilder.java`
- `src/test/java/com/agent/service/impl/HttpModelServiceTest.java`
- `src/test/java/com/agent/service/testcase/GeneratedTestcasePostProcessorTest.java`
- `src/test/java/com/agent/service/testcase/TestcaseGenerationServiceImplTest.java`
- `meeting.md`

### 统一结论
- 当前服务已经按 Java 21 真实路径运行，`8080` 常驻进程不再显示为历史 Java 8 软链路径。
- 无 `referenceUrl` 的失败路径已收口为“先查知识库，未命中即直返 400”，不再被 LLM 调用拖到超时。
- 有 `referenceUrl` 的成功路径已经在真实运行态下返回 `HTTP 200`，并完成 Java 21 编译验证，达到本轮“通过”标准。
- LLM 调用链已按任务类型收口 token 上限：需求优化走 512，代码生成走 1536，避免两次 4096 叠加导致接口长时间无响应。
- 生成结果已增加语法解析、占位符重写与 TODO/placeholder 拦截，当前返回代码无伪造凭证占位符。

### 验收结果
- `mvn -q -Dtest=HttpModelServiceTest,GeneratedTestcasePostProcessorTest,TestcaseGenerationServiceImplTest,TestcaseGenerationControllerTest,TestcaseGenerationControllerAdviceTest test`
  - 结果：通过
- `mvn -q -DskipTests package`
  - 结果：通过
- `bash scripts/start_service.sh`
  - 结果：通过；当前常驻进程 PID `3024796`
- 运行态核验
  - `ps -p 3024796 -o pid=,lstart=,args=`
  - 结果：Java 启动路径为 `/usr/lib/jvm/java-21-openjdk-amd64/bin/java`
- 健康检查
  - `curl -sS -i -m 10 http://127.0.0.1:8080/actuator/health`
  - 结果：`HTTP 200`
- 失败路径验收
  - `POST /api/testcase/generate` 仅传 `requirement`
  - 结果：`HTTP 400`，错误码 `TESTCASE_REFERENCE_URL_REQUIRED`
- 成功路径验收
  - `POST /api/testcase/generate` 传 `requirement + referenceUrl=https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html`
  - 结果：`HTTP 200`
- 生成结果静态检查
  - 结果：`has_placeholder=0`、`has_auth_env=1`、`has_project_env=1`、`degraded=true`
- Java 21 实编译验证
  - 结果：按生成类名落盘后 `javac_exit=0`
- LLM 真实耗时
  - 需求优化：`elapsedMs=48366`
  - 代码生成：`elapsedMs=119833`

### 核心问题

#### P1
- 当前知识库向量命中仍存在“只命中 apiId、取不回完整 metadata”的退化结果，导致主链路经常落到 `referenceUrl` 降级模式；这次虽然已通过，但知识库命中质量仍是后续必须治理的主问题。

#### P2
- 远端 LLM 实际耗时仍然偏高，只是本轮通过调用层 token 限制把总耗时压回到接口可接受区间；如果后续 prompt 继续膨胀或模型切换，仍可能再次逼近超时边界。

### 决策
- 测试用例生成链路以后统一按“400 明确失败 + 200 返回代码可编译”验收，不再接受单纯 `HTTP 200` 作为成功口径。
- 任务类型感知的 token 上限保留在服务端实现层，不要求用户每次手工调配置试错。

### 行动项
- P10：提交并推送本轮最终收口代码与会议纪要。
- 后续执行层：专项治理知识库 metadata 缺失和向量命中退化问题。

### 关键证据
- `.ascend_agent/logs/service.log`
- `/tmp/testcase_success.out`
- `/tmp/DeleteNonExistentWorkflowTest.java`

## 2026-03-24 19:34:36 +0800

### 主题
Sprint-1 第六批交付验收：测试用例生成链路按“可用结果”收口

### 参与角色
- P10 主线程：以“生成结果可编译可使用”为最终放行标准完成验收

### 评审范围
- `src/main/java/com/agent/service/testcase/GeneratedTestcasePostProcessor.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `src/main/java/com/agent/service/testcase/TestcasePromptBuilder.java`
- `src/test/java/com/agent/service/testcase/GeneratedTestcasePostProcessorTest.java`
- `src/test/java/com/agent/service/testcase/TestcaseGenerationServiceImplTest.java`
- `meeting.md`

### 统一结论
- 测试用例生成链路的验收标准从“HTTP 200”升级为“返回的 Java 测试代码可落地使用”。
- 当前主链路已补齐最小可交付收口：LLM 输出会经过语法解析、常见凭证占位符重写、TODO/placeholder 拦截，再返回给调用方。
- 当知识库无命中且用户未提供 `referenceUrl` 时，服务会明确返回 `TESTCASE_REFERENCE_URL_REQUIRED`，不再在缺少接口上下文时硬生成错误代码。
- 在提供参考链接的降级路径上，当前生成结果已验证为无占位符、通过 Java 21 实编译，达到本轮放行条件。

### 验收结果
- `mvn -q -Dtest=GeneratedTestcasePostProcessorTest,TestcaseGenerationServiceImplTest,TestcaseGenerationControllerTest,TestcaseGenerationControllerAdviceTest test`
  - 结果：通过
- `POST /api/testcase/generate`，仅传 `requirement`
  - 结果：`HTTP 400`，错误码 `TESTCASE_REFERENCE_URL_REQUIRED`
- `POST /api/testcase/generate`，传 `requirement + referenceUrl=https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html`
  - 结果：`HTTP 200`，返回 Java 测试类代码，`degraded=true`
- 生成结果静态检查
  - 结果：`has_placeholder=0`、`has_auth_env=2`、`has_project_env=2`
- Java 21 实编译验证
  - 结果：`javac_exit=0`
- 运行态健康检查
  - `curl -sS -i -m 10 http://127.0.0.1:8080/actuator/health`
  - 结果：通过

### 核心问题

#### P1
- 旧链路只证明“模型能返回字符串”，没有证明返回内容能被项目实际消费；如果不做后处理和编译级验收，测试代码很容易夹带 TODO、假 token 或 projectId 占位符，形成伪成功。

#### P2
- 当前“通过”口径仍是编译级可用，不包含携带真实华为云凭证执行远端接口；这一层需要后续单独建设可控的集成测试环境与密钥注入机制，不能和当前生成功能验收混为一谈。

### 决策
- 以后测试用例生成链路的默认验收口径统一为：失败路径明确报错，成功路径返回的 Java 代码至少要通过占位符校验和 Java 21 编译验证。
- 若知识库无命中且未提供 `referenceUrl`，直接报错并要求补充链接，不允许生成猜测性代码。

### 行动项
- P10：提交并推送本轮“可用结果收口”代码与会议纪要。
- 后续执行层：继续推进知识库命中率与真实集成测试环境建设，但不阻塞本轮交付。

### 关键证据
- `src/main/java/com/agent/service/testcase/GeneratedTestcasePostProcessor.java`
- `src/main/java/com/agent/service/testcase/TestcaseGenerationServiceImpl.java`
- `src/main/java/com/agent/service/testcase/TestcasePromptBuilder.java`
- `src/test/java/com/agent/service/testcase/GeneratedTestcasePostProcessorTest.java`
- `src/test/java/com/agent/service/testcase/TestcaseGenerationServiceImplTest.java`

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

## 2026-03-24 11:35:52 +0800

### 主题
P10 与 P9 对齐第三批“真正应用”方案，并拍板最小闭环路线

### 参与角色
- P10 主线程：定第三批方向、边界和入口策略
- P9：提供候选方案、执行顺序与风险判断

### 评审范围
- 当前已完成能力：知识库搜索、华为云 API 抓取、一层下钻、统一错误体、弱一致降级、运行态合同
- 第三批候选场景：API 问答助手、API 变更影响分析、华为云 API 清单生成器

### 统一结论
- 第三批不再继续抠基础设施，而是正式进入“真正应用”的最小闭环。
- P10 选择方案 A：`API 问答助手` 作为第三批主路线。
- 第三批目标是把现有知识库能力变成一个可演示的用户场景：用户输入自然语言问题，系统返回可调用 API、核心说明和源链接。
- 第三批仍然不是“完整主 Agent 收编”，禁止默认滑向 planner/workflow/自动执行。

### 核心问题

#### P1
- 如果第三批继续只做底层增强，项目会继续有“能力存在但没有应用闭环”的问题，无法形成可展示价值。
- 如果第三批直接跳到完整主 Agent，会重新引入边界失控，前两批刚建立的 stopline 会被绕过。

#### P2
- 方案 B（影响分析）和方案 C（清单生成）都能做，但都不如方案 A 更接近“立刻可演示、复用现有能力、无需新增前置系统”。
- 第三批需要一个明确用户入口；如果只停留在 curl，演示成本高，应用感不足。

### 决策
- 第三批路线拍板为：`API 问答助手`。
- 允许新增一个轻量用户入口作为第三批展示面：
  - 可选轻量前端页面或静态站点
  - 只能消费现有 `/api/knowledge/*`，不允许为此新开主 Agent 风格后端 API
- 明确不做：
  - 不做自动调用华为云 API
  - 不做工具执行
  - 不做 planner/workflow/orchestrator
  - 不做多步任务编排
  - 不做新的存储或中间件接入

### 行动项
- 负责人：P9 基于“API 问答助手”路线拆第三批任务包，优先形成最小展示闭环。
- 负责人：P10 后续只验三件事：用户入口是否成立、问答结果是否可验证、是否越过收编红线。
- 负责人：方案 B（影响分析）与方案 C（清单生成器）保留为后续批次备选，不在本批先做。

### 关键证据
- P9 候选方案会前材料：方案 A / B / C 对比
- 第三批允许复用能力：`/api/knowledge/search`、华为云抓取链路、运行态 stopline、弱一致降级

## 2026-03-24 11:40:00 +0800

### 主题
P10 纠偏第三批方向，废弃“API 问答助手”，改为“测试用例描述 -> Java 测试代码生成”

### 参与角色
- P10 主线程：纠偏方向、重新拍板第三批目标
- P9：基于真实目标提供修正版方案

### 评审范围
- 第三批真实产品目标
- 新增最小用户入口与最小产品闭环
- 与现有知识库、抓取、LLM 通道的复用关系

### 统一结论
- 之前拍板的“API 问答助手”方向作废，不再作为第三批执行目标。
- 第三批正确目标改为：用户输入测试用例需求描述，并可选提供参考链接，系统输出对应的 `Java` 测试用例代码。
- 第三批推荐方案定为：`RAG 测试用例生成（单次请求，非编排）`。

### 核心问题

#### P1
- 如果继续沿“问答助手”推进，会偏离真实产品目标，导致第三批成果与用户诉求不一致。
- 第三批必须把“生成 Java 测试代码”作为核心产物，而不是检索结果列表。

#### P2
- 方案可以复用当前知识库和可选参考链接抓取，但不能默认滑向完整 planner/workflow/orchestrator。
- 需要一个最小的新入口承载“生成”能力，否则只能把生成逻辑强塞进现有搜索接口，职责会继续混乱。

### 决策
- 第三批正式目标拍板为：`测试用例描述 + 可选参考链接 -> Java 测试用例代码`。
- 允许新增唯一一个轻量生成端点：`POST /api/testcase/generate`。
- 返回体最小标准拍板如下：
  - `javaTestCode`
  - `citations`
  - `degraded`
- 明确不做：
  - 不做 planner/workflow/orchestrator
  - 不做自动执行测试
  - 不做工程写文件落盘
  - 不做 PR 自动提交
  - 不引入新存储/中间件

### 行动项
- 负责人：P9 基于 `RAG 测试用例生成` 路线拆第三批任务包，先打通最小闭环。
- 负责人：P10 后续只验三件事：输入是否贴合测试需求、输出是否为可用 Java 测试代码、是否越过编排收编红线。

### 关键证据
- P9 修正版方案：`RAG 测试用例生成（单次请求，非编排）`
- 复用能力：知识库搜索、可选参考链接抓取、现有 LLM 通道、运行态合同

## 2026-03-24 11:43:21 +0800

### 主题
P10 再次收口第三批生成策略：无命中且无 URL 直接报错，描述优化与代码生成统一走自定义 LLM

### 参与角色
- P10 主线程：补充硬规则，避免第三批生成策略继续漂移
- P9：根据硬规则修正第三批执行框架

### 评审范围
- 知识库未命中场景的降级策略
- 生成链路中 LLM 的职责与实现方式
- 第三批最小产品闭环的错误语义

### 统一结论
- 当知识库未命中对应 API 且用户未提供 `referenceUrl` 时，系统直接返回异常，不生成任何代码。
- 此类异常的目标不是“尽量给点骨架”，而是明确要求用户补充 `URL` 后再生成。
- 第三批中“测试用例描述优化”和“Java 测试代码生成”两个步骤，统一由配置文件中的自定义 `LLM` 实现。

### 核心问题

#### P1
- 在无命中且无 URL 的场景下继续生成代码，会把“缺少证据”伪装成“可生成”，最终产出不可信测试代码。
- 如果描述优化和代码生成分成两套生成机制，后续提示词、质量和故障排查都会割裂。

#### P2
- 知识库检索仍然是前置增强能力，但不再承担“兜底生成”的职责。
- 可选参考链接仍保留：若知识库未命中但用户给了 `referenceUrl`，允许抓取该链接作为临时上下文再走生成。

### 决策
- 第三批错误语义补充拍板如下：
  - `KB 命中`：走 `RAG + 自定义 LLM` 生成
  - `KB 未命中 + 提供 referenceUrl`：抓取链接补上下文后走 `RAG + 自定义 LLM` 生成
  - `KB 未命中 + 未提供 referenceUrl`：直接报错，要求用户补充 URL
- 明确禁止：
  - 无证据时生成测试骨架
  - 用占位 `TODO` 代码冒充可用测试用例
- 第三批生成链路实现补充拍板如下：
  - 描述优化：走配置文件中的自定义 `LLM`
  - 代码生成：走配置文件中的自定义 `LLM`

### 行动项
- 负责人：P9 按本条硬规则修正第三批任务包，错误语义必须先于实现口径固定。
- 负责人：P10 后续验收时重点检查“无命中且无 URL 是否真的报错”以及“生成链路是否统一复用配置化 LLM”。

### 关键证据
- 用户最新拍板：`3直接抛出异常，不用生成代码，让用户来补充url，测试用例描述优化和代码生成用配置文件里自定义的LLM来实现`

## 2026-03-24 14:48:53 +0800

### 主题
P10 验收第三批最小生成闭环，并补运行态复核结论

### 参与角色
- P10 主线程：验收、运行态复核、纪要收口
- P8 执行提交：落地第三批最小闭环并提交代码

### 评审范围
- `POST /api/testcase/generate` 的实现是否符合第三批硬规则
- Java 21 构建与打包是否闭环
- 本地运行态是否已暴露允许入口，并能稳定启动

### 统一结论
- 第三批最小闭环已落地并提交，主提交为 `3eca0e4 feat(testcase): add minimal generation flow`。
- 代码验收通过：在 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` 下，`mvn -q -DskipTests compile`、`mvn -q -Dtest=TestcaseGenerationServiceImplTest,TestcaseGenerationControllerTest,TestcaseGenerationControllerAdviceTest test`、`mvn -q -DskipTests package` 均通过。
- 运行态复核通过到“服务可启动”层：`scripts/start_service.sh` 已使用 Java 21 拉起服务，当前 PID 为 `2891854`；`/actuator/health` 返回 `200`，`/actuator/info` 已暴露 `allowedEndpoints=["/api/knowledge/*","/api/testcase/generate"]`。
- 当前本地环境的端到端生成验收尚未闭环：由于运行时 `knowledge-base.llm.provider` 仍处于默认禁用态，向 `/api/testcase/generate` 发送有效 JSON 时返回了 `500 / INTERNAL_ERROR`，根因是 `DisabledLLMService` 抛出 `IllegalStateException`，不是第三批接口合同本身未实现。

### 核心问题

#### P1
- 当前 shell 默认 `java` 仍指向 `1.8.0_202`；若不显式切到 Java 21，构建会再次报 `invalid target release: 21`。

#### P2
- 第三批生成链路已按文档实现为“统一复用配置化自定义 LLM”，因此在未配置 `knowledge-base.llm.provider=custom` 与对应 `api-url/api-key/model` 前，线上只能验证启动与合同，无法验证真实生成结果。

### 决策
- 第三批代码层验收结论为“通过”，以提交 `3eca0e4` 为准。
- 第三批环境层验收结论为“待补 LLM 配置后再做一次端到端冒烟”，当前不能把 `500` 归因为 Batch 3 代码未实现。
- 后续每次涉及知识库或第三批生成能力的实质变更，必须继续同步更新 `meeting.md`，写入时间、结论、证据与残余风险。

### 行动项
- 负责人：P10 在用户补齐运行时 LLM 配置后，立即复跑 `/api/testcase/generate` 的真实请求冒烟，并将结果续写到本纪要。
- 负责人：执行层后续若继续改第三批链路，必须保持 Java 21 构建与 `meeting.md` 记录同步，不允许再次出现“代码已改但未入纪要”的状态。

### 关键证据
- 提交：`3eca0e4 feat(testcase): add minimal generation flow`
- 启动脚本：`bash scripts/start_service.sh`
- 健康检查：`curl -i http://127.0.0.1:8080/actuator/health`
- 运行态信息：`curl http://127.0.0.1:8080/actuator/info`
- 本地有效 JSON 请求返回：`500 / INTERNAL_ERROR`，根因 `IllegalStateException`（LLM provider disabled）

## 2026-03-24 15:15:45 +0800

### 主题
P10 复验 Java 21 默认版本与第三批运行态生成链路，定位剩余阻塞到 MaaS 鉴权

### 参与角色
- P10 主线程：复验、运行态定位、纪要收口
- 执行层：修默认 Java 版本并配合重启验证

### 评审范围
- 默认 `java` 是否已真正切到 Java 21
- 第三批服务在最新本地配置下是否可重包、重启、通过基础健康检查
- `POST /api/testcase/generate` 当前失败点是否仍在本地实现，还是已外移到远端 LLM

### 统一结论
- 默认 `java` 已切换到 Java 21，`java -version` 返回 `21.0.10`，默认命中路径为 `/usr/lib/jvm/java-21-openjdk-amd64/bin/java`。
- 第三批在 Java 21 下再次通过 `compile`、`package`、定向 `test`，服务以 PID `2905579` 成功启动，`/actuator/health` 返回 `200`，`/actuator/info` 继续暴露 `"/api/testcase/generate"`。
- 当前 `POST /api/testcase/generate` 仍返回 `500 / INTERNAL_ERROR`，但失败性质已变化：不再是本地 `DisabledLLMService` 或 Java 版本问题，而是远端 MaaS LLM 调用返回 `401`。
- 直接探测配置中的 MaaS Chat Completions 端点后，返回了 `ModelArts.81003 / Invalid authorization header`；因此当前阻塞已明确收敛到 MaaS 鉴权配置，而不是第三批本地代码链路。

### 核心问题

#### P1
- 当前本地代码只支持 `knowledge-base.llm.provider=custom` 或 `none`；运行态已按此收正。
- 但即便使用 `custom + v2/chat/completions`，远端仍明确返回 `401`，说明现有 `api-key` 不被目标端点接受。

#### P2
- 第三批生成链路已经走到远端 LLM 请求阶段，本地实现链路已经打通。
- 因此后续再出现 `500`，优先排查 MaaS `api-key`、账号权限、端点区域与接口协议，而不应再回头归因为 Java 8 或本地 Spring 配置未生效。

### 决策
- Java 21 默认版本问题本轮验收通过，后续不再接受“只是启动脚本内部用了 21”这种口径。
- 第三批本地实现与运行态装配问题本轮验收通过。
- 当前唯一剩余阻塞定义为：`knowledge-base.llm.api-key` 对 `https://api.modelarts-maas.com/v2/chat/completions` 鉴权失败，需要用户更换或核对有效的 MaaS API Key。

### 行动项
- 负责人：P10 在用户提供或修正有效 MaaS API Key 后，立即复跑 `/api/testcase/generate` 真实请求并补最终验收结论。
- 负责人：执行层后续若继续接入其他 LLM 供应商，必须先在直连 `curl` 层验证鉴权与协议，再接回应用链路，避免把外部鉴权错误包成应用内不透明 `500`。

### 关键证据
- `java -version`：`openjdk version "21.0.10" 2026-01-20`
- 默认 Java 路径：`/usr/lib/jvm/java-21-openjdk-amd64/bin/java`
- 真实生成请求返回：`500 / INTERNAL_ERROR`，错误类型 `RuntimeException`
- 直连 MaaS 端点返回：`401 / ModelArts.81003 / Invalid authorization header`

## 2026-03-24 16:14:14 +0800

### 主题
P10 复验用户修正后的 MaaS 配置并完成第三批端到端验收收口

### 参与角色
- P10 主线程：最终验收、运行态复核、纪要收口

### 评审范围
- 用户修正后的 MaaS key 是否已被当前运行实例加载
- MaaS 直连是否从鉴权失败恢复到可用
- `POST /api/testcase/generate` 的硬错误路径与成功路径是否均已按当前设计返回

### 统一结论
- 用户修正 key 后，重新打包并重启到最新配置，服务当前以 PID `2912209` 稳定运行，`/actuator/health` 返回 `200`。
- MaaS 直连已恢复：使用当前 `application.yml` 中的 `api-url/api-key/model/max-tokens` 直接调用远端 Chat Completions，返回 `HTTP 200`，说明鉴权与协议已打通。
- 第三批硬错误路径验收通过：对 `POST /api/testcase/generate` 发送无 `referenceUrl` 的请求，系统返回 `HTTP 400`，错误码为 `TESTCASE_REFERENCE_URL_REQUIRED`，符合“KB 未命中且无 URL 直接报错”的拍板规则。
- 第三批成功路径的**服务级验收**通过：对 `POST /api/testcase/generate` 发送带 `referenceUrl` 的请求，系统返回 `HTTP 200`，响应包含 `javaTestCode`、结构化 `citations`、`degraded=true`，符合当前 Batch 3 合同。
- 第三批成功路径的**产物可执行性验收**本轮未通过：生成代码仍包含 `AUTH_TOKEN`、`PROJECT_ID` 占位符，且本机当前没有 Java 21 编译器（只有 Java 21 runtime），因此本轮没有完成“生成代码可直接编译并可对接真实认证后执行”的验证。

### 核心问题

#### P1
- 本轮真正阻塞不是代码，而是运行时 MaaS 参数配置：先是 key 无效，后是 `max_tokens=2000000` 超出对端上限。
- 将 `max_tokens` 收回可用范围后，MaaS 与应用链路恢复正常。

#### P2
- 当前知识库短路径仍存在结构化命中不足的问题：例如“创建工作流”能搜到结果，但部分结果缺少 `httpMethod/endpoint` 等字段，因此会被生成链路判为“非具体命中”，这会影响“纯 KB 命中直接生成”的成功率。
- 该问题不阻塞当前第三批最小闭环，因为 `referenceUrl` 降级成功路径已可用，但它属于后续知识库数据质量治理项。

#### P3
- 当前生成代码只证明“LLM 能按上下文生成语法上像测试用例的 Java 文本”，还不能证明“它已经具备真实可运行性”。
- 现有产物中仍有认证与项目标识占位符，且没有统一的鉴权注入协议、测试环境约束和执行级验收脚本，所以不能把 `HTTP 200` 直接表述成“生成用例可用”。

### 决策
- 第三批端到端最小闭环的**服务级目标**本轮正式验收通过。
- 第三批端到端最小闭环的**产物可执行性目标**本轮不予放行，需单独补验。
- 当前对外口径可以明确为：
  - 默认 `java` 已是 21
  - 服务可启动且健康
  - `POST /api/testcase/generate` 已具备硬错误返回与 `referenceUrl` 成功生成能力
  - 但“生成出的 Java 用例已可直接使用”这一结论当前不能成立
- 后续若继续提升第三批质量，优先级放在知识库元数据结构化补齐，而不是继续纠缠 Java 或 MaaS 基础接通问题。

### 行动项
- 负责人：P10 将本条纪要提交并推送远端，作为本轮验收落点。
- 负责人：后续若要提升“无 `referenceUrl` 也能直接生成”的命中率，执行层需要针对知识库元数据补齐 `httpMethod/endpoint/requestBody/responseBody` 等结构化字段。
- 负责人：执行层下一步必须补一条新的验收链路：把生成代码落到临时测试工程中，用 Java 21 编译器做编译校验，并定义认证参数如何注入，之后才能评估“可用性”。

### 关键证据
- 健康检查：`HTTP 200 /actuator/health`
- MaaS 直连：`HTTP 200`，`glm-5` 返回合法 `choices[].message.content`
- 无 `referenceUrl` 请求：`HTTP 400` + `TESTCASE_REFERENCE_URL_REQUIRED`
- 带 `referenceUrl` 请求：`HTTP 200`，返回 `javaTestCode + citations + degraded`
- 生成代码检查：包含 `AUTH_TOKEN` 与 `PROJECT_ID` 占位符
- 本机工具链事实：存在 Java 21 runtime，但不存在 `javac 21`，因此本轮未完成 Java 21 下的生成产物编译验收

## 2026-03-24 19:27:28 +0800

### 主题
P10 补齐生成产物可用性验收，收口到“无占位符 + Java 21 真编译通过”

### 参与角色
- P10 主线程：代码收口、运行态复验、产物可用性验收

### 评审范围
- 生成产物是否仍含鉴权/项目标识占位符
- 生成产物是否能通过 Java 21 编译器真实编译
- 默认 `javac` 是否也已对齐到 Java 21

### 统一结论
- 第三批生成链路已增加产物后处理与语法校验：服务现在会对 LLM 生成结果做占位符收口、运行参数注入规范化，并对最终 Java 代码做解析验证。
- 最新真实请求结果已经满足三条硬条件：
  - `HTTP 200`
  - 返回代码中 `placeholder/TODO` 数量为 `0`
  - 返回代码中已改为通过 `HUAWEICLOUD_AUTH_TOKEN`、`HUAWEICLOUD_PROJECT_ID` 等运行参数注入，不再伪造默认认证值
- 对服务返回的最新 `DeleteWorkflowNotFoundTest.java` 做 Java 21 真实编译，`javac_exit=0`，说明产物至少在语法与依赖层面可编译。
- 默认 `javac` 也已对齐到 Java 21，`javac -version` 返回 `21.0.10`。

### 核心问题

#### P1
- 之前的问题不是单一接口错误，而是“LLM 能生成文本，但系统没有对产物可交付性做最后一道收口”。
- 这导致服务可以返回 `200`，但产物里仍含 `AUTH_TOKEN`、`PROJECT_ID` 等占位符，口径上容易误判为“可用”。

#### P2
- 通过在服务端补后处理与校验，当前系统至少能保证：
  - 不返回明显占位符
  - 不返回 `TODO`
  - 返回的 Java 文本可通过 Java 21 编译器编译

### 决策
- 第三批本轮按“服务级 + 产物编译级”双层口径放行。
- 当前可以对外确认的结果升级为：
  - 服务链路通过
  - 错误路径通过
  - `referenceUrl` 成功路径通过
  - 返回的 Java 用例无占位符，且可被 Java 21 编译
- 后续如果要再往上提一个层级，才是“带真实认证参数执行集成测试”。那是下一阶段，不再影响本轮通过结论。

### 行动项
- 负责人：P10 提交本轮代码与纪要，并推送远端。
- 负责人：后续若继续提升质量，增加“真实凭证注入后的集成执行”验收，而不是再回头争论当前最小闭环是否成立。

### 关键证据
- 无 `referenceUrl`：`HTTP 400` + `TESTCASE_REFERENCE_URL_REQUIRED`
- 带 `referenceUrl`：`HTTP 200`
- 产物检查：`has_placeholder=0`
- 运行参数注入：`has_auth_env=2`、`has_project_env=2`
- 编译结果：`javac_exit=0`
- 默认编译器：`javac 21.0.10`

## 2026-03-26 09:43:00 +0800

### 主题
P10 验收 testcase 生成链路 V3 收口，确认 BMS 卸载系统盘负例可稳定生成

### 参与角色
- P10 主线程：代码收口、服务重启、live 验收、纪要落盘

### 评审范围
- testcase 需求优化是否锚定正确 API
- RAG 上下文是否只使用 top1 命中并输出干净 citation
- 生成代码是否满足 `requiredConfig(...)`、JUnit5、Java 21 编译约束
- live 服务是否能稳定返回 BMS 负例 `400 / ModelArts.7000 / does not support detach volume device`

### 统一结论
- 本轮验收通过。
- 当前服务已完成以下收口：
  - 需求优化 prompt 增加显式期望和 KB top1 API 锚点，`refinedRequirement` 不再漂到无关 API。
  - 生成链路增加服务端自动重试，首轮生成若未通过后处理校验，不再直接向用户暴露 `INTERNAL_ERROR`。
  - 验收脚本增加 `requiredConfig(...)`、`refinedRequirement`、单 citation、禁止直连 `System.getenv("HUAWEICLOUD_...")` 等合同检查。
- 2026-03-26 09:40-09:42 期间，对同一请求连续进行了两次 live 验收，均通过：
  - 请求：`验证卸载 Lite Server 系统盘在 BMS 场景下返回 400`
  - 显式期望：`HTTP 400` / `ModelArts.7000` / `does not support detach volume device`
  - citations：仅 1 条，且指向 `DetachDevServerVolume.html`
  - degraded：`false`
- 第二次 live 结果的生成代码结构已达到当前验收口径：
  - 单一 `public class`
  - JUnit5
  - `@BeforeAll + requiredConfig(...)`
  - 断言命中真实负例 `400 / ModelArts.7000 / does not support detach volume device`

### 核心问题

#### P1
- 之前的主问题不是检索不到，而是“生成结果偶发不符合交付合同”，例如直接写 `System.getenv("HUAWEICLOUD_BASE_URL")`，导致后处理抛错并返回 `INTERNAL_ERROR`。
- 本轮通过“更强的 refinement 锚点 + 服务端重试”把这个问题从用户可见错误降为内部自愈。

#### P2
- 之前的验收脚本只检查“能返回代码并编译”，没有检查 `requiredConfig(...)`、`refinedRequirement`、citation 噪声和直连配置读取，验收口径偏松。
- 本轮已把这些合同项补进脚本，后续同类回归会更快暴露偏移。

### 决策
- 当前 testcase 生成链路按“服务级可用”放行。
- 当前 testcase 生成链路按“面向该 BMS 负例场景的生成正确性”放行。
- 当前 testcase 生成链路仍保留一个非阻塞优化项：
  - 个别通过样本仍会生成冗余形式，如 `Optional.ofNullable(requiredConfig(...)).orElse(requiredConfig(...))`
  - 该问题不影响当前验收通过，但后续应继续清理输出风格，使代码更规整

### 行动项
- 负责人：P10 提交本轮代码与纪要。
- 负责人：后续继续优化 post-processor，把冗余 `Optional.ofNullable(requiredConfig(...))` 进一步压平。
- 负责人：下一阶段若要升级“可用性”口径，补真实参数下的执行级验收，而不只是生成与编译级验收。

### 关键证据
- 单测回归：`mvn -q -Dtest=TestcasePromptBuilderTest,GeneratedTestcasePostProcessorTest,TestcaseGenerationServiceImplTest,KnowledgeBaseServiceImplWeakConsistencyTest,HuaweiCloudApiCrawlerServiceTest test`
- 打包：`mvn -q -DskipTests package`
- 健康检查：`GET /actuator/health => UP`
- live 验收 1：`verification passed`，响应文件 `/tmp/testcase-generate-response.3UiHtr.json`
- live 验收 2：`verification passed`，响应文件 `/tmp/testcase-generate-response.icbjRm.json`
