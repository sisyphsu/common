local params = {}
local off = 1
for i = 1, #KEYS do
    params[off] = KEYS[i]
    params[off + 1] = ARGV[1]
    off = off + 2
end
if (redis.call('MSETNX', unpack(params)) == 0) then
    return ""
end
for i = 1, #KEYS do
    redis.call('EXPIRE', KEYS[i], ARGV[2])
end
return "ok"