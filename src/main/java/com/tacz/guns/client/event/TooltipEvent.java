package com.tacz.guns.client.event;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.client.tooltip.AttachmentGunGridOverlay;
import com.tacz.guns.client.tooltip.ClientAmmoBoxTooltip;
import com.tacz.guns.client.tooltip.ClientAmmoStorageTooltip;
import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.client.tooltip.ClientBlockItemTooltip;
import com.tacz.guns.client.tooltip.ClientGunTooltip;
import com.tacz.guns.client.tooltip.PackInfoText;
import com.tacz.guns.client.tooltip.TooltipLineUtils;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.inventory.tooltip.AmmoBoxTooltip;
import com.tacz.guns.inventory.tooltip.AmmoStorageTooltip;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import com.tacz.guns.inventory.tooltip.BlockItemTooltip;
import com.tacz.guns.inventory.tooltip.GunTooltip;
import com.tacz.guns.item.AmmoStorageBlockItem;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 物品 tooltip 的 1.7.10 接线点：① 富信息块（原「tooltip 组件」）；② 高级提示下的 TACZ id 行。
 *
 * <p>
 * 上游：tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/TooltipEvent.java
 * （上游 {@code @Mod.EventBusSubscriber} 无 side 限定，但类在 client 包内；1.7.10 由 {@code ClientProxy}
 * 注册到 {@code MinecraftForge.EVENT_BUS}）。
 *
 * <p>
 * 1.20 的富 tooltip 走 {@code Item#getTooltipImage()} → {@code TooltipComponent} 管线；1.7.10 只有
 * {@code Item#addInformation(...)} 的纯文本通道，而 {@code item/**} 又受「L1 编译边界禁止 import client」
 * 约束、没法自己调 {@code client/tooltip/**}。这里用 {@link ItemTooltipEvent}（{@code addInformation} 之后、
 * 最终列表成型前唯一的客户端扩展点）把 5 个 {@code Client*Tooltip} 接回运行期。
 *
 * <p>
 * 逐类型来源（上游 file:line → 本类实现）：枪械 {@code api/item/gun/AbstractGunItem.java:398-411} →
 * {@link ClientGunTooltip}；配件 {@code item/AttachmentItem.java:99-101} → {@link ClientAttachmentItemTooltip}；
 * 弹药 {@code item/AmmoItem.java:91-110 appendHoverText}（本就是纯文本）→ {@link #ammoHoverText}；弹药盒
 * {@code item/AmmoBoxItem.java:259-274} → {@link ClientAmmoBoxTooltip}；弹药库
 * {@code item/AmmoStorageBlockItem.java:57-85} → {@link ClientAmmoStorageTooltip}；其它方块物品
 * {@code item/GunSmithTableItem.java:92-96} → {@link ClientBlockItemTooltip}。
 * 富信息块插在第 1 个下标（物品名之后），对应上游 {@code GuiGraphics#gatherTooltipComponents} 的
 * {@code list.add(1, ...)}（见 {@link #RICH_TOOLTIP_INDEX}）。
 *
 * <p>
 * 已知降级：① {@code renderImage(...)} 除配件枪械网格外全部不生效 —— {@code ItemTooltipEvent} 只能往
 * {@code List<String>} 塞文本、画不了位图（受影响的是弹药盒/弹药库的 16×16 图标）；② 少数行丢掉上游
 * {@code renderText} 的绘制颜色（如弹药名 {@code 0xffaa00}），只有写在 {@code ChatStyle} 里的颜色保留。
 * 排版方面：本类在事件末尾对<b>整张最终行表</b>做按可用宽度的折行（{@link #wrapTooltipLines}），
 * 绘制侧再由 {@code mixin/client/GuiScreenMixin} 补 1.20 等价的边界 clamp，两者合起来对齐上游
 * 「tooltip 不横向出屏」的观感。
 *
 * <p>
 * 配件「按住 Shift」的枪械图标网格：{@link ClientAttachmentItemTooltip#getLegacyTooltipLines()} 在文本行里
 * 预留网格区域，再由本类转发的 {@code GuiScreenEvent.DrawScreenEvent.Pre/Post} 交给
 * {@link AttachmentGunGridOverlay} 补画（tooltip 在 {@code drawScreen} 末尾画出，
 * {@code EntityRenderer.java:1136-1138} 的 Post 恰好在其后）。
 *
 * <p>
 * 平台替换：{@code getItemStack()}/{@code getToolTip()} → 1.7.10 事件的 public final 字段；
 * {@code getFlags().isAdvanced()} → {@link ItemTooltipEvent#showAdvancedItemTooltips}（F3+H）；
 * {@code Component} → {@code List<String>}（{@link #formatTooltip} 返回带 {@code §8} 前缀的 String）；
 * {@code ModItems.GUN_SMITH_TABLE.get()} → 静态字段；1.7.10 空栈是 null，需 null 守卫。
 *
 * <p>
 * <b>1.7.10 特有</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法} 这里改为实例方法（类名/方法名/参数/语义不变）。
 * 又因 {@code EventBus#register(Class)} 在 1.7.10 无效，本类提供 public 无参构造，由 {@code ClientProxy}
 * 反射 {@code new} 后注册到正确的总线。
 */
public class TooltipEvent {

    /**
     * 富信息块的插入下标：1.20 {@code GuiGraphics#gatherTooltipComponents} 里是
     * {@code list.add(1, ClientTooltipComponent.create(imageComponent))} —— 紧跟第 0 行物品名。
     */
    private static final int RICH_TOOLTIP_INDEX = 1;

    /** 上游 {@code item/AmmoStorageBlockItem.java:24} 的 {@code MAX_TOOLTIP_ITEMS}。 */
    private static final int MAX_TOOLTIP_ITEMS = 5;

    /** 上游 {@code BlockItem#getBlockEntityData(stack)} 的键；1.7.10 同键（见 {@code block/AmmoStorageBlock.java:66}）。 */
    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    /** 上游 {@code AmmoStorageBlockItem.ENTRIES_TAG}（同 {@code block/entity/AmmoStorageBlockEntity.java:58}）。 */
    private static final String ENTRIES_TAG = "Entries";
    /** 上游 {@code STACK_TAG}（同 {@code inventory/AmmoStorageInventory#save()} 写的 "Stack"）。 */
    private static final String STACK_TAG = "Stack";
    /** 上游 {@code COUNT_TAG}（同上 "Count"）。 */
    private static final String COUNT_TAG = "Count";

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.itemStack;
        if (stack == null || stack.getItem() == null) {
            return;
        }
        AttachmentGunGridOverlay.beginTooltip(stack);

        // ① 富信息块（上游 getTooltipImage() → TooltipComponent → ClientTooltipComponent）。
        appendRichTooltip(event.toolTip, stack);

        // ② 高级提示下的 TACZ id 行（上游 TooltipEvent 原逻辑，追加在末尾）。
        if (event.showAdvancedItemTooltips && RenderConfig.ENABLE_TACZ_ID_IN_TOOLTIP.get()) {
            if (stack.getItem() instanceof GunItemDataAccessor item) {
                event.toolTip.add(formatTooltip(GunItemDataAccessor.GUN_ID_TAG, item.getGunId(stack)));
            } else if (stack.getItem() instanceof AmmoItemDataAccessor item) {
                event.toolTip.add(formatTooltip(AmmoItemDataAccessor.AMMO_ID_TAG, item.getAmmoId(stack)));
            } else if (stack.getItem() instanceof AttachmentItemDataAccessor item) {
                event.toolTip
                    .add(formatTooltip(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG, item.getAttachmentId(stack)));
            } else
                if (stack.getItem() instanceof BlockItemDataAccessor item && !ModItems.GUN_SMITH_TABLE.equals(item)) {
                    event.toolTip.add(formatTooltip(BlockItemDataAccessor.BLOCK_ID, item.getBlockId(stack)));
                }
        }

        // ③ 1.7.10 平台兜底：整表按可用宽度折行（覆盖所有物品路径，含非 TACZ 物品与长 lore）。
        wrapTooltipLines(event.toolTip);
    }

    /**
     * 把最终 tooltip 行表就地折行到 {@link TooltipLineUtils#maxLineWidth()}（{@code min(300, scaledWidth-26)}）。
     *
     * <p>
     * 1.7.10 的 tooltip 排版器 {@code GuiScreen#drawHoveringText}（{@code GuiScreen.java:156-232}）的框宽 =
     * 最长一行，<b>不折行</b>；上游 1.20 的 tooltip 组件按 {@code getWidth(Font)} 预留、整体受排版器约束
     * （{@code GuiGraphics#renderTooltipInternal}，{@code tmp/minecraft-src-1.20.1/.../GuiGraphics.java:575-616}）。
     * 因此这里在 {@code ItemTooltipEvent}（{@code ItemStack.java:727}，进 tooltip 列表成型前唯一的客户端扩展点）
     * 对<b>整张最终行表</b>逐行折行：不管行来自 TACZ 富 tooltip、原版物品名/附魔、自定义 lore 还是其它模组，
     * 只要超宽就断行，从而与上游「tooltip 不出屏」对齐。
     *
     * <p>
     * 纯空白布局行（r6 配件图标网格的撑宽行/空行）不折，见 {@link TooltipLineUtils#wrapLines}。
     * 绘制侧还有第二道保险（{@code mixin/client/GuiScreenMixin} 的 1.20 等价边界 clamp），
     * 两者配合保证「不超宽 + 不越出屏幕边缘」。
     *
     * <p>
     * 绝不抛异常：tooltip 每帧重算，任何异常都会变成「悬停即崩」，因此内部吞掉并 debug 记录。
     */
    private static void wrapTooltipLines(@Nullable List<String> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }
        try {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            if (font == null) {
                return;
            }
            TooltipLineUtils.wrapLines(tooltip, font, TooltipLineUtils.maxLineWidth());
        } catch (RuntimeException e) {
            GunMod.LOGGER.debug("[TACZ] tooltip wrapping skipped", e);
        }
    }

    /**
     * {@code DrawScreenEvent.Pre}：清掉上一帧的网格残留（本帧若没有 tooltip 生成，Post 时就不会误画）。
     *
     * <p>
     * 依赖：{@code EntityRenderer.java:1136-1138} 保证 Pre 与 Post 成对 post。
     */
    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        AttachmentGunGridOverlay.clearFrame();
    }

    /**
     * {@code DrawScreenEvent.Post}：tooltip 已在 {@code drawScreen} 末尾画出，此时补画配件图标网格。
     *
     * <p>
     * 优先级 {@code LOWEST}：留出余量让其它 {@code Post} 监听器先做完（网格是最后覆盖上去的图层）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        AttachmentGunGridOverlay.render(event.mouseX, event.mouseY, event.gui);
    }

    public static String formatTooltip(String key, ResourceLocation value) {
        return EnumChatFormatting.DARK_GRAY + String.format("%s: \"%s\"", key, value);
    }

    // ================================================================================================
    // 富 tooltip 接线
    // ================================================================================================

    /**
     * 按物品类型求出富信息行并插到 {@link #RICH_TOOLTIP_INDEX}。
     *
     * <p>
     * <b>绝不抛异常</b>：tooltip 在鼠标悬停期间<b>每帧</b>都会重算，任何一次异常都会变成
     * 「打开背包就崩」。枪包未加载 / 索引缺失 / NBT 损坏都在内部静默跳过。
     */
    private static void appendRichTooltip(List<String> tooltip, ItemStack stack) {
        List<String> lines;
        try {
            lines = buildRichTooltip(stack, tooltip);
        } catch (Throwable t) {
            // 静默降级：不打印整栈（每帧都会命中），只 debug 一行
            GunMod.LOGGER.debug("[TACZ] rich tooltip skipped for {}", stack.getItem(), t);
            return;
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        try {
            tooltip.addAll(Math.min(RICH_TOOLTIP_INDEX, tooltip.size()), lines);
        } catch (UnsupportedOperationException e) {
            // 极端情况：事件调用方传了不可变列表（例如 ItemStack#getTooltip 的只读包装）→ 只能追加失败，跳过
            GunMod.LOGGER.debug("[TACZ] tooltip list is immutable, rich tooltip skipped");
        }
    }

    /**
     * @param target 本 tooltip 的最终文本行列表（配件图标网格需要持有它，才能在 Post 时按实际框宽/行数定位）
     * @return 要插入的文本行；{@code null}/空表表示「这类物品没有富信息」
     */
    @Nullable
    private static List<String> buildRichTooltip(ItemStack stack, List<String> target) {
        Item item = stack.getItem();

        // ── 枪械：上游 api/item/gun/AbstractGunItem.java:398-411 getTooltipImage()
        if (item instanceof IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(stack);
            if (gunId == null) {
                return null;
            }
            return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> {
                    GunTooltip data = new GunTooltip(
                        stack,
                        iGun,
                        index.getGunData()
                            .getAmmoId(),
                        index);
                    return new ClientGunTooltip(data).getLegacyTooltipLines();
                })
                .orElse(null);
        }

        // ── 配件：上游 item/AttachmentItem.java:99-101 getTooltipImage()
        if (item instanceof IAttachment iAttachment) {
            ResourceLocation attachmentId = iAttachment.getAttachmentId(stack);
            if (attachmentId == null) {
                return null;
            }
            AttachmentItemTooltip data = new AttachmentItemTooltip(attachmentId, iAttachment.getType(stack), stack);
            ClientAttachmentItemTooltip clientTooltip = new ClientAttachmentItemTooltip(data);
            List<String> lines = clientTooltip.getLegacyTooltipLines();
            // Shift 展开：文本行里已预留网格区域，登记给 DrawScreenEvent.Post 补画（TooltipEvent 是唯一接线点）
            AttachmentGunGridOverlay.GridLayout grid = clientTooltip.getGridLayout();
            if (grid != null) {
                AttachmentGunGridOverlay.submit(stack, clientTooltip, grid, target);
            }
            return lines;
        }

        // ── 弹药盒：上游 item/AmmoBoxItem.java:259-274 getTooltipImage()
        // 注意顺序：AmmoBoxItem 只实现 IAmmoBox，不实现 IAmmo，所以这里放前面也一样；
        // 但先判 IAmmoBox 可以避免将来出现同时实现两者的物品走错分支。
        if (item instanceof IAmmoBox iAmmoBox) {
            ResourceLocation ammoId = iAmmoBox.getAmmoId(stack);
            if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                return null;
            }
            int ammoCount = iAmmoBox.getAmmoCount(stack);
            if (ammoCount <= 0) {
                return null;
            }
            ItemStack ammoStack = AmmoItemBuilder.create()
                .setId(ammoId)
                .build();
            return new ClientAmmoBoxTooltip(new AmmoBoxTooltip(stack, ammoStack, ammoCount)).getLegacyTooltipLines();
        }

        // ── 弹药：上游 item/AmmoItem.java:91-110 appendHoverText()（纯文本，没有 tooltip 组件）
        if (item instanceof IAmmo iAmmo) {
            return ammoHoverText(stack, iAmmo);
        }

        // ── 弹药库：上游 item/AmmoStorageBlockItem.java:57-85 getTooltipImage()
        if (item instanceof AmmoStorageBlockItem) {
            return ammoStorageTooltip(stack);
        }

        // ── 其它方块物品：上游 item/GunSmithTableItem.java:92-96 getTooltipImage()
        if (item instanceof BlockItemDataAccessor blockItem) {
            ResourceLocation blockId = blockItem.getBlockId(stack);
            if (blockId == null || DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
                return null;
            }
            return new ClientBlockItemTooltip(new BlockItemTooltip(blockId)).getLegacyTooltipLines();
        }

        return null;
    }

    /**
     * 上游 {@code item/AmmoItem.java:91-110 appendHoverText()} 的等价实现（描述 + 枪包名）。
     *
     * <p>
     * 之所以放在这里而不是 {@code item/AmmoItem}：那段要读 {@code ClientAmmoIndex} /
     * {@code ClientAssetsManager}（client 包），而 {@code item/**} 受 L1 编译边界的 common 侧约束；
     * {@code item/AmmoItem.java:95-97} 的 TODO 就是要求「客户端通过 tooltip 事件接回」。
     */
    @Nullable
    private static List<String> ammoHoverText(ItemStack stack, IAmmo iAmmo) {
        ResourceLocation ammoId = iAmmo.getAmmoId(stack);
        if (ammoId == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        TimelessAPI.getClientAmmoIndex(ammoId)
            .ifPresent(index -> {
                String tooltipKey = index.getTooltipKey();
                if (tooltipKey != null) {
                    // 1.7.10 的 .lang 里换行是**字面** `\n` 两字符（Locale#loadLocaleData 不处理转义），
                    // 必须先按字面量切行，否则会把 `\n` 当正文画出来（裁决见 docs/PORT_STATUS.md:39/190）。
                    for (String segment : StatCollector.translateToLocal(tooltipKey)
                        .split("\\\\n", -1)) {
                        if (!segment.isEmpty()) {
                            lines.add(EnumChatFormatting.GRAY + segment);
                        }
                    }
                }
            });
        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(ammoId);
        if (packInfo != null) {
            // 空安全：getName() 可能为 null（第三方包显式写 `"name": null`），见 PackInfoText 类注释。
            IChatComponent name = PackInfoText.name(packInfo, ammoId.getResourceDomain())
                .setChatStyle(
                    new ChatStyle().setColor(EnumChatFormatting.BLUE)
                        .setItalic(Boolean.TRUE));
            lines.add(name.getFormattedText());
        }
        return lines.isEmpty() ? null : lines;
    }

    /**
     * 上游 {@code item/AmmoStorageBlockItem.java:57-85 getTooltipImage()} 的等价实现。
     *
     * <p>
     * 1.20 从 {@code BlockItem#getBlockEntityData(stack)} 读 {@code Entries}；1.7.10 的掉落物把整个方块实体
     * NBT 写在物品的 {@link #BLOCK_ENTITY_TAG} 子树里（{@code block/AmmoStorageBlock.java:196-210}），
     * 结构完全相同（{@code Entries} → 每项 {@code Stack}/{@code Count}，见
     * {@code inventory/AmmoStorageInventory#save()}），因此这里按同一格式反序列化。
     */
    @Nullable
    private static List<String> ammoStorageTooltip(ItemStack stack) {
        NBTTagCompound itemTag = stack.getTagCompound();
        if (itemTag == null || !itemTag.hasKey(BLOCK_ENTITY_TAG, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        NBTTagCompound blockEntityTag = itemTag.getCompoundTag(BLOCK_ENTITY_TAG);
        if (!blockEntityTag.hasKey(ENTRIES_TAG, Constants.NBT.TAG_LIST)) {
            return null;
        }
        NBTTagList entries = blockEntityTag.getTagList(ENTRIES_TAG, Constants.NBT.TAG_COMPOUND);
        List<AmmoStorageTooltip.Entry> mergedEntries = new ArrayList<>();
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entryTag = entries.getCompoundTagAt(i);
            if (!entryTag.hasKey(STACK_TAG, Constants.NBT.TAG_COMPOUND)) {
                continue;
            }
            ItemStack entryStack = ItemStack.loadItemStackFromNBT(entryTag.getCompoundTag(STACK_TAG));
            long count = entryTag.getLong(COUNT_TAG);
            if (entryStack == null || entryStack.getItem() == null || count <= 0) {
                continue;
            }
            mergeAmmoStorageEntry(mergedEntries, entryStack, count);
        }
        if (mergedEntries.isEmpty()) {
            return null;
        }
        // 上游 AmmoStorageBlockItem.java:80-83：只展示前 MAX_TOOLTIP_ITEMS 条，其余折算成「and N more...」
        int displayed = Math.min(MAX_TOOLTIP_ITEMS, mergedEntries.size());
        AmmoStorageTooltip data = new AmmoStorageTooltip(
            new ArrayList<>(mergedEntries.subList(0, displayed)),
            mergedEntries.size() - displayed);
        return new ClientAmmoStorageTooltip(data).getLegacyTooltipLines();
    }

    /**
     * 上游 {@code AmmoStorageBlockItem#mergeTooltipEntry}：同物品同 NBT 的条目合并计数。
     *
     * <p>
     * {@code ItemStack.areItemStacksEqual} 会比较 {@code stackSize}，这里要的是
     * 「同物品 + 同 damage + 同 NBT」（= 1.20 {@code ItemStack.isSameItemSameTags}），
     * 所以用 {@code areItemStackTagsEqual}。
     */
    private static void mergeAmmoStorageEntry(List<AmmoStorageTooltip.Entry> entries, ItemStack stack, long count) {
        for (int i = 0; i < entries.size(); i++) {
            AmmoStorageTooltip.Entry entry = entries.get(i);
            if (ItemStack.areItemStackTagsEqual(entry.ammo(), stack)) {
                entries.set(i, new AmmoStorageTooltip.Entry(entry.ammo(), saturatedAdd(entry.count(), count)));
                return;
            }
        }
        entries.add(new AmmoStorageTooltip.Entry(stack, count));
    }

    /** 对应上游 {@code AmmoStorageBlockItem#saturatedAdd}。 */
    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        // 溢出时 sum 变负（a、b 均 >= 1）
        return sum < 0L ? Long.MAX_VALUE : sum;
    }
}
