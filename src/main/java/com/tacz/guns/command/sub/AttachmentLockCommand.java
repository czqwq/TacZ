package com.tacz.guns.command.sub;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.item.IGun;

/**
 * 移植说明：{@code /tacz attachment_lock <target> <AttachmentLock:boolean>}。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/AttachmentLockCommand.java}）：
 * 
 * <pre>
 * Commands.literal("attachment_lock")
 *     .then(
 *         Commands.argument("target", EntityArgument.entities())
 *             .then(
 *                 Commands.argument("AttachmentLock", BoolArgumentType.bool())
 *                     .executes(AttachmentLockCommand::setAttachmentLock)))
 * </pre>
 * 
 * 对每个目标主手物品是 {@code IGun} 的实体调用 {@code iGun.setAttachmentLock(stack, locked)}，返回改动数量。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>{@code Commands.literal("attachment_lock")} → {@link #ATTACHMENT_LOCK_NAME}（由
 * {@link com.tacz.guns.command.RootCommand} 分发），参数从 {@code args[0]} 起算。</li>
 * <li>{@code EntityArgument.entities()} / {@code EntityArgument.getEntities(context, "target")}
 * → {@link CommandTargets#getLivingEntities(ICommandSender, String)}（1.7.10 只有玩家选择器，
 * 细节与丢失语义见该类注释）。</li>
 * <li>{@code BoolArgumentType.bool()} / {@code BoolArgumentType.getBool(context, ...)}
 * → {@link CommandBase#parseBoolean}（{@code Boolean.parseBoolean} 的严格版：只接受
 * {@code true/false/1/0}，非法值抛原版 {@code commands.generic.boolean.invalid}，不新增语言 key）。</li>
 * <li>{@code LivingEntity#getMainHandItem()} → {@link EntityLivingBase#getHeldItem()}（1.7.10 同名语义：
 * 玩家返回当前选中槽，非玩家返回 0 号装备槽）。</li>
 * <li>{@code ItemStack.EMPTY}/{@code stack.isEmpty()} → 1.7.10 用 {@code null} 表示空栈，故判 {@code stack != null}。</li>
 * <li>{@code instanceof IGun iGun}（Java 16 模式匹配）→ 显式强转（Java 8 写法）。</li>
 * <li>返回值：上游返回 {@code cnt}（brigadier 允许任意 int），1.7.10 {@code processCommand} 是 void，
 * 这里仍保留 {@code int} 返回以便日志/调试使用。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：brigadier 的参数缺失提示与 {@code CommandSyntaxException}（受检异常）；
 * 1.7.10 用 {@link WrongUsageException}（运行时异常）表达同样的"用法错误"。
 */
public class AttachmentLockCommand {

    public static final String ATTACHMENT_LOCK_NAME = "attachment_lock";
    /** 上游参数名，仅用于用法串与补全，字符串不可改 */
    private static final String ENTITY = "target";
    private static final String GUN_ATTACHMENT_LOCK = "AttachmentLock";

    private static final String USAGE = "/tacz attachment_lock <" + ENTITY + "> <" + GUN_ATTACHMENT_LOCK + ":boolean>";

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length != 2) {
            throw new WrongUsageException(USAGE);
        }
        List<EntityLivingBase> entities = CommandTargets.getLivingEntities(sender, args[0]);
        boolean locked = CommandBase.parseBoolean(sender, args[1]);
        int cnt = 0;
        for (EntityLivingBase living : entities) {
            ItemStack stack = living.getHeldItem();
            if (stack != null && stack.getItem() instanceof IGun) {
                ((IGun) stack.getItem()).setAttachmentLock(stack, locked);
                cnt++;
            }
        }
        return cnt;
    }

    public static String getUsage() {
        return USAGE;
    }
}
