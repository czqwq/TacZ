package com.tacz.guns.mixin.client;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.7.10 移植特有（<b>上游没有这个文件</b>）：
 * 把 {@code GuiContainerCreative$ContainerCreative} 的物品表与滚动暴露给
 * {@code client/gui/components/CreativePackFilters}，用来替代它在生产环境必然失败的 MCP 名反射。
 *
 * <h2>为什么需要它</h2>
 * {@code CreativePackFilters} 要「把创造物品栏当前页签的条目替换成筛选后的子集」。1.7.10 的载体是
 * {@code GuiContainerCreative$ContainerCreative#itemList}（{@code GuiContainerCreative.java:1006}
 * {@code public List<net.minecraft.item.ItemStack> itemList}）与
 * {@code ContainerCreative#scrollTo(float)}（第 1038 行）。
 *
 * <p>
 * 难点在于 {@code ContainerCreative} 是
 * <b>包私有</b>内部类（{@code GuiContainerCreative.java:1003} {@code static class ContainerCreative
 * extends Container}），于是：
 * <ul>
 * <li>从 {@code com.tacz.guns.*} 里<b>写不出这个类型</b>，无法直接访问；</li>
 * <li>也不能用 {@code @Mixin(GuiContainerCreative.class)} + {@code @Shadow} 绕：
 * {@code itemList} 声明在 {@code ContainerCreative} 上、{@code inventorySlots} 声明在父类
 * {@code Container} 上，而 <b>Mixin 的 {@code @Shadow} 只解析目标类自有的成员、不遍历父类</b>
 * （依据 UniMixins 0.3.1 内的 Mixin 源码：
 * {@code MixinPreProcessorStandard#attachFields} → {@code MixinTargetContext#findField} →
 * {@code TargetClassContext#findAliasedField}，只遍历 {@code this.classNode.fields} 与
 * {@code this.mixinFields}）。</li>
 * </ul>
 * 唯一干净的做法就是本类：用 {@code @Mixin(targets = "...$ContainerCreative")}（字符串目标，可以指向
 * 包私有内部类）+ {@code @Accessor} / {@code @Invoker}。这样**不硬编码 SRG 名** ——
 * Mixin 的 refmap 会把 {@code itemList} / {@code scrollTo} 这些 MCP 名映射成运行期字段名/方法名，
 * dev 与生产都能用。
 *
 * <h2>用法</h2>
 * 由 {@link CreativeModeInventoryScreenMixin} 在 {@code initGui} 里装一个桥：
 * 
 * <pre>
 * {@code
 * CreativePackFilters.containerAccessProvider = ...
 * // 桥内部：
 * if (screen.inventorySlots instanceof CreativeContainerAccessor accessor) { ... }
 * }
 * </pre>
 * 
 * 桥用 {@code instanceof} 判断：如果本类没被列进 {@code mixins.tacz.json}（Mixin 未应用），
 * {@code instanceof} 恰好为 {@code false}，桥返回 {@code null}，
 * {@code CreativePackFilters} 自动退回旧反射路径 —— <b>只降级、不崩溃</b>。
 *
 * <h2>生效要求</h2>
 * <b>必须</b>写进 {@code src/main/resources/mixins.tacz.json} 的 {@code client} 数组：
 * {@code "client.CreativeContainerAccessor"}。漏配的后果只是「内容包筛选不生效」，不会启动崩。
 */
@Mixin(targets = "net.minecraft.client.gui.inventory.GuiContainerCreative$ContainerCreative")
public interface CreativeContainerAccessor {

    /** 对应 {@code ContainerCreative#itemList}（第 1006 行，public 字段）。 */
    @Accessor("itemList")
    List<ItemStack> tacz$getItemList();

    /** 对应 {@code ContainerCreative#scrollTo(float)}（第 1038 行，public 方法）。 */
    @Invoker("scrollTo")
    void tacz$scrollTo(float value);
}
