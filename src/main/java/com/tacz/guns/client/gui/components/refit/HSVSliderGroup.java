package com.tacz.guns.client.gui.components.refit;

import java.awt.Color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.util.LaserColorUtil;

/**
 * 镭射颜色（HSV 的 H / S 两段）滑条组。
 *
 * <p>
 * <b>降级点</b>：上游 {@code LaserColorSlider extends ForgeSlider}，而 1.7.10 Forge
 * 没有 {@code ForgeSlider}（那是 1.8+ 的 GUI 控件）。这里用 {@link GuiButton} 重新实现一个
 * 「点击/拖动设置 0~1 之间取值」的最小滑条（拖动依赖 1.7.10 {@code GuiButton#drawButton}
 * 每帧回调 {@code mouseDragged} 的机制，与 {@code GuiOptionSlider} 同套路）。
 */
public class HSVSliderGroup {

    private final InventoryPlayer inventory;
    private final int gunItemIndex;
    private final AttachmentType type;

    private final LaserColorSlider hueSlider;
    private final LaserColorSlider saturationSlider;

    public HSVSliderGroup(int x, int y, int width, int height, InventoryPlayer inventory, int gunItemIndex,
        AttachmentType type) {
        this.inventory = inventory;
        this.gunItemIndex = gunItemIndex;
        this.type = type;

        int color = getColor(type);
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);

        this.hueSlider = new LaserColorSlider(x, y, width, height, this, hsb[0]);
        this.saturationSlider = new LaserColorSlider(x, y + 2 + height, width, height, this, hsb[1]);
    }

    public LaserColorSlider getHueSlider() {
        return this.hueSlider;
    }

    public LaserColorSlider getSaturationSlider() {
        return this.saturationSlider;
    }

    public void apply() {
        // 需要检查的实现
        // 这里写往客户端写 nbt 其实是脏写，只为了确保能实时预览染色效果
        // 需要在合适的时机向服务器发包通知改动
        // 不在此直接向服务器发包是因为这个组件在滑动时会被非常频繁的调用，不希望频繁向服务器发包
        ItemStack gun = this.inventory.getStackInSlot(this.gunItemIndex);
        if (gun != null && gun.getItem() instanceof IGun iGun) {
            int rgbNew = Color
                .HSBtoRGB((float) this.hueSlider.getValue(), (float) this.saturationSlider.getValue(), 1.0F);

            if (this.type == AttachmentType.NONE) {
                iGun.setLaserColor(gun, rgbNew);
                return;
            }

            ItemStack laser = iGun.getAttachment(gun, this.type);
            if (laser != null && laser.getItem() instanceof IAttachment iAttachment) {
                iAttachment.setLaserColor(laser, rgbNew);
            }
        }
    }

    private int getColor(AttachmentType type) {
        if (this.inventory == null) {
            return 0x00FF0000;
        }
        ItemStack gun = this.inventory.getStackInSlot(this.gunItemIndex);

        if (gun != null && gun.getItem() instanceof IGun iGun) {
            if (type == AttachmentType.NONE) {
                return LaserColorUtil.getLaserColor(gun);
            } else {
                ItemStack attachment = iGun.getAttachment(gun, type);
                return LaserColorUtil.getLaserColor(attachment);
            }
        }

        return 0x00FF0000;
    }

    /**
     * 0~1 取值的最小滑条。
     *
     * <p>
     * 上游用 {@code ForgeSlider(min=0, max=1, step=0.01)}；这里保留 0~1 与 0.01 步进的语义。
     */
    public static class LaserColorSlider extends GuiButton {

        private static final double STEP = 0.01D;

        private final HSVSliderGroup parent;
        private double value;
        private boolean dragging;

        public LaserColorSlider(int x, int y, int width, int height, HSVSliderGroup parent, double current) {
            super(0, x, y, width, height, "");
            this.parent = parent;
            this.value = clampStep(current);
        }

        private static double clampStep(double raw) {
            double snapped = Math.round(raw / STEP) * STEP;
            if (snapped < 0.0D) {
                return 0.0D;
            }
            return Math.min(snapped, 1.0D);
        }

        public double getValue() {
            return this.value;
        }

        public void setValue(double value) {
            this.value = clampStep(value);
        }

        /** 对应 1.20 {@code AbstractSliderButton#applyValue}。 */
        protected void applyValue() {
            this.parent.apply();
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) {
                return;
            }
            if (this.dragging) {
                this.value = clampStep((double) (mouseX - (this.xPosition + 4)) / (double) (this.width - 8));
                applyValue();
            }
            GuiGraphics graphics = GuiGraphics.create();
            graphics.fill(
                this.xPosition,
                this.yPosition,
                this.xPosition + this.width,
                this.yPosition + this.height,
                0xFF000000);
            int knobX = this.xPosition + 4 + (int) (this.value * (double) (this.width - 8));
            graphics.fill(knobX - 4, this.yPosition, knobX + 4, this.yPosition + this.height, 0xFFC0C0C0);
            graphics.renderOutline(this.xPosition, this.yPosition, this.width, this.height, 0xFFFFFFFF);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                this.value = clampStep((double) (mouseX - (this.xPosition + 4)) / (double) (this.width - 8));
                applyValue();
                this.dragging = true;
                return true;
            }
            this.dragging = false;
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            this.dragging = false;
        }
    }
}
