package com.tacz.guns.client.gui.components.smith;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.components.refit.IComponentTooltip;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;

/**
 * 枪械工作台左侧分类标签按钮。
 *
 * <p>
 * <b>1.7.10 修正（P2，显示缺失）</b>：上游 {@code tmp/.../GunSmithTableScreen.java:523} 给每个分类页签
 * 调 {@code typeButton.setTooltip(Tooltip.create(tabConfig.getName(), tabConfig.getName()))}；
 * 1.7.10 的 {@link GuiButton} 没有控件级 tooltip，原先直接省略 → 分类页签（枪械/弹药/配件/方块…）
 * 只剩图标、没有名称提示。现在按 {@link IComponentTooltip} 的约定把名称挂在控件上，
 * 由 {@code GunSmithTableScreen#drawScreen} 在**所有内容画完之后**统一绘制
 * （这样才不会被随后的物品/槽位盖住）。
 *
 * <p>
 * {@code IComponentTooltip} 虽然物理上放在 {@code components/refit} 包，但它只是
 * 「带一个文本提示的控件」的通用契约（{@code renderTooltip(Consumer&lt;List&lt;IChatComponent&gt;&gt;)}），
 * 与改装台无关，因此这里直接复用，不另造一套。
 */
public class TypeButton extends GuiButton implements IComponentTooltip {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/gun_smith_table.png");

    private final ItemStack stack;
    private final Consumer<GuiButton> onPress;
    /** 悬停提示（上游 {@code tabConfig.getName()}）。 */
    private final @Nullable IChatComponent tooltip;
    private boolean isSelected = false;
    private boolean hovered;

    public TypeButton(int x, int y, ItemStack stack, @Nullable IChatComponent tooltip, Consumer<GuiButton> onPress) {
        super(0, x, y, 24, 25, "");
        this.stack = stack;
        this.tooltip = tooltip;
        this.onPress = onPress;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width
            && mouseY < this.yPosition + this.height;
        this.field_146123_n = this.hovered;

        GuiGraphics gui = GuiGraphics.create();
        RenderSystem.enableDepthTest();

        int vOffset = this.hovered ? 204 + this.height : 204;
        if (this.isSelected) {
            gui.blit(TEXTURE, this.xPosition, this.yPosition, 0, vOffset, this.width, this.height, 256, 256);
        } else {
            gui.blit(TEXTURE, this.xPosition, this.yPosition, 26, vOffset, this.width, this.height, 256, 256);
        }

        gui.renderItem(this.stack, this.xPosition + 4, this.yPosition + 5);
    }

    @Override
    public void renderTooltip(Consumer<List<IChatComponent>> consumer) {
        if (this.hovered && this.tooltip != null) {
            consumer.accept(Collections.singletonList(this.tooltip));
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            this.onPress();
            return true;
        }
        return false;
    }

    /** 对应 1.20 {@code Button#onPress()}：选中自身并回调。 */
    public void onPress() {
        this.isSelected = true;
        if (this.onPress != null) {
            this.onPress.accept(this);
        }
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }
}
