# BlackDex 脱壳/修复新方法学习汇总（2026-04-15）

## 背景
面向 Android 13+ 及新型壳对抗，单次静态/单阶段 dump 成功率波动较大。需要将 BlackDex 从“单工具”升级为“多阶段导出 + 质量评估 + 可观测流水线”。

## 近期可借鉴的开源方向

### 1) Frida 生态（动态触发 + 时机控制）
- 项目：<https://github.com/frida/frida>
- 可借鉴点：
  - 在关键业务路径触发后再导出（例如登录后、解密后）；
  - 通过脚本化触发避免“导出过早导致空壳 dex”。

### 2) frida-dexdump / frida_dump（扫描与导出策略）
- 项目：<https://github.com/hluwa/frida-dexdump>
- 项目：<https://github.com/lasting-yang/frida_dump>
- 可借鉴点：
  - 以“多次采样 + 去重”提高命中率；
  - 用统一输出元数据支撑后处理与批量比较。

### 3) JADX（导出后修复验证）
- 项目：<https://github.com/skylot/jadx>
- 可借鉴点：
  - 将导出 dex 自动进入反编译/可读性检查流程；
  - 统计类/方法解析率，反推导出质量。

## 本次已落地到 BlackDex 的功能增强

### A. 多次尝试导出（Retry）
- 在 `DexDumpRepository` 中加入按模式分级的尝试次数：
  - 普通模式：最多 2 次；
  - 深度修复模式：最多 4 次。
- 每次尝试会更新状态消息，便于前端提示与调试。

### B. 结果可观测化（JSON 报告）
- 每次任务结束（成功/超时）自动写入 `dump_reports/dump_report_<timestamp>.json`。
- 报告字段包括：
  - 输入类型（包名/文件/URL）
  - 开始/结束时间与耗时
  - 尝试次数
  - fixCodeItem/hookDump 开关状态
  - 输出目录与导出文件列表
  - 结果状态和消息

### C. 为后续自动修复做准备
- 报告格式已能作为后处理入口（比如后续接入 JADX/baksmali 做解析质量评分、失败原因归类）。

### D. 对抗壳指纹与分阶段策略（新增）
- 在 Bcore 增加轻量壳指纹识别：`360 / 梆梆 / 爱加密 / unknown`。
- 检测依据：常见壳入口类（如 `com.stub.StubApp`、`com.secneo.apkwrapper.ApplicationWrapper`、`s.h.e.l.l.S`）与包名关键词。
- 根据识别结果自动选择不同延时导出策略（stage delays），针对“延迟解密/二次加载”样本提高命中率。
- 成功/失败结果会携带 `shellProfile` 与 `strategy` 信息，便于后续复盘。

### E. 导出后自动修复与清洗（DumpPostProcessor，新增）
- 新增独立 `DumpPostProcessor`：对导出目录执行后处理，不依赖定向绕过。
- 实装功能：
  - DEX 头校验；
  - magic 偏移 carve（输出 `*_carved.dex`）；
  - header magic 修复（输出 `*_repaired.dex`）；
  - SHA-256 去重统计（输出 `dump_postprocess_report.json`）。
- 已接入 `VMCore.cookieDumpDex` 结束阶段自动执行，形成“导出 -> 修复 -> 去重统计”的闭环。

### F. 可插拔脱壳引擎架构（新增）
- 新增 `DumpEngine` 抽象层与 `DumpEngineRegistry`，当前内置 `CookieDumpEngine` + `HookDumpEngine`（开关控制）。
- `BActivityThread` 的阶段调度不再直接耦合单一实现，而是调用引擎注册表统一执行。
- 每个阶段可产出引擎级结果（成功状态、dex数量、耗时），为后续接入“新引擎/混合引擎”打基础。

## 下一步建议（优先级）
1. **P0**：在 UI 增加“导出报告入口”与“失败原因提示”。
2. **P1**：接入导出后自动校验（类数量、可反编译比例、方法体有效率）。
3. **P1**：新增“触发点导出”能力（冷启动/页面触发/行为回放后）。
4. **P2**：引入插件化注入点（特定壳规则、反调试绕过模板）。

## 预期收益
- 提升新型壳样本上的导出稳定性（通过多次尝试与时机冗余）；
- 大幅提升问题定位效率（结构化报告替代纯日志）；
- 为 Android 新版本适配与深度修复建立可回归、可对比的基础设施。
