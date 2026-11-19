-- 全量巡检：对已入库申报按当前生效规则重扫，与前置预审并列，不另建一套判定。
CREATE TABLE inspection_run (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scenario VARCHAR(16) NOT NULL COMMENT 'all/asset/guarantee/lease',
  scanned INT NOT NULL,
  hit_decls INT NOT NULL,
  red_count INT NOT NULL,
  yellow_count INT NOT NULL,
  pass_count INT NOT NULL,
  new_risks INT NOT NULL COMMENT '本次新写入的风险事件数（已存在的不重复）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '全量巡检批次';

CREATE TABLE inspection_finding (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id BIGINT NOT NULL,
  scenario VARCHAR(16) NOT NULL,
  decl_no VARCHAR(64) NOT NULL,
  subject VARCHAR(255),
  result VARCHAR(8) NOT NULL COMMENT 'PASS/RED/YELLOW',
  hits JSON NOT NULL,
  form_snapshot JSON NOT NULL COMMENT '申报字段快照，供建议修正对照',
  KEY idx_run (run_id)
) COMMENT '全量巡检明细';
