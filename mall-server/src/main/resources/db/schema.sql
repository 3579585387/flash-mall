-- =====================================================================
-- FlashMall 闪购商城 —— 建表脚本
-- 说明：全部使用 IF NOT EXISTS，可重复执行（配合 spring.sql.init.mode=always）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 用户
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_user`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone`       VARCHAR(20) NOT NULL COMMENT '手机号',
    `nick_name`   VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `icon`        VARCHAR(512) DEFAULT NULL COMMENT '头像',
    `openid`      VARCHAR(64)  DEFAULT NULL COMMENT '微信 openid',
    `create_time` DATETIME     DEFAULT NULL,
    `update_time` DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ---------------------------------------------------------------------
-- 商家后台账号
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_employee`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `username`    VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`    VARCHAR(64)  NOT NULL COMMENT '密码(MD5)',
    `name`        VARCHAR(64)  NOT NULL COMMENT '姓名',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    `create_time` DATETIME     DEFAULT NULL,
    `update_time` DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商家账号';

-- ---------------------------------------------------------------------
-- 分类
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_category`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(64) NOT NULL COMMENT '分类名称',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT NULL,
    `update_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品分类';

-- ---------------------------------------------------------------------
-- 商品 SPU
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_goods`
(
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `merchant_id` BIGINT        NOT NULL DEFAULT 1 COMMENT '商家id',
    `category_id` BIGINT        NOT NULL COMMENT '分类id',
    `name`        VARCHAR(128)  NOT NULL COMMENT '商品名称',
    `sub_title`   VARCHAR(256)  DEFAULT NULL COMMENT '副标题',
    `cover_url`   VARCHAR(512)  DEFAULT NULL COMMENT '主图',
    `detail`      TEXT COMMENT '详情',
    `price`       DECIMAL(10, 2) NOT NULL COMMENT '展示价',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    `sales`       INT           NOT NULL DEFAULT 0 COMMENT '累计销量',
    `create_time` DATETIME      DEFAULT NULL,
    `update_time` DATETIME      DEFAULT NULL,
    `create_user` BIGINT        DEFAULT NULL,
    `update_user` BIGINT        DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_category_status` (`category_id`, `status`),
    KEY `idx_merchant` (`merchant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品SPU';

-- ---------------------------------------------------------------------
-- 商品 SKU（真实库存单位）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_goods_sku`
(
    `id`        BIGINT        NOT NULL AUTO_INCREMENT,
    `goods_id`  BIGINT        NOT NULL,
    `sku_code`  VARCHAR(64)   NOT NULL COMMENT 'SKU编码',
    `spec_json` VARCHAR(512)  NOT NULL DEFAULT '{}' COMMENT '规格快照',
    `price`     DECIMAL(10, 2) NOT NULL COMMENT '售价',
    `stock`     INT           NOT NULL DEFAULT 0 COMMENT '真实库存',
    `version`   INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `status`    TINYINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_code` (`sku_code`),
    KEY `idx_goods` (`goods_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品SKU';

-- ---------------------------------------------------------------------
-- 秒杀活动
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_seckill_activity`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `title`       VARCHAR(128) NOT NULL COMMENT '活动标题',
    `start_time`  DATETIME     NOT NULL,
    `end_time`    DATETIME     NOT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0未开始 1进行中 2已结束',
    `create_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_time` (`start_time`, `end_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀活动';

-- ---------------------------------------------------------------------
-- 秒杀商品（活动 × SKU，秒杀库存与真实库存解耦）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_seckill_sku`
(
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `activity_id`    BIGINT        NOT NULL,
    `sku_id`         BIGINT        NOT NULL,
    `goods_id`       BIGINT        NOT NULL,
    `seckill_price`  DECIMAL(10, 2) NOT NULL COMMENT '秒杀价',
    `seckill_stock`  INT           NOT NULL COMMENT '秒杀库存',
    `limit_per_user` INT           NOT NULL DEFAULT 1 COMMENT '每人限购',
    `version`        INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_sku` (`activity_id`, `sku_id`),
    KEY `idx_sku` (`sku_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀商品';

-- ---------------------------------------------------------------------
-- 订单主表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_orders`
(
    `id`             BIGINT         NOT NULL COMMENT '订单id(RedisIdWorker生成)',
    `order_no`       VARCHAR(32)    NOT NULL COMMENT '业务订单号',
    `user_id`        BIGINT         NOT NULL,
    `merchant_id`    BIGINT         NOT NULL DEFAULT 1,
    `activity_id`    BIGINT         DEFAULT NULL COMMENT '秒杀活动id',
    `total_amount`   DECIMAL(10, 2) NOT NULL COMMENT '商品总额',
    `pay_amount`     DECIMAL(10, 2) NOT NULL COMMENT '实付金额',
    `status`         TINYINT        NOT NULL DEFAULT 0 COMMENT '0待支付 1待接单 2已接单 3派送中 4已完成 5已取消 6退款中 7已退款',
    `pay_status`     TINYINT        NOT NULL DEFAULT 0 COMMENT '0未支付 1已支付 2已退款',
    `pay_method`     TINYINT        DEFAULT NULL COMMENT '1微信 2支付宝 3余额',
    `receiver_name`  VARCHAR(64)    NOT NULL,
    `receiver_phone` VARCHAR(20)    NOT NULL,
    `receiver_addr`  VARCHAR(512)   NOT NULL,
    `remark`         VARCHAR(256)   DEFAULT NULL,
    `pay_time`       DATETIME       DEFAULT NULL,
    `cancel_time`    DATETIME       DEFAULT NULL,
    `cancel_reason`  VARCHAR(256)   DEFAULT NULL,
    `create_time`    DATETIME       NOT NULL,
    `update_time`    DATETIME       NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_merchant_status_time` (`merchant_id`, `status`, `create_time`),
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单主表';

-- ---------------------------------------------------------------------
-- 订单明细（商品信息快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_order_item`
(
    `id`         BIGINT         NOT NULL AUTO_INCREMENT,
    `order_id`   BIGINT         NOT NULL,
    `sku_id`     BIGINT         NOT NULL,
    `goods_id`   BIGINT         NOT NULL,
    `goods_name` VARCHAR(128)   NOT NULL COMMENT '快照:商品名',
    `spec_json`  VARCHAR(512)   NOT NULL DEFAULT '{}' COMMENT '快照:规格',
    `cover_url`  VARCHAR(512)   DEFAULT NULL,
    `price`      DECIMAL(10, 2) NOT NULL COMMENT '快照:成交单价',
    `quantity`   INT            NOT NULL,
    `amount`     DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细';

-- ---------------------------------------------------------------------
-- 购物车
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_cart`
(
    `id`          BIGINT         NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT         NOT NULL,
    `sku_id`      BIGINT         NOT NULL,
    `goods_id`    BIGINT         NOT NULL,
    `goods_name`  VARCHAR(128)   NOT NULL,
    `spec_json`   VARCHAR(512)   NOT NULL DEFAULT '{}',
    `cover_url`   VARCHAR(512)   DEFAULT NULL,
    `price`       DECIMAL(10, 2) NOT NULL,
    `quantity`    INT            NOT NULL,
    `create_time` DATETIME       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='购物车';

-- ---------------------------------------------------------------------
-- 库存流水（幂等的关键：biz_key 唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_stock_log`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `biz_key`     VARCHAR(64) NOT NULL COMMENT '幂等键',
    `sku_id`      BIGINT      NOT NULL,
    `activity_id` BIGINT      DEFAULT NULL,
    `change_qty`  INT         NOT NULL COMMENT '正数回补 负数扣减',
    `type`        TINYINT     NOT NULL COMMENT '1下单扣减 2取消回补 3失败回补',
    `create_time` DATETIME    NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_key` (`biz_key`),
    KEY `idx_sku_time` (`sku_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='库存变更流水';

-- ---------------------------------------------------------------------
-- 消息幂等表（消费去重）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_mq_message`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `msg_id`      VARCHAR(64) NOT NULL COMMENT '消息唯一标识',
    `topic`       VARCHAR(64) NOT NULL,
    `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0处理中 1成功 2失败',
    `retry_count` INT         NOT NULL DEFAULT 0,
    `create_time` DATETIME    NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_id` (`msg_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='消息幂等表';

-- ---------------------------------------------------------------------
-- 秒杀下单去重记录（历史表，仅用于旧数据回填；新逻辑已改用 tb_seckill_user_count）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_seckill_order_record`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `activity_id` BIGINT      NOT NULL,
    `sku_id`      BIGINT      NOT NULL,
    `user_id`     BIGINT      NOT NULL,
    `order_no`    VARCHAR(32) NOT NULL,
    `create_time` DATETIME    NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_sku_user` (`activity_id`, `sku_id`, `user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀下单去重(历史表)';

-- ---------------------------------------------------------------------
-- 秒杀用户购买计数（每人限购 N 件的数据库最终兜底）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_seckill_user_count`
(
    `id`           BIGINT   NOT NULL AUTO_INCREMENT,
    `activity_id`  BIGINT   NOT NULL,
    `sku_id`       BIGINT   NOT NULL,
    `user_id`      BIGINT   NOT NULL,
    `bought_count` INT      NOT NULL DEFAULT 0 COMMENT '已购数量',
    `create_time`  DATETIME NOT NULL,
    `update_time`  DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_sku_user` (`activity_id`, `sku_id`, `user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀用户购买计数';

-- ---------------------------------------------------------------------
-- 支付流水（回调幂等：order_no + channel 唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_payment`
(
    `id`            BIGINT         NOT NULL AUTO_INCREMENT,
    `order_no`      VARCHAR(32)    NOT NULL,
    `trade_no`      VARCHAR(64)    DEFAULT NULL COMMENT '第三方交易号',
    `channel`       TINYINT        NOT NULL DEFAULT 1 COMMENT '1微信',
    `amount`        DECIMAL(10, 2) NOT NULL,
    `status`        TINYINT        NOT NULL DEFAULT 0 COMMENT '0待处理 1成功',
    `callback_time` DATETIME       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_channel` (`order_no`, `channel`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='支付流水';
