-- =====================================================================
-- 秒杀原子脚本：活动校验 + 库存扣减 + 一人一单(限购) + 结果标记 + 消息投递
-- 之所以全部放在一个脚本里：Redis 单线程执行 Lua，中间不会被其他请求插入，
-- 因此不需要额外加锁就能保证「不超卖、不重复下单」
--
-- ARGV[1] activityId
-- ARGV[2] skuId
-- ARGV[3] userId
-- ARGV[4] orderNo
-- ARGV[5] resultKey        （seckill:result:{orderNo}）
-- ARGV[6] resultTtlSeconds （结果缓存有效期，秒）
--
-- 返回值：0 抢购成功 / 1 已售罄 / 2 超出限购 / 3 活动未开始 / 4 活动已结束
-- =====================================================================

local activityId = ARGV[1]
local skuId      = ARGV[2]
local userId     = ARGV[3]
local orderNo    = ARGV[4]
local resultKey  = ARGV[5]
local resultTtl  = tonumber(ARGV[6] or '1800')
if resultTtl == nil or resultTtl <= 0 then
    resultTtl = 1800
end

local stockKey   = 'seckill:stock:'   .. activityId .. ':' .. skuId
local userKey    = 'seckill:user:'    .. activityId .. ':' .. skuId
local soldoutKey = 'seckill:soldout:' .. activityId .. ':' .. skuId
local limitKey   = 'seckill:limit:'   .. activityId .. ':' .. skuId
local beginKey   = 'seckill:begin:'   .. activityId
local endKey     = 'seckill:end:'     .. activityId

-- 1. 售罄标记：活动结束后绝大多数请求在这里就被挡掉，不再执行后续判断
if redis.call('exists', soldoutKey) == 1 then
    return 1
end

-- 2. 活动时间校验（预热到 Redis，避免秒杀主链路查数据库）
local now = tonumber(redis.call('time')[1])
local beginTime = tonumber(redis.call('get', beginKey) or '0')
local endTime   = tonumber(redis.call('get', endKey) or '0')
if beginTime > 0 and now < beginTime then
    return 3
end
if endTime > 0 and now > endTime then
    return 4
end

-- key 的保底 TTL：活动结束后 1 小时；没有预热数据时默认 1 天
local keyTtl = 86400
if endTime > 0 then
    keyTtl = endTime + 3600 - now
    if keyTtl < 1 then
        keyTtl = 1
    end
end

-- 3. 库存校验
local stock = tonumber(redis.call('get', stockKey) or '0')
if stock <= 0 then
    redis.call('set', soldoutKey, '1', 'EX', keyTtl)
    return 1
end

-- 4. 一人一单（支持每人限购 N 件）
local limit  = tonumber(redis.call('get', limitKey) or '1')
local bought = tonumber(redis.call('hget', userKey, userId) or '0')
if bought >= limit then
    return 2
end

-- 5. 扣减库存 + 记录该用户已购数量
redis.call('decrby', stockKey, 1)
redis.call('hincrby', userKey, userId, 1)
redis.call('expire', userKey, keyTtl)
if stock - 1 <= 0 then
    redis.call('set', soldoutKey, '1', 'EX', keyTtl)
end

-- 6. 先写结果标记，再投递异步下单消息。
--    结果 key 和消息在同一个脚本里写，消费者不可能在 PROCESSING 之前写入 SUCCESS。
redis.call('set', resultKey, 'PROCESSING', 'EX', resultTtl)
redis.call('xadd', 'stream.seckill.order', 'MAXLEN', '~', 10000, '*',
        'orderNo', orderNo,
        'userId', userId,
        'activityId', activityId,
        'skuId', skuId)

return 0
