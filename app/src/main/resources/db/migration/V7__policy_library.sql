-- 政策法规库：分类、子库、有效期，以及文档分片与问答。
CREATE TABLE policy_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '政策分类';

CREATE TABLE policy_library (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_category (category_id)
) COMMENT '子政策法规库';

ALTER TABLE policy
  ADD COLUMN library_id BIGINT NULL AFTER id,
  ADD COLUMN expire_date DATE NULL COMMENT '失效日期，空表示长期有效' AFTER effect_date;

INSERT INTO policy_category (name) VALUES ('三资监管'), ('未分类');

INSERT INTO policy_library (category_id, name, description)
SELECT c.id, '资产处置', '资产进场交易相关法规' FROM policy_category c WHERE c.name = '三资监管';
INSERT INTO policy_library (category_id, name, description)
SELECT c.id, '对外担保', '担保与资金相关法规' FROM policy_category c WHERE c.name = '三资监管';
INSERT INTO policy_library (category_id, name, description)
SELECT c.id, '资产租赁', '资源租赁相关法规' FROM policy_category c WHERE c.name = '三资监管';
INSERT INTO policy_library (category_id, name, description)
SELECT c.id, '未归类', '尚未指定子库的政策' FROM policy_category c WHERE c.name = '未分类';

UPDATE policy SET library_id = (SELECT id FROM policy_library WHERE name = '对外担保' LIMIT 1)
WHERE title LIKE '%担保%';
UPDATE policy SET library_id = (SELECT id FROM policy_library WHERE name = '资产租赁' LIMIT 1)
WHERE title LIKE '%租赁%';
UPDATE policy SET library_id = (SELECT id FROM policy_library WHERE name = '资产处置' LIMIT 1)
WHERE title LIKE '%处置%' AND library_id IS NULL;
UPDATE policy SET library_id = (SELECT id FROM policy_library WHERE name = '未归类' LIMIT 1)
WHERE library_id IS NULL;

CREATE TABLE policy_chunk (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  seq INT NOT NULL,
  heading VARCHAR(255),
  content TEXT NOT NULL,
  char_count INT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_policy (policy_id)
) COMMENT '政策分片';

CREATE TABLE policy_qa (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  chunk_id BIGINT NULL,
  question VARCHAR(512) NOT NULL,
  answer TEXT NOT NULL,
  source VARCHAR(16) NOT NULL DEFAULT 'ai' COMMENT 'ai/human',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_policy (policy_id)
) COMMENT '政策问答对';
