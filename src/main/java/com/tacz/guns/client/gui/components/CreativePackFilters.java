package com.tacz.guns.client.gui.components;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.RenderSystem;

/**
 * 创造模式物品栏左侧的「内容包筛选」侧栏。A screen-local content pack filter; the registered creative
 * tabs keep their full contents.
 *
 * <p>
 * 移植说明（1.20.1 → 1.7.10）：{@code CreativeModeInventoryScreen} → {@link GuiContainerCreative}；
 * {@code CreativeModeTab} → {@link CreativeTabs}；1.7.10 的 {@code CreativeTabs} 没有注册名/命名空间，
 * 改按页签 label（{@code tab.tacz.*} / {@code tacz.type.*.name}）识别本模组页签，与上游一致：
 * 侧栏只在 TACZ 页签显示（{@link #isFilterableTab}）；
 * 1.7.10 没有 {@code AbstractWidget} ⇒ 控件统一换成 {@link GuiButton}，{@code Tooltip.create(...)}
 * 降级为悬停时画标准 tooltip 框；tab 的当前显示列表：上游 {@code CreativeModeInventoryScreen.ItemPickerMenu#items}
 * → {@code GuiContainerCreative$ContainerCreative#itemList}（包私有内部类，见下）；{@code guiLeft/guiTop} 在
 * 1.7.10 {@code GuiContainer} 里是 {@code protected}，用反射读取（{@link #readField}），取不到也不影响功能 ——
 * {@link #guiLeft}/{@link #guiTop} 会回退到 {@code (width-195)/2}、{@code (height-136)/2}，正好等于原版
 * {@code GuiContainer} 的默认居中位置。
 *
 * <h2>containerAccessProvider 注入钩子（移植特有）</h2>
 * {@code ContainerCreative} 是 {@code GuiContainerCreative} 的<b>包私有</b>内部类
 * （GuiContainerCreative.java:1003 {@code static class ContainerCreative}），本包写不出它的类型，也无法用 Mixin
 * {@code @Shadow} 绕过：{@code itemList} 声明在 {@code ContainerCreative} 上、{@code inventorySlots} 声明在其父类
 * {@code Container} 上，而 Mixin 的 {@code @Shadow} 只解析「目标类自己声明的成员」、<b>不遍历父类</b>。
 * 因此原先「按 MCP 名反射」（{@code readField(container, "itemList")} / {@code findMethod(..., "scrollTo")}）在
 * 生产环境必然失败（运行期字段/方法名是 SRG 名），结果是 {@link #getContainerItemList()} 返回 {@code null} →
 * {@link #showItems} 直接 return →「内容包筛选点了没反应」。
 *
 * <p>
 * 修法：本类只暴露一个可注入的静态钩子 {@code containerAccessProvider}，由
 * {@code mixin.client.CreativeModeInventoryScreenMixin#initGui} 赋值；实现方是
 * {@code mixin/client/CreativeContainerAccessor}（{@code @Accessor("itemList")} / {@code @Invoker("scrollTo")}，
 * refmap 自动把 MCP 名映射成运行期 SRG 名 ⇒ dev 与生产都能用、且不硬编码 SRG 名）。
 * 钩子为 {@code null} 时自动回退旧的 MCP 名反射（dev 可用；生产会失败但不崩，只是筛选不生效）——
 * 即使 {@code mixins.tacz.json} 漏配 {@code CreativeContainerAccessor} 也只是功能降级，<b>不会导致启动崩溃</b>。
 */
public class CreativePackFilters {

    /**
     * 1.7.10 桥接：访问 {@code GuiContainerCreative$ContainerCreative} 的物品表与滚动。
     *
     * <p>
     * 因为 {@code ContainerCreative} 是包私有类，本包不能直接引用它，只能通过这个接口间接使用；
     * 实现见 {@code mixin/client/CreativeModeInventoryScreenMixin} 的静态嵌套桥。
     */
    public interface ContainerAccess {

        /** 当前页签的完整物品表（{@code ContainerCreative#itemList}），可读可写、非 null。 */
        @Nonnull
        List<ItemStack> itemList();

        /** 对应 {@code ContainerCreative#scrollTo(float)}，按滚动位置重建 45 个可见槽位。 */
        void scrollTo(float value);
    }

    /**
     * 「拿 {@code ContainerCreative} 访问器」的注入钩子；{@code null} 表示回退到反射路径。
     *
     * <p>
     * 由 {@code com.tacz.guns.mixin.client.CreativeModeInventoryScreenMixin} 在 {@code initGui} 里赋值，
     * 传进来的 {@link GuiContainerCreative} 只会被读 {@code inventorySlots}，不会持有实例引用（无内存泄漏）。
     */
    @Nullable
    public static Function<GuiContainerCreative, ContainerAccess> containerAccessProvider;

    /** 取当前屏幕的容器访问器；没有钩子或实现不可用（Mixin 未应用）时返回 {@code null}。 */
    @Nullable
    private ContainerAccess containerAccess() {
        Function<GuiContainerCreative, ContainerAccess> provider = containerAccessProvider;
        if (provider == null) {
            return null;
        }
        try {
            return provider.apply(this.screen);
        } catch (RuntimeException e) {
            // 钩子实现异常时不能让 GUI 崩，退回反射路径
            return null;
        }
    }

    /**
     * 侧栏选项卡背景图。
     *
     * <p>
     * <b>1.7.10 修正（真 bug）</b>：上游 1.20.1 用 {@code textures/gui/recipe_book.png}
     * （{@code tmp/TACZ-1.20.1/.../CreativePackFilters.java:29}），但该贴图是 <b>1.13 才加入原版</b>的，
     * 1.7.10 的 {@code assets/minecraft/textures/gui/} 下只有
     * {@code book / demo_background / icons / options_background / resource_packs / stream_indicator / widgets}.png
     * （实测列举，<b>没有</b> {@code recipe_book.png}）。原样保留会让
     * {@code TextureManager#bindTexture} 走缺图分支，侧栏渲染成紫黑 / 纯白方块。
     *
     * <p>
     * 改用 1.7.10 原版创造模式物品栏的选项卡贴图。左侧（第二排）选项卡的取样坐标见
     * {@code net/minecraft/client/gui/inventory/GuiContainerCreative.java:40}（贴图常量）与
     * {@code :953}（{@code k += 64} 表示左侧列）/{@code :933}（选中时 {@code k += 32}）：
     * u 恒为 0，v = 64（未选中）/ 96（选中），尺寸 28×32。
     */
    private static final ResourceLocation TAB_TEXTURE = new ResourceLocation(
        "textures/gui/container/creative_inventory/tabs.png");
    /** 左侧选项卡贴图的取样 u（{@code GuiContainerCreative.java:927}：{@code j = i * 28}，第 0 列）。 */
    private static final int TAB_TEXTURE_U = 0;
    /** 左侧选项卡未选中的取样 v（{@code GuiContainerCreative.java:953}：{@code k += 64}）。 */
    private static final int TAB_TEXTURE_V_IDLE = 64;
    /** 左侧选项卡选中的取样 v（{@code GuiContainerCreative.java:933}：{@code k += 32}）。 */
    private static final int TAB_TEXTURE_V_SELECTED = 96;
    /**
     * 原版选项卡贴图的单元高度（{@link #TAB_HEIGHT} 是上游 1.20 的 26，原版 sprite 是 32）。
     *
     * <p>
     * 绘制时整张 28×32 sprite 会被**纵向压进** 28×{@link #TAB_HEIGHT} 的框里，
     * 使「绘制矩形 == 命中矩形」且相邻选项卡（间距 {@link #TAB_SPACING}=29）不再重叠，见
     * {@code PackFilterButton#drawButton} 的注释。
     */
    private static final int TAB_TEXTURE_HEIGHT = 32;
    private static final ResourceLocation ALL_PACKS_ICON = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/creative_pack_all.png");
    private static final int VISIBLE_PACKS = 3;
    private static final int TAB_HEIGHT = 26;
    private static final int TAB_SPACING = 29;

    private final GuiContainerCreative screen;
    private final Runnable resetScroll;
    private final Map<CreativeTabs, String> selections = new IdentityHashMap<>();
    private final List<PackFilterButton> buttons = new ArrayList<>();
    private final List<FilterOption> options = new ArrayList<>();
    private ActionButton previousButton;
    private ActionButton nextButton;
    private CreativeTabs currentTab;
    private List<ItemStack> sourceItems;
    private long packInfoVersion = -1L;
    private int packScroll;

    public CreativePackFilters(GuiContainerCreative screen, Runnable resetScroll) {
        this.screen = screen;
        this.resetScroll = resetScroll;
    }

    public void init(Consumer<GuiButton> add, CreativeTabs tab) {
        this.buttons.clear();
        for (int i = 0; i <= VISIBLE_PACKS; i++) {
            PackFilterButton button = new PackFilterButton(
                guiLeft(this.screen) - 28,
                guiTop(this.screen) + 11 + TAB_SPACING * i);
            this.buttons.add(button);
            add.accept(button);
        }
        this.previousButton = new ActionButton(
            guiLeft(this.screen) - 24,
            guiTop(this.screen) - 3,
            20,
            12,
            new ChatComponentText("\u25B2"),
            new ChatComponentTranslation("gui.tacz.creative_pack_filter.previous"),
            () -> scrollPacks(-1));
        this.nextButton = new ActionButton(
            guiLeft(this.screen) - 24,
            guiTop(this.screen) + 127,
            20,
            12,
            new ChatComponentText("\u25BC"),
            new ChatComponentTranslation("gui.tacz.creative_pack_filter.next"),
            () -> scrollPacks(1));
        add.accept(this.previousButton);
        add.accept(this.nextButton);
        updateTab(tab, true);
    }

    public void updateTab(CreativeTabs tab, boolean force) {
        if (!RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR.get()) {
            if (this.currentTab == tab) {
                showItems(getDisplayItems(tab));
            }
            this.selections.clear();
            hideWidgets();
            return;
        }
        if (tab == null || !isFilterableTab(tab)) {
            hideWidgets();
            return;
        }

        long reloadVersion = ClientAssetsManager.INSTANCE.getPackInfoReloadVersion();
        // 1.7.10 修正（P0，真 bug）：**不能**照搬上游的 `sourceItems == displayItems` 身份比较。
        // 1.20 的 `CreativeModeTab#getDisplayItems()` 返回 tab 内部缓存的同一个 Collection 实例，所以
        // 那条身份判等是有效的「内容没变就跳过」；1.7.10 的等价物
        // `CreativeTabs#displayAllReleventItems(List)` 是**往调用方传入的列表里灌数据**
        // （见下方 getDisplayItems），每次调用都是新 List + 新 ItemStack 实例 → 身份判等**恒为 false**
        // → 下面 updateButtons()/applyFilter() → showItems() 的
        // `resetScroll.run() + scrollTo(0)` 会被执行；而 {@code CreativeModeInventoryScreenMixin}
        // 是在 `drawScreen` HEAD 每帧调本方法（force=false），于是**每帧**都：
        // ① 重建整页 ItemStack（含遍历 Item.itemRegistry）；② 把创造栏物品列表钉回第一页（scrollTo(0)）
        // → 创造模式物品栏**无法滚动**。此处改用两个真实信号：「页签身份」+「枪包版本」。
        if (!force && this.currentTab == tab && this.sourceItems != null && this.packInfoVersion == reloadVersion) {
            return;
        }
        List<ItemStack> displayItems = getDisplayItems(tab);
        if (this.currentTab != tab) {
            this.packScroll = 0;
        }
        this.currentTab = tab;
        this.sourceItems = displayItems;
        this.packInfoVersion = reloadVersion;
        this.options.clear();

        List<ItemStack> allItems = new ArrayList<>();
        for (ItemStack stack : displayItems) {
            allItems.add(stack == null ? null : stack.copy());
        }
        this.options.add(
            new FilterOption(
                null,
                new ChatComponentTranslation("gui.tacz.creative_pack_filter.all"),
                null,
                ALL_PACKS_ICON,
                allItems));
        Map<String, List<ItemStack>> groupedItems = groupItems(displayItems);
        for (Map.Entry<String, List<ItemStack>> group : groupedItems.entrySet()) {
            String namespace = group.getKey();
            List<ItemStack> items = group.getValue();
            PackInfo info = ClientAssetsManager.INSTANCE.getPackInfo(namespace);
            IChatComponent name;
            if (info == null || info.getName() == null
                || info.getName()
                    .trim()
                    .isEmpty()
                || "custom.tacz.error.no_name".equals(info.getName())) {
                name = new ChatComponentText(namespace);
            } else {
                name = new ChatComponentTranslation(info.getName());
            }
            ResourceLocation logo = info == null ? null : info.getLogo(namespace);
            if (logo != null && !resourceExists(logo)) {
                logo = null;
            }
            // 分组图标取组内首个物品的副本。
            // 1.7.10 修正（防御）：上面 allItems 的分支明确允许 null 元素（见本方法开头
            // `stack == null ? null : stack.copy()`），说明 displayItems 可以含 null；
            // 空组 / 首元素为 null 时不能直接 items.get(0).copy()（会 NPE / IndexOutOfBoundsException）。
            // icon 为 null 是安全的：PackFilterButton#drawButton 只在 logo 为 null 时用它，
            // 而 {@code mcshim.GuiGraphics#renderItem} 对 null 直接 return（GuiGraphics.java:543-545），
            // 等价于上游 {@code ItemStack.EMPTY} 只画空槽。
            ItemStack icon = items.isEmpty() || items.get(0) == null ? null
                : items.get(0)
                    .copy();
            FilterOption option = new FilterOption(namespace, name, icon, logo, items);
            if (GunMod.MOD_ID.equals(namespace)) {
                this.options.add(1, option);
            } else {
                this.options.add(option);
            }
        }
        String selected = this.selections.get(tab);
        if (selected != null && !groupedItems.containsKey(selected)) {
            this.selections.remove(tab);
        }
        this.packScroll = Mth.clamp(this.packScroll, 0, maxScroll());
        updateButtons();
        applyFilter();
    }

    /**
     * 1.7.10 的 {@link CreativeTabs} 没有注册名，无法照上游用 namespace 判断「是不是 TACZ 的 tab」。
     *
     * <p>
     * 上游：{@code CreativeModeInventoryScreen#getTabId} 用
     * {@code BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)} 判断 namespace 是否为 {@code tacz}
     * 且 {@code tab.getType() == CATEGORY}（见 {@code tmp/.../client/gui/components/CreativePackFilters.java:80-84}）。
     * 1.7.10 可用的等价信息是 {@link CreativeTabs#getTabLabel()}：本模组的 16 个页签由
     * {@code init/ModCreativeTabs} 构造，label 恒为 {@code tab.tacz.*} 或 {@code tacz.type.*.name}
     * （{@code ModCreativeTabs.java:64-127}），据此可以精确识别出 TACZ 页签，
     * 从而与上游一致：只在 TACZ 页签显示内容包侧栏，原版/其它模组页签不显示。
     */
    private static boolean isFilterableTab(CreativeTabs tab) {
        if (tab == null || tab == CreativeTabs.tabAllSearch || tab == CreativeTabs.tabInventory) {
            return false;
        }
        String label = tab.getTabLabel();
        return label != null && (label.startsWith("tab.tacz.") || label.startsWith("tacz.type."));
    }

    private void hideWidgets() {
        this.currentTab = null;
        this.sourceItems = null;
        this.buttons.forEach(button -> button.setOption(null));
        this.previousButton.visible = this.nextButton.visible = false;
        this.previousButton.enabled = this.nextButton.enabled = false;
    }

    /** Keeps every stack, including multiple NBT variants of the same registered item. */
    static Map<String, List<ItemStack>> groupItems(Collection<ItemStack> items) {
        Map<String, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack stack : items) {
            if (stack != null && stack.getItem() != null) {
                ItemStack copy = stack.copy();
                ResourceLocation contentId = getContentId(copy);
                if (contentId == null) {
                    continue;
                }
                String namespace = contentId.getResourceDomain();
                List<ItemStack> group = groups.get(namespace);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(namespace, group);
                }
                group.add(copy);
            }
        }
        return groups;
    }

    static ResourceLocation getContentId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        if (stack.getItem() instanceof IGun gun) {
            return gun.getGunId(stack);
        }
        if (stack.getItem() instanceof IAmmo ammo) {
            return ammo.getAmmoId(stack);
        }
        if (stack.getItem() instanceof IAttachment attachment) {
            return attachment.getAttachmentId(stack);
        }
        if (stack.getItem() instanceof IBlock block) {
            return block.getBlockId(stack);
        }
        if (stack.getItem() instanceof IAmmoBox box) {
            ResourceLocation ammoId = box.getAmmoId(stack);
            if (!DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                return ammoId;
            }
        }
        // 1.7.10 的 RegistryNamespaced#getNameForObject 返回 String（"domain:path"），不是 ResourceLocation
        String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        return registryName == null ? null : new ResourceLocation(registryName);
    }

    private void select(FilterOption option) {
        if (option.namespace() == null) {
            this.selections.remove(this.currentTab);
        } else {
            this.selections.put(this.currentTab, option.namespace());
        }
        applyFilter();
    }

    private void applyFilter() {
        String selected = this.selections.get(this.currentTab);
        FilterOption option = null;
        for (FilterOption entry : this.options) {
            if (Objects.equals(entry.namespace(), selected)) {
                option = entry;
                break;
            }
        }
        if (option == null && !this.options.isEmpty()) {
            option = this.options.get(0);
        }
        if (option != null) {
            showItems(option.items());
        }
    }

    private void showItems(Collection<ItemStack> items) {
        List<ItemStack> containerItems = getContainerItemList();
        if (containerItems == null) {
            return;
        }
        containerItems.clear();
        for (ItemStack stack : items) {
            containerItems.add(stack == null ? null : stack.copy());
        }
        this.resetScroll.run();
        scrollContainerTo(0.0F);
    }

    private int maxScroll() {
        return Math.max(0, this.options.size() - 1 - VISIBLE_PACKS);
    }

    private void scrollPacks(int delta) {
        this.packScroll = Mth.clamp(this.packScroll + delta, 0, maxScroll());
        updateButtons();
    }

    private void updateButtons() {
        if (this.options.isEmpty()) {
            return;
        }
        this.buttons.get(0)
            .setOption(this.options.get(0));
        for (int i = 1; i < this.buttons.size(); i++) {
            int optionIndex = this.packScroll + i;
            this.buttons.get(i)
                .setOption(optionIndex < this.options.size() ? this.options.get(optionIndex) : null);
        }
        this.previousButton.visible = this.nextButton.visible = maxScroll() > 0;
        this.previousButton.enabled = this.packScroll > 0;
        this.nextButton.enabled = this.packScroll < maxScroll();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR.get() || this.currentTab == null || delta == 0) {
            return false;
        }
        int x = guiLeft(this.screen) - 28;
        int y = guiTop(this.screen) + 11;
        if (mouseX >= x && mouseX < x + 32 && mouseY >= y && mouseY < y + TAB_SPACING * VISIBLE_PACKS + TAB_HEIGHT) {
            scrollPacks(delta > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    // ================================================================================================
    // 1.7.10 专属桥接
    // ================================================================================================

    private static List<ItemStack> getDisplayItems(CreativeTabs tab) {
        List<ItemStack> list = new ArrayList<>();
        // 1.20 CreativeModeTab#getDisplayItems() → 1.7.10 CreativeTabs#displayAllReleventItems(List)
        tab.displayAllReleventItems(list);
        return list;
    }

    private static boolean resourceExists(ResourceLocation location) {
        try {
            // 1.20 ResourceManager#getResource(...).isEmpty() → 1.7.10 抛 IOException 表示不存在
            return Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 读取 {@code GuiContainerCreative$ContainerCreative#itemList}。
     *
     * <p>
     * 优先走 {@link #containerAccessProvider}（Mixin {@code @Accessor}，refmap 会自动映射到运行期 SRG 名，
     * dev 与生产都可用）；钩子缺失时才回退到旧的 MCP 名反射（仅 dev 有效，生产返回 {@code null}）。
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private List<ItemStack> getContainerItemList() {
        ContainerAccess access = containerAccess();
        if (access != null) {
            return access.itemList();
        }
        Object container = this.screen.inventorySlots;
        if (container == null) {
            return null;
        }
        Object value = readField(container, "itemList");
        return value instanceof List ? (List<ItemStack>) value : null;
    }

    /**
     * 调用 {@code GuiContainerCreative$ContainerCreative#scrollTo(float)}。
     *
     * <p>
     * 同 {@link #getContainerItemList()}：优先走注入钩子，缺失时回退旧反射。
     */
    private void scrollContainerTo(float value) {
        ContainerAccess access = containerAccess();
        if (access != null) {
            access.scrollTo(value);
            return;
        }
        Object container = this.screen.inventorySlots;
        if (container == null) {
            return;
        }
        // 回退路径：1.7.10 的 ContainerCreative 是包私有类，只能按 MCP 名反射（生产环境会失败，静默跳过）
        try {
            Method method = findMethod(container.getClass(), "scrollTo", float.class);
            if (method != null) {
                method.setAccessible(true);
                method.invoke(container, value);
            }
        } catch (ReflectiveOperationException ignored) {
            // 缺失时静默跳过：筛选列表已写入，仅滚动位置未复位
        }
    }

    @Nullable
    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Nullable
    private static Object readField(Object target, String name) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return null;
    }

    private static int guiLeft(GuiContainerCreative screen) {
        Object value = readField(screen, "guiLeft");
        return value instanceof Integer ? (Integer) value : (screen.width - 195) / 2;
    }

    private static int guiTop(GuiContainerCreative screen) {
        Object value = readField(screen, "guiTop");
        return value instanceof Integer ? (Integer) value : (screen.height - 136) / 2;
    }

    private record FilterOption(@Nullable String namespace, IChatComponent name, ItemStack icon,
        @Nullable ResourceLocation logo, List<ItemStack> items) {}

    /** 带回调的 {@link GuiButton}（1.7.10 没有 {@code Button.OnPress}），并复刻上游 {@code Tooltip.create(...)}。 */
    private static class ActionButton extends GuiButton {

        private final Runnable action;
        private final @Nullable IChatComponent tooltip;

        private ActionButton(int x, int y, int width, int height, IChatComponent message,
            @Nullable IChatComponent tooltip, Runnable action) {
            super(0, x, y, width, height, message == null ? "" : message.getFormattedText());
            this.tooltip = tooltip;
            this.action = action;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            super.drawButton(mc, mouseX, mouseY);
            if (!this.visible || this.tooltip == null) {
                return;
            }
            boolean hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
            if (hovered) {
                GuiGraphics graphics = GuiGraphics.create();
                graphics.drawHoveringText(
                    Collections.singletonList(this.tooltip.getFormattedText()),
                    mouseX,
                    mouseY,
                    mc.fontRenderer);
            }
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                this.action.run();
                return true;
            }
            return false;
        }
    }

    private class PackFilterButton extends GuiButton {

        @Nullable
        private FilterOption option;

        private PackFilterButton(int x, int y) {
            super(0, x, y, 32, TAB_HEIGHT, "");
        }

        private void setOption(@Nullable FilterOption option) {
            this.option = option;
            this.visible = this.enabled = option != null;
            this.displayString = option == null || option.name() == null ? ""
                : option.name()
                    .getFormattedText();
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (this.option != null && super.mousePressed(mc, mouseX, mouseY)) {
                select(this.option);
                return true;
            }
            return false;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible || this.option == null) {
                return;
            }
            boolean hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
            this.field_146123_n = hovered;

            boolean selected = Objects.equals(this.option.namespace(), selections.get(currentTab));
            RenderSystem.enableBlend();
            GuiGraphics graphics = GuiGraphics.create();
            // 1.7.10：整块贴原版左侧选项卡 sprite（28×32），不再做上游 1.20 的九宫格拉伸
            // （{@code mcshim.GuiGraphics#blitNineSliced} 的 10 参重载本身就退化为「整图拉伸」，
            // 语义与 1.20 对不上，见该类 javadoc 的降级点说明）。
            // **1.7.10 修正（P2，命中区/尺寸）**：原先把 32px 高的 sprite 从 `y-3` 起画，
            // 而按钮命中矩形是 `(x, y, 32, 26)`，于是
            // ① 选项卡可见的上下各 3px 不可点、右侧多出 4px 空白可点区；
            // ② 相邻选项卡间距 29 < 32px，后画的会盖住前一个的底边 3px，而重叠区里
            // 「看到的是后一个、点到的是前一个」（{@code GuiScreen#mouseClicked} 按列表顺序首个命中即返回）。
            // 现在把整张 28×32 sprite **纵向压进** 28×26 的绘制矩形：绘制矩形 == 命中矩形、相邻不再重叠。
            // 代价是 32→26 的纵向压缩（无信息损失；原版 sprite 在上游 1.20 的 26px 选项卡也是同一形状）。
            // 图标仍画在 y+5..y+21（中心 y+13），正好是 26px 按钮的竖直中心。
            graphics.blit(
                TAB_TEXTURE,
                this.xPosition,
                this.yPosition,
                28,
                TAB_HEIGHT,
                TAB_TEXTURE_U,
                selected ? TAB_TEXTURE_V_SELECTED : TAB_TEXTURE_V_IDLE,
                28,
                TAB_TEXTURE_HEIGHT,
                256,
                256);
            if (this.option.logo() != null) {
                // UVs cover the full image, independently of its pixel dimensions.
                graphics.blit(this.option.logo(), this.xPosition + 8, this.yPosition + 5, 0, 0, 16, 16, 16, 16);
            } else {
                graphics.renderItem(this.option.icon(), this.xPosition + 8, this.yPosition + 5);
            }
            // 上游 setTooltip(Tooltip.create(name)) → 悬停画标准 tooltip 框（AbstractWidget#render 的 tooltip 层）。
            // 1.7.10 没有控件级 tooltip，这里在自绘的最后画（drawScreen TAIL，晚于面板内容）。
            if (hovered && this.option.name() != null) {
                graphics.drawHoveringText(
                    Collections.singletonList(
                        this.option.name()
                            .getFormattedText()),
                    mouseX,
                    mouseY,
                    mc.fontRenderer);
            }
        }
    }
}
