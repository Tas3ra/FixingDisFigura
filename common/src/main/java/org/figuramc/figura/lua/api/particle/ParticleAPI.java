package org.figuramc.figura.lua.api.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.particles.ParticleOptions;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.ducks.ParticleEngineAccessor;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.api.world.WorldAPI;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.utils.LuaUtils;
import org.luaj.vm2.LuaError;

@LuaWhitelist
@LuaTypeDoc(
        name = "ParticleAPI",
        value = "particles"
)
public class ParticleAPI {

    private final Avatar owner;

    public ParticleAPI(Avatar owner) {
        this.owner = owner;
    }

    public static ParticleEngineAccessor getParticleEngine() {
        return (ParticleEngineAccessor) Minecraft.getInstance().particleEngine;
    }

    private LuaParticle generate(String id, double x, double y, double z, double w, double t, double h) {
        String originalId = id;
        try {
            id = convertOldToNewParticleFormat(id, x, y, z);
            ParticleOptions options = ParticleArgument.readParticle(new StringReader(id), WorldAPI.getCurrentWorld().registryAccess());
            Particle p = getParticleEngine().figura$makeParticle(options, x, y, z, w, t, h);
            if (p == null) throw new LuaError("Could not parse particle \"" + id + "\"");
            return new LuaParticle(id, p, owner);
        } catch (Exception e) {
            String message = e.getMessage();
            throw new LuaError("Could not parse particle \"" + originalId + "\"" + (message == null ? "" : ": " + message));
        }
    }

    private String convertOldToNewParticleFormat(String id, double x, double y, double z) {
        if (id == null)
            return "";

        id = id.trim();
        if (id.isEmpty() || id.contains("{"))
            return id;

        String[] parts = id.split("\\s+");
        String type = parts[0];
        String vanillaType = type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type;

        return switch (vanillaType) {
            case "block", "block_marker", "falling_dust", "dust_pillar", "block_crumble" ->
                    blockParticle(vanillaType, parts.length > 1 ? parts[1] : "minecraft:stone");
            case "dust" -> dustParticle(parts);
            case "dust_color_transition" -> dustColorTransitionParticle(parts);
            case "item" -> itemParticle(parts.length > 1 ? parts[1] : "minecraft:stone");
            case "sculk_charge" -> "sculk_charge{roll:" + (parts.length > 1 ? parts[1] : "0.0") + "}";
            case "shriek" -> "shriek{delay:" + (parts.length > 1 ? parts[1] : "0") + "}";
            case "vibration" -> vibrationParticle(parts, x, y, z);
            case "entity_effect", "flash", "tinted_leaves" -> colorParticle(vanillaType, parts);
            case "effect", "instant_effect" -> spellParticle(vanillaType, parts);
            case "dragon_breath" -> "dragon_breath{power:" + (parts.length > 1 ? parts[1] : "1.0") + "}";
            case "trail" -> trailParticle(parts, x, y, z);
            default -> id;
        };
    }

    private static String blockParticle(String type, String block) {
        String blockName = normalizeIdentifier(block);
        String properties = null;

        if (block.contains("[") && block.endsWith("]")) {
            blockName = block.substring(0, block.indexOf("["));
            blockName = normalizeIdentifier(blockName);
            properties = block.substring(block.indexOf("[") + 1, block.lastIndexOf("]"));
        }

        StringBuilder ret = new StringBuilder(type).append("{block_state:{Name:\"").append(blockName).append("\"");
        if (properties != null && !properties.isBlank()) {
            ret.append(",Properties:{");
            String[] split = properties.split(",");
            for (int i = 0; i < split.length; i++) {
                String[] pair = split[i].split("=", 2);
                if (i > 0)
                    ret.append(",");
                ret.append(pair[0].trim()).append(":\"").append(pair.length > 1 ? pair[1].trim() : "").append("\"");
            }
            ret.append("}");
        }
        return ret.append("}}").toString();
    }

    private static String dustParticle(String[] parts) {
        if (parts.length >= 5)
            return "dust{color:" + rgb(parts[1], parts[2], parts[3]) + ",scale:" + parts[4] + "}";
        return "dust{color:16711680,scale:1.0}";
    }

    private static String dustColorTransitionParticle(String[] parts) {
        if (parts.length >= 8)
            return "dust_color_transition{from_color:" + rgb(parts[1], parts[2], parts[3]) + ",scale:" + parts[4] + ",to_color:" + rgb(parts[5], parts[6], parts[7]) + "}";
        return "dust_color_transition{from_color:3790560,scale:1.0,to_color:16711680}";
    }

    private static String itemParticle(String item) {
        return "item{item:{id:\"" + normalizeIdentifier(item) + "\"}}";
    }

    private static String vibrationParticle(String[] parts, double x, double y, double z) {
        String dsX = parts.length > 1 ? parts[1] : Integer.toString((int) Math.floor(x));
        String dsY = parts.length > 2 ? parts[2] : Integer.toString((int) Math.floor(y + 1.0));
        String dsZ = parts.length > 3 ? parts[3] : Integer.toString((int) Math.floor(z));
        String arrival = parts.length > 4 ? parts[4] : "20";
        return "vibration{arrival_in_ticks:" + arrival + ",destination:{type:\"block\",pos:[" + dsX + "," + dsY + "," + dsZ + "]}}";
    }

    private static String colorParticle(String type, String[] parts) {
        if (parts.length >= 4)
            return type + "{color:" + argb(parts[1], parts[2], parts[3], parts.length > 4 ? parts[4] : "1.0") + "}";
        if (type.equals("entity_effect"))
            return type + "{color:" + argb("0", "0", "0", "1.0") + "}";
        return type + "{color:-1}";
    }

    private static String spellParticle(String type, String[] parts) {
        if (parts.length >= 4)
            return type + "{color:" + rgb(parts[1], parts[2], parts[3]) + ",power:" + (parts.length > 4 ? parts[4] : "1.0") + "}";
        return type + "{color:16777215,power:1.0}";
    }

    private static String trailParticle(String[] parts, double x, double y, double z) {
        String tx = parts.length > 1 ? parts[1] : Double.toString(x);
        String ty = parts.length > 2 ? parts[2] : Double.toString(y + 1.0);
        String tz = parts.length > 3 ? parts[3] : Double.toString(z);
        String color = parts.length > 6 ? Integer.toString(argb(parts[4], parts[5], parts[6], "1.0")) : (parts.length > 4 ? colorLiteral(parts[4]) : "-1");
        String duration = parts.length > 7 ? parts[7] : (parts.length > 5 && parts.length < 7 ? parts[5] : "20");
        return "trail{target:[" + tx + "," + ty + "," + tz + "],color:" + color + ",duration:" + duration + "}";
    }

    private static int rgb(String r, String g, String b) {
        return (colorChannel(r) << 16) | (colorChannel(g) << 8) | colorChannel(b);
    }

    private static int argb(String r, String g, String b, String a) {
        return (colorChannel(a) << 24) | rgb(r, g, b);
    }

    private static int colorChannel(String value) {
        double parsed = Double.parseDouble(value);
        int channel = parsed <= 1.0 ? (int) Math.round(parsed * 255.0) : (int) Math.round(parsed);
        return Math.max(0, Math.min(255, channel));
    }

    private static String colorLiteral(String value) {
        try {
            return Integer.toString((int) Long.decode(value).longValue());
        } catch (NumberFormatException ignored) {
            return "-1";
        }
    }

    private static String normalizeIdentifier(String id) {
        id = id == null || id.isBlank() ? "minecraft:stone" : id.trim();
        return id.contains(":") ? id : "minecraft:" + id;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = {String.class, FiguraVec3.class},
                            argumentNames = {"name", "pos"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, FiguraVec3.class, FiguraVec3.class},
                            argumentNames = {"name", "pos", "vel"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, Double.class, Double.class, Double.class},
                            argumentNames = {"name", "posX", "posY", "posZ"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, FiguraVec3.class, Double.class, Double.class, Double.class},
                            argumentNames = {"name", "pos", "velX", "velY", "velZ"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, Double.class, Double.class, Double.class, FiguraVec3.class},
                            argumentNames = {"name", "posX", "posY", "posZ", "vel"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, Double.class, Double.class, Double.class, Double.class, Double.class, Double.class},
                            argumentNames = {"name", "posX", "posY", "posZ", "velX", "velY", "velZ"}
                    )
            },
            value = "particles.new_particle"
    )
    public LuaParticle newParticle(@LuaNotNil String id, Object x, Object y, Double z, Object w, Double t, Double h) {
        FiguraVec3 pos, vel;

        // Parse pos and vel
        Pair<FiguraVec3, FiguraVec3> pair = LuaUtils.parse2Vec3("newParticle", x, y, z, w, t, h, 2);
        pos = pair.getFirst();
        vel = pair.getSecond();

        LuaParticle particle = generate(id, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
        particle.spawn();
        return particle;
    }

    @LuaWhitelist
    @LuaMethodDoc("particles.remove_particles")
    public ParticleAPI removeParticles() {
        getParticleEngine().figura$clearParticles(owner.owner);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "id"
            ),
            value = "particles.is_present"
    )
    public boolean isPresent(String id) {
        try {
            ParticleOptions options = ParticleArgument.readParticle(new StringReader(convertOldToNewParticleFormat(id, 0, 0, 0)), WorldAPI.getCurrentWorld().registryAccess());
            return getParticleEngine().figura$makeParticle(options, 0, 0, 0, 0, 0, 0) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    @LuaWhitelist
    public LuaParticle __index(String id) {
        return generate(id, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public String toString() {
        return "ParticleAPI";
    }
}
