package com.tacz.guns.command.sub;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/**
 * 移植说明：{@code /tacz debug}。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/DebugCommand.java}）：
 * 
 * <pre>
 * Commands.literal("debug")
 *     .then(
 *         Commands.argument("enable", BoolArgumentType.bool())
 *             .executes(DebugCommand::setValue))
 *     .then(PegboardStressCommand.get())
 * </pre>
 * 
 * 注意 {@code "enable"} 是<b>参数名</b>而不是字面量，所以上游的实际写法是 {@code /tacz debug <true|false>}；
 * 另一个兄弟节点字面量 {@code pegboard} 由 {@link PegboardStressCommand} 提供。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>{@code Commands.literal("debug")} → {@link #DEBUG_NAME}（由 {@link com.tacz.guns.command.RootCommand}
 * 分发），子参数从 {@code args[0]} 起算。</li>
 * <li>{@code BoolArgumentType.bool()} → {@link CommandBase#parseBoolean}（只接受 true/false/1/0）。</li>
 * <li>字面量兄弟节点 {@code pegboard} → {@code args[0].equals(PegboardStressCommand.PEGBOARD_NAME)}
 * 后把剩余参数转交给 {@link PegboardStressCommand#processCommand}。</li>
 * <li>{@code CommandSourceStack#getEntity() instanceof ServerPlayer} → {@code sender instanceof EntityPlayerMP}
 * （1.7.10 的服务端玩家就是 {@link EntityPlayerMP}；命令方块/控制台执行时不发消息，与上游一致）。</li>
 * <li>{@code Component.literal("TacZ Debug Mode is Turn On/Off")} → {@link ChatComponentText}（原样英文字面量，
 * 上游也没有走语言文件，故不新增 key）。</li>
 * <li>{@code sendSystemMessage(...)} → {@link ICommandSender#addChatMessage}。</li>
 * <li>{@code Command.SINGLE_SUCCESS} → {@link #SINGLE_SUCCESS}（值 1）；{@code DEBUG} 字段名与语义不变。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：brigadier 的节点冲突检查与参数缺失提示，1.7.10 用 {@link WrongUsageException} 代替。
 */
public class DebugCommand {

    public static boolean DEBUG = false;
    public static final String DEBUG_NAME = "debug";
    /** 上游参数名（不是字面量），字符串不可改 */
    private static final String ENABLE = "enable";

    /** 上游 {@code com.mojang.brigadier.Command.SINGLE_SUCCESS} */
    public static final int SINGLE_SUCCESS = 1;

    private static final String USAGE = "/tacz " + DEBUG_NAME
        + " <"
        + ENABLE
        + ":boolean>"
        + " | /tacz "
        + DEBUG_NAME
        + " "
        + PegboardStressCommand.PEGBOARD_NAME
        + " ...";

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException(USAGE);
        }
        // 字面量兄弟节点：debug -> pegboard
        if (PegboardStressCommand.PEGBOARD_NAME.equals(args[0])) {
            String[] subArgs = new String[args.length - 1];
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
            return PegboardStressCommand.processCommand(sender, subArgs);
        }
        // 参数节点：debug -> <enable:boolean>
        if (args.length != 1) {
            throw new WrongUsageException(USAGE);
        }
        DEBUG = CommandBase.parseBoolean(sender, args[0]);
        if (sender instanceof EntityPlayerMP) {
            if (DEBUG) {
                sender.addChatMessage(new ChatComponentText("TacZ Debug Mode is Turn On"));
            } else {
                sender.addChatMessage(new ChatComponentText("TacZ Debug Mode is Turn Off"));
            }
        }
        return SINGLE_SUCCESS;
    }

    public static String getUsage() {
        return USAGE;
    }
}
