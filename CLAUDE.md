# Java测试用例生成Agent - 项目指南

## 项目概述

本项目构建一个智能的Java测试用例生成系统，能够理解用户自然语言输入，匹配项目API，并自动生成高质量的单元测试代码。

## 工作方式

本项目充分利用Claude的subagent能力来完成复杂任务：

### Subagent协作架构

```
用户需求
    ↓
主Agent（协调者）
    ↓
┌─────────────────────────────────────┐
│   Plan Subagent                     │
│   - 设计实现计划                     │
│   - 架构方案设计                     │
│   - 识别关键文件                     │
│   - 考虑架构权衡                     │
└─────────────────────────────────────┘
    ↓ 输出设计计划
┌─────────────────────────────────────┐
│   General-Purpose Subagent          │
│   - 代码实现                         │
│   - 文件操作                         │
│   - 多步骤任务执行                   │
└─────────────────────────────────────┘
    ↓ 提交代码
┌─────────────────────────────────────┐
│   人工评审 (Human Review)           │
│   - 审查设计和代码                   │
│   - 质量把控                         │
│   - 反馈修改意见                     │
└─────────────────────────────────────┘
    ↓ 通过/修改
    集成到主分支
```

## Subagent使用指南

### 1. Plan Subagent - 用于设计阶段

**何时使用**：
- 需要设计实现策略
- 规划多步骤任务
- 进行架构决策
- 识别关键文件和模块

**使用方式**：
```
主Agent调用：
Agent(
  subagent_type="Plan",
  prompt="设计知识库模块的实现方案，包括：
  1. 模块结构和职责划分
  2. 核心接口定义
  3. 数据模型设计
  4. 关键技术选型
  5. 实现步骤规划"
)
```

**输出**：
- 详细的实现计划
- 架构设计方案
- 关键文件列表
- 技术权衡分析

### 2. General-Purpose Subagent - 用于开发阶段

**何时使用**：
- 实现代码功能
- 执行多步骤任务
- 文件读写操作
- 复杂的代码生成

**使用方式**：
```
主Agent调用：
Agent(
  subagent_type="general-purpose",
  prompt="根据设计文档实现KnowledgeBaseService：
  1. 创建接口定义
  2. 实现核心类
  3. 添加异常处理
  4. 编写Javadoc注释

  设计文档位置：docs/KNOWLEDGE_BASE.md"
)
```

**输出**：
- 实现的Java代码
- 测试代码（如需要）
- 实现说明文档

### 3. Explore Subagent - 用于代码探索

**何时使用**：
- 探索现有代码库
- 查找特定功能实现
- 理解代码结构
- 快速定位文件

**使用方式**：
```
主Agent调用：
Agent(
  subagent_type="Explore",
  prompt="探索项目中的配置管理实现，找出：
  1. 配置文件的位置和格式
  2. 配置加载的实现类
  3. 配置项的定义"
)
```

## 工作流程

### 阶段1：需求分析和设计

1. **用户提出需求**
   ```
   用户："实现知识库模块"
   ```

2. **主Agent调用Plan Subagent**
   ```
   主Agent → Plan Subagent: "设计知识库模块实现方案"
   ```

3. **Plan Subagent输出设计**
   - 生成设计文档
   - 定义接口和数据模型
   - 规划实现步骤

4. **人工评审设计**
   - 审查设计方案
   - 提出修改意见
   - 批准或要求调整

### 阶段2：代码实现

1. **主Agent调用General-Purpose Subagent**
   ```
   主Agent → General-Purpose Subagent: "实现知识库模块代码"
   ```

2. **Subagent实现代码**
   - 创建Java类和接口
   - 实现业务逻辑
   - 添加注释和文档

3. **人工评审代码**
   - 检查代码质量
   - 验证功能实现
   - 批准或要求修改

### 阶段3：迭代优化

1. **根据反馈调整**
   - Plan Subagent调整设计
   - General-Purpose Subagent修改代码

2. **持续改进**
   - 优化性能
   - 完善功能
   - 修复问题

## 主Agent的职责

作为协调者，主Agent负责：

1. **理解用户需求**
   - 与用户交互
   - 澄清需求细节
   - 确定工作范围

2. **选择合适的Subagent**
   - 设计任务 → Plan Subagent
   - 开发任务 → General-Purpose Subagent
   - 探索任务 → Explore Subagent

3. **整合Subagent输出**
   - 汇总设计方案
   - 组织代码实现
   - 生成最终文档

4. **与用户沟通**
   - 报告进度
   - 请求反馈
   - 解答疑问

## 项目结构

```
ascend_agent/
├── docs/                    # 设计文档（Plan Subagent输出）
│   ├── DESIGN.md           # 总体设计
│   ├── KNOWLEDGE_BASE.md   # 知识库模块设计
│   └── ADR/                # 架构决策记录
├── src/
│   ├── main/java/          # 源代码（General-Purpose Subagent输出）
│   │   └── com/agent/
│   │       ├── config/     # 配置管理
│   │       ├── service/    # 服务接口
│   │       ├── core/       # 核心逻辑
│   │       └── util/       # 工具类
│   └── test/java/          # 测试代码
├── config.yaml             # 配置文件
├── CLAUDE.md              # 本文件
└── README.md              # 项目说明
```

## 开发规范

### 设计阶段（Plan Subagent）

1. **输出设计文档**
   - 使用Markdown格式
   - 包含架构图和流程图
   - 提供代码示例

2. **设计内容要求**
   - 明确问题和目标
   - 提供技术选型依据
   - 定义清晰的接口
   - 考虑扩展性和维护性

### 开发阶段（General-Purpose Subagent）

1. **代码质量**
   - 遵循Google Java Style Guide
   - 使用有意义的命名
   - 方法长度适中（<50行）
   - 单一职责原则

2. **注释规范**
   - 类和接口有Javadoc
   - 公共方法有注释
   - 复杂逻辑有说明

3. **异常处理**
   - 充分的参数验证
   - 有意义的错误信息
   - 记录异常日志

## 协作示例

### 示例：实现知识库模块

**Step 1: 用户需求**
```
用户："实现知识库模块，支持Java项目API索引和语义检索"
```

**Step 2: 主Agent调用Plan Subagent**
```
主Agent: 调用Plan Subagent设计知识库模块
↓
Plan Subagent: 输出设计文档到 docs/KNOWLEDGE_BASE.md
- 模块结构
- 接口定义
- 数据模型
- 实现流程
```

**Step 3: 人工评审设计**
```
用户: 审查设计文档
✓ 设计合理
→ 批准，开始实现
```

**Step 4: 主Agent调用General-Purpose Subagent**
```
主Agent: 调用General-Purpose Subagent实现代码
↓
General-Purpose Subagent:
- 创建 KnowledgeBaseService 接口
- 实现 KnowledgeBaseServiceImpl 类
- 创建相关的工具类
- 添加注释和文档
```

**Step 5: 人工评审代码**
```
用户: 审查代码实现
✓ 代码质量良好
✗ 需要添加更多异常处理
→ 要求修改
```

**Step 6: Subagent修改**
```
General-Purpose Subagent:
- 添加参数验证
- 完善异常处理
- 增加错误日志
```

**Step 7: 最终批准**
```
用户: 再次审查
✓ 符合要求
→ 合并到主分支
```

## 当前项目状态

### 已完成
- [x] 项目初始化
- [x] 总体设计文档（`docs/DESIGN.md`）
- [x] 知识库模块设计（`docs/KNOWLEDGE_BASE.md`）
- [x] Subagent协作架构定义（本文件）

### 进行中
- [ ] 知识库模块实现

### 待开始
- [ ] API匹配模块设计和实现
- [ ] 测试代码生成模块设计和实现
- [ ] 集成测试

## 重要原则

1. **充分利用Subagent**
   - 设计任务使用Plan Subagent
   - 开发任务使用General-Purpose Subagent
   - 不要手动完成Subagent能做的工作

2. **保持职责清晰**
   - 主Agent负责协调和沟通
   - Plan Subagent负责设计
   - General-Purpose Subagent负责实现
   - 人工负责评审和决策

3. **文档先行**
   - 先设计后实现
   - 设计文档要详细完整
   - 代码要符合设计

4. **质量优先**
   - 充分的设计和评审
   - 代码质量高于速度
   - 可维护性优先

## 注意事项

- Subagent在后台运行，完成后会通知主Agent
- 主Agent不应该重复Subagent的工作
- 如果Subagent输出不满意，可以重新调用并提供更详细的指令
- 人工评审是质量保证的关键环节
