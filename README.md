# StudyPath 智学规划 📚

一款**纯本地、开源**的 AI 学习规划 Android 应用。用自然语言描述学习需求，调用你自己的大模型 API 自动生成结构化学习计划，并支持**部分完成打卡、总进度加权联动、AI 动态重规划**。

> BYOK（Bring Your Own Key）：API Key 仅保存在手机本地数据库，不经过任何第三方服务器。

## ✨ 功能

- **多模型接入**：任何 OpenAI 兼容接口均可使用，内置 DeepSeek / 智谱 GLM / Kimi / 通义千问 / OpenAI / Groq / OpenRouter 预设，也支持自定义 Base URL
- **阶段折叠 + 路线流程图**：计划页以阶段为单位可展开/收起；总进度卡下方的阶段路线图横向呈现已完成✓/进行中/未开始节点，点击节点直达
- **手动创建计划**：不想用 AI？自己建计划——先建阶段、再往每个阶段里添加任务（学什么/怎么做/交付物/达标标准/日期用日历选择器）
- **多计划独立提醒**：每个计划详情页右上角 🔔 单独设置提醒时刻，多个计划互不干扰（WorkManager 实现，重启不丢）
- **任务 AI 执行教练**：拿着某个小任务直接问「具体该怎么做」，AI 带着任务的全部上下文给手把手步骤
- **交付记录**：每个任务可提交文字与图片凭证（笔记截图、看板链接说明…），本地保存，直观回看学习轨迹
- **纯离线数据**：Room 本地存储，无账号、无服务器、无追踪
- **计划细化到每一天**：AI 把每个任务排到具体日期（从当天起按天连续排布，单日总量不超过每日可投入时间），计划页顶部展示「今日待学」
- **每日学习提醒**：设置提醒时刻后，每天定点检查当天计划，有未完成任务就推送通知（WorkManager 实现，重启不丢）
- **AI 生成学习计划**：输出「阶段 → 小任务」结构化计划，每个任务细化到 **学什么（知识点列举）/ 怎么做 / 交付物 / 达标标准 / 常见坑 / 推荐资源 / 预计时长**，粒度以 30~150 分钟的可执行动作为准
- **部分完成打卡**：每个小任务支持 0–100% 滑条打卡（不只是完成/未完成）
- **总进度加权联动**：`总进度 = Σ(任务时长 × 任务完成度) / Σ(任务时长)`——完成一小部分，总进度立刻增长
- **AI 动态重规划**：进度落后或情况变化时，AI 根据实际完成情况重新安排剩余内容（已完成记录保留）
- **导入 / 导出**：一键导出 Excel（.xlsx，Excel/WPS 直接打开）与 JSON（含任务进度，可重新导入，可当备份）；支持导入其他 AI 按同格式生成的 JSON 计划
- **底部三栏导航**：主页 / 今日任务 / 我的——主页列出全部计划（已完成灰显），今日任务跨计划聚合当天内容并支持问 AI 与成果打卡，「我的」可自定义头像昵称、管理提醒与检查更新
- **纯离线数据**：Room 本地存储，无账号、无服务器、无追踪

## 🏗️ 技术栈与架构

Kotlin · Jetpack Compose (Material 3) · Room · Retrofit + OkHttp · kotlinx.serialization · Kotlin Coroutines / Flow · MVVM

```
app/src/main/java/com/studypath/app/
├── core/                     # 纯 Kotlin 业务核心
│   ├── PlanParser.kt         #   模型输出 JSON 容错解析
│   ├── ProgressCalculator.kt #   加权进度计算
│   └── ai/                   #   提示词与结构化计划模型
├── data/
│   ├── api/                  # OpenAI 兼容 API 客户端 + 服务商预设
│   ├── db/                   # Room 实体 / DAO / 数据库
│   └── repo/                 # 仓库层（进度聚合、重规划写入）
└── ui/                       # Compose 界面（首页/新建/详情/设置）+ ViewModel
```

亮点设计：

- **结构化输出解析**：提示词强制模型只输出 JSON，`PlanParser` 对代码块包裹、首尾杂文字做容错截取，解析失败给出可读错误
- **加权进度**：任务以预估时长为权重参与总进度计算，部分完成也会实时推进（Room `SUM(estimatedMinutes * progress)` 聚合查询）
- **重规划数据合并**：重规划只删除未完成任务、保留已完成历史，新阶段追加在原计划之后，进度数据不丢失

## 📥 下载安装

直接从 [Releases](https://github.com/brain4688/studypath/releases/latest) 下载最新的 `StudyPath-vX.Y.Z-release.apk`（正式签名，Android 8.0+），安装即可使用。

首次使用：**设置 → 新增模型配置**（推荐 DeepSeek，国内可直连）→ 填入自己的 API Key → 测试连通 → 生成第一个学习计划。正式签名 APK 均在本地用私有密钥构建，CI 构建产物请从 Actions 的 Artifacts 获取（debug 签名）。

## 🚀 快速开始

### 环境要求

- Android Studio（建议 Koala 或更新）
- JDK 17
- Android 8.0+（API 26）真机或模拟器

### 构建

> ⚠️ **项目必须放在纯英文（ASCII）路径下**（例如 `D:\studypath`）。AGP 会拒绝中文路径；即使跳过检查，编译可通过但单元测试进程会因路径编码无法加载类。

推荐直接用 **Android Studio 打开项目**，首次 Sync 会按 `gradle/wrapper/gradle-wrapper.properties` 自动下载 Gradle 8.7 与全部依赖并构建。

命令行方式（需本机安装 Gradle 8.7+）：

```bash
gradle :app:assembleDebug        # 构建 debug APK
gradle :app:testDebugUnitTest    # 运行单元测试
```

如需恢复 Wrapper，可在装有 Gradle 的环境中执行 `gradle wrapper --gradle-version 8.7`。CI 已配置为使用 `gradle/actions/setup-gradle` 直接安装 Gradle 运行，不依赖 Wrapper。

## 🧪 测试

核心逻辑（加权进度计算、模型输出 JSON 容错解析）配有 JUnit 单元测试：

```bash
gradle :app:testDebugUnitTest
```

覆盖场景：正常加权、空计划、溢出钳位；裸 JSON / Markdown 代码块包裹 / 前后杂文字输出、字段缺省回退、非法输出拒绝等。

### 正式签名（可选）

release 构建会自动读取根目录 `keystore.properties`（含 `storeFile/storePassword/keyAlias/keyPassword`），该文件与密钥库均已 gitignore。没有此文件时 release 包回退为 debug 签名。

### 使用

1. 在 **设置 → 新增模型配置** 中选择服务商预设（推荐 DeepSeek / 智谱 GLM，国内可直连），填入自己的 API Key，点「测试」验证连通性
2. 首页 → **新建学习计划** → 描述学习需求 → 生成计划
3. 学习过程中拖动任务滑条打卡，部分完成也会推进总进度
4. 进度落后时，在计划详情右上角点 🔄 让 AI 重新规划剩余内容

## 🗺️ Roadmap

- [ ] 每日学习提醒通知
- [ ] 番茄钟计时自动折算任务进度
- [ ] 学习统计图表
- [ ] 计划导出 Markdown / 分享图
- [ ] 单元测试与 UI 测试

## 🤝 参与

欢迎 Issue 与 PR！提交前请确保项目能正常构建（`gradle :app:assembleDebug` 或 Android Studio 直接运行）。

## 📄 许可证

[MIT](LICENSE)
