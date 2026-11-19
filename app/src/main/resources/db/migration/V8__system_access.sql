-- 系统管理：用户、角色，以及菜单 / 知识库访问权限。
CREATE TABLE sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '角色';

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(64) NOT NULL,
  role_id BIGINT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '用户';

CREATE TABLE sys_role_menu (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  menu_key VARCHAR(64) NOT NULL,
  UNIQUE KEY uk_role_menu (role_id, menu_key)
) COMMENT '角色可访问的菜单';

CREATE TABLE sys_role_library (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  library_id BIGINT NOT NULL,
  UNIQUE KEY uk_role_lib (role_id, library_id)
) COMMENT '角色可访问的知识库（子政策法规库）';

INSERT INTO sys_role (code, name, remark) VALUES
('admin', '系统管理员', '全部菜单与知识库'),
('officer', '合规专员', '业务菜单，不含系统管理'),
('lease', '租赁查阅', '仅可访问资产租赁知识库');

INSERT INTO sys_user (username, display_name, role_id, enabled)
SELECT 'lin', '林衡', id, 1 FROM sys_role WHERE code = 'admin';
INSERT INTO sys_user (username, display_name, role_id, enabled)
SELECT 'zhou', '周宁', id, 1 FROM sys_role WHERE code = 'officer';
INSERT INTO sys_user (username, display_name, role_id, enabled)
SELECT 'chen', '陈禾', id, 1 FROM sys_role WHERE code = 'lease';

INSERT INTO sys_role_menu (role_id, menu_key)
SELECT r.id, m.k FROM sys_role r
JOIN (
  SELECT '/' k UNION ALL SELECT '/policies' UNION ALL SELECT '/rules'
  UNION ALL SELECT '/precheck' UNION ALL SELECT '/inspection'
  UNION ALL SELECT '/risks' UNION ALL SELECT '/tickets' UNION ALL SELECT '/assistant'
  UNION ALL SELECT '/system/users' UNION ALL SELECT '/system/roles'
) m WHERE r.code = 'admin';

INSERT INTO sys_role_menu (role_id, menu_key)
SELECT r.id, m.k FROM sys_role r
JOIN (
  SELECT '/' k UNION ALL SELECT '/policies' UNION ALL SELECT '/rules'
  UNION ALL SELECT '/precheck' UNION ALL SELECT '/inspection'
  UNION ALL SELECT '/risks' UNION ALL SELECT '/tickets' UNION ALL SELECT '/assistant'
) m WHERE r.code = 'officer';

INSERT INTO sys_role_menu (role_id, menu_key)
SELECT r.id, m.k FROM sys_role r
JOIN (
  SELECT '/' k UNION ALL SELECT '/policies' UNION ALL SELECT '/assistant'
) m WHERE r.code = 'lease';

INSERT INTO sys_role_library (role_id, library_id)
SELECT r.id, l.id FROM sys_role r JOIN policy_library l WHERE r.code IN ('admin', 'officer');

INSERT INTO sys_role_library (role_id, library_id)
SELECT r.id, l.id FROM sys_role r JOIN policy_library l
WHERE r.code = 'lease' AND l.name = '资产租赁';
