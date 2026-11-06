package com.tacz.guns.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.GunPropertyDiagrams;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import com.tacz.guns.client.gui.components.refit.IComponentTooltip;
import com.tacz.guns.client.gui.components.refit.IStackTooltip;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageInstallVirtualAttachment;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageSetAttachmentVariant;
import com.tacz.guns.network.message.ClientMessageSetSlotAdapter;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonAttachmentVariantIndex;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;
import com.tacz.guns.sound.SoundManager;
import com.tacz.guns.util.SlotAdapterHelper;
import com.tacz.guns.util.VariantHelper;
import com.tacz.guns.util.VirtualOemAttachment;

/**
 * 枪械改装界面。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>{@code Screen} → {@link GuiScreen}：{@code init()}→{@link #initGui()}，
 * {@code render(GuiGraphics,...)}→{@link #drawScreen(int, int, float)}，
 * {@code isPauseScreen()}→{@link #doesGuiPauseGame()}，
 * {@code onClose()}→{@link #onGuiClosed()}。</li>
 * <li>{@code addRenderableWidget/clearWidgets/renderables} → {@code buttonList}
 * （1.7.10 的控件列表，由 {@code GuiScreen#drawScreen} 统一绘制）。</li>
 * <li>{@code LocalPlayer} → {@link EntityPlayerSP}，{@code getMinecraft().player} → {@code mc.thePlayer}，
 * {@code getMainHandItem()} → {@code getHeldItem()}，
 * {@code Inventory} → {@link InventoryPlayer}（{@code selected}→{@code currentItem}、
 * {@code getItem(i)}→{@code getStackInSlot(i)}、{@code getContainerSize()}→{@code getSizeInventory()}、
 * {@code getFreeSlot()}→{@code getFirstEmptyStack()}）。</li>
 * <li>{@code Component} → {@link IChatComponent}；{@code player.sendSystemMessage(...)} →
 * {@code player.addChatComponentMessage(...)}。</li>
 * <li>1.7.10 没有旁观者模式，{@code player.isSpectator()} 判断删除。</li>
 * <li>上游的 {@code @Mod.EventBusSubscriber} 在 1.7.10 由 {@code ClientProxy} 显式注册，
 * 注解删除。</li>
 * <li>{@code ItemStack.EMPTY} 在 1.7.10 用 {@code null}。</li>
 * </ul>
 */
public class GunRefitScreen extends GuiScreen {

    public static final ResourceLocation SLOT_TEXTURE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/refit_slot.png");
    public static final ResourceLocation TURN_PAGE_TEXTURE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/refit_turn_page.png");
    public static final ResourceLocation UNLOAD_TEXTURE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/refit_unload.png");
    public static final ResourceLocation ICONS_TEXTURE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/refit_slot_icons.png");

    public static final int ICON_UV_SIZE = 32;
    public static final int SLOT_SIZE = 18;
    private static final int VARIANT_BUTTON_WIDTH = 78;
    private static final int VARIANT_BUTTON_HEIGHT = 12;
    private static final int VARIANT_BUTTON_SPACING = 1;
    private static final int INVENTORY_ATTACHMENT_START_Y = 50;
    private static final int LIST_COLUMN_GAP = 4;
    private static final int INVENTORY_ATTACHMENT_SLOT_COUNT = 8;
    private static boolean HIDE_GUN_PROPERTY_DIAGRAMS = true;

    private int currentPage = 0;

    public GunRefitScreen() {
        super();
        RefitTransform.init();
    }

    public static int getSlotTextureXOffset(ItemStack gunItem, AttachmentType attachmentType) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return -1;
        }
        if (!iGun.allowAttachmentType(gunItem, attachmentType)) {
            return ICON_UV_SIZE * 6;
        }
        switch (attachmentType) {
            case GRIP -> {
                return 0;
            }
            case LASER -> {
                return ICON_UV_SIZE;
            }
            case MUZZLE -> {
                return ICON_UV_SIZE * 2;
            }
            case SCOPE -> {
                return ICON_UV_SIZE * 3;
            }
            case STOCK -> {
                return ICON_UV_SIZE * 4;
            }
            case EXTENDED_MAG -> {
                return ICON_UV_SIZE * 5;
            }
            default -> {
                return -1;
            }
        }
    }

    public static int getSlotsTextureWidth() {
        return ICON_UV_SIZE * 7;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        // 添加配件槽位
        this.addAttachmentTypeButtons();
        // 添加可选配件列表
        this.addInventoryAttachmentButtons();
        // 添加属性图隐藏按钮
        if (HIDE_GUN_PROPERTY_DIAGRAMS) {
            this.add(
                new FlatColorButton(
                    11,
                    11,
                    288,
                    16,
                    new ChatComponentTranslation("gui.tacz.gun_refit.property_diagrams.show"),
                    b -> switchHideButton()));
        } else {
            this.add(new FlatColorButton(14, 14, 12, 12, new ChatComponentText("S"), b -> {
                EntityPlayerSP player = this.mc.thePlayer;
                if (player == null) {
                    return;
                }
                if (IGun.mainHandHoldGun(player)) {
                    IClientPlayerGunOperator.fromLocalPlayer(player)
                        .fireSelect();
                    this.initGui();
                }
            }).setTooltips(new ChatComponentTranslation("gui.tacz.gun_refit.property_diagrams.fire_mode.switch")));
            int buttonYOffset = GunPropertyDiagrams.getHidePropertyButtonYOffset();
            this.add(
                new FlatColorButton(
                    11,
                    buttonYOffset,
                    288,
                    12,
                    new ChatComponentTranslation("gui.tacz.gun_refit.property_diagrams.hide"),
                    b -> switchHideButton()));
        }
    }

    /** 对应 1.20 {@code addRenderableWidget}。 */
    private void add(GuiButton button) {
        this.buttonList.add(button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        GuiGraphics graphics = GuiGraphics.create();
        // 上游 1.20.1 GunRefitScreen#render 只调 super.render，而 Screen#render 仅遍历 renderables、
        // **不**调 renderBackground（tmp/minecraft-src-1.20.1/.../Screen.java:113-118；GUI 由
        // GameRenderer.java:1139-1141 直接渲染，无自动背景层）⇒ 世界与第一人称枪原样透出。
        // 1.7.10 GuiScreen#drawScreen 同样不画背景，因此这里不能补暗色渐变层：
        // 那会把界面中心正在预览的枪体压暗，与上游观感不一致。
        super.drawScreen(mouseX, mouseY, partialTick);

        if (!HIDE_GUN_PROPERTY_DIAGRAMS) {
            GunPropertyDiagrams.draw(graphics, this.fontRendererObj, 11, 11);
        }

        for (GuiButton widget : new ArrayList<>(this.buttonList)) {
            if (widget instanceof IComponentTooltip tooltip) {
                tooltip.renderTooltip(
                    components -> graphics.renderComponentTooltip(this.fontRendererObj, components, mouseX, mouseY));
            } else if (widget instanceof FlatColorButton flatColorButton) {
                // r4 修复（P2）：FlatColorButton 的 tooltip 在 1.7.10 版被改成「由屏幕统一绘制」
                // （见 FlatColorButton 类注释：不能在 drawButton 里画，会被后画的按钮盖住），
                // 但本界面此前**只**遍历了 IComponentTooltip / IStackTooltip 两种接口，漏了
                // FlatColorButton → 变体/适配器按钮的 tooltip 全部不显示。
                // 受影响最明显的是「槽位适配器需要先卸下配件」的提示
                // （{@code gui.tacz.refit.adapter_requires_unload}，见 addSlotAdapterButtons 里
                // noneButton/adapterButton 的 setTooltips）——按钮被禁用但玩家看不到原因。
                // 上游依据：1.20.1 {@code FlatColorButton#renderWidget} 末尾直接调用
                // {@code this.renderToolTip(graphics, minecraft.screen, mouseX, mouseY)}（上游 :58）。
                // 同一写法已在 GunSmithTableScreen#drawScreen（:886-887）使用。
                flatColorButton.renderToolTip(graphics, this, mouseX, mouseY);
            }
        }
        for (GuiButton widget : new ArrayList<>(this.buttonList)) {
            if (widget instanceof IStackTooltip tooltip) {
                tooltip.renderTooltip(stack -> renderAttachmentTooltip(graphics, stack, mouseX, mouseY));
            }
        }
    }

    /**
     * 上游 1.20 {@code AbstractButton#isValidClickButton(int)} 只接受左键（{@code button == 0}）；
     * 1.7.10 {@code GuiScreen#handleMouseInput} 对任意鼠标键都调 {@code mouseClicked}，
     * {@code GuiButton#mousePressed} 也不判键号 → 右键会误触装/卸/变体/适配器按钮。
     * 本界面没有容器槽位（无原版右键分堆语义），因此按上游只把左键交给按钮。
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    private void renderAttachmentTooltip(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        if (!isBuiltinOemAttachment(stack)) {
            graphics.renderTooltip(this.fontRendererObj, stack, mouseX, mouseY);
            return;
        }
        List<IChatComponent> tooltip = IComponentTooltip.getTooltipFromItem(stack);
        if (!tooltip.isEmpty()) {
            tooltip.set(
                0,
                tooltip.get(0)
                    .createCopy()
                    .appendSibling(new ChatComponentTranslation("gui.tacz.refit.builtin_suffix")));
        }
        // 1.20 的 ItemStack#getTooltipImage()（自定义 tooltip 图片）在 1.7.10 无对应，传 null
        graphics.renderTooltip(this.fontRendererObj, tooltip, (Object) null, stack, mouseX, mouseY);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void addInventoryAttachmentButtons() {
        EntityPlayerSP player = this.mc.thePlayer;
        if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE || player == null) {
            return;
        }
        int startX = this.width - 30;
        int startY = INVENTORY_ATTACHMENT_START_Y;
        int pageStart = this.currentPage * INVENTORY_ATTACHMENT_SLOT_COUNT;
        int count = 0;
        int currentY = startY;
        InventoryPlayer inventory = player.inventory;
        ItemStack gunItem = player.getHeldItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }
        AttachmentType attachmentType = RefitTransform.getCurrentTransformType();
        Set<ResourceLocation> displayedAttachmentIds = new HashSet<>();
        List<RefitAttachmentEntry> attachmentEntries = new ArrayList<>();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack inventoryItem = inventory.getStackInSlot(i);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(inventoryItem);
            if (attachment != null && attachment.getType(inventoryItem) == attachmentType) {
                if (!iGun.allowAttachment(gunItem, inventoryItem)) {
                    continue;
                }
                displayedAttachmentIds.add(attachment.getAttachmentId(inventoryItem));
                attachmentEntries.add(new RefitAttachmentEntry(i, inventoryItem, false));
            }
        }

        getVirtualOemAttachments(gunItem, iGun, attachmentType, displayedAttachmentIds)
            .forEach(attachment -> attachmentEntries.add(new RefitAttachmentEntry(-1, attachment, true)));
        // 仅显式标注的内置原厂配件置顶，通用配件保持原有顺序。
        attachmentEntries.sort(Comparator.comparingInt(entry -> isBuiltinOemAttachment(entry.stack()) ? 0 : 1));

        for (RefitAttachmentEntry entry : attachmentEntries) {
            count++;
            if (count <= pageStart) {
                continue;
            }
            if (count > pageStart + INVENTORY_ATTACHMENT_SLOT_COUNT) {
                continue;
            }
            InventoryAttachmentSlot button;
            if (entry.virtualOem()) {
                button = new InventoryAttachmentSlot(startX, currentY, entry.stack(), inventory, b -> {
                    ItemStack attachmentStack = ((InventoryAttachmentSlot) b).getItemStack();
                    IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
                    if (attachment == null) {
                        return;
                    }
                    SoundPlayManager.playerRefitSound(attachmentStack, player, SoundManager.INSTALL_SOUND);
                    ClientMessageInstallVirtualAttachment message = new ClientMessageInstallVirtualAttachment(
                        inventory.currentItem,
                        attachmentType,
                        attachment.getAttachmentId(attachmentStack));
                    NetworkHandler.CHANNEL.sendToServer(message);
                });
            } else {
                button = new InventoryAttachmentSlot(startX, currentY, entry.slotIndex(), inventory, b -> {
                    int slotIndex = ((InventoryAttachmentSlot) b).getSlotIndex();
                    SoundPlayManager
                        .playerRefitSound(inventory.getStackInSlot(slotIndex), player, SoundManager.INSTALL_SOUND);
                    ClientMessageRefitGun message = new ClientMessageRefitGun(
                        slotIndex,
                        inventory.currentItem,
                        attachmentType);
                    NetworkHandler.CHANNEL.sendToServer(message);
                });
            }
            this.add(button);
            currentY = currentY + SLOT_SIZE;
        }
        int totalPage = (count - 1) / INVENTORY_ATTACHMENT_SLOT_COUNT;
        RefitTurnPageButton turnPageButtonUp = new RefitTurnPageButton(startX, startY - 10, true, b -> {
            if (this.currentPage > 0) {
                this.currentPage--;
                this.initGui();
            }
        });
        RefitTurnPageButton turnPageButtonDown = new RefitTurnPageButton(
            startX,
            startY + SLOT_SIZE * INVENTORY_ATTACHMENT_SLOT_COUNT + 2,
            false,
            b -> {
                if (this.currentPage < totalPage) {
                    this.currentPage++;
                    this.initGui();
                }
            });
        if (this.currentPage < totalPage) {
            this.add(turnPageButtonDown);
        }
        if (this.currentPage > 0) {
            this.add(turnPageButtonUp);
        }
    }

    private void addAttachmentTypeButtons() {
        final EntityPlayerSP player = this.mc.thePlayer;
        if (player == null) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(player.getHeldItem());
        if (iGun == null) {
            return;
        }
        int startX = this.width - 30;
        int startY = 10;
        InventoryPlayer inventory = player.inventory;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE) {
                    TimelessAPI.getGunDisplay(player.getHeldItem())
                        .map(GunDisplayInstance::getLaserConfig)
                        .ifPresent(laserConfig -> {
                            if (laserConfig.canEdit()) {
                                // 添加镭射颜色选择器
                                HSVSliderGroup hsvSliderGroup = new HSVSliderGroup(
                                    this.width - 140,
                                    this.height - 64,
                                    120,
                                    16,
                                    inventory,
                                    inventory.currentItem,
                                    AttachmentType.NONE);
                                this.add(hsvSliderGroup.getHueSlider());
                                this.add(hsvSliderGroup.getSaturationSlider());
                            }
                        });
                }
                continue;
            }
            GunAttachmentSlot button = new GunAttachmentSlot(
                startX,
                startY,
                type,
                inventory.currentItem,
                inventory,
                b -> {
                    GunAttachmentSlot slot = (GunAttachmentSlot) b;
                    AttachmentType buttonType = slot.getType();
                    boolean allow = slot.isAllow();
                    boolean isCurrent = RefitTransform.getCurrentTransformType() == buttonType;
                    // 与上游一致的三种目标视图：
                    // ① 槽位不允许 → 退回概览；② 点的就是当前槽位 → 退回概览；③ 其它 → 切到该槽位。
                    AttachmentType targetView = (!allow || (isCurrent && buttonType != AttachmentType.NONE))
                        ? AttachmentType.NONE
                        : buttonType;
                    boolean changed = RefitTransform.changeRefitScreenView(targetView);
                    // 如果这个槽位不允许安装配件，则默认退回概览，不选中槽位。
                    if (!allow) {
                        if (changed) {
                            this.initGui();
                        }
                        return;
                    }
                    // 点击的是当前选中的槽位，则退回概览
                    if (isCurrent && buttonType != AttachmentType.NONE) {
                        if (changed) {
                            this.initGui();
                        }
                        return;
                    }
                    // 切换选中的槽位。
                    if (changed) {
                        this.initGui();
                    }
                });
            if (RefitTransform.getCurrentTransformType() == type) {
                button.setSelected(true);
                int adapterListX = this.width - 30 - VARIANT_BUTTON_WIDTH - LIST_COLUMN_GAP;
                boolean hasSlotAdapters = !SlotAdapterHelper.getAvailableSlotAdapters(player.getHeldItem(), type)
                    .isEmpty();
                int variantListX = hasSlotAdapters ? adapterListX - VARIANT_BUTTON_WIDTH - LIST_COLUMN_GAP
                    : startX + SLOT_SIZE - VARIANT_BUTTON_WIDTH;
                // 添加拆卸配件按钮
                RefitUnloadButton unloadButton = new RefitUnloadButton(startX + 5, startY + SLOT_SIZE + 2, b -> {
                    ItemStack attachmentItem = button.getAttachmentItem();
                    if (!isEmpty(attachmentItem)) {
                        int freeSlot = inventory.getFirstEmptyStack();
                        if (freeSlot != -1 || VirtualOemAttachment.isMarked(attachmentItem)) {
                            SoundPlayManager.playerRefitSound(attachmentItem, player, SoundManager.UNINSTALL_SOUND);
                            ClientMessageUnloadAttachment message = new ClientMessageUnloadAttachment(
                                inventory.currentItem,
                                RefitTransform.getCurrentTransformType());
                            NetworkHandler.CHANNEL.sendToServer(message);
                        } else {
                            player.addChatComponentMessage(
                                new ChatComponentTranslation("gui.tacz.gun_refit.unload.no_space"));
                        }
                    }
                });
                if (!isEmpty(button.getAttachmentItem())) {
                    this.add(unloadButton);
                    addVariantButtons(
                        type,
                        button.getAttachmentItem(),
                        inventory,
                        variantListX,
                        startY + SLOT_SIZE + 14);

                    if (button.getAttachmentItem()
                        .getItem() instanceof IAttachment iAttachment) {
                        TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(button.getAttachmentItem()))
                            .map(ClientAttachmentIndex::getLaserConfig)
                            .ifPresent(laserConfig -> {
                                if (laserConfig.canEdit()) {
                                    // 添加镭射颜色选择器
                                    HSVSliderGroup hsvSliderGroup = new HSVSliderGroup(
                                        this.width - 140,
                                        this.height - 64,
                                        120,
                                        16,
                                        inventory,
                                        inventory.currentItem,
                                        type);
                                    this.add(hsvSliderGroup.getHueSlider());
                                    this.add(hsvSliderGroup.getSaturationSlider());
                                }
                            });
                    }
                }
                addSlotAdapterButtons(
                    type,
                    button.getAttachmentItem(),
                    inventory,
                    adapterListX,
                    INVENTORY_ATTACHMENT_START_Y);
            }
            this.add(button);
            startX = startX - SLOT_SIZE;
        }
    }

    private void addVariantButtons(AttachmentType type, ItemStack attachmentItem, InventoryPlayer inventory, int x,
        int y) {
        if (!(attachmentItem != null && attachmentItem.getItem() instanceof IAttachment attachment)) {
            return;
        }
        ResourceLocation attachmentId = attachment.getAttachmentId(attachmentItem);
        List<Map.Entry<ResourceLocation, CommonAttachmentVariantIndex>> variants = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentVariantIndex> entry : TimelessAPI
            .getAllCommonAttachmentVariantIndex()) {
            if (entry.getValue()
                .appliesTo(attachmentId)) {
                variants.add(entry);
            }
        }
        variants.sort(
            Comparator.comparingInt(
                entry -> entry.getValue()
                    .getSort()));
        if (variants.isEmpty()) {
            return;
        }
        ResourceLocation current = VariantHelper.getVariant(attachmentItem);
        IChatComponent standardLabel = current == null
            ? new ChatComponentText("> ").appendSibling(new ChatComponentTranslation("gui.tacz.refit.variant_default"))
            : new ChatComponentTranslation("gui.tacz.refit.variant_default");
        this.add(new FlatColorButton(x, y, VARIANT_BUTTON_WIDTH, VARIANT_BUTTON_HEIGHT, standardLabel, b -> {
            NetworkHandler.CHANNEL
                .sendToServer(new ClientMessageSetAttachmentVariant(inventory.currentItem, type, null));
            this.initGui();
        }));
        int currentY = y + VARIANT_BUTTON_HEIGHT + VARIANT_BUTTON_SPACING;
        for (Map.Entry<ResourceLocation, CommonAttachmentVariantIndex> entry : variants) {
            ResourceLocation variantId = entry.getKey();
            IChatComponent name = indexName(
                entry.getValue()
                    .getPojo()
                    .getName(),
                variantId);
            IChatComponent label = variantId.equals(current) ? new ChatComponentText("> ").appendSibling(name) : name;
            this.add(new FlatColorButton(x, currentY, VARIANT_BUTTON_WIDTH, VARIANT_BUTTON_HEIGHT, label, b -> {
                NetworkHandler.CHANNEL
                    .sendToServer(new ClientMessageSetAttachmentVariant(inventory.currentItem, type, variantId));
                this.initGui();
            }));
            currentY += VARIANT_BUTTON_HEIGHT + VARIANT_BUTTON_SPACING;
        }
    }

    private void addSlotAdapterButtons(AttachmentType type, ItemStack attachmentItem, InventoryPlayer inventory, int x,
        int y) {
        ItemStack gunItem = inventory.getStackInSlot(inventory.currentItem);
        List<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>> adapters = SlotAdapterHelper
            .getAvailableSlotAdapterIndices(gunItem, type);
        if (adapters.isEmpty()) {
            return;
        }

        ResourceLocation attachmentId = null;
        if (!isEmpty(attachmentItem)) {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (attachment == null || attachment.getType(attachmentItem) != type) {
                return;
            }
            attachmentId = attachment.getAttachmentId(attachmentItem);
        }

        ResourceLocation current = SlotAdapterHelper.getSelectedAvailableSlotAdapter(gunItem, type);
        final ResourceLocation currentAttachmentId = attachmentId;
        int currentY = y;

        boolean noneSelected = current == null;
        IChatComponent noneLabel = noneSelected
            ? new ChatComponentText("> ").appendSibling(new ChatComponentTranslation("gui.tacz.refit.adapter_none"))
            : new ChatComponentTranslation("gui.tacz.refit.adapter_none");
        FlatColorButton noneButton = new FlatColorButton(
            x,
            currentY,
            VARIANT_BUTTON_WIDTH,
            VARIANT_BUTTON_HEIGHT,
            noneLabel,
            b -> {
                this.currentPage = 0;
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetSlotAdapter(inventory.currentItem, type, null));
                this.initGui();
            });
        if (currentAttachmentId != null && !SlotAdapterHelper.allowsDirectAttachment(gunItem, currentAttachmentId)) {
            noneButton.enabled = false;
            noneButton.setTooltips(new ChatComponentTranslation("gui.tacz.refit.adapter_requires_unload"));
        }
        this.add(noneButton);
        currentY += VARIANT_BUTTON_HEIGHT + VARIANT_BUTTON_SPACING;

        for (Map.Entry<ResourceLocation, CommonSlotAdapterIndex> entry : adapters) {
            ResourceLocation adapterId = entry.getKey();
            IChatComponent name = indexName(
                entry.getValue()
                    .getPojo()
                    .getName(),
                adapterId);
            IChatComponent label = adapterId.equals(current) ? new ChatComponentText("> ").appendSibling(name) : name;
            FlatColorButton adapterButton = new FlatColorButton(
                x,
                currentY,
                VARIANT_BUTTON_WIDTH,
                VARIANT_BUTTON_HEIGHT,
                label,
                b -> {
                    this.currentPage = 0;
                    NetworkHandler.CHANNEL
                        .sendToServer(new ClientMessageSetSlotAdapter(inventory.currentItem, type, adapterId));
                    this.initGui();
                });
            if (currentAttachmentId != null && !entry.getValue()
                .allowsAttachment(currentAttachmentId)) {
                adapterButton.enabled = false;
                adapterButton.setTooltips(new ChatComponentTranslation("gui.tacz.refit.adapter_requires_unload"));
            }
            this.add(adapterButton);
            currentY += VARIANT_BUTTON_HEIGHT + VARIANT_BUTTON_SPACING;
        }
    }

    @Override
    public void onGuiClosed() {
        // 关闭界面时，一次性上传所有的染色数据
        EntityPlayerSP player = this.mc.thePlayer;
        if (player != null) {
            ItemStack gun = player.getHeldItem();
            if (gun != null && gun.getItem() instanceof IGun) {
                ClientMessageLaserColor message = new ClientMessageLaserColor(gun, player.inventory.currentItem);
                NetworkHandler.CHANNEL.sendToServer(message);
            }
        }
        super.onGuiClosed();
    }

    private void switchHideButton() {
        HIDE_GUN_PROPERTY_DIAGRAMS = !HIDE_GUN_PROPERTY_DIAGRAMS;
        this.initGui();
    }

    private static List<ItemStack> getVirtualOemAttachments(ItemStack gunItem, IGun iGun, AttachmentType type,
        Set<ResourceLocation> excludedAttachmentIds) {
        ResourceLocation currentAttachmentId = iGun.getAttachmentId(gunItem, type);
        List<Map.Entry<ResourceLocation, CommonAttachmentIndex>> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            if (entry.getValue()
                .getType() != type) {
                continue;
            }
            if (!VirtualOemAttachment.isAllowedVirtualOem(iGun.getGunId(gunItem), entry.getKey(), type)) {
                continue;
            }
            if (!VirtualOemAttachment.shouldShowIcon(iGun.getGunId(gunItem), entry.getKey(), type)) {
                continue;
            }
            if (excludedAttachmentIds.contains(entry.getKey())) {
                continue;
            }
            if (entry.getKey()
                .equals(currentAttachmentId)) {
                continue;
            }
            entries.add(entry);
        }
        entries.sort(
            Comparator.comparingInt(
                entry -> entry.getValue()
                    .getSort()));
        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : entries) {
            ItemStack stack = AttachmentItemBuilder.create()
                .setId(entry.getKey())
                .build();
            if (iGun.allowAttachment(gunItem, stack)) {
                result.add(stack);
            }
        }
        return result;
    }

    private static boolean isBuiltinOemAttachment(ItemStack attachmentStack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (attachment == null) {
            return false;
        }
        return VirtualOemAttachment.isBuiltinAttachment(attachment.getAttachmentId(attachmentStack));
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getItem() == null;
    }

    /**
     * 变体 / 槽位适配器索引里的 {@code name} 是 lang key，第三方枪包可能不写它
     * （{@code AttachmentVariantIndexPOJO.name} / {@code SlotAdapterIndexPOJO.name} 都是无校验字段，
     * {@code CommonAttachmentVariantIndex#checkIndex} / {@code CommonSlotAdapterIndex#checkIndex}
     * 也只校验 {@code display}）。
     *
     * <p>
     * <b>为什么必须在这里判空（r4 修复 P2）</b>：1.7.10 的
     * {@code new ChatComponentTranslation(null).getFormattedText()} 会走
     * {@code StatCollector.translateToLocal(null)} → {@code StringTranslate.tryTranslateKey(null)}
     * 返回 null（{@code HashMap#get(null)} = null）→ {@code initializeFromFormat(null)} →
     * {@code Pattern#matcher(null)} 抛 <b>NPE</b>
     * （{@code ChatComponentTranslation.java:67/90}、{@code StringTranslate.java:132-136}）。
     * 而 {@link FlatColorButton} 的构造器会<b>立刻</b>调 {@code message.getFormattedText()} 取显示串，
     * 所以这个 NPE 会在 {@link #initGui()} 里抛出 → 打开改装界面直接崩（不是画不出来，是构造期抛）。
     * 这与 r2 修掉的 {@code PackInfo.name == null} 是同一类问题（上游 1.20 的
     * {@code Component.translatable(null)} 同样不安全，属上游缺陷；本工程按「NPE 不能逃出」的契约修）。
     *
     * @param name 索引里的 lang key，可能为 null / 空白
     * @param id   索引 id，作为缺名时的回退显示（与 {@code PackInfoText} 的回退口径一致：宁显示 id 不崩）
     */
    private static IChatComponent indexName(String name, ResourceLocation id) {
        if (name != null && !name.trim()
            .isEmpty()) {
            return new ChatComponentTranslation(name);
        }
        return new ChatComponentText(id == null ? "" : id.toString());
    }

    private record RefitAttachmentEntry(int slotIndex, ItemStack stack, boolean virtualOem) {}
}
