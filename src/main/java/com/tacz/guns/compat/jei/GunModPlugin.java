package com.tacz.guns.compat.jei;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;

import cpw.mods.fml.common.Loader;

/**
 * JEI 兼容入口。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/jei/}
 * —— {@code GunModPlugin}（{@code @JeiPlugin implements IModPlugin}：注册配方分类、配方、催化剂、
 * 配方转移、物品子类型）+ {@code GunModSubtype} + {@code category/*}（6 个
 * {@code IRecipeCategory}）+ {@code entry/*}（5 个查询数据类）+ {@code transfer/*}
 * （{@code IRecipeTransferHandler}），共 14 个文件。
 *
 * <p>
 * <b>1.7.10 形态</b>：JEI 只支持 1.8+，<b>1.7.10 没有 JEI</b>；等价物是
 * <b>NEI（NotEnoughItems）</b>，但它是完全不同的 API（{@code codechicken.nei.api.API} /
 * {@code INEIRecipeHandler} / {@code TemplateRecipeHandler}），不是 JEI 的接口子集 ——
 * 上游那些 {@code IRecipeCategory}/{@code IRecipeTransferHandler} 实现既不能保留签名，
 * 也无法在无 JEI 类型的前提下编译，因此那 13 个纯 JEI 绑定实现类已删除（逐条原因见
 * {@code docs/PORT_STATUS.md}）。
 *
 * <p>
 * 保留本类是为了让「JEI 入口」这个名字有落点，且探测逻辑与上游一致：
 * {@link #LOADED} = JEI 模组在 <b>且</b> {@code mezz.jei.api.IModPlugin} 可加载（1.7.10 恒 {@code false}）。
 * {@link #getPluginUid()} 保留上游实现（返回 {@code tacz:jei}）。
 *
 * <p>
 * <b>NEI（1.7.10 的等价物）已实装</b>：见 {@code com.tacz.guns.compat.nei}（入口
 * {@code NEITaczConfig}，实现 {@code codechicken.nei.api.IConfigureNEI}）——把上游 5 个 JEI 分类
 * 逐个映射为 NEI {@code TemplateRecipeHandler}，并用 {@code API#setItemListEntries} 完成上游
 * {@code registerItemSubtypes} 的子类型等价物。设计/验证清单见 {@code docs/review/r8/nei.md}。
 * 本类继续保留，仅作为「上游 JEI 入口」的符号落点与探测逻辑，不参与 NEI 注册。
 */
public final class GunModPlugin {

    private static final String MOD_ID = "jei";
    private static final String PROBE_CLASS = "mezz.jei.api.IModPlugin";
    /** 上游 {@code GunModPlugin.UID}。 */
    private static final ResourceLocation UID = new ResourceLocation(GunMod.MOD_ID, "jei");

    /** JEI 是否可用。1.7.10 上恒为 {@code false}。 */
    public static final boolean LOADED = Loader.isModLoaded(MOD_ID) && probe();

    private GunModPlugin() {}

    /**
     * 上游 {@code GunModPlugin#getPluginUid()}。
     *
     * <p>
     * 1.7.10 侧 {@code ResourceLocation} 是 {@code net.minecraft.util.ResourceLocation}，语义相同。
     */
    public static ResourceLocation getPluginUid() {
        return UID;
    }

    /** JEI 插件是否应被接上（= {@link #LOADED}）。 */
    public static boolean isAvailable() {
        return LOADED;
    }

    /**
     * 探测入口（1.7.10 侧没有 JEI，正常流程不会调用；留作显式自检点）。
     *
     * <p>
     * 上游 {@code GunModPlugin} 的其余 5 个回调（{@code registerCategories} / {@code registerRecipes} /
     * {@code registerRecipeCatalysts} / {@code registerRecipeTransferHandlers} / {@code registerItemSubtypes}）
     * 都要求 JEI 的注册器类型，1.7.10 无法表达，故不再保留空方法 —— 需要时按类注释里的 NEI 方案重写。
     */
    public static void init() {
        if (!LOADED) {
            return;
        }
        GunMod.LOGGER.info("Detected JEI ({}), but 1.7.10 has no JEI API; use NEI instead", UID);
    }

    /** 反射探测 {@link #PROBE_CLASS}（{@code initialize=false}，不触发对方静态初始化）。 */
    private static boolean probe() {
        try {
            Class.forName(PROBE_CLASS, false, GunModPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
