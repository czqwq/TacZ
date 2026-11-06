package com.tacz.guns.mixin.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.client.tooltip.GuiTooltipRenderer;

/**
 * 1.7.10 移植特有：给原版 tooltip 绘制补上「按可用宽度折行 + 1.20 等价的边界 clamp」。
 *
 * <p>
 * 搬运/对齐依据：
 * <ul>
 * <li>1.7.10
 * {@code GuiScreen#drawHoveringText}（{@code build/rfg/minecraft-src/java/net/minecraft/client/gui/GuiScreen.java:156-232}）
 * 不折行、翻转后无左侧 clamp；</li>
 * <li>上游 1.20.1 {@code DefaultTooltipPositioner#positionTooltip}
 * （{@code tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/client/gui/screens/inventory/tooltip/DefaultTooltipPositioner.java:22-31}）
 * 翻转后 {@code Math.max(x - 24 - width, 4)}；</li>
 * <li>1.7.10 没有可注入的 MCP 名给 Forge 新增的 {@code drawHoveringText}（{@code mcp-srg.srg} 无该条目，
 * Mixin AP 会报 {@code Unable to locate obfuscation mapping}），因此改为注入**已映射的入口**：
 * {@code renderToolTip}（MCP 名，srg {@code func_146285_a}，物品 tooltip 的唯一落点，见
 * {@code GuiScreen.java:122-140}）、{@code func_146283_a}（srg 名本身，原版 List tooltip 入口，见
 * {@code GuiScreen.java:151-154}；{@code drawCreativeTabHoveringText} 也汇聚于此）与
 * {@code drawCreativeTabHoveringText}（MCP 名，srg {@code func_146279_a}）。几处入口最终都落到同一份
 * 1.7.10 绘制代码，这里换成我们「折行 + clamp」的实现。</li>
 * </ul>
 *
 * <p>
 * 渲染异常时 {@link GuiTooltipRenderer#renderHoveringText} 返回 {@code false}，这里不 cancel，
 * 原版方法照常执行（只降级、不崩）。
 *
 * <p>
 * <b>⚠ 本类里不允许出现任何嵌套 / 内部 / 匿名类</b>：javac 只要类里含嵌套类型就会写 {@code NestMembers}，
 * 与 {@code mixins.tacz.json} 的 {@code "compatibilityLevel": "JAVA_8"} 冲突，启动报
 * {@code Unsupported mixin ... requires ... [NESTING]}。
 *
 * <p>
 * 已登记于 {@code src/main/resources/mixins.tacz.json} 的 {@code client} 数组。
 */
@Mixin(GuiScreen.class)
public abstract class GuiScreenMixin {

    @Shadow
    public Minecraft mc;

    @Shadow
    protected FontRenderer fontRendererObj;

    /**
     * 物品 tooltip：对应 1.7.10 {@code GuiScreen#renderToolTip(ItemStack,int,int)}
     * （{@code GuiScreen.java:122-140}）。行内容与稀有度着色按原版逐行复刻后交给折行/clamp 渲染器。
     */
    @Inject(method = "renderToolTip", at = @At("HEAD"), cancellable = true)
    private void tacz$renderItemTooltip(ItemStack itemIn, int x, int y, CallbackInfo ci) {
        if (itemIn == null || itemIn.getItem() == null) {
            return;
        }
        List<String> lines = itemIn.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                lines.set(i, itemIn.getRarity().rarityColor + lines.get(i));
            } else {
                lines.set(i, EnumChatFormatting.GRAY + lines.get(i));
            }
        }
        FontRenderer font = itemIn.getItem()
            .getFontRenderer(itemIn);
        if (GuiTooltipRenderer.renderHoveringText(lines, x, y, font == null ? this.fontRendererObj : font)) {
            ci.cancel();
        }
    }

    /**
     * 原版 List tooltip 入口：对应 1.7.10 {@code GuiScreen#func_146283_a(List,int,int)}
     * （{@code GuiScreen.java:151-154}）。{@code drawCreativeTabHoveringText} 等原版/模组调用都汇聚于此。
     *
     * <p>
     * 方法名用 srg 名（{@code func_146283_a} 没有 MCP 名，见类注释），Mixin AP 会原样保留；
     * dev 运行期与生产运行期同名（MCP 未映射项不参与重混淆）。
     */
    @Inject(method = "func_146283_a", at = @At("HEAD"), cancellable = true)
    private void tacz$renderListTooltip(List<String> textLines, int x, int y, CallbackInfo ci) {
        if (GuiTooltipRenderer.renderHoveringText(textLines, x, y, this.fontRendererObj)) {
            ci.cancel();
        }
    }

    /** 创造栏页签 tooltip（{@code GuiScreen.java:146-149}），单独兜底防止上游改走别的入口。 */
    @Inject(method = "drawCreativeTabHoveringText", at = @At("HEAD"), cancellable = true)
    private void tacz$renderCreativeTabTooltip(String tabName, int mouseX, int mouseY, CallbackInfo ci) {
        if (GuiTooltipRenderer.renderHoveringText(Arrays.asList(tabName), mouseX, mouseY, this.fontRendererObj)) {
            ci.cancel();
        }
    }
}
