package com.tacz.guns.init;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;

/**
 * 移植说明：自定义画（painting）。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModPainting.java}）：
 * {@code DeferredRegister<PaintingVariant>} + {@code new PaintingVariant(32, 32)} —— 1.20 的画变体是
 * 数据驱动的注册表条目，可以任意新增尺寸/贴图。
 *
 * <p>
 * <b>1.7.10 形态</b>：画的变体是硬编码的 {@code net.minecraft.entity.item.EntityPainting.EnumArt} 枚举
 * （26 个固定条目 + 固定贴图 {@code painting/p_kong.png} 等），<b>没有注册表</b>，
 * 无法在不改原版类（需 ASM/mixin 注入 enum 常量）的前提下新增自定义画。
 * 因此本工程：
 * <ul>
 * <li>保留资源标识常量 {@link #BLOOD_STRIKE_1}（{@code tacz:blood_strike_1}），便于以后用 mixin 实现时直接使用；</li>
 * <li>{@link #register()} 只打一条 info 日志（说明该特性在 1.7.10 降级为「不可用」），
 * 保持 {@code CommonProxy#preInit} 的调用顺序与上游一致。</li>
 * </ul>
 * <p>
 * <b>丢掉的语义</b>：1.7.10 侧不会出现 TACZ 的自定义画；枪包里的 painting 资源仍然随资源打包，
 * 无副作用。
 */
public class ModPainting {

    public static final int BLOOD_STRIKE_WIDTH = 32;
    public static final int BLOOD_STRIKE_HEIGHT = 32;

    /**
     * 上游 {@code RegistryObject<PaintingVariant> BLOOD_STRIKE_1} 的 1.7.10 等价标识。
     */
    public static final ResourceLocation BLOOD_STRIKE_1 = new ResourceLocation(GunMod.MOD_ID, "blood_strike_1");

    private ModPainting() {}

    /**
     * 对应上游 {@code PAINTINGS.register(bus)}：1.7.10 无画变体注册表，仅记录降级。
     */
    public static void register() {
        GunMod.LOGGER.info(
            "Custom painting variants are not supported on 1.7.10 (hardcoded EnumArt); '{}' stays a resource-only id",
            BLOOD_STRIKE_1);
    }
}
