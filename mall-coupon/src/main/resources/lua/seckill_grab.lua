-- 秒杀原子抢购脚本。KEYS[1]=库存计数key，KEYS[2]=该场次已抢购用户集合key，ARGV[1]=memberId
-- Redis 单个 Lua 脚本内的操作是原子的，不会有"先查后改"之间被别的请求插进来的竞态。
-- 返回值：1=抢购成功  0=已售罄  -1=该用户已经抢过  -2=活动不存在（没预热到 Redis 或已下线）
local stockKey = KEYS[1]
local userKey = KEYS[2]
local memberId = ARGV[1]

if redis.call('SISMEMBER', userKey, memberId) == 1 then
    return -1
end

local stock = redis.call('GET', stockKey)
if stock == false then
    return -2
end

if tonumber(stock) <= 0 then
    return 0
end

redis.call('DECR', stockKey)
redis.call('SADD', userKey, memberId)
return 1
