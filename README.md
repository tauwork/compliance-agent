# 政策合规智能体 MVP（单体应用）

三资监管 · 资产处置、对外担保、资产租赁三场景闭环。**M1-M4 已交付**，M5 驾驶舱按场景分口径统计，预审意见单按场景隔离。

## 架构

单体 = 一个后端jar + 一个MySQL实例（Docker两容器）。六业务模块进程内聚合：

| 模块 | 职责 | 状态 |
|------|------|------|
| policy-module | 政策/条款/义务/控制点管理、五级视图、义务人工修正 | ✅ |
| rule-module | 规则DSL管理 + 确定性评估（内置解释器，同数据同规则必同结论） | ✅ |
| audit-module | 三场景前置预审/申报单/风险事件/分口径统计 + MySQL哈希链防篡改留痕 | ✅ |
| remediation-module | 整改工单状态机（复核通过联动风险事件闭环） | ✅ |
| agent-module | 政策拆解流水线（五步+人工门禁）/ AI归因 / 对话助手 | ✅ |
| llm-module | DeepSeek客户端（OpenAI兼容，脱敏/限流/留痕/降级） | ✅ |

技术栈：Java 21 · Spring Boot 3.3 · MyBatis-Plus · Flyway · MySQL 8 · Vue 3 · Vite · Element Plus · Pinia · Vue Router

## 快速开始

```bash
# Docker（推荐）
docker compose up -d --build
sh scripts/demo-seed.sh

# 或本地一键（见 docs/操作手册与演示剧本.md）
sh scripts/start-all.sh
```

前端 http://localhost:5173 ｜ API http://localhost:8080/api/stats/overview

## 七屏功能（与界面原型一致）

1. **合规驾驶舱**——三资KPI、闭环运行状态、哈希链防篡改校验
2. **政策法规库**——上传政策、拆解状态跟踪
3. **政策拆解工作台**——五级视图（条款→义务→控制点→规则草稿），AI产出可人工修正，人工门禁审批发布
4. **前置预审**——申报单提交即时出具红/黄/通过意见单（命中规则+条款依据+建议）
5. **风险预警**——事件列表、AI归因分析、人工审批派单、证据链回放
6. **整改闭环**——泳道式工单流转，复核通过自动联动风险事件closed
7. **对话助手**——政策条款问答，答案溯源到原文条号

## 核心链路（10分钟演示剧本）

上传政策 → 五步拆解（DeepSeek/降级双路径）→ 人工门禁发布规则 → 带病申报即时红色预审 → 风险事件+AI归因 → 人工审批派单 → 工单六步闭环 → 事件自动closed → 哈希链全程留痕。

详见 `docs/操作手册与演示剧本.md`（含10分钟汇报讲解词、应急预案、验收清单）。

## 验收

```bash
sh scripts/acceptance-all.sh   # 全链路12项断言
sh scripts/acceptance-m1.sh    # M1专项（含篡改检测）
```

## LLM策略（如实口径）

拆解/归因/问答支持DeepSeek与确定性降级双路径：配置`DEEPSEEK_API_KEY`走LLM（结构化JSON提示词），未配置或失败自动降级（界面标注模式）。**核心判定从不依赖LLM**——规则评估确定性可复现。

> 规则引擎说明：原方案DSL→DRL→Drools，因开发环境JDK23下Drools 7.74对Map事实谓词编译缺陷（getDialect NPE），M1起改为内置DSL解释器，语义等价；JDK21运行时如需RETE可换回，DSL不变。

## 目录结构

```
policy-compliance-mvp/
├── docker-compose.yml / Dockerfile   # app + mysql 两容器
├── app/                # 启动模块（Flyway V1/V2迁移）
├── rule-module/ ... llm-module/      # 六业务模块
├── web/                # Vue3 + Element Plus 七屏
├── scripts/            # start-all / demo-seed / acceptance-all / acceptance-m1
└── docs/操作手册与演示剧本.md
```
