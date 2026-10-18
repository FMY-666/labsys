-- ============================================================
--  labsys 数据库初始化脚本
--  用法：mysql -u root -p < db/init.sql
--  说明：脚本可重复执行（表用 IF NOT EXISTS，数据用 INSERT IGNORE），
--        已经存在的表和账号不会被覆盖。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `labsys`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `labsys`;

-- ------------------------------------------------------------
-- 1. 用户表（系统自身的表统一 sys_ 前缀）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`         bigint       NOT NULL AUTO_INCREMENT                COMMENT '主键',
  `username`   varchar(50)  NOT NULL                               COMMENT '登录名，唯一',
  `password`   varchar(100) NOT NULL                               COMMENT 'BCrypt 加密后的密码，不存明文',
  `role`       varchar(20)  NOT NULL DEFAULT 'USER'                COMMENT '角色：ADMIN / USER',
  `created_at` datetime              DEFAULT CURRENT_TIMESTAMP     COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 登录令牌表（一次登录发一张票，可一人多票）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_token` (
  `id`         bigint      NOT NULL AUTO_INCREMENT                 COMMENT '主键',
  `user_id`    bigint      NOT NULL                                COMMENT '所属用户 id',
  `token`      varchar(64) NOT NULL                                COMMENT '令牌内容（UUID）',
  `created_at` datetime             DEFAULT CURRENT_TIMESTAMP      COMMENT '签发时间',
  `expires_at` datetime    NOT NULL                                COMMENT '过期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录令牌表';

-- ------------------------------------------------------------
-- 3. 设备台账表（业务数据表不带 sys_ 前缀）
--    status 四种状态：AVAILABLE 在库 / BORROWED 借出 / REPAIRING 维修中 / SCRAPPED 已报废
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `device` (
  `id`         bigint       NOT NULL AUTO_INCREMENT                COMMENT '主键',
  `device_no`  varchar(50)  NOT NULL                               COMMENT '设备编号，唯一',
  `name`       varchar(100) NOT NULL                               COMMENT '设备名称',
  `category`   varchar(50)           DEFAULT NULL                  COMMENT '分类',
  `status`     varchar(20)  NOT NULL DEFAULT 'AVAILABLE'           COMMENT '状态：AVAILABLE/BORROWED/REPAIRING/SCRAPPED',
  `location`   varchar(100)          DEFAULT NULL                  COMMENT '存放位置',
  `brand`      varchar(50)           DEFAULT NULL                  COMMENT '品牌',
  `model`      varchar(50)           DEFAULT NULL                  COMMENT '型号',
  `created_at` datetime              DEFAULT CURRENT_TIMESTAMP     COMMENT '入库时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_no` (`device_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账表';

-- ------------------------------------------------------------
-- 4. 初始账号（密码都是 123456，存的是 BCrypt 哈希）
--    admin   = 管理员，可以增删改设备
--    student = 普通用户，只能查看
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_user` (`username`, `password`, `role`) VALUES
  ('admin',   '$2a$10$S0sz2qc7NSX7hKI4xRKUce2fO4x0rRVTGq..99Y33OUTICJt3NZOq', 'ADMIN'),
  ('student', '$2a$10$S0sz2qc7NSX7hKI4xRKUce2fO4x0rRVTGq..99Y33OUTICJt3NZOq', 'USER');

-- ------------------------------------------------------------
-- 5. 示例设备数据
-- ------------------------------------------------------------
INSERT IGNORE INTO `device` (`device_no`, `name`, `category`, `status`, `location`, `brand`, `model`) VALUES
  ('NET-SW-0001', '三层核心交换机', '交换机', 'AVAILABLE', 'A栋301机房-A机柜', 'H3C',    'S5560X'),
  ('NET-RT-0001', '出口路由器',     '路由器', 'BORROWED',  'A栋301机房-B机柜', 'Huawei', 'AR6300'),
  ('SRV-0001',    '实验用服务器',   '服务器', 'REPAIRING', 'A栋302机房-C机柜', 'Dell',   'PowerEdge R750');
