package com.tacz.debug;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.Timer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gui.GunRefitScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 调试桥命令实现。所有命令都在客户端主线程执行（tick START 或 render END），因此可以直接读写
 * {@link Minecraft} / {@link World} / 实体等对象，等价于「用手玩游戏」，但由 CLI 程控。
 *
 * <p>
 * 命令注册表见 {@link #HANDLERS}；新增命令只需在静态块里 {@code register(name, handler)}。
 * handler 抛出异常时会被 DevBridge 捕获并以 {@code {"ok":false,"error":...}} 返回，不会崩游戏。
 */
@SideOnly(Side.CLIENT)
public final class DevCommands {

    public interface Handler {

        JsonObject run(JsonObject args) throws Exception;
    }

    private static final Map<String, Handler> HANDLERS = new LinkedHashMap<String, Handler>();

    /** 冻结前的 GUI（unfreeze 时恢复）。 */
    private static GuiScreen savedScreen;
    private static boolean frozen;

    private DevCommands() {}

    public static JsonObject execute(String command, JsonObject args, boolean renderPhase) throws Exception {
        Handler handler = HANDLERS.get(command);
        if (handler == null) {
            throw new IllegalArgumentException("unknown command: " + command);
        }
        JsonObject result = handler.run(args == null ? new JsonObject() : args);
        return result == null ? new JsonObject() : result;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    private static EntityClientPlayerMP player() {
        EntityClientPlayerMP p = mc().thePlayer;
        if (p == null) {
            throw new IllegalStateException("no player: not in a world (load a world first)");
        }
        return p;
    }

    private static World world() {
        World w = mc().theWorld;
        if (w == null) {
            throw new IllegalStateException("no world loaded");
        }
        return w;
    }

    private static void register(String name, Handler handler) {
        HANDLERS.put(name, handler);
    }

    private static String requireString(JsonObject args, String key) {
        if (!args.has(key) || args.get(key)
            .isJsonNull()) {
            throw new IllegalArgumentException("missing required argument: " + key);
        }
        return args.get(key)
            .getAsString();
    }

    private static double requireDouble(JsonObject args, String key) {
        if (!args.has(key)) {
            throw new IllegalArgumentException("missing required argument: " + key);
        }
        return args.get(key)
            .getAsDouble();
    }

    private static double getDouble(JsonObject args, String key, double def) {
        try {
            return args.has(key) ? args.get(key)
                .getAsDouble() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static int getInt(JsonObject args, String key, int def) {
        try {
            return args.has(key) ? args.get(key)
                .getAsInt() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static long getLong(JsonObject args, String key, long def) {
        try {
            return args.has(key) ? args.get(key)
                .getAsLong() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static boolean getBool(JsonObject args, String key, boolean def) {
        try {
            return args.has(key) ? args.get(key)
                .getAsBoolean() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static String getString(JsonObject args, String key, String def) {
        try {
            return args.has(key) && !args.get(key)
                .isJsonNull() ? args.get(key)
                    .getAsString() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(truncated, total " + text.length() + ")";
    }

    private static JsonObject posJson(double x, double y, double z) {
        JsonObject pos = new JsonObject();
        pos.addProperty("x", round3(x));
        pos.addProperty("y", round3(y));
        pos.addProperty("z", round3(z));
        return pos;
    }

    private static JsonElement itemJson(ItemStack stack) {
        if (stack == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        Item item = stack.getItem();
        if (item == null) {
            o.addProperty("id", "null");
            return o;
        }
        o.addProperty("id", String.valueOf(Item.itemRegistry.getNameForObject(item)));
        try {
            o.addProperty("numericId", Item.getIdFromItem(item));
        } catch (Throwable ignored) {
            // ignore
        }
        o.addProperty("count", stack.stackSize);
        try {
            o.addProperty("damage", stack.getItemDamage());
            o.addProperty("displayName", stack.getDisplayName());
            o.addProperty("unlocalizedName", stack.getUnlocalizedName());
        } catch (Throwable ignored) {
            // ignore
        }
        NBTTagCompound tag = stack.stackTagCompound;
        if (tag != null) {
            o.addProperty("nbt", truncate(tag.toString(), 6000));
        }
        return o;
    }

    private static JsonObject playerJson(EntityClientPlayerMP p) {
        JsonObject r = new JsonObject();
        r.addProperty("name", p.getCommandSenderName());
        r.add("pos", posJson(p.posX, p.posY, p.posZ));
        r.add("motion", posJson(p.motionX, p.motionY, p.motionZ));
        r.addProperty("yaw", round3(p.rotationYaw));
        r.addProperty("pitch", round3(p.rotationPitch));
        r.addProperty("onGround", p.onGround);
        r.addProperty("dimension", p.dimension);
        r.addProperty("health", round3(p.getHealth()));
        r.addProperty("maxHealth", round3(p.getMaxHealth()));
        r.addProperty("sprinting", p.isSprinting());
        r.addProperty("sneaking", p.isSneaking());
        r.addProperty("usingItem", p.isUsingItem());
        r.addProperty("creative", p.capabilities.isCreativeMode);
        r.addProperty("flying", p.capabilities.isFlying);
        r.addProperty("allowFlying", p.capabilities.allowFlying);
        r.add("heldItem", itemJson(p.getHeldItem()));
        r.addProperty("currentItem", p.inventory.currentItem);
        JsonArray hotbar = new JsonArray();
        for (int i = 0; i < 9; i++) {
            hotbar.add(itemJson(p.inventory.mainInventory[i]));
        }
        r.add("hotbar", hotbar);
        return r;
    }

    private static JsonObject worldJson(World w) {
        JsonObject r = new JsonObject();
        try {
            r.addProperty(
                "name",
                w.getWorldInfo()
                    .getWorldName());
            r.addProperty("seed", w.getSeed());
            r.addProperty("worldTime", w.getWorldTime());
            r.addProperty("totalTime", w.getTotalWorldTime());
            r.addProperty("raining", w.isRaining());
            r.addProperty("thundering", w.isThundering());
            r.addProperty("difficulty", String.valueOf(w.difficultySetting != null ? w.difficultySetting.name() : "?"));
        } catch (Throwable t) {
            r.addProperty("infoError", String.valueOf(t));
        }
        r.addProperty("dimensionId", w.provider.dimensionId);
        try {
            r.addProperty("dimensionName", w.provider.getDimensionName());
        } catch (Throwable ignored) {
            // ignore
        }
        try {
            if (mc().getIntegratedServer() != null) {
                r.addProperty(
                    "folder",
                    mc().getIntegratedServer()
                        .getFolderName());
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return r;
    }

    private static JsonObject glJson() {
        JsonObject r = new JsonObject();
        try {
            r.addProperty("vendor", GL11.glGetString(GL11.GL_VENDOR));
            r.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER));
            r.addProperty("version", GL11.glGetString(GL11.GL_VERSION));
        } catch (Throwable t) {
            r.addProperty("error", String.valueOf(t));
        }
        return r;
    }

    private static JsonObject settingValue(GameSettings settings, String name) {
        JsonObject r = new JsonObject();
        try {
            Field field = GameSettings.class.getField(name);
            Object value = field.get(settings);
            r.addProperty("name", name);
            if (value == null) {
                r.add("value", JsonNull.INSTANCE);
            } else if (value instanceof Boolean) {
                r.addProperty("value", (Boolean) value);
            } else if (value instanceof Integer) {
                r.addProperty("value", (Integer) value);
            } else if (value instanceof Long) {
                r.addProperty("value", (Long) value);
            } else if (value instanceof Float) {
                r.addProperty("value", (Float) value);
            } else if (value instanceof Double) {
                r.addProperty("value", (Double) value);
            } else if (value instanceof String) {
                r.addProperty("value", (String) value);
            } else if (value instanceof Enum) {
                r.addProperty("value", ((Enum<?>) value).name());
            } else {
                r.addProperty("value", String.valueOf(value));
            }
            r.addProperty(
                "type",
                field.getType()
                    .getSimpleName());
        } catch (Throwable t) {
            r.addProperty("name", name);
            r.addProperty("error", "unknown setting");
        }
        return r;
    }

    private static Object coerceSetting(Field field, JsonElement value) throws Exception {
        Class<?> type = field.getType();
        if (type == boolean.class) {
            return value.getAsBoolean();
        }
        if (type == int.class) {
            return value.getAsInt();
        }
        if (type == long.class) {
            return value.getAsLong();
        }
        if (type == float.class) {
            return value.getAsFloat();
        }
        if (type == double.class) {
            return value.getAsDouble();
        }
        if (type == String.class) {
            return value.getAsString();
        }
        if (type.isEnum()) {
            String wanted = value.getAsString();
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name()
                    .equalsIgnoreCase(wanted)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("bad enum value '" + wanted + "' for " + field.getName());
        }
        throw new IllegalArgumentException("unsupported setting type: " + type);
    }

    private static KeyBinding keyBinding(String name) {
        GameSettings g = mc().gameSettings;
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.equals("forward") || n.equals("w")) {
            return g.keyBindForward;
        }
        if (n.equals("back") || n.equals("s")) {
            return g.keyBindBack;
        }
        if (n.equals("left") || n.equals("a")) {
            return g.keyBindLeft;
        }
        if (n.equals("right") || n.equals("d")) {
            return g.keyBindRight;
        }
        if (n.equals("jump") || n.equals("space")) {
            return g.keyBindJump;
        }
        if (n.equals("sneak") || n.equals("shift")) {
            return g.keyBindSneak;
        }
        if (n.equals("sprint")) {
            return g.keyBindSprint;
        }
        if (n.equals("attack") || n.equals("left_click")) {
            return g.keyBindAttack;
        }
        if (n.equals("use") || n.equals("right_click")) {
            return g.keyBindUseItem;
        }
        if (n.equals("pick") || n.equals("pick_block")) {
            return g.keyBindPickBlock;
        }
        if (n.equals("drop")) {
            return g.keyBindDrop;
        }
        if (n.equals("inventory")) {
            return g.keyBindInventory;
        }
        if (n.equals("chat")) {
            return g.keyBindChat;
        }
        if (n.equals("command")) {
            return g.keyBindCommand;
        }
        if (n.startsWith("hotbar")) {
            try {
                int index = Integer.parseInt(n.substring("hotbar".length()));
                if (index >= 1 && index <= 9) {
                    return g.keyBindsHotbar[index - 1];
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException("unknown key: " + name);
    }

    private static void setKey(String name, boolean pressed) {
        KeyBinding binding = keyBinding(name);
        KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
        if (pressed) {
            KeyBinding.onTick(binding.getKeyCode());
        }
    }

    private static List<Entity> worldEntities(World w, double x, double y, double z, double radius) {
        List<Entity> result = new ArrayList<Entity>();
        for (Object object : w.loadedEntityList) {
            if (!(object instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) object;
            double dx = entity.posX - x;
            double dy = entity.posY - y;
            double dz = entity.posZ - z;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                result.add(entity);
            }
        }
        Collections.sort(result, new Comparator<Entity>() {

            @Override
            public int compare(Entity a, Entity b) {
                double da = distanceSq(a, x, y, z);
                double db = distanceSq(b, x, y, z);
                return Double.compare(da, db);
            }
        });
        return result;
    }

    private static double distanceSq(Entity entity, double x, double y, double z) {
        double dx = entity.posX - x;
        double dy = entity.posY - y;
        double dz = entity.posZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static JsonObject entityJson(Entity entity, double distance) {
        JsonObject r = new JsonObject();
        r.addProperty("id", entity.getEntityId());
        r.addProperty(
            "type",
            entity.getClass()
                .getSimpleName());
        try {
            r.addProperty("name", entity.getCommandSenderName());
        } catch (Throwable ignored) {
            // ignore
        }
        r.addProperty("distance", round3(Math.sqrt(distance)));
        r.add("pos", posJson(entity.posX, entity.posY, entity.posZ));
        r.add("motion", posJson(entity.motionX, entity.motionY, entity.motionZ));
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            r.addProperty("health", round3(living.getHealth()));
            r.addProperty("maxHealth", round3(living.getMaxHealth()));
            r.addProperty("dead", living.isDead);
        }
        if (entity instanceof EntityItem) {
            r.add("item", itemJson(((EntityItem) entity).getEntityItem()));
        }
        if (entity instanceof EntityPlayer) {
            r.addProperty("player", ((EntityPlayer) entity).getCommandSenderName());
        }
        return r;
    }

    // ------------------------------------------------------------------
    // commands
    // ------------------------------------------------------------------

    static {
        register("inventory", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                JsonArray slots = new JsonArray();
                int limit = getInt(args, "slots", 36);
                for (int i = 0; i < Math.min(limit, p.inventory.mainInventory.length); i++) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("slot", i);
                    entry.add("item", itemJson(p.inventory.mainInventory[i]));
                    slots.add(entry);
                }
                JsonObject r = new JsonObject();
                r.addProperty("currentItem", p.inventory.currentItem);
                r.add("slots", slots);
                return r;
            }
        });

        register("game.focus", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                unfreeze();
                Minecraft m = mc();
                boolean active = false;
                boolean grabbed = false;
                try {
                    active = Display.isActive();
                } catch (Throwable ignored) {
                    // ignore
                }
                if (active) {
                    // 走原版聚焦路径：displayGuiScreen(null) + 抓鼠标 + inGameHasFocus
                    m.setIngameFocus();
                }
                if (!m.inGameHasFocus && getBool(args, "force", true)) {
                    m.inGameHasFocus = true;
                    try {
                        m.mouseHelper.grabMouseCursor();
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }
                try {
                    grabbed = Mouse.isGrabbed();
                } catch (Throwable ignored) {
                    // ignore
                }
                JsonObject r = new JsonObject();
                r.addProperty("displayActive", active);
                r.addProperty("mouseGrabbed", grabbed);
                r.addProperty("inGameHasFocus", m.inGameHasFocus);
                r.addProperty(
                    "screen",
                    m.currentScreen == null ? null
                        : m.currentScreen.getClass()
                            .getSimpleName());
                return r;
            }
        });

        register("gui.current", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                GuiScreen screen = m.currentScreen;
                JsonObject r = new JsonObject();
                r.addProperty("open", screen != null);
                r.addProperty("frozen", frozen);
                r.addProperty("displayWidth", m.displayWidth);
                r.addProperty("displayHeight", m.displayHeight);
                if (screen != null) {
                    r.addProperty(
                        "screen",
                        screen.getClass()
                            .getName());
                    r.addProperty(
                        "simpleName",
                        screen.getClass()
                            .getSimpleName());
                    r.addProperty("doesGuiPauseGame", screen.doesGuiPauseGame());
                    ScaledResolution scaled = new ScaledResolution(m, m.displayWidth, m.displayHeight);
                    r.addProperty("scaleFactor", scaled.getScaleFactor());
                    r.addProperty("scaledWidth", scaled.getScaledWidth());
                    r.addProperty("scaledHeight", scaled.getScaledHeight());
                    r.addProperty("buttonCount", screenButtons(screen).size());
                }
                return r;
            }
        });

        register("gui.buttons", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                GuiScreen screen = mc().currentScreen;
                if (screen == null) {
                    throw new IllegalStateException("no GUI screen open");
                }
                JsonArray buttons = new JsonArray();
                for (Object object : screenButtons(screen)) {
                    if (!(object instanceof GuiButton button)) {
                        continue;
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", button.id);
                    entry.addProperty("x", button.xPosition);
                    entry.addProperty("y", button.yPosition);
                    entry.addProperty("width", button.width);
                    entry.addProperty("height", button.height);
                    try {
                        entry.addProperty("text", String.valueOf(button.displayString));
                    } catch (Throwable ignored) {
                        // ignore
                    }
                    entry.addProperty("enabled", button.enabled);
                    entry.addProperty("visible", button.visible);
                    buttons.add(entry);
                }
                JsonObject r = new JsonObject();
                r.addProperty(
                    "screen",
                    screen.getClass()
                        .getName());
                r.add("buttons", buttons);
                return r;
            }
        });

        register("gui.click", new Handler() {

            @Override
            public JsonObject run(JsonObject args) throws Exception {
                Minecraft m = mc();
                GuiScreen screen = m.currentScreen;
                if (screen == null) {
                    throw new IllegalStateException("no GUI screen open");
                }
                String before = screen.getClass()
                    .getName();
                int x = (int) Math.round(requireDouble(args, "x"));
                int y = (int) Math.round(requireDouble(args, "y"));
                int button = getInt(args, "button", 0);
                boolean release = getBool(args, "release", true);
                Method clicked = GuiScreen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
                clicked.setAccessible(true);
                clicked.invoke(screen, x, y, button);
                if (release) {
                    try {
                        Method released = GuiScreen.class
                            .getDeclaredMethod("mouseReleased", int.class, int.class, int.class);
                        released.setAccessible(true);
                        released.invoke(screen, x, y, button);
                    } catch (Throwable ignored) {
                        // 有些屏幕没有覆盖 mouseReleased，忽略
                    }
                }
                JsonObject r = new JsonObject();
                r.addProperty("before", before);
                r.addProperty("x", x);
                r.addProperty("y", y);
                GuiScreen after = m.currentScreen;
                r.addProperty(
                    "after",
                    after == null ? null
                        : after.getClass()
                            .getName());
                r.addProperty(
                    "screenChanged",
                    after == null || !before.equals(
                        after.getClass()
                            .getName()));
                return r;
            }
        });

        register("gui.open", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                String name = requireString(args, "name");
                Minecraft m = mc();
                GuiScreen screen;
                if (name.equalsIgnoreCase("refit") || name.equalsIgnoreCase("gun_refit")) {
                    screen = new GunRefitScreen();
                } else {
                    throw new IllegalArgumentException("unknown gui: " + name + " (known: refit)");
                }
                m.displayGuiScreen(screen);
                JsonObject r = new JsonObject();
                r.addProperty(
                    "screen",
                    m.currentScreen == null ? null
                        : m.currentScreen.getClass()
                            .getName());
                return r;
            }
        });

        register("gui.close", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                boolean wasOpen = m.currentScreen != null;
                if (wasOpen) {
                    m.displayGuiScreen(null);
                }
                JsonObject r = new JsonObject();
                r.addProperty("wasOpen", wasOpen);
                r.addProperty(
                    "screen",
                    m.currentScreen == null ? null
                        : m.currentScreen.getClass()
                            .getName());
                return r;
            }
        });

        register("status", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                JsonObject r = new JsonObject();
                JsonObject client = new JsonObject();
                client.addProperty("inWorld", m.theWorld != null);
                client.addProperty("singleplayer", m.isSingleplayer());
                client.addProperty(
                    "screen",
                    m.currentScreen == null ? null
                        : m.currentScreen.getClass()
                            .getName());
                client.addProperty("paused", m.isGamePaused());
                client.addProperty("frozen", frozen);
                client.addProperty("hideGUI", m.gameSettings.hideGUI);
                client.addProperty("inGameHasFocus", m.inGameHasFocus);
                client.addProperty("displayWidth", m.displayWidth);
                client.addProperty("displayHeight", m.displayHeight);
                client.addProperty("fps", DevBridge.fps());
                client.addProperty("tick", DevBridge.tick());
                client.addProperty("renderTick", DevBridge.renderTick());
                client.addProperty(
                    "uptimeMs",
                    DevBridge.get() == null ? -1L
                        : DevBridge.get()
                            .uptimeMs());
                try {
                    client.addProperty(
                        "lang",
                        m.getLanguageManager()
                            .getCurrentLanguage()
                            .getLanguageCode());
                } catch (Throwable ignored) {
                    // ignore
                }
                JsonObject settings = new JsonObject();
                String[] names = { "hideGUI", "thirdPersonView", "fovSetting", "renderDistanceChunks", "guiScale",
                    "fancyGraphics", "viewBobbing", "gammaSetting", "limitFramerate", "enableVsync", "pauseOnLostFocus",
                    "showDebugInfo", "particles" };
                for (String name : names) {
                    JsonObject one = settingValue(m.gameSettings, name);
                    if (!one.has("error")) {
                        settings.add(name, one.get("value"));
                    }
                }
                client.add("gameSettings", settings);
                client.add("gl", glJson());
                r.add("client", client);
                if (m.theWorld != null) {
                    r.add("world", worldJson(m.theWorld));
                }
                if (m.thePlayer != null) {
                    r.add("player", playerJson(m.thePlayer));
                }
                JsonObject bridge = new JsonObject();
                DevBridge bridgeInstance = DevBridge.get();
                if (bridgeInstance == null) {
                    bridge.addProperty("running", false);
                } else {
                    bridge.addProperty("running", true);
                    bridge.addProperty("version", DevBridge.VERSION);
                    bridge.addProperty("port", bridgeInstance.port());
                    bridge.addProperty("pendingTicks", bridgeInstance.pendingTickRequest());
                    bridge.addProperty("overlaySuppressed", DevBridge.isOverlaySuppressed());
                }
                r.add("bridge", bridge);
                return r;
            }
        });

        register("tick", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                int count = getInt(args, "count", 1);
                int applied = DevBridge.requestTicks(count);
                JsonObject r = new JsonObject();
                r.addProperty("requested", count);
                r.addProperty("applied", applied);
                r.addProperty("tick", DevBridge.tick());
                return r;
            }
        });

        register("time.speed", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                float speed = (float) getDouble(args, "speed", 1.0D);
                Timer timer = DevBridge.timer();
                if (timer == null) {
                    throw new IllegalStateException("timer not accessible");
                }
                timer.timerSpeed = speed;
                JsonObject r = new JsonObject();
                r.addProperty("timerSpeed", timer.timerSpeed);
                return r;
            }
        });

        register("freeze", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                freeze();
                JsonObject r = new JsonObject();
                r.addProperty("frozen", frozen);
                return r;
            }
        });

        register("unfreeze", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                unfreeze();
                JsonObject r = new JsonObject();
                r.addProperty("frozen", frozen);
                return r;
            }
        });

        register("player.status", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                return playerJson(player());
            }
        });

        register("player.tp", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                double x = requireDouble(args, "x");
                double y = requireDouble(args, "y");
                double z = requireDouble(args, "z");
                p.setPosition(x, y, z);
                p.motionX = 0.0D;
                p.motionY = 0.0D;
                p.motionZ = 0.0D;
                p.fallDistance = 0.0F;
                p.prevPosX = p.lastTickPosX = x;
                p.prevPosY = p.lastTickPosY = y;
                p.prevPosZ = p.lastTickPosZ = z;
                if (args.has("yaw") || args.has("pitch")) {
                    applyLook(p, getDouble(args, "yaw", p.rotationYaw), getDouble(args, "pitch", p.rotationPitch));
                }
                boolean packet = getBool(args, "packet", true);
                if (packet) {
                    p.sendQueue.addToSendQueue(
                        new C03PacketPlayer.C06PacketPlayerPosLook(
                            p.posX,
                            p.boundingBox.minY,
                            p.posY,
                            p.posZ,
                            p.rotationYaw,
                            p.rotationPitch,
                            p.onGround));
                }
                JsonObject r = playerJson(p);
                r.addProperty("packet", packet);
                return r;
            }
        });

        register("player.look", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                applyLook(p, requireDouble(args, "yaw"), requireDouble(args, "pitch"));
                JsonObject r = new JsonObject();
                r.addProperty("yaw", round3(p.rotationYaw));
                r.addProperty("pitch", round3(p.rotationPitch));
                return r;
            }
        });

        register("player.view", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                if (args.has("x") && args.has("y") && args.has("z")) {
                    double x = requireDouble(args, "x");
                    double y = requireDouble(args, "y");
                    double z = requireDouble(args, "z");
                    p.setPosition(x, y, z);
                    p.motionX = 0.0D;
                    p.motionY = 0.0D;
                    p.motionZ = 0.0D;
                    p.prevPosX = p.lastTickPosX = x;
                    p.prevPosY = p.lastTickPosY = y;
                    p.prevPosZ = p.lastTickPosZ = z;
                }
                if (args.has("yaw") || args.has("pitch")) {
                    applyLook(p, getDouble(args, "yaw", p.rotationYaw), getDouble(args, "pitch", p.rotationPitch));
                }
                if (args.has("lookAt")) {
                    JsonObject target = args.getAsJsonObject("lookAt");
                    lookAt(
                        p,
                        target.get("x")
                            .getAsDouble(),
                        target.get("y")
                            .getAsDouble(),
                        target.get("z")
                            .getAsDouble());
                }
                JsonObject r = new JsonObject();
                r.add("pos", posJson(p.posX, p.posY, p.posZ));
                r.addProperty("yaw", round3(p.rotationYaw));
                r.addProperty("pitch", round3(p.rotationPitch));
                return r;
            }
        });

        register("player.motion", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                p.motionX = getDouble(args, "x", 0.0D);
                p.motionY = getDouble(args, "y", 0.0D);
                p.motionZ = getDouble(args, "z", 0.0D);
                return playerJson(p);
            }
        });

        register("player.slot", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                int slot = getInt(args, "slot", 0);
                if (slot < 0 || slot > 8) {
                    throw new IllegalArgumentException("hotbar slot must be 0-8, got " + slot);
                }
                p.inventory.currentItem = slot;
                JsonObject r = new JsonObject();
                r.addProperty("currentItem", p.inventory.currentItem);
                r.add("held", itemJson(p.getHeldItem()));
                return r;
            }
        });

        register("player.sprint", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                p.setSprinting(getBool(args, "enabled", true));
                JsonObject r = new JsonObject();
                r.addProperty("sprinting", p.isSprinting());
                return r;
            }
        });

        register("player.fly", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                boolean enabled = getBool(args, "enabled", true);
                p.capabilities.allowFlying = enabled;
                p.capabilities.isFlying = enabled;
                try {
                    p.sendPlayerAbilities();
                } catch (Throwable ignored) {
                    // 1.7.10 里 sendPlayerAbilities 由 EntityClientPlayerMP 提供；失败不影响状态
                }
                JsonObject r = new JsonObject();
                r.addProperty("allowFlying", p.capabilities.allowFlying);
                r.addProperty("flying", p.capabilities.isFlying);
                return r;
            }
        });

        register("input.key", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                String key = requireString(args, "key");
                boolean pressed = getBool(args, "pressed", true);
                setKey(key, pressed);
                JsonObject r = new JsonObject();
                r.addProperty("key", key);
                r.addProperty("keyCode", keyBinding(key).getKeyCode());
                r.addProperty("pressed", keyBinding(key).getIsKeyPressed());
                return r;
            }
        });

        register("input.tap", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                String key = requireString(args, "key");
                KeyBinding binding = keyBinding(key);
                KeyBinding.setKeyBindState(binding.getKeyCode(), true);
                KeyBinding.onTick(binding.getKeyCode());
                KeyBinding.setKeyBindState(binding.getKeyCode(), false);
                JsonObject r = new JsonObject();
                r.addProperty("key", key);
                r.addProperty("keyCode", binding.getKeyCode());
                return r;
            }
        });

        register("input.clear", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                String[] keys = { "forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use" };
                for (String key : keys) {
                    KeyBinding binding = keyBinding(key);
                    KeyBinding.setKeyBindState(binding.getKeyCode(), false);
                }
                JsonObject r = new JsonObject();
                r.addProperty("cleared", true);
                return r;
            }
        });

        register("input.status", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                String[] keys = { "forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use" };
                JsonObject r = new JsonObject();
                for (String key : keys) {
                    r.addProperty(key, keyBinding(key).getIsKeyPressed());
                }
                return r;
            }
        });

        register("chat.send", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                String message = requireString(args, "message");
                EntityClientPlayerMP p = player();
                p.sendChatMessage(message);
                JsonObject r = new JsonObject();
                r.addProperty("sent", message);
                return r;
            }
        });

        register("command", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                String command = requireString(args, "command");
                String normalized = command.startsWith("/") ? command.substring(1) : command;
                EntityClientPlayerMP p = player();
                p.sendChatMessage("/" + normalized);
                JsonObject r = new JsonObject();
                r.addProperty("sent", normalized);
                r.addProperty("tick", DevBridge.tick());
                return r;
            }
        });

        register("chat.tail", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                int count = getInt(args, "count", 10);
                JsonArray messages = new JsonArray();
                for (String line : DevBridge.chatTail(count)) {
                    messages.add(new JsonPrimitive(line));
                }
                JsonObject r = new JsonObject();
                r.add("messages", messages);
                return r;
            }
        });

        register("world.info", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                return worldJson(world());
            }
        });

        register("world.block", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                World w = world();
                int x = getInt(args, "x", 0);
                int y = getInt(args, "y", 64);
                int z = getInt(args, "z", 0);
                Block block = w.getBlock(x, y, z);
                JsonObject r = new JsonObject();
                r.addProperty("x", x);
                r.addProperty("y", y);
                r.addProperty("z", z);
                if (block == null || block == Blocks.air) {
                    r.addProperty("block", "minecraft:air");
                    r.addProperty("air", true);
                    return r;
                }
                r.addProperty("block", String.valueOf(Block.blockRegistry.getNameForObject(block)));
                r.addProperty("meta", w.getBlockMetadata(x, y, z));
                TileEntity tileEntity = w.getTileEntity(x, y, z);
                if (tileEntity != null) {
                    r.addProperty(
                        "tileEntity",
                        tileEntity.getClass()
                            .getSimpleName());
                    try {
                        NBTTagCompound nbt = new NBTTagCompound();
                        tileEntity.writeToNBT(nbt);
                        r.addProperty("tileNbt", truncate(nbt.toString(), 4000));
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }
                return r;
            }
        });

        register("world.entities", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                World w = world();
                EntityClientPlayerMP p = mc().thePlayer;
                double x = getDouble(args, "x", p == null ? 0.0D : p.posX);
                double y = getDouble(args, "y", p == null ? 64.0D : p.posY);
                double z = getDouble(args, "z", p == null ? 0.0D : p.posZ);
                double radius = getDouble(args, "radius", 48.0D);
                int limit = getInt(args, "limit", 80);
                List<Entity> entities = worldEntities(w, x, y, z, radius);
                JsonArray array = new JsonArray();
                int count = 0;
                for (Entity entity : entities) {
                    if (count++ >= limit) {
                        break;
                    }
                    array.add(entityJson(entity, distanceSq(entity, x, y, z)));
                }
                JsonObject r = new JsonObject();
                r.addProperty("total", entities.size());
                r.addProperty("returned", array.size());
                r.add("entities", array);
                return r;
            }
        });

        register("world.load", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                String name = requireString(args, "name");
                JsonObject r = new JsonObject();
                r.addProperty("name", name);
                if (m.theWorld != null) {
                    String current = m.theWorld.getWorldInfo()
                        .getWorldName();
                    if (name.equals(current)) {
                        r.addProperty("alreadyLoaded", true);
                        r.add("world", worldJson(m.theWorld));
                        return r;
                    }
                    throw new IllegalStateException(
                        "a world is already loaded ('" + current
                            + "'); restart the client before loading another world");
                }
                File saveDir = new File(new File(m.mcDataDir, "saves"), name);
                if (!saveDir.isDirectory()) {
                    throw new IllegalStateException("world folder not found: " + saveDir.getAbsolutePath());
                }
                m.launchIntegratedServer(name, name, null);
                r.addProperty("loaded", true);
                r.addProperty("tick", DevBridge.tick());
                return r;
            }
        });

        register("world.create", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                String name = requireString(args, "name");
                if (m.theWorld != null) {
                    throw new IllegalStateException(
                        "a world is already loaded; restart the client before creating another world");
                }
                File saveDir = new File(new File(m.mcDataDir, "saves"), name);
                if (saveDir.exists()) {
                    throw new IllegalStateException(
                        "world folder already exists: " + saveDir.getAbsolutePath() + " (use world.load)");
                }
                long seed = getLong(args, "seed", 20260101L);
                String mode = getString(args, "gamemode", "creative").toLowerCase(Locale.ROOT);
                WorldSettings.GameType gameType;
                if (mode.equals("creative") || mode.equals("1")) {
                    gameType = WorldSettings.GameType.CREATIVE;
                } else if (mode.equals("survival") || mode.equals("0")) {
                    gameType = WorldSettings.GameType.SURVIVAL;
                } else if (mode.equals("adventure") || mode.equals("2")) {
                    gameType = WorldSettings.GameType.ADVENTURE;
                } else {
                    throw new IllegalArgumentException("unknown gamemode: " + mode);
                }
                WorldSettings settings = new WorldSettings(
                    seed,
                    gameType,
                    getBool(args, "structures", true),
                    getBool(args, "hardcore", false),
                    WorldType.DEFAULT);
                settings.enableCommands();
                if (getBool(args, "bonusChest", false)) {
                    settings.enableBonusChest();
                }
                m.launchIntegratedServer(name, name, settings);
                JsonObject r = new JsonObject();
                r.addProperty("created", true);
                r.addProperty("name", name);
                r.addProperty("seed", seed);
                r.addProperty("gamemode", gameType.name());
                r.addProperty("tick", DevBridge.tick());
                return r;
            }
        });

        register("render.info", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                JsonObject r = new JsonObject();
                r.addProperty("displayWidth", m.displayWidth);
                r.addProperty("displayHeight", m.displayHeight);
                Framebuffer framebuffer = m.getFramebuffer();
                if (framebuffer != null) {
                    JsonObject fb = new JsonObject();
                    fb.addProperty("width", framebuffer.framebufferWidth);
                    fb.addProperty("height", framebuffer.framebufferHeight);
                    fb.addProperty("textureWidth", framebuffer.framebufferTextureWidth);
                    fb.addProperty("textureHeight", framebuffer.framebufferTextureHeight);
                    fb.addProperty("object", framebuffer.framebufferObject);
                    fb.addProperty("texture", framebuffer.framebufferTexture);
                    r.add("framebuffer", fb);
                }
                try {
                    r.addProperty("framebufferEnabled", OpenGlHelper.isFramebufferEnabled());
                } catch (Throwable ignored) {
                    // ignore
                }
                r.addProperty("hideGUI", m.gameSettings.hideGUI);
                r.addProperty("thirdPersonView", m.gameSettings.thirdPersonView);
                r.addProperty("fov", m.gameSettings.fovSetting);
                r.addProperty("renderDistance", m.gameSettings.renderDistanceChunks);
                r.addProperty("guiScale", m.gameSettings.guiScale);
                r.addProperty("fancyGraphics", m.gameSettings.fancyGraphics);
                r.addProperty("viewBobbing", m.gameSettings.viewBobbing);
                r.add("gl", glJson());
                r.addProperty("frozen", frozen);
                return r;
            }
        });

        register("render.hud", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                boolean hide = getBool(args, "hide", true);
                m.gameSettings.hideGUI = hide;
                // 1.7.10 的 HUD 渲染条件是「!hideGUI || currentScreen != null」，
                // 冻结屏打开时仅设 hideGUI 不够，还要取消 RenderGameOverlayEvent.Pre(ALL)。
                DevBridge.setOverlaySuppressed(hide);
                JsonObject r = new JsonObject();
                r.addProperty("hideGUI", m.gameSettings.hideGUI);
                r.addProperty("overlaySuppressed", DevBridge.isOverlaySuppressed());
                return r;
            }
        });

        register("render.frame", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                Framebuffer framebuffer = m.getFramebuffer();
                if (framebuffer == null) {
                    throw new IllegalStateException("main framebuffer not available");
                }
                String name = getString(args, "name", "frame");
                name = name.replace('\\', '/');
                while (name.startsWith("/")) {
                    name = name.substring(1);
                }
                if (!name.toLowerCase(Locale.ROOT)
                    .endsWith(".png")) {
                    name = name + ".png";
                }
                File screenshots = new File(m.mcDataDir, "screenshots");
                File target = new File(screenshots, name);
                File parent = target.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                if (target.exists()) {
                    target.delete();
                }
                ScreenShotHelper.saveScreenshot(m.mcDataDir, name, m.displayWidth, m.displayHeight, framebuffer);
                if (!target.exists()) {
                    throw new IllegalStateException("screenshot was not written: " + target.getAbsolutePath());
                }
                JsonObject r = new JsonObject();
                r.addProperty("file", target.getAbsolutePath());
                r.addProperty("width", framebuffer.framebufferWidth);
                r.addProperty("height", framebuffer.framebufferHeight);
                r.addProperty("bytes", target.length());
                return r;
            }
        });

        register("setting.get", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                String name = requireString(args, "name");
                return settingValue(mc().gameSettings, name);
            }
        });

        register("setting.set", new Handler() {

            @Override
            public JsonObject run(JsonObject args) throws Exception {
                String name = requireString(args, "name");
                GameSettings settings = mc().gameSettings;
                Field field;
                try {
                    field = GameSettings.class.getField(name);
                } catch (NoSuchFieldException e) {
                    throw new IllegalArgumentException("unknown game setting: " + name);
                }
                JsonElement value = args.has("value") ? args.get("value") : JsonNull.INSTANCE;
                JsonObject before = settingValue(settings, name);
                Object oldValue = field.get(settings);
                field.set(settings, coerceSetting(field, value));
                JsonObject r = new JsonObject();
                r.addProperty("name", name);
                r.add("old", before.get("value"));
                r.add("new", settingValue(settings, name).get("value"));
                r.addProperty(
                    "changed",
                    !String.valueOf(oldValue)
                        .equals(String.valueOf(field.get(settings))));
                return r;
            }
        });

        register("item.held", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                JsonObject r = new JsonObject();
                r.add("item", itemJson(p.getHeldItem()));
                r.addProperty("currentItem", p.inventory.currentItem);
                return r;
            }
        });

        register("gun.status", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                EntityClientPlayerMP p = player();
                JsonObject r = new JsonObject();
                ItemStack held = p.getHeldItem();
                r.add("held", itemJson(held));
                if (held != null && held.getItem() instanceof IGun gun) {
                    r.addProperty("gunId", String.valueOf(gun.getGunId(held)));
                    r.addProperty("currentAmmo", gun.getCurrentAmmoCount(held));
                    r.addProperty("bulletInBarrel", gun.hasBulletInBarrel(held));
                    r.addProperty("useInventoryAmmo", gun.useInventoryAmmo(held));
                    try {
                        r.addProperty("fireMode", String.valueOf(gun.getFireMode(held)));
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }
                try {
                    IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(p);
                    LocalPlayerDataHolder holder = operator.getDataHolder();
                    r.addProperty("aiming", operator.isAim());
                    r.addProperty("charging", operator.isCharging());
                    r.addProperty("chargeProgress", round3(operator.getChargeProgress()));
                    r.addProperty("shootCooldown", operator.getClientShootCoolDown());
                    JsonObject data = new JsonObject();
                    data.addProperty("aiming", holder.clientIsAiming);
                    data.addProperty("aimingProgress", round3(holder.clientAimingProgress));
                    data.addProperty("aimingTimestamp", holder.clientAimingTimestamp);
                    data.addProperty("charging", holder.isCharging);
                    data.addProperty("chargeProgress", round3(holder.chargeProgress));
                    data.addProperty("stateLocked", holder.clientStateLock);
                    data.addProperty("reloadStarting", holder.clientReloadStarting);
                    data.addProperty("bolting", holder.isBolting);
                    data.addProperty("shootTimestamp", holder.clientShootTimestamp);
                    data.addProperty("lastShootTimestamp", holder.clientLastShootTimestamp);
                    data.addProperty("baseTimestamp", holder.clientBaseTimestamp);
                    r.add("clientData", data);
                } catch (Throwable t) {
                    r.addProperty("clientDataError", String.valueOf(t));
                }
                return r;
            }
        });

        register("gun.shoot", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                IClientPlayerGunOperator operator = gunOperator();
                boolean charged = operator.chargeShoot(true);
                ShootResult result = operator.shoot();
                JsonObject r = new JsonObject();
                r.addProperty("charged", charged);
                r.addProperty("result", String.valueOf(result));
                return r;
            }
        });

        register("gun.reload", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                gunOperator().reload();
                JsonObject r = new JsonObject();
                r.addProperty("reloading", true);
                return r;
            }
        });

        register("gun.bolt", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                gunOperator().bolt();
                JsonObject r = new JsonObject();
                r.addProperty("bolting", true);
                return r;
            }
        });

        register("gun.unload", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                gunOperator().unloadAmmo();
                JsonObject r = new JsonObject();
                r.addProperty("unloading", true);
                return r;
            }
        });

        register("gun.aim", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                IClientPlayerGunOperator operator = gunOperator();
                boolean aim = getBool(args, "on", true);
                operator.aim(aim);
                JsonObject r = new JsonObject();
                r.addProperty("aiming", operator.isAim());
                return r;
            }
        });

        register("gun.fireSelect", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                gunOperator().fireSelect();
                JsonObject r = new JsonObject();
                r.addProperty("fireSelect", true);
                return r;
            }
        });

        register("gun.melee", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                gunOperator().melee();
                JsonObject r = new JsonObject();
                r.addProperty("melee", true);
                return r;
            }
        });

        register("gun.inspect", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                gunOperator().inspect();
                JsonObject r = new JsonObject();
                r.addProperty("inspect", true);
                return r;
            }
        });

        register("gun.crawl", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                requireNotFrozen();
                IClientPlayerGunOperator operator = gunOperator();
                boolean crawl = getBool(args, "on", true);
                operator.crawl(crawl);
                JsonObject r = new JsonObject();
                r.addProperty("crawling", operator.isCrawl());
                return r;
            }
        });

        register("quit", new Handler() {

            @Override
            public JsonObject run(JsonObject args) {
                Minecraft m = mc();
                m.shutdown();
                JsonObject r = new JsonObject();
                r.addProperty("quitting", true);
                return r;
            }
        });
    }

    private static void applyLook(EntityClientPlayerMP p, double yaw, double pitch) {
        p.rotationYaw = (float) yaw;
        p.rotationPitch = (float) pitch;
        p.prevRotationYaw = (float) yaw;
        p.prevRotationPitch = (float) pitch;
        p.rotationYawHead = (float) yaw;
        p.prevRotationYawHead = (float) yaw;
    }

    private static void lookAt(EntityClientPlayerMP p, double x, double y, double z) {
        double dx = x - p.posX;
        double dy = y - (p.posY + (double) p.getEyeHeight());
        double dz = z - p.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0D / Math.PI));
        applyLook(p, yaw, pitch);
    }

    private static IClientPlayerGunOperator gunOperator() {
        EntityClientPlayerMP p = player();
        try {
            return IClientPlayerGunOperator.fromLocalPlayer(p);
        } catch (Throwable t) {
            throw new IllegalStateException(
                "local player does not implement IClientPlayerGunOperator (mixin missing?): " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> screenButtons(GuiScreen screen) {
        try {
            Field field = GuiScreen.class.getDeclaredField("buttonList");
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof List<?>) {
                return (List<Object>) value;
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return new ArrayList<Object>();
    }

    private static void requireNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("world is frozen; call `mcctl unfreeze` first");
        }
    }

    private static void freeze() {
        Minecraft m = mc();
        if (m.theWorld == null || frozen) {
            return;
        }
        savedScreen = m.currentScreen;
        frozen = true;
        m.displayGuiScreen(new DevFreezeScreen());
    }

    private static void unfreeze() {
        Minecraft m = mc();
        if (!frozen) {
            return;
        }
        frozen = false;
        GuiScreen previous = savedScreen;
        savedScreen = null;
        if (m.currentScreen instanceof DevFreezeScreen) {
            m.displayGuiScreen(previous);
        }
    }
}
