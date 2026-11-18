-- M5：预审意见单按场景隔离。三张申报表各自自增，declaration_id 会撞号，
-- 查询意见单必须带 scenario，否则担保/租赁会读到资产处置的意见。
ALTER TABLE precheck_opinion
  ADD COLUMN scenario VARCHAR(16) NOT NULL DEFAULT 'asset'
  COMMENT '场景：asset资产处置/guarantee对外担保/lease资产租赁' AFTER declaration_id;

-- 历史数据没有场景列。命中码能区分的回填；空命中（通过）无法区分，保持 asset。
UPDATE precheck_opinion SET scenario = 'guarantee'
  WHERE JSON_SEARCH(hits, 'one', 'G%', NULL, '$[*].code') IS NOT NULL;
UPDATE precheck_opinion SET scenario = 'lease'
  WHERE JSON_SEARCH(hits, 'one', 'L%', NULL, '$[*].code') IS NOT NULL;

ALTER TABLE precheck_opinion ADD KEY idx_scenario_decl (scenario, declaration_id);
