package com.pcmvp.agent.service;

/**
 * 政策拆解流水线（M2实现，M1预留状态机骨架）。
 *
 * 五步拆解（K1政策拆解五步法）：
 *   STRUCTURING 全文结构化 → EXTRACTION 条款要素抽取 → OBLIGATION 义务清单
 *   → CONTROL_POINT 控制点映射 → RULE_DRAFT 规则DSL化
 * 每步产出落库并允许人工修正后再进入下一步；RULE_DRAFT 完成后进入
 * PENDING_APPROVAL（人工门禁，对应既有MVP的interrupt设计），
 * 审批通过 → 联动 rule-module 发布；驳回 → 回到 RULE_DRAFT。
 * 每步调用经 llm-module（DeepSeek），产出存模型版本+提示词版本。
 */
public enum DecomposeStep {
    STRUCTURING,
    EXTRACTION,
    OBLIGATION,
    CONTROL_POINT,
    RULE_DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
