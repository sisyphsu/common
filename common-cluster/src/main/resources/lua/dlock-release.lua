local token = ARGV[1]
if (token == nil) then
    return { 'need token' }
end
local result = {}
local vals redis.call('MGET', unpack(KEYS))
for i = 1, #vals do
    if (vals[i] == token) then
        result[result.len] = ''
    else
    end
end

for i = 1, #KEYS do
    redis.call('EXPIRE', KEYS[i], expire)
end
return 'ok'