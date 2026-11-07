-- =====================================================================
-- FlashMall 初始化数据（可重复执行，全部 INSERT IGNORE）
-- 说明：只在数据不存在时插入，不会覆盖你手工改过的数据
-- 后台账号：admin / 123456     密码为 MD5(123456)
-- =====================================================================

INSERT IGNORE INTO `tb_employee` (`id`, `username`, `password`, `name`, `status`, `create_time`, `update_time`)
VALUES (1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '管理员', 1, NOW(), NOW());

INSERT IGNORE INTO `tb_category` (`id`, `name`, `sort`, `create_time`, `update_time`)
VALUES (1, '数码家电', 1, NOW(), NOW()),
       (2, '服饰鞋包', 2, NOW(), NOW()),
       (3, '食品生鲜', 3, NOW(), NOW());

INSERT IGNORE INTO `tb_goods` (`id`, `merchant_id`, `category_id`, `name`, `sub_title`, `cover_url`, `detail`, `price`,
                               `status`, `sales`, `create_time`, `update_time`, `create_user`, `update_user`)
VALUES (1, 1, 1, '星耀 Pro 旗舰手机', '骁龙 8 Gen3 / 2K 屏 / 120W 闪充', 'https://picsum.photos/seed/phone/600/600',
        '这是旗舰手机的详情描述。', 6999.00, 1, 120, NOW(), NOW(), 1, 1),
       (2, 1, 2, '轻羽绒保暖外套', '90 白鸭绒 / 三防面料', 'https://picsum.photos/seed/coat/600/600',
        '这是羽绒服的详情描述。', 299.00, 1, 340, NOW(), NOW(), 1, 1),
       (3, 1, 3, '云南高山速溶咖啡', '冻干工艺 / 冷萃口感', 'https://picsum.photos/seed/coffee/600/600',
        '这是咖啡的详情描述。', 129.00, 1, 880, NOW(), NOW(), 1, 1),
       (4, 1, 3, '每日坚果礼盒', '7 种坚果 / 30 小包', 'https://picsum.photos/seed/nut/600/600',
        '这是坚果礼盒的详情描述。', 59.90, 1, 1520, NOW(), NOW(), 1, 1),
       (5, 1, 1, '静音空气净化器', 'CADR 600 / 除甲醛', 'https://picsum.photos/seed/purifier/600/600',
        '这是净化器的详情描述。', 1999.00, 1, 66, NOW(), NOW(), 1, 1);

INSERT IGNORE INTO `tb_goods_sku` (`id`, `goods_id`, `sku_code`, `spec_json`, `price`, `stock`, `version`, `status`)
VALUES (1, 1, 'PHONE-BLACK-256', '{"颜色":"曜石黑","容量":"256G"}', 6999.00, 100, 0, 1),
       (2, 1, 'PHONE-WHITE-512', '{"颜色":"月光白","容量":"512G"}', 7399.00, 50, 0, 1),
       (3, 2, 'COAT-BLACK-L', '{"颜色":"黑色","尺码":"L"}', 299.00, 500, 0, 1),
       (4, 3, 'COFFEE-100G', '{"规格":"100g/罐"}', 129.00, 800, 0, 1),
       (5, 4, 'NUT-30PACK', '{"规格":"30 包/盒"}', 59.90, 2000, 0, 1),
       (6, 5, 'PURIFIER-STD', '{"规格":"标准版"}', 1999.00, 60, 0, 1);

-- 演示用秒杀活动：立即开始，一年后结束（避免种子数据过期）
INSERT IGNORE INTO `tb_seckill_activity` (`id`, `title`, `start_time`, `end_time`, `status`, `create_time`)
VALUES (1, '开业狂欢 · 限时闪购', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), 1, NOW());

INSERT IGNORE INTO `tb_seckill_sku` (`id`, `activity_id`, `sku_id`, `goods_id`, `seckill_price`, `seckill_stock`,
                                     `limit_per_user`, `version`)
VALUES (1, 1, 3, 2, 99.00, 100, 1, 0),
       (2, 1, 5, 4, 9.90, 200, 2, 0);

-- 兼容旧版本：把历史去重记录回填成购买计数（新表已有数据时 INSERT IGNORE 不会覆盖）
INSERT IGNORE INTO `tb_seckill_user_count` (`activity_id`, `sku_id`, `user_id`, `bought_count`,
                                            `create_time`, `update_time`)
SELECT `activity_id`, `sku_id`, `user_id`, COUNT(*), NOW(), NOW()
FROM `tb_seckill_order_record`
GROUP BY `activity_id`, `sku_id`, `user_id`;
