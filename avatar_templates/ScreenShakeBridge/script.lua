-- Screen Shake Bridge
-- Both players need this avatar script, the same CHANNEL, and the same SHARED_KEY.
-- It cannot shake players who do not opt in by running this same bridge handler.

local CHANNEL = "tas3ra.screen_shake.v1"
local SHARED_KEY = "CHANGE_THIS_SHARED_KEY_WITH_YOUR_FRIENDS"

local TRIGGER_KEY = "key.keyboard.v"
local LOOK_DISTANCE = 16

local DEFAULT_STRENGTH = 0.65
local DEFAULT_DURATION = 16
local MAX_STRENGTH = 1.25
local MAX_DURATION = 40
local MIN_TICKS_BETWEEN_SHAKES_FROM_SAME_PLAYER = 20

local bridge = require("modular_bridge")

bridge.configure({
   channel = CHANNEL,
   key = SHARED_KEY,
   requireSignature = true,
   requireCapability = true,
   maxPayload = 512,
   rateWindowTicks = 20,
   maxPerWindow = 3,
})

local EVENT_SHAKE = "camera.shake"
local lastShakeFrom = {}

local shake = {
   active = false,
   age = 0,
   left = 0,
   duration = 0,
   strength = 0,
   seed = 0,
}

local function now()
   local ok, value = pcall(function() return world:getTime(0) end)
   if ok and type(value) == "number" then return value end
   ok, value = pcall(function() return world.getTime(0) end)
   if ok and type(value) == "number" then return value end
   return 0
end

local function clamp(value, minValue, maxValue, fallback)
   value = tonumber(value)
   if value == nil or value ~= value then value = fallback end
   if value < minValue then return minValue end
   if value > maxValue then return maxValue end
   return value
end

local function resetCamera()
   renderer:setOffsetCameraRot(nil)
   renderer:setCameraPos(nil)
end

local function startShake(strength, duration)
   strength = clamp(strength, 0, MAX_STRENGTH, DEFAULT_STRENGTH)
   duration = math.floor(clamp(duration, 1, MAX_DURATION, DEFAULT_DURATION))
   if strength <= 0 or duration <= 0 then return false end

   shake.active = true
   shake.age = 0
   shake.left = duration
   shake.duration = duration
   shake.strength = strength
   shake.seed = math.random() * 1000
   return true
end

local function safeEntityUUID(entity)
   if not entity then return nil end

   if type(entity) == "table" then
      entity = entity[1]
   end

   local ok, isPlayer = pcall(function() return entity:isPlayer() end)
   if not ok or not isPlayer then return nil end

   local uuid
   ok, uuid = pcall(function() return entity:getUUID() end)
   if ok then return uuid end
   return nil
end

local function lookedAtPlayerUUID()
   local ok, entity = pcall(function()
      return player:getTargetedEntity(LOOK_DISTANCE)
   end)
   if not ok then return nil end
   return safeEntityUUID(entity)
end

local function sendShake(target, strength, duration)
   strength = clamp(strength, 0, MAX_STRENGTH, DEFAULT_STRENGTH)
   duration = math.floor(clamp(duration, 1, MAX_DURATION, DEFAULT_DURATION))

   return bridge.send(target, EVENT_SHAKE, {
      strength = strength,
      duration = duration,
   })
end

bridge.on(EVENT_SHAKE, function(body, meta)
   if type(body) ~= "table" then return end

   local t = now()
   local last = lastShakeFrom[meta.from]
   if last ~= nil and t - last < MIN_TICKS_BETWEEN_SHAKES_FROM_SAME_PLAYER then return end
   lastShakeFrom[meta.from] = t

   startShake(body.strength, body.duration)
end)

events.TICK:register(function()
   if not shake.active then return end

   shake.age = shake.age + 1
   shake.left = shake.left - 1
   if shake.left <= 0 then
      shake.active = false
      resetCamera()
   end
end, "screen_shake_bridge_tick")

events.RENDER:register(function(delta, context)
   if not shake.active then return end
   if context ~= "FIRST_PERSON" and context ~= "RENDER" then return end

   local t = shake.age + (tonumber(delta) or 0)
   local fade = math.max(shake.left / shake.duration, 0)
   local amp = shake.strength * fade
   local phase = t + shake.seed

   local pitch = math.sin(phase * 2.7) * amp
   local yaw = math.sin(phase * 3.9) * amp
   local roll = math.sin(phase * 5.1) * amp * 0.45
   local bobX = math.sin(phase * 4.3) * amp * 0.008
   local bobY = math.cos(phase * 3.4) * amp * 0.006

   renderer:setOffsetCameraRot(pitch, yaw, roll)
   renderer:setCameraPos(bobX, bobY, 0)
end, "screen_shake_bridge_render")

local shakeKey = keybinds:newKeybind("Bridge Shake Looked Player", TRIGGER_KEY)
shakeKey:onPress(function()
   local uuid = lookedAtPlayerUUID()
   if not uuid then
      print("[Screen Shake Bridge] Look at an opted-in player first.")
      return
   end

   local ok, err = sendShake(uuid, DEFAULT_STRENGTH, DEFAULT_DURATION)
   if not ok then
      print("[Screen Shake Bridge] " .. tostring(err))
   end
end)

ScreenShakeBridge = {
   shake = sendShake,
   shakeLookedAtPlayer = function(strength, duration)
      local uuid = lookedAtPlayerUUID()
      if not uuid then return false, "no targeted player" end
      return sendShake(uuid, strength, duration)
   end,
   selfTest = function(strength, duration)
      return startShake(strength or DEFAULT_STRENGTH, duration or DEFAULT_DURATION)
   end,
}
