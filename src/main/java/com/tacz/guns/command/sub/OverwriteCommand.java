package com.tacz.guns.command.sub;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.tacz.guns.config.PreLoadConfig;

/**
 * 移植说明：{@code /tacz overwrite <true|false>}。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/OverwriteCommand.java}）：
 * 
 * <pre>
 * Commands.literal("overwrite")
 *     .then(
 *         Commands.argument("enable", BoolArgumentType.bool())
 *             .executes(OverwriteCommand::setOverwrite))
 * </pre>
 * 
 * 写 {@code PreLoadConfig.override.set(!enable)}，然后按 {@code override.get()} 给执行者发
 * {@code commands.tacz.reload.overwrite_off} / {@code commands.tacz.reload.overwrite_on}。
 * 注意"传 true 表示关闭覆盖"这个反向语义来自上游（{@code set(!enable)}），此处保持不变。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>{@code Commands.literal("overwrite")} → {@link #OVERWRITE_NAME}（由
 * {@link com.tacz.guns.command.RootCommand} 分发）；{@code "enable"} 是上游<b>参数名</b>，
 * 所以调用形态是 {@code /tacz overwrite <true|false>}。</li>
 * <li>{@code BoolArgumentType.bool()} → {@link CommandBase#parseBoolean}。</li>
 * <li>{@code CommandSourceStack#getEntity() instanceof ServerPlayer} → {@code sender instanceof EntityPlayerMP}。</li>
 * <li>{@code Component.translatable(key)} → {@link ChatComponentTranslation}（key 未改动）；
 * {@code sendSystemMessage(...)} → {@link ICommandSender#addChatMessage}。</li>
 * <li>1.7.10 的 {@code ConfigValue#get()} 返回 {@link Boolean} 包装类型（未 load 时可能为 null），
 * 因此用 {@code Boolean.TRUE.equals(...)} 判定，避免上游 {@code boolean} 的拆箱语义在移植后 NPE。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：无（除 brigadier 的参数解析报错改为 {@link WrongUsageException}）。
 */
public class OverwriteCommand {

    public static final String OVERWRITE_NAME = "overwrite";
    /** 上游参数名，字符串不可改 */
    private static final String ENABLE = "enable";

    private static final String USAGE = "/tacz " + OVERWRITE_NAME + " <" + ENABLE + ":boolean>";
    /** 上游 {@code com.mojang.brigadier.Command.SINGLE_SUCCESS} */
    private static final int SINGLE_SUCCESS = 1;

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException(USAGE);
        }
        boolean enable = CommandBase.parseBoolean(sender, args[0]);
        PreLoadConfig.override.set(Boolean.valueOf(!enable));
        // 上游由 Cloth Config 界面关闭时落盘（compat/cloth/MenuIntegration:31）；1.7.10 无该界面 → 命令内直接保存
        PreLoadConfig.save();
        if (sender instanceof EntityPlayerMP) {
            if (Boolean.TRUE.equals(PreLoadConfig.override.get())) {
                sender.addChatMessage(new ChatComponentTranslation("commands.tacz.reload.overwrite_off"));
            } else {
                sender.addChatMessage(new ChatComponentTranslation("commands.tacz.reload.overwrite_on"));
            }
        }
        return SINGLE_SUCCESS;
    }

    public static String getUsage() {
        return USAGE;
    }
}
