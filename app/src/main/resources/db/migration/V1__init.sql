-- ============================================================
-- 政策合规智能体 MVP · M1 初始化（MySQL 8）
-- 场景：资产处置进场交易（演示数据，全部虚构）
-- ============================================================

-- ---------- 政策资产库（政策→条款→义务→控制点） ----------
CREATE TABLE policy (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL COMMENT '政策标题',
  doc_no VARCHAR(64) COMMENT '文号',
  issuer VARCHAR(128) COMMENT '发文机关',
  effect_date DATE COMMENT '生效日期',
  version VARCHAR(16) NOT NULL DEFAULT 'v1' COMMENT '版本',
  status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft/decomposing/decomposed',
  source_text MEDIUMTEXT COMMENT '政策全文',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '政策法规';

CREATE TABLE clause (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  clause_no VARCHAR(32) NOT NULL COMMENT '条款号，如 第8条',
  content TEXT NOT NULL COMMENT '条款原文',
  clause_type VARCHAR(16) COMMENT 'duty义务/forbid禁止/procedure程序/authorize授权',
  revised_by VARCHAR(16) NOT NULL DEFAULT 'ai' COMMENT 'ai/human 产出与修正标记',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_policy (policy_id)
) COMMENT '政策条款';

CREATE TABLE obligation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  clause_id BIGINT NOT NULL,
  description VARCHAR(512) NOT NULL COMMENT '义务描述',
  dept VARCHAR(64) COMMENT '责任部门',
  threshold VARCHAR(128) COMMENT '量化阈值',
  deadline VARCHAR(64) COMMENT '时限',
  revised_by VARCHAR(16) NOT NULL DEFAULT 'ai',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_clause (clause_id)
) COMMENT '义务清单';

CREATE TABLE control_point (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  obligation_id BIGINT NOT NULL,
  biz_stage VARCHAR(32) COMMENT '业务环节：立项/评估/审批/交易/签约',
  check_mode VARCHAR(16) COMMENT '事前/事中/事后',
  bind_fields VARCHAR(255) COMMENT '关联申报单字段',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_obligation (obligation_id)
) COMMENT '控制点';

-- ---------- 规则库（条款→义务→控制点→规则DSL） ----------
CREATE TABLE rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE COMMENT '规则编号 R1..R5',
  name VARCHAR(255) NOT NULL,
  severity VARCHAR(8) NOT NULL COMMENT '红/黄/蓝',
  dsl TEXT NOT NULL COMMENT '[{"field":..,"op":"eq|neq|gt|gte|lt|lte|in","value":..}] 全部AND',
  clause_ref VARCHAR(64) COMMENT '依据条款，如 演示办法第8条',
  clause_id BIGINT COMMENT '关联条款ID（拆解链路发布时回填，M2）',
  status VARCHAR(16) NOT NULL DEFAULT 'published' COMMENT 'draft/trial/published/rejected',
  enabled TINYINT NOT NULL DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '审计规则';

-- ---------- 资产处置申报单（业务事件源） ----------
CREATE TABLE declaration (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  decl_no VARCHAR(32) NOT NULL UNIQUE COMMENT '申报编号',
  asset_name VARCHAR(128) NOT NULL,
  asset_category VARCHAR(32) COMMENT '房产/土地/股权/设备',
  book_value DECIMAL(14,2) COMMENT '账面价值（万元）',
  appraised_value DECIMAL(14,2) COMMENT '评估值（万元）',
  assessment_date DATE COMMENT '评估基准日',
  disposal_mode VARCHAR(32) COMMENT '进场挂牌转让/非公开协议转让',
  exemption TINYINT NOT NULL DEFAULT 0 COMMENT '豁免情形（非公开协议适用）',
  resolution_no VARCHAR(64) COMMENT '党委会/董事会决议编号',
  listing_price DECIMAL(14,2) COMMENT '拟挂牌价（万元）',
  deal_price DECIMAL(14,2) COMMENT '成交价（万元）',
  submitter VARCHAR(64),
  status VARCHAR(16) NOT NULL DEFAULT 'submitted',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '资产处置申报单';

-- ---------- 前置预审意见单 ----------
CREATE TABLE precheck_opinion (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  declaration_id BIGINT NOT NULL,
  result VARCHAR(8) NOT NULL COMMENT 'PASS/RED/YELLOW',
  hits JSON NOT NULL COMMENT '命中规则数组：[{code,name,severity,clauseRef,advice}]',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_decl (declaration_id)
) COMMENT '前置预审意见单';

-- ---------- 风险事件 ----------
CREATE TABLE risk_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_ref VARCHAR(64) NOT NULL COMMENT '来源事件编号（申报单号/预审单号）',
  rule_code VARCHAR(32) NOT NULL,
  rule_name VARCHAR(255),
  severity VARCHAR(8),
  summary VARCHAR(512),
  detail JSON COMMENT '触发事实快照',
  status VARCHAR(16) NOT NULL DEFAULT 'new' COMMENT 'new/analyzing/ticketed/closed',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_event_rule (event_ref, rule_code)
) COMMENT '风险事件';

-- ---------- 整改工单 ----------
CREATE TABLE remediation_ticket (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  risk_event_id BIGINT,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  suggestion TEXT COMMENT 'AI整改建议（M3接入）',
  status VARCHAR(16) NOT NULL DEFAULT 'created' COMMENT 'created/assigned/executing/resolved/verified/rejected/closed',
  assignee VARCHAR(64),
  history JSON COMMENT '流转记录',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '整改工单';

-- ---------- 日志哈希链（防篡改留痕，MySQL实现） ----------
CREATE TABLE audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL COMMENT '业务事件编号',
  ts DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  biz_type VARCHAR(32) NOT NULL COMMENT 'DECL_SUBMIT/DECL_PRECHECK/RISK_EVENT/TICKET',
  action VARCHAR(64) NOT NULL,
  entity_ref VARCHAR(64) NOT NULL COMMENT '关联实体，如申报单号',
  payload TEXT COMMENT '事实快照JSON原文（TEXT存原文，避免JSON列规范化破坏哈希）',
  prev_hash VARCHAR(64) NOT NULL,
  hash VARCHAR(64) NOT NULL,
  KEY idx_event (event_id),
  KEY idx_ts (ts)
) COMMENT '日志哈希链：hash = sha256(prev_hash + 行内容) 串联';

-- ---------- LLM 调用留痕 ----------
CREATE TABLE llm_call_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  model VARCHAR(64),
  purpose VARCHAR(64) COMMENT 'decompose/analyze/chat',
  prompt_masked TEXT,
  response TEXT,
  latency_ms BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT 'LLM调用留痕（M2起使用）';

-- ============================================================
-- 种子数据（演示版政策 + 5条种子规则，全部虚构）
-- ============================================================
INSERT INTO policy (title, doc_no, issuer, effect_date, status, source_text) VALUES
('市属国有企业资产处置监督管理办法（演示版）', '演示〔2026〕3号', '市国资委（虚构）', '2026-03-01', 'decomposed',
'第一章 总则
第一条（目的）为规范市属国有企业资产处置行为，防止国有资产流失，制定本办法。
第二条（适用范围）本办法适用于市属国有企业及其所属子企业的资产转让、出售、置换等处置行为。
第三条（基本原则）资产处置应当遵循公开、公平、公正原则，依法合规、阳光操作。

第二章 决策程序
第六条（决策要件）资产处置应当履行党委会前置研究、董事会决策程序，并形成书面决议。
第七条（报批）重大资产处置事项应当按照管理权限报国有资产监督管理机构批准或备案。

第三章 评估与进场交易
第八条（进场交易）企业转让重大资产，账面价值达到50万元的，应当在依法设立的产权交易机构公开进行。符合国家规定的非公开协议转让情形的，经批准可以采取非公开协议转让方式。
第十二条（评估有效期）资产处置应当依法开展资产评估，评估结果使用有效期为自评估基准日起1年，超过有效期仍未完成交易的，应当重新评估。
第十四条（首次挂牌价）进场转让的首次挂牌价不得低于经核准或备案的评估结果。
第十五条（成交价）成交价格低于挂牌价90%的，应当重新决策并说明理由。

第四章 监督管理
第十六条（责任追究）违反本办法造成国有资产损失的，依照有关规定追究责任。');

INSERT INTO clause (policy_id, clause_no, content, clause_type) VALUES
(1, '第8条', '企业转让重大资产，账面价值达到50万元的，应当在依法设立的产权交易机构公开进行。符合国家规定的非公开协议转让情形的，经批准可以采取非公开协议转让方式。', 'duty'),
(1, '第12条', '资产处置应当依法开展资产评估，评估结果使用有效期为自评估基准日起1年，超过有效期仍未完成交易的，应当重新评估。', 'duty'),
(1, '第14条', '进场转让的首次挂牌价不得低于经核准或备案的评估结果。', 'forbid'),
(1, '第6条', '资产处置应当履行党委会前置研究、董事会决策程序，并形成书面决议。', 'procedure'),
(1, '第15条', '成交价格低于挂牌价90%的，应当重新决策并说明理由。', 'procedure');

INSERT INTO obligation (clause_id, description, dept, threshold, deadline) VALUES
(1, '账面价值≥50万元的资产转让必须进场公开交易', '资产管理部', '账面价值≥50万元', '立项后30日内进场'),
(2, '评估结果超有效期不得沿用，须重新评估', '资产管理部', '基准日起1年', '交易前'),
(3, '首次挂牌价不得低于评估结果', '资产管理部', '挂牌价≥评估值', '挂牌前'),
(4, '处置决策须有党委会/董事会书面决议', '董事会办公室', '决议编号非空', '申报前'),
(5, '成交价低于挂牌价90%须重新决策说明', '资产管理部', '成交价≥挂牌价×0.9', '成交前');

INSERT INTO control_point (obligation_id, biz_stage, check_mode, bind_fields) VALUES
(1, '交易', '事前', 'disposal_mode,exemption,book_value'),
(2, '评估', '事前', 'assessment_date'),
(3, '交易', '事前', 'listing_price,appraised_value'),
(4, '审批', '事前', 'resolution_no'),
(5, '交易', '事后', 'deal_price,listing_price');

INSERT INTO rule (code, name, severity, dsl, clause_ref, status) VALUES
('R1', '应进场未进场', '红',
 '[{"field":"disposal_mode","op":"eq","value":"非公开协议转让"},{"field":"exemption","op":"eq","value":false},{"field":"book_value","op":"gte","value":50}]',
 '演示办法第8条', 'published'),
('R2', '评估报告超有效期', '红',
 '[{"field":"days_since_assessment","op":"gt","value":365}]',
 '演示办法第12条', 'published'),
('R3', '挂牌价低于评估结果', '红',
 '[{"field":"listing_price","op":"lt_field","value":"appraised_value"}]',
 '演示办法第14条', 'published'),
('R4', '决策程序要件缺失', '黄',
 '[{"field":"resolution_no","op":"eq","value":""}]',
 '演示办法第6条', 'published'),
('R5', '成交价显著低于挂牌价', '黄',
 '[{"field":"deal_discount","op":"lt","value":0.9}]',
 '演示办法第15条', 'published');
