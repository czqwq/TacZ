package com.tacz.guns.compat.nei;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.tacz.guns.GunMod;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * 枪械模组的 NEI（NotEnoughItems）插件入口。
 *
 * <p>
 * <b>为什么需要这个类</b>：1.7.10 没有 JEI，配方查看器是 NEI。NEI 通过
 * {@code codechicken.core.ClassDiscoverer} 扫描所有 mod jar，找出「类名以 {@code NEI} 开头、
 * 以 {@code Config} 结尾、实现 {@link IConfigureNEI}」的类（见 CCC 的 {@code ClassDiscoverer}
 * 与 NEI 的 {@code ClientHandler#loadPluginsList}），然后实例化并调用 {@link #loadConfig()}。
 * 因此类名 {@code NEITaczConfig} 是 API 约定的一部分，不能改。
 *
 * <p>
 * <b>与上游 1.20.1 JEI 插件的对应关系</b>（上游 {@code compat/jei/GunModPlugin}，
 * 1.7.10 侧见 {@code docs/review/r8/nei.md}）：
 * <table border="1">
 * <tr>
 * <th>上游 JEI 分类</th>
 * <th>本移植 NEI handler</th>
 * </tr>
 * <tr>
 * <td>{@code GunSmithTableCategory}（每个工作台一个）</td>
 * <td>{@link GunSmithTableRecipeHandler}（每个工作台一个实例，含 R/U 双向 + 催化剂）</td>
 * </tr>
 * <tr>
 * <td>{@code GunAmmoQueryCategory}</td>
 * <td>{@link GunAmmoQueryHandler}</td>
 * </tr>
 * <tr>
 * <td>{@code AmmoQueryCategory}</td>
 * <td>{@link AmmoQueryHandler}</td>
 * </tr>
 * <tr>
 * <td>{@code AttachmentQueryCategory}</td>
 * <td>{@link AttachmentQueryHandler}</td>
 * </tr>
 * <tr>
 * <td>{@code GunAttachmentQueryCategory}</td>
 * <td>{@link GunAttachmentQueryHandler}</td>
 * </tr>
 * <tr>
 * <td>{@code registerItemSubtypes}（JEI 子类型）</td>
 * <td>{@link NeiItemVariants}（{@code API.setItemListEntries} 注入 NBT 变体）</td>
 * </tr>
 * <tr>
 * <td>JEI category icon / mod 归属</td>
 * <td>{@link NeiHandlerInfos}（{@code HandlerInfo}：页签图标 + mod 名）</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>加载时机</b>：NEI 在「进入世界后」于独立线程加载插件（{@code NEIClientConfig#bootNEI}；
 * 插件线程跑完后 NEI 才 {@code RecipeCatalysts.loadCatalystInfo()}，所以本类里用
 * {@code API.addRecipeCatalyst} 登记的催化剂能被正确合并）。此时客户端枪包索引/配方已经由
 * {@code CommonAssetsManager} 同步并解析完毕。所有 handler 的查询都在玩家按 R/U 时惰性执行，
 * 不依赖加载瞬间的数据快照。
 */
public class NEITaczConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        GunMod.LOGGER.info("[TACZ] loading NEI integration (gun smith table recipes + item queries)");
        try {
            NeiItemVariants.register();
        } catch (Throwable throwable) {
            GunMod.LOGGER.error("[TACZ] failed to register NEI item variants", throwable);
        }
        try {
            registerGunSmithTables();
        } catch (Throwable throwable) {
            GunMod.LOGGER.error("[TACZ] failed to register NEI gun smith table handlers", throwable);
        }
        try {
            registerQueryHandlers();
        } catch (Throwable throwable) {
            GunMod.LOGGER.error("[TACZ] failed to register NEI query handlers", throwable);
        }
        try {
            // 页签图标 / mod 名（NEI HandlerInfo）。NEI 的 handler 信息事件在 FML LoadComplete 就发了，
            // 早于本插件加载，因此这里投递到客户端主线程补登记（详见 NeiHandlerInfos 的类注释）。
            NeiHandlerInfos.register();
        } catch (Throwable throwable) {
            GunMod.LOGGER.error("[TACZ] failed to register NEI handler infos", throwable);
        }
        try {
            // 预热查询数据（约 1.7 万次配件兼容性判断）：放在插件加载线程做，避免玩家第一次按 R/U 时卡顿。
            if (NeiQueryData.hasData()) {
                NeiQueryData.get();
            }
        } catch (Throwable throwable) {
            GunMod.LOGGER.error("[TACZ] failed to prewarm NEI query data", throwable);
        }
    }

    /**
     * 每个枪械工作台类型注册一个 recipe + usage handler，并把工作台方块物品登记为 NEI 催化剂
     * （对工作台物品按 U → 显示它支持的全部配方）。
     */
    private static List<GunSmithTableRecipeHandler> registerGunSmithTables() {
        List<GunSmithTableRecipeHandler> handlers = GunSmithTableRecipeHandler.createAll();
        for (GunSmithTableRecipeHandler handler : handlers) {
            // R/U 双向都由同一个 handler 的 loadCraftingRecipes/loadUsageRecipes 承担，
            // 与上游 JEI「一个 category 同时参与 recipe 与 usage 查询」的语义一致。
            API.registerRecipeHandler(handler);
            API.registerUsageHandler(handler);
            ItemStack tableIcon = handler.getTableIcon();
            if (tableIcon != null) {
                // 上游 JEI registerRecipeCatalysts 的等价物。
                API.addRecipeCatalyst(tableIcon, handler);
            }
        }
        GunMod.LOGGER.info("[TACZ] NEI gun smith table handlers: {}", handlers.size());
        for (GunSmithTableRecipeHandler handler : handlers) {
            GunMod.LOGGER.info("[TACZ]   - {} ({})", handler.getRecipeName(), handler.getHandlerId());
        }
        return handlers;
    }

    private static void registerQueryHandlers() {
        // 上游 JEI 把 AmmoStorageUpgradeRecipe 展开成原版 shaped 配方合并进 CRAFTING 分类；
        // NEI 里单开一页展示同样的 3x3 形状（NEI 内置工作台 handler 不认自定义 IRecipe）。
        registerQueryHandler(new GunAmmoQueryHandler());
        registerQueryHandler(new AmmoQueryHandler());
        registerQueryHandler(new AttachmentQueryHandler());
        registerQueryHandler(new GunAttachmentQueryHandler());
        registerQueryHandler(new AmmoStorageUpgradeHandler());
    }

    /** R/U 双向都注册（{@link TemplateRecipeHandler} 同时实现 {@code ICraftingHandler} 与 {@code IUsageHandler}）。 */
    private static void registerQueryHandler(TemplateRecipeHandler handler) {
        API.registerRecipeHandler(handler);
        API.registerUsageHandler(handler);
    }

    @Override
    public String getName() {
        return "Timeless & Classics Guns: Zero NEI";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }
}
