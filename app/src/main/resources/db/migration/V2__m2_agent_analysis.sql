-- M2：Agent分析留痕（归因分析→人工审批→派单）
CREATE TABLE agent_analysis (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  risk_event_id BIGINT NOT NULL,
  result TEXT COMMENT '归因分析结论',
  suggestion TEXT COMMENT '整改建议',
  model VARCHAR(64) COMMENT '产出模型（demo-fallback=降级模板）',
  status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_risk (risk_event_id)
) COMMENT 'AI归因分析（人工门禁对象）';
