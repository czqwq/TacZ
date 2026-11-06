package com.tacz.guns.command.sub;

import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;

import com.tacz.guns.config.sync.SyncConfig;

/**
 * 移植说明：{@code /tacz config <key> <state:boolean>}。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/ConfigCommand.java}）：
 * 
 * <pre>
 * Commands.literal("config")
 *     .then(
 *         Commands.argument("key", EnumArgument.enumArgument(ConfigKey.class))
 *             .then(
 *                 Commands.argument("state", BoolArgumentType.bool())
 *                     .executes(ConfigCommand::setConfig)))
 * </pre>
 * 
 * 按 {@link ConfigKey} 写 {@code SyncConfig} 的服务端开关，然后发送
 * {@code Component.translatable(key.lang + "." + (state ? "enabled" : "disabled"))}。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>{@code Commands.literal("config")} → {@link #CONFIG_NAME}（由 {@link com.tacz.guns.command.RootCommand}
 * 分发），{@code args[0] = key}、{@code args[1] = state}。</li>
 * <li>{@code EnumArgument.enumArgument(ConfigKey.class)} → {@link ConfigKey#byName(String)}
 * （{@code valueOf} + 大小写不敏感，与 Forge {@code EnumArgument} 的宽松匹配一致）；
 * 非法值在 1.7.10 由 {@link WrongUsageException} 报用法（上游由 brigadier 的 argument 解析报错）。</li>
 * <li>{@code BoolArgumentType.bool()} → {@link CommandBase#parseBoolean}（只接受 true/false/1/0）。</li>
 * <li>Java 16 {@code switch} 表达式（{@code case x -> ...}）→ Java 8 经典 {@code switch}/{@code break}。</li>
 * <li>{@code Component.translatable(...)} → {@link ChatComponentTranslation}；
 * {@code sendSystemMessage(...)} → {@link ICommandSender#addChatMessage}。
 * <b>语言 key 未改动</b>：{@code commands.tacz.config.<snake_case>.<enabled|disabled>}。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：上游在 {@code key == null} 时返回 0（brigadier 下不可达的防御分支）；
 * 1.7.10 在解析阶段就把未知 key 当成用法错误抛出，因此该分支不会再被走到。
 */
public class ConfigCommand {

    public static final String CONFIG_NAME = "config";
    /** 上游参数名，仅用于用法串，字符串不可改 */
    private static final String KEY = "key";
    private static final String ENABLE = "state";

    private static final String USAGE = "/tacz " + CONFIG_NAME + " <" + KEY + "> <" + ENABLE + ":boolean>";
    /** 上游 {@code com.mojang.brigadier.Command.SINGLE_SUCCESS} */
    private static final int SINGLE_SUCCESS = 1;

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length != 2) {
            throw new WrongUsageException(USAGE);
        }
        ConfigKey key = ConfigKey.byName(args[0]);
        if (key == null) {
            throw new WrongUsageException(USAGE);
        }
        boolean state = CommandBase.parseBoolean(sender, args[1]);
        switch (key) {
            case defaultTableLimit:
                SyncConfig.ENABLE_TABLE_FILTER.set(Boolean.valueOf(state));
                break;
            case serverShootNetworkCheck:
                SyncConfig.SERVER_SHOOT_NETWORK_V.set(Boolean.valueOf(state));
                break;
            case serverShootCooldownCheck:
                SyncConfig.SERVER_SHOOT_COOLDOWN_V.set(Boolean.valueOf(state));
                break;
            default:
                break;
        }
        sender.addChatMessage(new ChatComponentTranslation(key.lang + "." + (state ? "enabled" : "disabled")));
        return SINGLE_SUCCESS;
    }

    public static String getUsage() {
        return USAGE;
    }

    /** {@link ConfigKey#values()} 的名字，供补全使用（与枚举声明顺序一致） */
    public static String[] getKeyNames() {
        ConfigKey[] keys = ConfigKey.values();
        String[] names = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            names[i] = keys[i].name();
        }
        return names;
    }

    /**
     * 配置项枚举，与上游逐字一致（名字、顺序、语言 key）。
     */
    public enum ConfigKey {

        defaultTableLimit("commands.tacz.config.default_table_limit"),
        serverShootNetworkCheck("commands.tacz.config.server_shoot_network_check"),
        serverShootCooldownCheck("commands.tacz.config.server_shoot_cooldown_check"),;

        public final String lang;

        ConfigKey(String lang) {
            this.lang = lang;
        }

        /**
         * 1.7.10 版 {@code EnumArgument} 的解析：先按原名匹配，再按大写匹配（大小写不敏感）。
         *
         * @return 匹配到的枚举，未知值返回 {@code null}
         */
        public static ConfigKey byName(String name) {
            if (name == null) {
                return null;
            }
            for (ConfigKey key : values()) {
                if (key.name()
                    .equals(name)) {
                    return key;
                }
            }
            String upper = name.toUpperCase(Locale.ROOT);
            for (ConfigKey key : values()) {
                if (key.name()
                    .toUpperCase(Locale.ROOT)
                    .equals(upper)) {
                    return key;
                }
            }
            return null;
        }
    }
}
