package com.tacz.guns.item;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * common 侧 → client 侧物品显示钩子（1.7.10 无 {@code IClientItemExtensions}/BEWLR）。
 *
 * <p>
 * 1.7.10 的「编译期边界」硬性要求：common 包（item/entity/...）**不得** import {@code com.tacz.guns.client.**}。
 * 上游 1.20.1 的 {@code Item#getName}/{@code Item#initializeClient} 在客户端读取枪包客户端索引
 * （{@code TimelessAPI.getClientXxxIndex}/{@code ClientAssetsManager}）并注册自定义物品渲染器，
 * 这些职责在 1.7.10 改由 client 侧（{@code client-port} 工作流）通过本类的静态字段注入，common 侧只做空安全调用。
 *
 * <p>
 * client 侧注入点：{@code com.tacz.guns.client.init.ClientSetupEvent} 的
 * {@code registerItemRenderers()}（{@code renderer} 钩子）与 {@code registerItemNameProviders()}（名称/染色钩子）。
 * 
 * <pre>
 * {@code
 * ItemClientHooks.itemNameProvider = stack -> { ... };         // 读 ClientAmmoIndex/ClientAttachmentIndex/ClientBlockIndex
 * ItemClientHooks.nameColorProvider = stack -> 0xRRGGBB;       // 0 表示不染色
 * ItemClientHooks.registerItemRenderer = item -> MinecraftForgeClient.registerItemRenderer(item, renderer);
 * }
 * </pre>
 * 
 * 若客户端未注入（服务端/独立测试），所有方法安全回退到服务端原版名称。
 */
public final class ItemClientHooks {

    /**
     * 客户端物品名称解析器：返回 null 表示不处理，回退服务端名称。
     * client 侧实现见 {@code client/resource/ClientAssetsManager} 相关索引。
     */
    @Nullable
    public static Function<ItemStack, String> itemNameProvider = null;

    /**
     * 客户端物品名称染色（ARGB）。0 表示不染色。
     */
    @Nullable
    public static ToIntFunction<ItemStack> nameColorProvider = null;

    /**
     * 客户端物品渲染器注册器（{@code IItemRenderer}）。由 {@code ClientSetupEvent#registerItemRenderers()} 注入。
     */
    @Nullable
    public static Consumer<Item> registerItemRenderer = null;

    private ItemClientHooks() {}

    /**
     * @param fallback 服务端可用的兜底名称（通常是 {@code super.getItemStackDisplayName}）
     */
    public static String getItemName(ItemStack stack, String fallback) {
        Function<ItemStack, String> provider = itemNameProvider;
        if (provider == null || stack == null) {
            return fallback;
        }
        String name = provider.apply(stack);
        return name == null ? fallback : name;
    }

    public static int getNameColor(ItemStack stack) {
        ToIntFunction<ItemStack> provider = nameColorProvider;
        if (provider == null || stack == null) {
            return 0;
        }
        return provider.applyAsInt(stack);
    }

    /**
     * 客户端注册物品渲染器时调用（遍历 {@code init/ModItems} 的 public static 字段）。
     */
    public static void registerRenderer(Item item) {
        Consumer<Item> consumer = registerItemRenderer;
        if (consumer != null && item != null) {
            consumer.accept(item);
        }
    }
}
