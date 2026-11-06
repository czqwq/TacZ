package com.tacz.guns.client.init;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械挂板（GunPegboard / GlowGunPegboard）的染色。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/init/GunPegboardColors.java}，
 * 通过三个事件注册：
 * <ol>
 * <li>{@code RegisterColorHandlersEvent.Block}：给方块注册 {@code BlockColor}（从
 * {@code GunPegboardBlockEntity#getColor()} 取色）；</li>
 * <li>{@code RegisterColorHandlersEvent.Item}：给物品注册 {@code ItemColor}（读
 * {@code DyeableLeatherItem#hasCustomColor/getColor}）；</li>
 * <li>{@code FMLClientSetupEvent} 里的 {@code ItemProperties.register(item, "tacz:dyed", ...)}：
 * 给 1.20 的 JSON 物品模型提供 {@code dyed} 谓词（用于在染色/未染色模型间切换）。</li>
 * </ol>
 *
 * <p>
 * <b>1.7.10 三项全都不需要注册</b>（能力等价，只是落点不同）：
 * <ul>
 * <li>1.7.10 没有 {@code BlockColor}/{@code ItemColor} 注册表（那是 1.8+ 的 {@code IBlockColor}/{@code IItemColor}），
 * 方块/物品染色是<b>类上的方法</b>：
 * {@code Block#colorMultiplier(IBlockAccess,int,int,int)} / {@code Block#getRenderColor(int)} 与
 * {@code Item#getColorFromItemStack(ItemStack,int)}。对应实现已经落在
 * {@code block/GunPegboardBlock} 与 {@code item/GunPegboardItem}（由 {@code common-game} 完成）。</li>
 * <li>1.7.10 <b>没有 JSON 物品模型系统</b>，也就没有 {@code ItemProperties}（模型谓词）；
 * 「染色/未染色用不同贴图」在 1.7.10 由 {@code Item#getColorFromItemStack} 的
 * {@code renderPass} + {@code IIcon} 多图标实现，或直接顶点染色（当前实现是后者：读
 * {@code display.color} NBT 后调色）。</li>
 * </ul>
 *
 * <p>
 * 因此本类在 1.7.10 侧保持零逻辑，只作为上游注册点的定位锚点保留（由
 * {@link ClientSetupEvent#onClientSetup()} 调用，调用本身无副作用）。
 */
@SideOnly(Side.CLIENT)
public final class GunPegboardColors {

    private GunPegboardColors() {}

    /**
     * 对应上游的 {@code RegisterColorHandlersEvent.Block}/{@code .Item}/{@code FMLClientSetupEvent} 三段注册。
     *
     * <p>
     * 1.7.10 的染色走 {@code Block}/{@code Item} 上的方法，无需注册，见类 javadoc。
     */
    public static void setup() {
        // 1.7.10：染色由 block/GunPegboardBlock#colorMultiplier 与
        // item/GunPegboardItem#getColorFromItemStack 承担，此处无注册可做
    }
}
