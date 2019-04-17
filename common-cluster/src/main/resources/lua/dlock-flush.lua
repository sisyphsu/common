local token = ARGV[1]
local expire = ARGV[2]
if (token == nil) then
    return { 'need token' }
end
if (expire == nil) then
    return { 'need expire' }
end
local vals redis.call('MGET', unpack(KEYS))
for i = 1, #vals do
    if not (vals[i] == token) then
        return { 'token error: ' + vals[i], KEYS[i] }
    end
end

for i = 1, #KEYS do
    redis.call('EXPIRE', KEYS[i], expire)
end
return {}