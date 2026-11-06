package com.tacz.guns.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.inventory.AmmoStorageEntry;
import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageAmmoStorageClick;

/**
 * 弹药储存（AmmoStorage）界面。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>{@code AbstractContainerScreen} → {@link GuiContainer}：
 * {@code renderBg}→{@code drawGuiContainerBackgroundLayer}，
 * {@code leftPos/topPos}→{@code guiLeft/guiTop}，
 * {@code imageWidth/imageHeight}→{@code xSize/ySize}。</li>
 * <li>1.7.10 {@code GuiContainer#drawScreen} 已经包含「画背景 + 画槽位 + 画物品栏 tooltip」，
 * 所以这里的悬停提示改成在 {@code drawScreen} 末尾追加。</li>
 * <li>1.7.10 没有 {@code menu.getCarried()}，用 {@code thePlayer.inventory.getItemStack()}。</li>
 * <li>鼠标回调签名：{@code mouseClicked(double,double,int)}→{@code mouseClicked(int,int,int)}，
 * {@code mouseDragged(...)}→{@code mouseClickMove(int,int,int,long)}，
 * {@code mouseReleased(...)}→{@code mouseMovedOrUp(int,int,int)}（后两者返回 void）。</li>
 * <li>1.20 的 {@code ItemStack#getTooltipImage()}（自定义 tooltip 图片）在 1.7.10 无对应，
 * 传给 {@code GuiGraphics#renderTooltip} 的图片参数传 {@code null}。</li>
 * </ul>
 */
public class AmmoStorageScreen extends GuiContainer {

    private static final ResourceLocation CONTAINER_LOCATION = new ResourceLocation(
        "textures/gui/container/generic_54.png");
    private static final int CONTAINER_ROWS = AmmoStorageMenu.STORAGE_ROWS;
    private int virtualClickButton = -1;
    /** 容器标题（对应 1.20 {@code AbstractContainerScreen#title}，由 GuiHandler 传入）。 */
    private final IChatComponent title;

    public AmmoStorageScreen(AmmoStorageMenu menu, InventoryPlayer inventory, IChatComponent title) {
        super(menu);
        this.xSize = 176;
        this.ySize = 114 + CONTAINER_ROWS * 18 + AmmoStorageMenu.STATUS_HEIGHT;
        this.title = title == null ? new ChatComponentTranslation("gui.tacz.ammo_storage.title") : title;
    }

    /**
     * 对应 1.20 {@code AbstractContainerScreen#renderLabels} 的默认实现（上游 AmmoStorageScreen 未覆写）：
     * 容器标题 (8,6) + 玩家背包标题 (8, imageHeight-94)，颜色 4210752。
     * 1.7.10 {@code GuiContainer#drawGuiContainerForegroundLayer} 默认是空实现，必须自己画。
     */
    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GuiGraphics graphics = GuiGraphics.create();
        graphics.drawString(this.fontRendererObj, this.title, 8, 6, 0x404040, false);
        graphics.drawString(
            this.fontRendererObj,
            new ChatComponentTranslation("container.inventory"),
            8,
            this.ySize - 94,
            0x404040,
            false);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        GuiGraphics graphics = GuiGraphics.create();
        int storageHeight = CONTAINER_ROWS * 18 + 17 + AmmoStorageMenu.STATUS_HEIGHT;
        graphics.blit(CONTAINER_LOCATION, this.guiLeft, this.guiTop, 0, 0, this.xSize, 17);
        graphics.blit(
            CONTAINER_LOCATION,
            this.guiLeft,
            this.guiTop + 17,
            this.xSize,
            AmmoStorageMenu.STATUS_HEIGHT,
            0.0F,
            7.0F,
            this.xSize,
            1,
            256,
            256);
        graphics.blit(
            CONTAINER_LOCATION,
            this.guiLeft,
            this.guiTop + 17 + AmmoStorageMenu.STATUS_HEIGHT,
            0,
            17,
            this.xSize,
            CONTAINER_ROWS * 18);
        graphics.blit(CONTAINER_LOCATION, this.guiLeft, this.guiTop + storageHeight, 0, 126, this.xSize, 96);
        renderCapacity(graphics);
        AmmoStorageMenu menu = this.menu();
        List<AmmoStorageEntry> entries = menu.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            AmmoStorageEntry entry = entries.get(i);
            int x = this.guiLeft + AmmoStorageMenu.INVENTORY_X + i % AmmoStorageMenu.STORAGE_COLUMNS * 18;
            int y = this.guiTop + AmmoStorageMenu.STORAGE_Y + i / AmmoStorageMenu.STORAGE_COLUMNS * 18;
            graphics.renderItem(entry.sample(), x, y);
            renderSizeLabel(graphics, x, y, formatCount(entry.count()));
        }
        int hovered = getVirtualCell(mouseX, mouseY);
        if (hovered >= 0) {
            int x = this.guiLeft + AmmoStorageMenu.INVENTORY_X + hovered % AmmoStorageMenu.STORAGE_COLUMNS * 18;
            int y = this.guiTop + AmmoStorageMenu.STORAGE_Y + hovered / AmmoStorageMenu.STORAGE_COLUMNS * 18;
            // 对应 1.20 AbstractContainerScreen#renderSlotHighlight
            graphics.fillGradient(x, y, x + 16, y + 16, 0x80FFFFFF, 0x80FFFFFF);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderStorageTooltip(mouseX, mouseY);
    }

    private void renderStorageTooltip(int mouseX, int mouseY) {
        GuiGraphics graphics = GuiGraphics.create();
        AmmoStorageMenu menu = this.menu();
        if (this.func_146978_c(8, 17, 160, 21, mouseX, mouseY)) {
            List<IChatComponent> tooltip = new ArrayList<>();
            tooltip.add(
                new ChatComponentTranslation(
                    "gui.tacz.ammo_storage.capacity_detail",
                    menu.getUsedStacks(),
                    menu.getCapacityStacks()));
            tooltip.add(
                new ChatComponentTranslation(
                    "gui.tacz.ammo_storage.free_capacity",
                    Math.max(0, menu.getCapacityStacks() - menu.getUsedStacks())));
            graphics.renderComponentTooltip(this.fontRendererObj, tooltip, mouseX, mouseY);
            return;
        }
        int cell = getVirtualCell(mouseX, mouseY);
        ItemStack carried = this.mc.thePlayer == null ? null : this.mc.thePlayer.inventory.getItemStack();
        if (cell >= 0 && cell < menu.getEntries()
            .size() && carried == null) {
            AmmoStorageEntry entry = menu.getEntries()
                .get(cell);
            ItemStack sample = entry.sample();
            List<IChatComponent> tooltip = new ArrayList<>();
            if (sample != null && sample.getItem() != null) {
                for (Object line : sample.getTooltip(
                    this.mc.thePlayer,
                    this.mc.gameSettings != null && this.mc.gameSettings.advancedItemTooltips)) {
                    tooltip.add(new ChatComponentText(String.valueOf(line)));
                }
            }
            tooltip.add(greyWrap(new ChatComponentTranslation("gui.tacz.ammo_storage.count", entry.count()), false));
            tooltip.add(
                greyWrap(
                    new ChatComponentTranslation("gui.tacz.ammo_storage.remaining", entry.remainingCapacity()),
                    entry.remainingCapacity() == 0));
            // 1.20 的 ItemStack#getTooltipImage() 在 1.7.10 无对应，图片参数传 null
            graphics.renderTooltip(this.fontRendererObj, tooltip, (Object) null, sample, mouseX, mouseY);
            return;
        }
        // 上游这里是 super.renderTooltip(...)：1.7.10 GuiContainer#drawScreen 已自动画槽位 tooltip
    }

    /**
     * 对应 1.20 {@code Component#withStyle(ChatFormatting.X)}：1.7.10 用 {@link net.minecraft.util.EnumChatFormatting} 前缀。
     */
    private static IChatComponent greyWrap(IChatComponent component, boolean red) {
        return new ChatComponentText(
            (red ? EnumChatFormatting.RED : EnumChatFormatting.GRAY) + component.getUnformattedText());
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        AmmoStorageMenu menu = this.menu();
        int cell = getVirtualCell(mouseX, mouseY);
        if (cell >= 0 && (button == 0 || button == 1)) {
            int entryId = cell < menu.getEntries()
                .size() ? menu.getEntries()
                    .get(cell)
                    .id() : -1;
            AmmoStorageMenu.Action action = isShiftKeyDown() ? AmmoStorageMenu.Action.QUICK_MOVE
                : button == 1 ? AmmoStorageMenu.Action.PICKUP_HALF : AmmoStorageMenu.Action.PICKUP;
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageAmmoStorageClick(menu.windowId, entryId, action));
            this.virtualClickButton = button;
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.virtualClickButton < 0) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state == this.virtualClickButton) {
            this.virtualClickButton = -1;
            return;
        }
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    private int getVirtualCell(int mouseX, int mouseY) {
        for (int cell = 0; cell < AmmoStorageMenu.STORAGE_SLOT_COUNT; cell++) {
            int x = AmmoStorageMenu.INVENTORY_X + cell % AmmoStorageMenu.STORAGE_COLUMNS * 18;
            int y = AmmoStorageMenu.STORAGE_Y + cell / AmmoStorageMenu.STORAGE_COLUMNS * 18;
            if (this.func_146978_c(x, y, 16, 16, mouseX, mouseY)) {
                return cell;
            }
        }
        return -1;
    }

    private void renderCapacity(GuiGraphics graphics) {
        AmmoStorageMenu menu = this.menu();
        IChatComponent capacity = new ChatComponentTranslation(
            "gui.tacz.ammo_storage.capacity",
            menu.getUsedStacks(),
            menu.getCapacityStacks());
        IChatComponent types = new ChatComponentTranslation(
            "gui.tacz.ammo_storage.types",
            menu.getEntries()
                .size(),
            AmmoStorageMenu.STORAGE_SLOT_COUNT);
        graphics.drawString(this.fontRendererObj, capacity, this.guiLeft + 8, this.guiTop + 18, 0x404040, false);
        graphics.drawString(
            this.fontRendererObj,
            types,
            this.guiLeft + this.xSize - 8 - this.fontRendererObj.getStringWidth(types.getUnformattedText()),
            this.guiTop + 18,
            menu.getEntries()
                .size() == AmmoStorageMenu.STORAGE_SLOT_COUNT ? 0xAA3030 : 0x404040,
            false);
        int x = this.guiLeft + 8;
        int y = this.guiTop + 31;
        double fullness = menu.getCapacityStacks() <= 0 ? 0
            : Math.min(1, (double) menu.getUsedStacks() / menu.getCapacityStacks());
        int color = fullness >= 1 ? 0xFFC45A48 : fullness >= 0.75 ? 0xFFD7A744 : 0xFF65A85C;
        graphics.fill(x, y, x + 160, y + 6, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + 159, y + 5, 0xFF777777);
        graphics.fill(x + 1, y + 1, x + 1 + (int) Math.round(158 * fullness), y + 5, color);
    }

    private String formatCount(long count) {
        if (count >= 1_000_000_000L) {
            return formatCompact(count, 1_000_000_000L, "b");
        }
        if (count >= 1_000_000L) {
            return formatCompact(count, 1_000_000L, "m");
        }
        if (count >= 1_000L) {
            return formatCompact(count, 1_000L, "k");
        }
        return Long.toString(count);
    }

    private String formatCompact(long count, long unit, String suffix) {
        long whole = count / unit;
        long decimal = count % unit / (unit / 10);
        return decimal == 0 || whole >= 10 ? whole + suffix : whole + "." + decimal + suffix;
    }

    private void renderSizeLabel(GuiGraphics graphics, int x, int y, String text) {
        float scale = 0.666F;
        graphics.pose()
            .pushPose();
        graphics.pose()
            .translate(0.0F, 0.0F, 200.0F);
        graphics.pose()
            .scale(scale, scale, 1.0F);
        graphics.drawString(
            this.fontRendererObj,
            text,
            (int) ((x + 17 - this.fontRendererObj.getStringWidth(text) * scale) / scale),
            (int) ((y + 12) / scale),
            0xFFFFFFFF,
            true);
        graphics.pose()
            .popPose();
    }

    /** 1.20 的 {@code this.menu} 在 1.7.10 是 {@code this.inventorySlots}。 */
    private AmmoStorageMenu menu() {
        return (AmmoStorageMenu) this.inventorySlots;
    }
}
