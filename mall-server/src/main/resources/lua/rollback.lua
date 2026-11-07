-- =====================================================================
-- 秒杀失败/取消回补脚本：把 Redis 库存、用户已购数量、售罄标记恢复回去
-- 必须与扣减逻辑对称，否则会出现「库存被永久占用、实际卖不出去」的幽灵库存
--
-- KEYS[1] 库存 key
-- KEYS[2] 用户已购 hash key
-- KEYS[3] 售罄标记 key
-- ARGV[1] userId
-- ARGV[2] quantity（默认 1）
-- =====================================================================

local stockKey   = KEYS[1]
local userKey    = KEYS[2]
local soldoutKey = KEYS[3]
local userId     = ARGV[1]
local quantity   = tonumber(ARGV[2] or '1')
if quantity == nil or quantity <= 0 then
    quantity = 1
end

if redis.call('exists', stockKey) == 1 then
    redis.call('incrby', stockKey, quantity)
end

-- 回补后把用户已购数量减回去；减到 0 时直接删除字段，避免 hash 里堆积无用字段
local remain = redis.call('hincrby', userKey, userId, -quantity)
if remain <= 0 then
    redis.call('hdel', userKey, userId)
end

-- 回补后一定还有库存，清掉售罄标记，否则后续请求会被误挡
redis.call('del', soldoutKey)

return 1
