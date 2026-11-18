-- M3扩展：场景A（资金·对外担保）与场景C（资源·租赁）闭环
-- 三场景共用：规则引擎评估 / 预审意见单 / 风险事件 / 哈希链留痕 / 整改闭环

-- ---------- 场景A：对外担保申报单 ----------
CREATE TABLE guarantee_declaration (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  decl_no VARCHAR(32) NOT NULL UNIQUE COMMENT '申报编号',
  guarantor VARCHAR(128) NOT NULL COMMENT '担保人（本企业）',
  guaranteeee VARCHAR(128) NOT NULL COMMENT '被担保方',
  guarantee_type VARCHAR(32) COMMENT '担保类型：融资担保/履约担保',
  guarantee_amount DECIMAL(14,2) COMMENT '本次担保金额（万元）',
  balance_ratio DECIMAL(6,4) COMMENT '担保余额占净资产比例（0-1）',
  guaranteeee_rating VARCHAR(16) COMMENT '被担保方评级：AAA/AA/BBB/BB以下/未评级',
  is_holding_sub TINYINT NOT NULL DEFAULT 0 COMMENT '是否本集团控股子企业',
  counter_guarantee TINYINT NOT NULL DEFAULT 0 COMMENT '是否落实反担保',
  resolution_no VARCHAR(64) COMMENT '党委会/董事会决议编号',
  submitter VARCHAR(64),
  status VARCHAR(16) NOT NULL DEFAULT 'submitted',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '对外担保申报单（场景A）';

-- ---------- 场景C：资产租赁申报单 ----------
CREATE TABLE lease_declaration (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  decl_no VARCHAR(32) NOT NULL UNIQUE COMMENT '申报编号',
  asset_name VARCHAR(128) NOT NULL COMMENT '租赁资产名称',
  asset_type VARCHAR(32) COMMENT '商铺/仓库/土地/广告位/车位',
  pricing_mode VARCHAR(32) COMMENT '定价方式：公开竞价/协议定价',
  lease_term_months INT COMMENT '租期（月）',
  monthly_rent DECIMAL(12,2) COMMENT '租金（万元/月）',
  market_rent_ref DECIMAL(12,2) COMMENT '市场参考租金（万元/月）',
  is_renewal TINYINT NOT NULL DEFAULT 0 COMMENT '是否到期续签',
  resolution_no VARCHAR(64) COMMENT '决策决议编号',
  submitter VARCHAR(64),
  status VARCHAR(16) NOT NULL DEFAULT 'submitted',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '资产租赁申报单（场景C）';

-- ============ 种子政策：对外担保管理实施细则（演示版） ============
INSERT INTO policy (title, doc_no, issuer, effect_date, status, source_text) VALUES
('市属国有企业对外担保管理实施细则（演示版）', '演示〔2025〕11号', '市国资委（虚构）', '2025-09-01', 'decomposed',
'第一条（担保总额控制）企业对外担保余额合计不得超过本企业最近一期经审计净资产的百分之五十。
第二条（被担保方资质）为非控股子企业提供担保的，被担保方信用等级不得低于BBB级，并应当落实足额反担保措施。
第三条（决策程序）对外担保应当履行党委会前置研究、董事会决策程序，并形成书面决议。
第四条（禁止情形）不得为无实质性业务关联且未评级的第三方企业提供担保。');

INSERT INTO clause (policy_id, clause_no, content, clause_type) VALUES
(2, '第一条', '企业对外担保余额合计不得超过本企业最近一期经审计净资产的百分之五十。', 'forbid'),
(2, '第二条', '为非控股子企业提供担保的，被担保方信用等级不得低于BBB级，并应当落实足额反担保措施。', 'duty'),
(2, '第三条', '对外担保应当履行党委会前置研究、董事会决策程序，并形成书面决议。', 'procedure'),
(2, '第四条', '不得为无实质性业务关联且未评级的第三方企业提供担保。', 'forbid');

INSERT INTO obligation (clause_id, description, dept, threshold, deadline) VALUES
(6, '担保余额占净资产比例不得超过50%', '财务部', 'balance_ratio≤0.5', '担保审批前'),
(7, '非控股子企业担保须评级≥BBB且落实反担保', '财务部', '评级≥BBB；反担保', '担保审批前'),
(8, '对外担保须有党委会/董事会书面决议', '董事会办公室', '决议编号非空', '担保审批前'),
(9, '禁止为未评级第三方担保', '财务部', '被担保方已评级', '担保审批前');

INSERT INTO control_point (obligation_id, biz_stage, check_mode, bind_fields) VALUES
(6, '审批', '事前', 'balance_ratio'),
(7, '审批', '事前', 'is_holding_sub,guaranteeee_rating,counter_guarantee'),
(8, '审批', '事前', 'resolution_no'),
(9, '审批', '事前', 'guaranteeee_rating');

-- ============ 种子政策：资源性资产租赁管理办法（演示版） ============
INSERT INTO policy (title, doc_no, issuer, effect_date, status, source_text) VALUES
('市属国有企业资源性资产租赁管理办法（演示版）', '演示〔2026〕5号', '市国资委（虚构）', '2026-02-01', 'decomposed',
'第一条（公开竞价）资源性资产租赁应当通过产权交易机构或公共资源交易平台公开竞价确定承租方，协议定价仅限于按规定批准的例外情形。
第二条（租期上限）资源性资产单次租赁期限不得超过五年，超过五年的应当重新评估并报批。
第三条（租金定价）首次租金不得低于市场参考租金；租期内租金原则上每两年调整一次。
第四条（续签程序）租赁到期续签的，应当重新履行评估、竞价与决策程序，不得直接续签。
第五条（决策要件）资产租赁应当履行董事会决策程序并形成书面决议。');

INSERT INTO clause (policy_id, clause_no, content, clause_type) VALUES
(3, '第一条', '资源性资产租赁应当通过产权交易机构或公共资源交易平台公开竞价确定承租方，协议定价仅限于按规定批准的例外情形。', 'procedure'),
(3, '第二条', '资源性资产单次租赁期限不得超过五年，超过五年的应当重新评估并报批。', 'forbid'),
(3, '第三条', '首次租金不得低于市场参考租金；租期内租金原则上每两年调整一次。', 'forbid'),
(3, '第四条', '租赁到期续签的，应当重新履行评估、竞价与决策程序，不得直接续签。', 'procedure'),
(3, '第五条', '资产租赁应当履行董事会决策程序并形成书面决议。', 'procedure');

INSERT INTO obligation (clause_id, description, dept, threshold, deadline) VALUES
(10, '资源性资产租赁应公开竞价', '资产管理部', 'pricing_mode=公开竞价', '签约前'),
(11, '单次租期不超过5年（60个月）', '资产管理部', 'lease_term_months≤60', '签约前'),
(12, '首次租金不低于市场参考租金', '资产管理部', 'monthly_rent≥market_rent_ref', '签约前'),
(13, '到期续签须重新评估竞价决策', '资产管理部', '续签走全流程', '续签前'),
(14, '租赁须有董事会书面决议', '董事会办公室', '决议编号非空', '签约前');

INSERT INTO control_point (obligation_id, biz_stage, check_mode, bind_fields) VALUES
(10, '签约', '事前', 'pricing_mode'),
(11, '签约', '事前', 'lease_term_months'),
(12, '签约', '事前', 'monthly_rent,market_rent_ref'),
(13, '签约', '事前', 'is_renewal,resolution_no'),
(14, '签约', '事前', 'resolution_no');

-- ============ 种子规则：场景A（担保 G1-G4）+ 场景C（租赁 L1-L4） ============
INSERT INTO rule (code, name, severity, dsl, clause_ref, status) VALUES
('G1', '担保余额超上限', '红',
 '[{"field":"balance_ratio","op":"gt","value":0.5}]',
 '担保细则（演示）第一条', 'published'),
('G2', '非控股担保资质不符', '红',
 '[{"field":"is_holding_sub","op":"eq","value":false},{"field":"guaranteeee_rating","op":"in","value":["BB以下","未评级"]}]',
 '担保细则（演示）第二、四条', 'published'),
('G3', '担保决策要件缺失', '黄',
 '[{"field":"resolution_no","op":"eq","value":""}]',
 '担保细则（演示）第三条', 'published'),
('G4', '非控股担保未落实反担保', '黄',
 '[{"field":"is_holding_sub","op":"eq","value":false},{"field":"counter_guarantee","op":"eq","value":false}]',
 '担保细则（演示）第二条', 'published'),
('L1', '租赁未公开竞价', '黄',
 '[{"field":"pricing_mode","op":"eq","value":"协议定价"}]',
 '租赁办法（演示）第一条', 'published'),
('L2', '租期超过五年上限', '红',
 '[{"field":"lease_term_months","op":"gt","value":60}]',
 '租赁办法（演示）第二条', 'published'),
('L3', '租金低于市场参考', '红',
 '[{"field":"monthly_rent","op":"lt_field","value":"market_rent_ref"}]',
 '租赁办法（演示）第三条', 'published'),
('L4', '到期续签未重走程序', '黄',
 '[{"field":"is_renewal","op":"eq","value":true},{"field":"resolution_no","op":"eq","value":""}]',
 '租赁办法（演示）第四条', 'published');
