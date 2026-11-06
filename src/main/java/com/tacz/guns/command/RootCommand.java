package com.tacz.guns.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

import com.tacz.guns.command.sub.AttachmentLockCommand;
import com.tacz.guns.command.sub.ConfigCommand;
import com.tacz.guns.command.sub.ConvertCommand;
import com.tacz.guns.command.sub.DebugCommand;
import com.tacz.guns.command.sub.DummyAmmoCommand;
import com.tacz.guns.command.sub.HideTooltipPartCommand;
import com.tacz.guns.command.sub.OverwriteCommand;
import com.tacz.guns.command.sub.ReloadCommand;

/**
 * 移植说明：{@code /tacz} 根命令。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/command/RootCommand.java}）：
 * brigadier 的 {@code Commands.literal(ROOT_NAME).requires(source -> source.hasPermission(2))}，
 * 再 {@code root.then(...)} 挂 8 个子命令，最后交给 {@code CommandDispatcher}。
 *
 * <p>
 * <b>1.7.10 形态</b>：{@link CommandBase} 只有一个平铺的
 * {@link #processCommand(ICommandSender, String[])}，没有语法树。因此这里按 {@code args[0]} 手工分发：
 * {@code Commands.literal("reload")} → {@code args[0].equals("reload")}，各子命令类接收"去掉命令名之后"的
 * {@code String[]}，等价于上游 brigadier 节点下挂的参数。子命令名、参数名与顺序和上游逐字一致。
 *
 * <p>
 * 权限：上游 {@code .requires(src -> src.hasPermission(2))} 只作用在根节点，子节点继承，
 * 因此这里只需 {@link #getRequiredPermissionLevel()} 返回 2（{@link CommandBase} 默认是 4）。
 *
 * <p>
 * <b>丢掉的语义</b>：
 * <ul>
 * <li>brigadier 的自动补全 / 错误定位（"Unknown argument at position N"）没有对应物；
 * 补全只做到第一层子命令名（{@link #addTabCompletionOptions}），参数缺失时抛
 * {@link WrongUsageException} 给出用法串。</li>
 * <li>brigadier 的 {@code Command.SINGLE_SUCCESS}（=1）在 1.7.10 没有消费方（{@code processCommand} 是 void），
 * 子命令仍以 {@code int} 返回，仅用于日志与统计，返回值不再影响游戏逻辑。</li>
 * </ul>
 */
public class RootCommand extends CommandBase {

    private static final String ROOT_NAME = "tacz";
    private static final String USAGE = "/tacz <reload|config|debug|...>";

    /**
     * 子命令名，顺序与上游 {@code RootCommand#register} 里 {@code root.then(...)} 的顺序一致。
     * 这些字符串就是命令名本身，不允许改动。
     */
    public static final String[] SUB_COMMANDS = { AttachmentLockCommand.ATTACHMENT_LOCK_NAME, DebugCommand.DEBUG_NAME,
        DummyAmmoCommand.DUMMY_NAME, OverwriteCommand.OVERWRITE_NAME, ReloadCommand.RELOAD_NAME,
        HideTooltipPartCommand.HIDE_TOOLTIP_PART_NAME, ConvertCommand.CONVERT_NAME, ConfigCommand.CONFIG_NAME };

    public static String getUsageString() {
        return USAGE;
    }

    @Override
    public String getCommandName() {
        return ROOT_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException(USAGE);
        }
        String name = args[0];
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);

        if (AttachmentLockCommand.ATTACHMENT_LOCK_NAME.equals(name)) {
            AttachmentLockCommand.processCommand(sender, subArgs);
        } else if (DebugCommand.DEBUG_NAME.equals(name)) {
            DebugCommand.processCommand(sender, subArgs);
        } else if (DummyAmmoCommand.DUMMY_NAME.equals(name)) {
            DummyAmmoCommand.processCommand(sender, subArgs);
        } else if (OverwriteCommand.OVERWRITE_NAME.equals(name)) {
            OverwriteCommand.processCommand(sender, subArgs);
        } else if (ReloadCommand.RELOAD_NAME.equals(name)) {
            ReloadCommand.processCommand(sender, subArgs);
        } else if (HideTooltipPartCommand.HIDE_TOOLTIP_PART_NAME.equals(name)) {
            HideTooltipPartCommand.processCommand(sender, subArgs);
        } else if (ConvertCommand.CONVERT_NAME.equals(name)) {
            ConvertCommand.processCommand(sender, subArgs);
        } else if (ConfigCommand.CONFIG_NAME.equals(name)) {
            ConfigCommand.processCommand(sender, subArgs);
        } else {
            throw new WrongUsageException(USAGE);
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUB_COMMANDS);
        }
        return null;
    }
}
