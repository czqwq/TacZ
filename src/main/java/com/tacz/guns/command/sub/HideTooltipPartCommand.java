package com.tacz.guns.command.sub;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.item.GunTooltipPart;

/**
 * 移植说明：{@code /tacz hide_tooltip_part <target> <mask:int>}。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/HideTooltipPartCommand.java}）：
 * 
 * <pre>
 * Commands.literal("hide_tooltip_part")
 *     .then(
 *         Commands.argument("target", EntityArgument.entities())
 *             .then(
 *                 Commands.argument("mask", IntegerArgumentType.integer(0))
 *                     .executes(HideTooltipPartCommand::setHide)))
 * </pre>
 * 
 * 对目标主手 {@code IGun} 物品调用 {@code GunTooltipPart.setHideFlags(stack, mask)}
 * （把 {@link GunTooltipPart} 各位的掩码写进 NBT 的 {@code HideFlags}），返回改动数量。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>{@code Commands.literal("hide_tooltip_part")} → {@link #HIDE_TOOLTIP_PART_NAME}（由
 * {@link com.tacz.guns.command.RootCommand} 分发）。</li>
 * <li>{@code EntityArgument.getEntities(context, "target")}
 * → {@link CommandTargets#getLivingEntities(ICommandSender, String)}。</li>
 * <li>{@code IntegerArgumentType.integer(0)} → {@code CommandBase.parseIntWithMin(sender, str, 0)}。</li>
 * <li>{@code LivingEntity#getMainHandItem()} → {@link EntityLivingBase#getHeldItem()}；
 * {@code ItemStack.EMPTY} → {@code null}。</li>
 * <li>{@code GunTooltipPart.setHideFlags(stack, mask)} 保持调用（方法名与语义不变，掩码位序见
 * {@link GunTooltipPart}）：{@code item/GunTooltipPart#setHideFlags(ItemStack, int)} 已是 1.7.10 形态
 * （写 NBT {@code HideFlags}，读端 {@code getHideFlags} 同键）。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：brigadier 的受检 {@code CommandSyntaxException} → 运行时
 * {@link WrongUsageException}；其余无。
 */
public class HideTooltipPartCommand {

    public static final String HIDE_TOOLTIP_PART_NAME = "hide_tooltip_part";
    /** 上游参数名，仅用于用法串，字符串不可改 */
    private static final String ENTITY = "target";
    private static final String MASK = "mask";

    private static final String USAGE = "/tacz " + HIDE_TOOLTIP_PART_NAME
        + " <"
        + ENTITY
        + "> <"
        + MASK
        + ":integer(>=0)>";

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length != 2) {
            throw new WrongUsageException(USAGE);
        }
        List<EntityLivingBase> entities = CommandTargets.getLivingEntities(sender, args[0]);
        int mask = CommandBase.parseIntWithMin(sender, args[1], 0);
        int cnt = 0;
        for (EntityLivingBase living : entities) {
            ItemStack stack = living.getHeldItem();
            if (stack != null && stack.getItem() instanceof IGun) {
                GunTooltipPart.setHideFlags(stack, mask);
                cnt++;
            }
        }
        return cnt;
    }

    public static String getUsage() {
        return USAGE;
    }
}
