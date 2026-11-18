-- M3扩展：规则场景隔离（三资三场景各自评估，防止跨场景误命中）
ALTER TABLE rule ADD COLUMN scenario VARCHAR(16) NOT NULL DEFAULT 'asset' COMMENT '场景：asset资产处置/guarantee对外担保/lease资产租赁';
UPDATE rule SET scenario='guarantee' WHERE code LIKE 'G%';
UPDATE rule SET scenario='lease' WHERE code LIKE 'L%';
