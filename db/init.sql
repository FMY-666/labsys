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
  `enabled`    tinyint      NOT NULL DEFAULT 1                     COMMENT '1=启用，0=停用（停用后令牌立即失效）',
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
--    角色不在用户表里，由下面的 sys_user_role 决定
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_user` (`username`, `password`) VALUES
  ('admin',   '$2a$10$S0sz2qc7NSX7hKI4xRKUce2fO4x0rRVTGq..99Y33OUTICJt3NZOq'),
  ('student', '$2a$10$S0sz2qc7NSX7hKI4xRKUce2fO4x0rRVTGq..99Y33OUTICJt3NZOq'),
  ('keeper',  '$2a$10$S0sz2qc7NSX7hKI4xRKUce2fO4x0rRVTGq..99Y33OUTICJt3NZOq');

-- ------------------------------------------------------------
-- 5. 示例设备数据
-- ------------------------------------------------------------
INSERT IGNORE INTO `device` (`device_no`, `name`, `category`, `status`, `location`, `brand`, `model`) VALUES
  ('NET-SW-0001', '三层核心交换机', '交换机', 'AVAILABLE', 'A栋301机房-A机柜', 'H3C',    'S5560X'),
  ('NET-RT-0001', '出口路由器',     '路由器', 'BORROWED',  'A栋301机房-B机柜', 'Huawei', 'AR6300'),
  ('SRV-0001',    '实验用服务器',   '服务器', 'REPAIRING', 'A栋302机房-C机柜', 'Dell',   'PowerEdge R750');

-- ------------------------------------------------------------
-- 6. 角色表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`        bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code` varchar(50) NOT NULL                COMMENT '程序判断用的英文标识',
  `role_name` varchar(50) NOT NULL                COMMENT '中文名',
  `enabled`   tinyint(1)  NOT NULL DEFAULT 1      COMMENT '1 启用 0 停用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ------------------------------------------------------------
-- 7. 菜单表（parent_id 指向本表的 id，顶级统一填 0）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`  bigint      NOT NULL DEFAULT 0      COMMENT '父菜单 id，顶级为 0',
  `menu_name`  varchar(50) NOT NULL                COMMENT '菜单名称',
  `path`       varchar(100)         DEFAULT NULL   COMMENT '前端路由地址，目录节点为空',
  `sort_order` int         NOT NULL DEFAULT 0      COMMENT '排序，数字小的在前',
  `visible`    tinyint(1)  NOT NULL DEFAULT 1      COMMENT '1 显示 0 隐藏',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- ------------------------------------------------------------
-- 8. 两张关联表（多对多没有别的表示方法，只能拆中间表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id`      bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL                COMMENT '用户 id',
  `role_id` bigint NOT NULL                COMMENT '角色 id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `id`      bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` bigint NOT NULL                COMMENT '角色 id',
  `menu_id` bigint NOT NULL                COMMENT '菜单 id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- ------------------------------------------------------------
-- 9. 操作日志表（username 冗余，用户改名后历史记录仍显示当时的名字）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `id`         bigint       NOT NULL AUTO_INCREMENT                COMMENT '主键',
  `user_id`    bigint       NOT NULL                               COMMENT '操作人 id',
  `username`   varchar(50)           DEFAULT NULL                  COMMENT '操作人登录名，审计用冗余字段',
  `method`     varchar(10)  NOT NULL                               COMMENT 'HTTP 方法',
  `path`       varchar(200) NOT NULL                               COMMENT '请求路径',
  `ip`         varchar(50)           DEFAULT NULL                  COMMENT '来源 IP',
  `success`    tinyint(1)   NOT NULL DEFAULT 1                     COMMENT '1 成功 0 失败',
  `cost_ms`    int                   DEFAULT NULL                  COMMENT '耗时（毫秒）',
  `created_at` datetime              DEFAULT CURRENT_TIMESTAMP     COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_oper_created_at` (`created_at`),
  KEY `idx_oper_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ------------------------------------------------------------
-- 10. 权限初始数据
--    菜单 id 写死，因为 parent_id 要引用它们
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_role` (`id`, `role_code`, `role_name`, `enabled`) VALUES
  (1, 'ADMIN',   '系统管理员', 1),
  (2, 'KEEPER',  '实验员',     1),
  (3, 'STUDENT', '学生',       1);

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `sort_order`, `visible`) VALUES
  -- 一级：单页直接挂顶层，分组目录的 path 为 NULL（本身不跳转，只用来折叠子菜单）
  -- 分组按业务域切分，而不是按“谁在用”切分：同一个业务的学生视角和管理视角放在一起，
  -- 各角色看到的是同一个分组下不同的子集。
  (1,  0,  '设备台账', '/devices',               1, 1),
  -- 数据看板统计的是设备和工单，跟业务分组不是一类东西，所以单独占一个一级位置，
  -- 排在设备台账后面：先看台账，再看整体状况。
  (10, 0,  '数据看板', '/dashboard',             2, 1),
  (12, 0,  '预约',     NULL,                     3, 1),
  (13, 0,  '报修',     NULL,                     4, 1),
  (4,  0,  '系统管理', NULL,                     5, 1),
  -- 预约：学生只看自己的，实验员/管理员管全量
  (2,  12, '我的预约', '/my-reservations',       1, 1),
  (3,  12, '预约管理', '/reservations',          2, 1),
  (11, 12, '预约归档', '/reservation-archive',   3, 1),
  -- 报修
  (9,  13, '我的报修', '/my-tickets',            1, 1),
  (8,  13, '工单管理', '/tickets',               2, 1),
  -- 系统管理（仅管理员）
  (5,  4,  '用户管理', '/users',                 1, 1),
  (6,  4,  '角色管理', '/roles',                 2, 1),
  (7,  4,  '操作日志', '/logs',                  3, 1),
  -- 学生专用的平铺入口：学生的侧边栏不分层级，三个页面直接摆在一级。
  -- parent_id 是全局字段，没法让同一行对不同角色呈现不同层级，所以只能另建两行；
  -- 两行的 path 与 (2)/(9) 相同，指向同一个前端组件，区别只在于挂在哪一层。
  (14, 0,  '我的预约', '/my-reservations',       2, 1),
  (15, 0,  '我的报修', '/my-tickets',            3, 1);

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`) VALUES
  (1, 1),  -- admin   -> 系统管理员
  (2, 3),  -- student -> 学生
  (3, 2);  -- keeper  -> 实验员

-- 注意：分组目录也必须授权，否则子菜单挂不上去（会变成孤儿节点被丢掉）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
  -- 系统管理员：全部分组 + 全部页面
  (1, 1), (1, 12), (1, 13), (1, 10), (1, 4),
  (1, 2), (1, 3), (1, 11),
  (1, 9), (1, 8),
  (1, 5), (1, 6), (1, 7),
  -- 实验员：设备台账 + 数据看板 + 两个分组的全部管理页。
  -- 只授 (2,4) 这种只有目录没有子项的授权不要加：目录会拼出一个空分组，
  -- 前端拿到 children 为空、path 为 NULL 的节点，会渲染成一个点了没反应的菜单项。
  (2, 1), (2, 10), (2, 12), (2, 13),
  (2, 3), (2, 11),
  (2, 9), (2, 8),
  -- 学生：设备台账 + 自己的两个页面，走 (14)/(15) 这两个平铺行，不授任何分组目录。
  -- 如果错授成 (3,2)/(3,9)，那两个菜单挂在 (12)/(13) 下，没有目录就无法拼树，会被直接丢掉。
  (3, 1), (3, 14), (3, 15);

-- ------------------------------------------------------------
-- 11. 设备预约表（第四部分）
--     一行 = 一次预约申请，占用 [start_time, end_time) 这个左闭右开区间。
--     device 管“这台机器是什么”，reservation 管“它在什么时候归谁”——
--     单个 status 字段装不下未来的一张时间表，所以必须单独建表。
--     status 生命周期：
--       PENDING 待审批 → APPROVED 已通过 → BORROWED 已借出 → RETURNED 已归还
--       PENDING 也可转 REJECTED（被拒）或 CANCELLED（自己取消）
--     approver_id 在审批之前没有值，因此允许 NULL。
--     这里刻意没有加“同一时段唯一”约束：区间重叠表达不成等值唯一索引，
--     只能靠索引加速判断 + 3-6 的锁来防超借。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reservation` (
  `id`          bigint      NOT NULL AUTO_INCREMENT             COMMENT '主键',
  `user_id`     bigint      NOT NULL                            COMMENT '预约人，对应 sys_user.id',
  `device_id`   bigint      NOT NULL                            COMMENT '被预约设备，对应 device.id',
  `start_time`  datetime    NOT NULL                            COMMENT '占用开始时间',
  `end_time`    datetime    NOT NULL                            COMMENT '占用结束时间',
  `status`      varchar(20) NOT NULL DEFAULT 'PENDING'          COMMENT 'PENDING待审批/APPROVED已通过/BORROWED使用中/RETURNING待确认归还/RETURNED已归还/REJECTED已拒绝/CANCELLED已取消/OVERDUE超时未还',
  `purpose`     varchar(255)         DEFAULT NULL               COMMENT '预约用途',
  `approver_id` bigint               DEFAULT NULL               COMMENT '审批人，对应 sys_user.id',
  `created_at`  datetime             DEFAULT CURRENT_TIMESTAMP  COMMENT '申请时间',
  `return_requested_at` datetime     DEFAULT NULL               COMMENT '借用人申请归还的时间',
  `returned_at` datetime             DEFAULT NULL               COMMENT '管理员确认归还的时间',
  -- 超时违规只记一次：定时任务靠这一位防止同一条预约反复扣分
  `overdue_violation` tinyint NOT NULL DEFAULT 0                COMMENT '1=这条预约已经记过一次超时违规',
  PRIMARY KEY (`id`),
  -- 冲突检测按 (device_id, 时间段) 查，这条索引让区间查询走索引而不是全表扫描
  KEY `idx_res_device_time` (`device_id`, `start_time`, `end_time`),
  KEY `idx_res_user` (`user_id`),
  KEY `idx_res_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备预约表';

-- ------------------------------------------------------------
-- 12. 工单表（第五部分）
--     一台设备出故障 -> 报一条工单 -> 受理 -> 解决/驳回。
--     状态机见 TicketService.TRANSITIONS，非法流转在代码里拦。
--     overdue 是“超时催办”标记，由定时任务按 ticket-overdue-hours 置 1。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ticket` (
  `id`          bigint       NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `device_id`   bigint       NOT NULL                             COMMENT '故障设备，对应 device.id',
  `reporter_id` bigint       NOT NULL                             COMMENT '报修人，对应 sys_user.id',
  `fault_desc`  varchar(500) NOT NULL                             COMMENT '故障描述',
  `priority`    varchar(10)  NOT NULL DEFAULT 'NORMAL'            COMMENT 'LOW/NORMAL/HIGH/URGENT',
  `status`      varchar(20)  NOT NULL DEFAULT 'PENDING'           COMMENT 'PENDING待受理/ACCEPTED处理中/RESOLVED已解决/REJECTED已驳回',
  `handler_id`  bigint                DEFAULT NULL                COMMENT '受理人，对应 sys_user.id',
  `overdue`     tinyint(1)   NOT NULL DEFAULT 0                   COMMENT '1 表示超时未处理，由定时任务标记',
  `created_at`  datetime              DEFAULT CURRENT_TIMESTAMP   COMMENT '报修时间',
  `updated_at`  datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后变动时间',
  `resolved_at` datetime              DEFAULT NULL                COMMENT '解决时间',
  PRIMARY KEY (`id`),
  KEY `idx_ticket_status` (`status`),
  KEY `idx_ticket_device` (`device_id`),
  KEY `idx_ticket_reporter` (`reporter_id`),
  KEY `idx_ticket_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备报修工单表';

-- 处理流水：只追加不修改，一个工单能看到完整历史
CREATE TABLE IF NOT EXISTS `ticket_log` (
  `id`          bigint      NOT NULL AUTO_INCREMENT             COMMENT '主键',
  `ticket_id`   bigint      NOT NULL                            COMMENT '所属工单',
  `from_status` varchar(20)          DEFAULT NULL               COMMENT '原状态，创建时为空',
  `to_status`   varchar(20) NOT NULL                            COMMENT '新状态',
  `operator_id` bigint      NOT NULL                            COMMENT '操作人',
  `remark`      varchar(255)         DEFAULT NULL               COMMENT '处理说明',
  `created_at`  datetime             DEFAULT CURRENT_TIMESTAMP  COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_tlog_ticket` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单处理流水表';

-- ------------------------------------------------------------
-- 13. 第五部分：工单表已在上方 12 节，
--     菜单与授权统一放在第 10 节定义（含分组目录），这里不再重复插入。
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- 14. 预约信用（超时惩罚）
--     一人一行，没有违规记录的人不建行（查不到就当默认值 100 分处理）。
--     刻意不存 score 列：分数由 violations 算出来（100 - 10 × 违规次数），
--     存两份状态迟早对不上 —— 抵消一次违规时分数要跟着变，漏改一处就出现
--     “分数说 80 但只警告过一次”的矛盾。
--
--     规则（CreditService 里实现）：
--       违规 1 次        -> 警告
--       违规 2、3 次     -> 禁止预约 24 小时（banned_until = 违规时刻 + 1 天）
--       违规 4 次及以上  -> permanent_ban = 1，必须管理员解禁
--       按时归还累计 3 次 -> violations - 1，on_time_streak 归零
--       违规 ≥ 2 次      -> 单次最长预约时长降为 1/4
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_credit` (
  `user_id`        bigint   NOT NULL                            COMMENT '对应 sys_user.id，主键即用户',
  `violations`     int      NOT NULL DEFAULT 0                  COMMENT '当前未抵消的违规次数',
  `on_time_streak` int      NOT NULL DEFAULT 0                  COMMENT '距上次抵消已累计的按时归还次数 0~2',
  `banned_until`   datetime          DEFAULT NULL               COMMENT '禁止预约的截止时间，NULL 表示没禁',
  `permanent_ban`  tinyint  NOT NULL DEFAULT 0                  COMMENT '1=封禁，必须管理员解禁',
  `updated_at`     datetime          DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约信用与违规记录';

-- ------------------------------------------------------------
-- 15. 设备分类 / 存放位置字典
--     之前这两个字段是自由文本，"网络设备" 和 "网络" 会变成两类，筛选就废了。
--     没有单独的字典管理页：新增设备时下拉框允许直接输入新值，
--     后端保存设备时顺手把新值记进来，字典跟着实际使用自然长大。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `device_dict` (
  `id`    bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type`  varchar(20) NOT NULL                COMMENT 'CATEGORY 分类 / LOCATION 存放位置',
  `value` varchar(50) NOT NULL                COMMENT '取值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_value` (`type`, `value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分类与位置字典';

-- 用现有设备数据把字典初始化，避免升级后下拉框是空的
INSERT IGNORE INTO `device_dict` (`type`, `value`)
  SELECT 'CATEGORY', `category` FROM `device` WHERE `category` IS NOT NULL AND `category` <> '';
INSERT IGNORE INTO `device_dict` (`type`, `value`)
  SELECT 'LOCATION', `location` FROM `device` WHERE `location` IS NOT NULL AND `location` <> '';


