package com.tacz.guns.command.sub;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.item.IGun;

/**
 * 移植说明：{@code /tacz dummy <target> <dummyAmount:int>}。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/DummyAmmoCommand.java}）：
 * 
 * <pre>
 * Commands.literal("dummy")
 *     .then(
 *         Commands.argument("target", EntityArgument.entities())
 *             .then(
 *                 Commands.argument("dummyAmount", IntegerArgumentType.integer(0))
 *                     .executes(DummyAmmoCommand::setDummy)))
 * </pre>
 * 
 * 给目标主手的枪械设置"虚拟备弹"数量，返回改动数量。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>{@code Commands.literal("dummy")} → {@link #DUMMY_NAME}（由 {@link com.tacz.guns.command.RootCommand}
 * 分发）。</li>
 * <li>{@code EntityArgument.getEntities(context, "target")}
 * → {@link CommandTargets#getLivingEntities(ICommandSender, String)}（1.7.10 只有玩家选择器）。</li>
 * <li>{@code IntegerArgumentType.integer(0)} / {@code getInteger(...)}
 * → {@code CommandBase.parseIntWithMin}（{@code min = 0}，越界抛原版
 * {@code commands.generic.num.tooSmall}，不新增语言 key）。</li>
 * <li>{@code LivingEntity#getMainHandItem()} → {@link EntityLivingBase#getHeldItem()}。</li>
 * <li>{@code ItemStack.EMPTY}/{@code isEmpty()} → 1.7.10 以 {@code null} 表示空栈。</li>
 * <li>{@code instanceof IGun iGun} → 显式强转（Java 8）。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：brigadier 的 {@code CommandSyntaxException}（受检）→ 1.7.10 的
 * {@link net.minecraft.command.CommandException}（运行时，{@link WrongUsageException} 的子类型）。
 */
public class DummyAmmoCommand {

    public static final String DUMMY_NAME = "dummy";
    /** 上游参数名，仅用于用法串，字符串不可改 */
    private static final String ENTITY = "target";
    private static final String AMOUNT = "dummyAmount";

    private static final String USAGE = "/tacz " + DUMMY_NAME + " <" + ENTITY + "> <" + AMOUNT + ":integer(>=0)>";

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length != 2) {
            throw new WrongUsageException(USAGE);
        }
        List<EntityLivingBase> entities = CommandTargets.getLivingEntities(sender, args[0]);
        int amount = CommandBase.parseIntWithMin(sender, args[1], 0);
        int cnt = 0;
        for (EntityLivingBase living : entities) {
            ItemStack stack = living.getHeldItem();
            if (stack != null && stack.getItem() instanceof IGun) {
                ((IGun) stack.getItem()).setDummyAmmoAmount(stack, amount);
                cnt++;
            }
        }
        return cnt;
    }

    public static String getUsage() {
        return USAGE;
    }
}
