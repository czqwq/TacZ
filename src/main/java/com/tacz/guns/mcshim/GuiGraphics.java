package com.tacz.guns.mcshim;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.gui.GuiGraphics}。
 *
 * <p>
 * 1.7.10 没有 GUI 绘制抽象层：所有 GUI 绘制都是 {@code TextureManager#bindTexture} + {@link Tessellator} 手画四边形 +
 * {@link FontRenderer}。本类把这些包成 1.20 的方法形态，让上游 GUI 代码的改动尽量只剩 import 与 {@code Component} →
 * {@link IChatComponent}、{@code Font} → {@link FontRenderer}。矩阵：每次绘制前 {@code glPushMatrix()}、把
 * {@link #pose()} 栈顶矩阵 {@code glMultMatrix} 进去、绘制后 {@code glPopMatrix()}，因此
 * {@code pose().translate(...)} / {@code scale(...)} 与 1.20 语义一致。
 *
 * <p>
 * 行为差异：{@code setColor} 在 1.7.10 没有全局 tint（Tessellator 用逐顶点颜色）⇒ 颜色存字段里、在
 * {@link #blit}/{@link #fill} 里乘进顶点颜色（<b>文字不受影响</b>）；{@code drawString} 的 float 版本直接取整
 * （{@code FontRenderer} 只接受 int，需要缩放请配 {@code pose().scale(...)}）；{@code FormattedCharSequence} 不存在
 * ⇒ 改收 {@link IChatComponent}；{@code blitNineSliced} 的 10 参重载按「忽略切边、整图拉伸」降级、11 参按真九宫格；
 * {@code renderTooltip} 系列照抄 {@code GuiScreen#drawHoveringText} 的算法自实现（该方法 {@code protected}，mcshim
 * 包调不到）；{@code renderItem} 走 {@code RenderItem#renderItemAndEffectIntoGUI}（RenderItem.java:575），并自行保存/还原
 * {@code zLevel} + 切换 {@code enableGUIStandardItemLighting} 与 {@code GL_RESCALE_NORMAL}（1.20 由
 * {@code GuiGraphics#renderItem} 内部完成同样的事）；{@code bufferSource} 概念在 1.7.10 不存在，相关方法为空实现。
 *
 * @author client-port
 */
public class GuiGraphics {

    /** 1.7.10 {@code GuiScreen#zLevel} 的等价物。{@code drawString} / {@code renderItem} 用它决定图层。 */
    private static final float DEFAULT_Z_LEVEL = 0.0F;

    private final Minecraft minecraft;
    private final PoseStack pose = new PoseStack();

    /** 1.20 里由 shader uniform 承担的全局 tint；这里手动乘进顶点颜色。 */
    private float colorR = 1.0F;
    private float colorG = 1.0F;
    private float colorB = 1.0F;
    private float colorA = 1.0F;

    /** 1.7.10 的绘制层级（GUI 里通常 0，tooltip 里 300）。 */
    private float zLevel = DEFAULT_Z_LEVEL;

    public GuiGraphics(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /** 便捷构造：直接用当前客户端实例。 */
    public static GuiGraphics create() {
        return new GuiGraphics(Minecraft.getMinecraft());
    }

    public Minecraft minecraft() {
        return this.minecraft;
    }

    /** 对应 1.20 {@code GuiGraphics#pose()}。 */
    public PoseStack pose() {
        return this.pose;
    }

    public float zLevel() {
        return this.zLevel;
    }

    public void setZLevel(float zLevel) {
        this.zLevel = zLevel;
    }

    // ================================================================================================
    // 颜色
    // ================================================================================================

    /** 对应 1.20 {@code GuiGraphics#setColor(float, float, float, float)}。 */
    public void setColor(float red, float green, float blue, float alpha) {
        this.colorR = red;
        this.colorG = green;
        this.colorB = blue;
        this.colorA = alpha;
    }

    /** 对应 1.20 {@code GuiGraphics#setColor(Color)}。 */
    public void setColor(Vector4f color) {
        setColor(color.x, color.y, color.z, color.w);
    }

    /** 把当前 tint 复位成不透明白（1.20 里没有这个方法，1.7.10 侧需要的补丁）。 */
    public void resetColor() {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int tinted(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r * this.colorR);
        g = (int) (g * this.colorG);
        b = (int) (b * this.colorB);
        a = (int) (a * this.colorA);
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    // ================================================================================================
    // 矩阵桥接
    // ================================================================================================

    private void pushPoseToGL() {
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, this.zLevel);
        if (!this.pose.isEmpty()) {
            this.pose.applyToModelView();
        }
    }

    private void popPoseFromGL() {
        GL11.glPopMatrix();
    }

    // ================================================================================================
    // fill
    // ================================================================================================

    /** 对应 1.20 {@code GuiGraphics#fill(int, int, int, int, int)}。 */
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        fill(minX, minY, maxX, maxY, 0, color);
    }

    /** 对应 1.20 {@code GuiGraphics#fill(int, int, int, int, int, int)}（带 z）。 */
    public void fill(int minX, int minY, int maxX, int maxY, int z, int color) {
        if (minX >= maxX) {
            int i = minX;
            minX = maxX;
            maxX = i;
        }
        if (minY >= maxY) {
            int j = minY;
            minY = maxY;
            maxY = j;
        }
        int argb = tinted(color);
        float a = (argb >>> 24) / 255.0F;
        float r = (argb >>> 16 & 0xFF) / 255.0F;
        float g = (argb >>> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;

        pushPoseToGL();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            RenderSystem.enableBlend();
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setColorRGBA_F(r, g, b, a);
            tessellator.addVertex(minX, maxY, z);
            tessellator.addVertex(maxX, maxY, z);
            tessellator.addVertex(maxX, minY, z);
            tessellator.addVertex(minX, minY, z);
            tessellator.draw();

            GL11.glEnable(GL11.GL_ALPHA_TEST);
            RenderSystem.disableBlend();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        } finally {
            popPoseFromGL();
        }
    }

    /** 对应 1.20 {@code GuiGraphics#fillGradient(int, int, int, int, int, int)}。 */
    public void fillGradient(int minX, int minY, int maxX, int maxY, int colorFrom, int colorTo) {
        fillGradient(minX, minY, maxX, maxY, 0, colorFrom, colorTo);
    }

    /** 对应 1.20 {@code GuiGraphics#fillGradient(int, int, int, int, int, int, int)}。 */
    public void fillGradient(int minX, int minY, int maxX, int maxY, int z, int colorFrom, int colorTo) {
        int from = tinted(colorFrom);
        int to = tinted(colorTo);
        float a1 = (from >>> 24) / 255.0F;
        float r1 = (from >>> 16 & 0xFF) / 255.0F;
        float g1 = (from >>> 8 & 0xFF) / 255.0F;
        float b1 = (from & 0xFF) / 255.0F;
        float a2 = (to >>> 24) / 255.0F;
        float r2 = (to >>> 16 & 0xFF) / 255.0F;
        float g2 = (to >>> 8 & 0xFF) / 255.0F;
        float b2 = (to & 0xFF) / 255.0F;

        pushPoseToGL();
        try {
            RenderSystem.disableTexture();
            RenderSystem.enableBlend();
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setColorRGBA_F(r1, g1, b1, a1);
            tessellator.addVertex(maxX, minY, z);
            tessellator.addVertex(minX, minY, z);
            tessellator.setColorRGBA_F(r2, g2, b2, a2);
            tessellator.addVertex(minX, maxY, z);
            tessellator.addVertex(maxX, maxY, z);
            tessellator.draw();

            GL11.glEnable(GL11.GL_ALPHA_TEST);
            RenderSystem.disableBlend();
            RenderSystem.enableTexture();
        } finally {
            popPoseFromGL();
        }
    }

    /** 对应 1.20 {@code GuiGraphics#renderOutline(int, int, int, int, int)}。 */
    public void renderOutline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // ================================================================================================
    // blit（贴图）
    // ================================================================================================

    /** 对应 1.20 {@code GuiGraphics#blit(ResourceLocation, int, int, int, int, int, int)}。 */
    public void blit(ResourceLocation texture, int x, int y, int uOffset, int vOffset, int width, int height) {
        blit(texture, x, y, uOffset, vOffset, width, height, 256, 256);
    }

    /** 对应 1.20 {@code GuiGraphics#blit(ResourceLocation, int, int, int, int, int, int, int, int)}。 */
    public void blit(ResourceLocation texture, int x, int y, int uOffset, int vOffset, int width, int height,
        int textureWidth, int textureHeight) {
        blit(texture, x, y, width, height, uOffset, vOffset, width, height, textureWidth, textureHeight);
    }

    /**
     * 对应 1.20 {@code GuiGraphics#blit(ResourceLocation, int, int, int, int, float, float, int, int, int, int)}。
     *
     * @param x             目标左上角 x
     * @param y             目标左上角 y
     * @param width         目标宽 / 源图取样宽
     * @param height        目标高 / 源图取样高
     * @param uOffset       源图 u 起点（像素）
     * @param vOffset       源图 v 起点（像素）
     * @param textureWidth  整张贴图宽（像素）
     * @param textureHeight 整张贴图高（像素）
     */
    public void blit(ResourceLocation texture, int x, int y, int width, int height, float uOffset, float vOffset,
        int uWidth, int vHeight, int textureWidth, int textureHeight) {
        blit(texture, x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    /** 带颜色的 blit（对应 1.20 的 {@code blit(..., int color)} 重载）。 */
    public void blit(ResourceLocation texture, int x, int y, int width, int height, float uOffset, float vOffset,
        int uWidth, int vHeight, int textureWidth, int textureHeight, int color) {
        float u0 = uOffset / textureWidth;
        float u1 = (uOffset + uWidth) / textureWidth;
        float v0 = vOffset / textureHeight;
        float v1 = (vOffset + vHeight) / textureHeight;

        int argb = tinted(color);
        float a = (argb >>> 24) / 255.0F;
        float r = (argb >>> 16 & 0xFF) / 255.0F;
        float g = (argb >>> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;

        this.minecraft.getTextureManager()
            .bindTexture(texture);
        pushPoseToGL();
        try {
            RenderSystem.enableBlend();
            RenderSystem.enableTexture();
            // 1.7.10 的 GUI 贴图默认不吃光照，这里显式关掉避免受上一次实体渲染影响
            RenderSystem.disableLighting();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setColorRGBA_F(r, g, b, a);
            tessellator.addVertexWithUV(x, y + height, 0.0D, u0, v1);
            tessellator.addVertexWithUV(x + width, y + height, 0.0D, u1, v1);
            tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
            tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
            tessellator.draw();

            RenderSystem.disableBlend();
        } finally {
            popPoseFromGL();
        }
    }

    /**
     * 对应 1.20 {@code GuiGraphics#blitNineSliced(ResourceLocation, int, int, int, int, int, int, int, int)}：
     * 9 参数版本 {@code (texture, x, y, width, height, left, top, right, bottom)}，贴图尺寸按 256×256 假设。
     */
    public void blitNineSliced(ResourceLocation texture, int x, int y, int width, int height, int left, int top,
        int right, int bottom) {
        blitNineSliced(texture, x, y, width, height, left, top, right, bottom, 256, 256);
    }

    /**
     * 10 参数版本。<b>降级点</b>：上游 2 个调用点（{@code GunSmithTableScreen} / {@code CreativePackFilters}）用它画
     * 整块装饰底图，且参数比 1.20 的九宫格切边语义少一个 ⇒ 按「忽略切边、整图拉伸」实现。
     */
    public void blitNineSliced(ResourceLocation texture, int x, int y, int width, int height, int left, int top,
        int right, int bottom, int textureSize) {
        // TODO(port): 上游用 1.20 的 blitNineSliced 十参重载做九宫格拉伸，1.7.10 侧参数语义缺失一项，
        // 此处降级为整图拉伸；如后续需要精确九宫格请改用下方 11 参重载。
        blit(texture, x, y, width, height, 0.0F, 0.0F, textureSize, textureSize, textureSize, textureSize);
    }

    /**
     * 11 参数版本，真正的九宫格切分，对应 1.20
     * {@code GuiGraphics#blitNineSliced(ResourceLocation, int, int, int, int, int, int, int, int, int, int)}。
     *
     * @param left          左切边像素
     * @param top           上切边像素
     * @param right         右切边像素
     * @param bottom        下切边像素
     * @param textureWidth  整张贴图宽
     * @param textureHeight 整张贴图高
     */
    public void blitNineSliced(ResourceLocation texture, int x, int y, int width, int height, int left, int top,
        int right, int bottom, int textureWidth, int textureHeight) {
        if (left + right >= textureWidth || top + bottom >= textureHeight) {
            // 源图比切边还小，退化成拉伸
            blit(texture, x, y, width, height, 0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
            return;
        }
        int innerSensorW = textureWidth - left - right;
        int innerSensorH = textureHeight - top - bottom;
        int innerW = Math.max(0, width - left - right);
        int innerH = Math.max(0, height - top - bottom);

        // 四角
        blit(texture, x, y, left, top, 0.0F, 0.0F, left, top, textureWidth, textureHeight);
        blit(
            texture,
            x + width - right,
            y,
            right,
            top,
            textureWidth - right,
            0.0F,
            right,
            top,
            textureWidth,
            textureHeight);
        blit(
            texture,
            x,
            y + height - bottom,
            left,
            bottom,
            0.0F,
            textureHeight - bottom,
            left,
            bottom,
            textureWidth,
            textureHeight);
        blit(
            texture,
            x + width - right,
            y + height - bottom,
            right,
            bottom,
            textureWidth - right,
            textureHeight - bottom,
            right,
            bottom,
            textureWidth,
            textureHeight);
        // 四边
        blit(texture, x + left, y, innerW, top, left, 0.0F, innerSensorW, top, textureWidth, textureHeight);
        blit(
            texture,
            x + left,
            y + height - bottom,
            innerW,
            bottom,
            left,
            textureHeight - bottom,
            innerSensorW,
            bottom,
            textureWidth,
            textureHeight);
        blit(texture, x, y + top, left, innerH, 0.0F, top, left, innerSensorH, textureWidth, textureHeight);
        blit(
            texture,
            x + width - right,
            y + top,
            right,
            innerH,
            textureWidth - right,
            top,
            right,
            innerSensorH,
            textureWidth,
            textureHeight);
        // 中心
        blit(
            texture,
            x + left,
            y + top,
            innerW,
            innerH,
            left,
            top,
            innerSensorW,
            innerSensorH,
            textureWidth,
            textureHeight);
    }

    // ================================================================================================
    // 剪裁（scissor）
    // ================================================================================================

    /**
     * 对应 1.20 {@code GuiGraphics#enableScissor(int, int, int, int)}。1.7.10 走 {@link RenderSystem#enableScissor}；
     * 坐标系是 GUI 缩放的，内部会换算成窗口像素。
     */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        int scale = currentScaleFactor();
        int height = scaledHeight();
        RenderSystem.enableScissor(x1 * scale, (height - y2) * scale, (x2 - x1) * scale, (y2 - y1) * scale);
    }

    /** 对应 1.20 {@code GuiGraphics#disableScissor()}。 */
    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    // ================================================================================================
    // 文字
    // ================================================================================================

    /**
     * 对应 1.20 {@code GuiGraphics#drawString(Font, String, int, int, int)}。<b>默认值必须是
     * {@code dropShadow = true}</b>：1.20.1 的这条重载没有「不发阴影」的语义（内部就是
     * {@code drawString(font, text, x, y, color, true)}），想不画阴影的调用点一律显式写 {@code false}。
     */
    public int drawString(FontRenderer font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    /** 对应 1.20 {@code GuiGraphics#drawString(Font, String, int, int, int, boolean)}。 */
    public int drawString(FontRenderer font, String text, int x, int y, int color, boolean dropShadow) {
        if (text == null) {
            return 0;
        }
        pushPoseToGL();
        try {
            int c = tinted(color);
            if (dropShadow) {
                return font.drawStringWithShadow(text, x, y, c);
            }
            return font.drawString(text, x, y, c);
        } finally {
            popPoseFromGL();
        }
    }

    /** float 坐标重载：1.7.10 字体只吃 int，这里取整。 */
    public int drawString(FontRenderer font, String text, float x, float y, int color, boolean dropShadow) {
        return drawString(font, text, (int) x, (int) y, color, dropShadow);
    }

    /** 对应 1.20 {@code GuiGraphics#drawString(Font, Component, int, int, int)}；与 String 重载同理，默认带阴影。 */
    public int drawString(FontRenderer font, IChatComponent text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    /** 对应 1.20 {@code GuiGraphics#drawString(Font, Component, int, int, int, boolean)}。 */
    public int drawString(FontRenderer font, IChatComponent text, int x, int y, int color, boolean dropShadow) {
        return text == null ? 0 : drawString(font, text.getFormattedText(), x, y, color, dropShadow);
    }

    /** float 坐标重载。 */
    public int drawString(FontRenderer font, IChatComponent text, float x, float y, int color, boolean dropShadow) {
        return drawString(font, text, (int) x, (int) y, color, dropShadow);
    }

    /** 对应 1.20 {@code GuiGraphics#drawCenteredString(Font, String, int, int, int)}。 */
    public int drawCenteredString(FontRenderer font, String text, int x, int y, int color) {
        return drawString(font, text, x - font.getStringWidth(text) / 2, y, color, true);
    }

    /** 对应 1.20 {@code GuiGraphics#drawCenteredString(Font, Component, int, int, int)}。 */
    public int drawCenteredString(FontRenderer font, IChatComponent text, int x, int y, int color) {
        return text == null ? 0 : drawCenteredString(font, text.getFormattedText(), x, y, color);
    }

    // ================================================================================================
    // 物品
    // ================================================================================================

    /** 对应 1.20 {@code GuiGraphics#renderItem(ItemStack, int, int)}。 */
    public void renderItem(ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        RenderItem renderItem = RenderItem.getInstance();
        float prevZ = renderItem.zLevel;
        renderItem.zLevel = this.zLevel;
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        try {
            renderItem.renderItemAndEffectIntoGUI(
                this.minecraft.fontRenderer,
                this.minecraft.getTextureManager(),
                stack,
                x,
                y);
        } finally {
            renderItem.zLevel = prevZ;
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.disableStandardItemLighting();
            // 1.7.10 的 GUI 物品渲染会留下 OpenGlHelper 的光照坐标，还原成「无光照」避免污染后续绘制
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        }
    }

    /** 对应 1.20 {@code GuiGraphics#renderFakeItem(ItemStack, int, int)}（1.7.10 无「假物品」概念，等同渲染）。 */
    public void renderFakeItem(ItemStack stack, int x, int y) {
        renderItem(stack, x, y);
    }

    /** 对应 1.20 {@code GuiGraphics#renderItemDecorations(Font, ItemStack, int, int)}（数量/耐久叠加层）。 */
    public void renderItemDecorations(FontRenderer font, ItemStack stack, int x, int y) {
        renderItemDecorations(font, stack, x, y, (String) null);
    }

    public void renderItemDecorations(FontRenderer font, ItemStack stack, int x, int y, String text) {
        if (stack == null) {
            return;
        }
        RenderItem renderItem = RenderItem.getInstance();
        float prevZ = renderItem.zLevel;
        renderItem.zLevel = this.zLevel;
        try {
            renderItem.renderItemOverlayIntoGUI(
                font == null ? this.minecraft.fontRenderer : font,
                this.minecraft.getTextureManager(),
                stack,
                x,
                y,
                text);
        } finally {
            renderItem.zLevel = prevZ;
        }
    }

    // ================================================================================================
    // Tooltip
    // ================================================================================================

    /** 对应 1.20 {@code GuiGraphics#renderTooltip(Font, ItemStack, int, int)}。 */
    public void renderTooltip(FontRenderer font, ItemStack stack, int mouseX, int mouseY) {
        if (stack == null) {
            return;
        }
        FontRenderer actual = this.minecraft.fontRenderer;
        List<String> lines = new ArrayList<String>();
        List<?> raw = stack.getTooltip(
            this.minecraft.thePlayer,
            this.minecraft.gameSettings != null && this.minecraft.gameSettings.advancedItemTooltips);
        for (int i = 0; i < raw.size(); i++) {
            String line = String.valueOf(raw.get(i));
            if (i == 0) {
                lines.add(stack.getRarity().rarityColor + line);
            } else {
                lines.add(EnumChatFormatting.GRAY + line);
            }
        }
        FontRenderer itemFont = stack.getItem() == null ? null
            : stack.getItem()
                .getFontRenderer(stack);
        drawHoveringText(lines, mouseX, mouseY, itemFont == null ? (font == null ? actual : font) : itemFont);
    }

    /** 对应 1.20 {@code GuiGraphics#renderTooltip(Font, List<Component>, int, int)}。 */
    public void renderTooltip(FontRenderer font, List<IChatComponent> components, int mouseX, int mouseY) {
        drawHoveringText(toFormatted(components), mouseX, mouseY, font);
    }

    /** 对应 1.20 {@code GuiGraphics#renderComponentTooltip(Font, List<Component>, int, int)}。 */
    public void renderComponentTooltip(FontRenderer font, List<IChatComponent> components, int mouseX, int mouseY) {
        drawHoveringText(toFormatted(components), mouseX, mouseY, font);
    }

    /**
     * 对应 1.20 {@code GuiGraphics#renderComponentTooltip(Font, List, int, int, ItemStack)}：第 5 参只用于取物品自带的
     * 字体（{@code getFontRenderer}），1.7.10 同样支持。
     */
    public void renderComponentTooltip(FontRenderer font, List<IChatComponent> components, int mouseX, int mouseY,
        ItemStack stack) {
        FontRenderer actual = font;
        if (stack != null && stack.getItem() != null) {
            FontRenderer itemFont = stack.getItem()
                .getFontRenderer(stack);
            if (itemFont != null) {
                actual = itemFont;
            }
        }
        drawHoveringText(toFormatted(components), mouseX, mouseY, actual);
    }

    /**
     * 对应 1.20 {@code GuiGraphics#renderTooltip(Font, List, Optional, ItemStack, int, int)}。<b>降级点</b>：1.20 的
     * {@code Optional<TooltipComponent>}（自定义图片 tooltip）在 1.7.10 无对应，第二参被忽略、只画文字行。
     */
    public void renderTooltip(FontRenderer font, List<IChatComponent> components, Object tooltipImage, ItemStack stack,
        int mouseX, int mouseY) {
        // TODO(port): 上游传 ItemStack#getTooltipImage()（1.20 的自定义 tooltip 图片组件），
        // 1.7.10 没有该机制，此处忽略图片部分，只渲染文字行。
        renderComponentTooltip(font, components, mouseX, mouseY, stack);
    }

    /** 把 {@code List<IChatComponent>} 转成 1.7.10 tooltip 需要的 {@code List<String>}。 */
    private static List<String> toFormatted(List<IChatComponent> components) {
        List<String> result = new ArrayList<String>();
        if (components == null) {
            return result;
        }
        for (IChatComponent component : components) {
            if (component != null) {
                result.add(component.getFormattedText());
            }
        }
        return result;
    }

    /**
     * 照抄 1.7.10 {@code GuiScreen#drawHoveringText} 的算法（mcshim 包里调不到该 {@code protected} 方法）；
     * 与原版唯一差异是 {@code zLevel} 用本类字段。
     */
    public void drawHoveringText(List<String> textLines, int x, int y, FontRenderer font) {
        if (textLines == null || textLines.isEmpty() || font == null) {
            return;
        }
        int scaledWidth = scaledWidth();
        int scaledHeight = scaledHeight();

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        int maxWidth = 0;
        for (String line : textLines) {
            int w = font.getStringWidth(line);
            if (w > maxWidth) {
                maxWidth = w;
            }
        }

        int tx = x + 12;
        int ty = y - 12;
        int totalHeight = 8;
        if (textLines.size() > 1) {
            totalHeight += 2 + (textLines.size() - 1) * 10;
        }
        if (tx + maxWidth > scaledWidth) {
            tx -= 28 + maxWidth;
        }
        if (ty + totalHeight + 6 > scaledHeight) {
            ty = scaledHeight - totalHeight - 6;
        }

        float prevZ = this.zLevel;
        this.zLevel = 300.0F;
        RenderItem renderItem = RenderItem.getInstance();
        float prevItemZ = renderItem.zLevel;
        renderItem.zLevel = this.zLevel;

        int background = -267386864;
        fillGradient(tx - 3, ty - 4, tx + maxWidth + 3, ty - 3, background, background);
        fillGradient(tx - 3, ty + totalHeight + 3, tx + maxWidth + 3, ty + totalHeight + 4, background, background);
        fillGradient(tx - 3, ty - 3, tx + maxWidth + 3, ty + totalHeight + 3, background, background);
        fillGradient(tx - 4, ty - 3, tx - 3, ty + totalHeight + 3, background, background);
        fillGradient(tx + maxWidth + 3, ty - 3, tx + maxWidth + 4, ty + totalHeight + 3, background, background);
        int border1 = 1347420415;
        int border2 = (border1 & 16711422) >> 1 | border1 & -16777216;
        fillGradient(tx - 3, ty - 3 + 1, tx - 3 + 1, ty + totalHeight + 3 - 1, border1, border2);
        fillGradient(tx + maxWidth + 2, ty - 3 + 1, tx + maxWidth + 3, ty + totalHeight + 3 - 1, border1, border2);
        fillGradient(tx - 3, ty - 3, tx + maxWidth + 3, ty - 3 + 1, border1, border1);
        fillGradient(tx - 3, ty + totalHeight + 2, tx + maxWidth + 3, ty + totalHeight + 3, border2, border2);

        for (int i = 0; i < textLines.size(); i++) {
            String line = textLines.get(i);
            font.drawStringWithShadow(line, tx, ty, -1);
            if (i == 0) {
                ty += 2;
            }
            ty += 10;
        }

        this.zLevel = prevZ;
        renderItem.zLevel = prevItemZ;

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    // ================================================================================================
    // 尺寸
    // ================================================================================================

    /** 当前 GUI 缩放下的逻辑宽（对应 1.20 的 {@code Screen#width}）。 */
    public int scaledWidth() {
        return new ScaledResolution(this.minecraft, this.minecraft.displayWidth, this.minecraft.displayHeight)
            .getScaledWidth();
    }

    /** 当前 GUI 缩放下的逻辑高（对应 1.20 的 {@code Screen#height}）。 */
    public int scaledHeight() {
        return new ScaledResolution(this.minecraft, this.minecraft.displayWidth, this.minecraft.displayHeight)
            .getScaledHeight();
    }

    /** 当前 GUI scale factor。 */
    public int currentScaleFactor() {
        return new ScaledResolution(this.minecraft, this.minecraft.displayWidth, this.minecraft.displayHeight)
            .getScaleFactor();
    }

    // ================================================================================================
    // 1.20 有、1.7.10 无对应物的空实现（保持上游调用点可编译）
    // ================================================================================================

    /** 对应 1.20 {@code GuiGraphics#bufferSource()}。1.7.10 无批次概念，返回 {@code null}。 */
    public Object bufferSource() {
        return null;
    }

    /** 对应 1.20 {@code GuiGraphics#flush()}。1.7.10 立即模式，无需 flush。 */
    public void flush() {
        // 1.7.10 Tessellator 是立即模式，不存在延迟批次
    }

    /**
     * 对应 1.20 {@code GuiGraphics#drawManaged(Runnable)}：1.20 用它保证 {@code setShaderColor} 等状态在批次结束时
     * 恢复，1.7.10 直接执行。
     */
    public void drawManaged(Runnable runnable) {
        runnable.run();
    }
}
