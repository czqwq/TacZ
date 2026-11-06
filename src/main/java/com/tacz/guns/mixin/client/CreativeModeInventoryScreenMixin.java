package com.tacz.guns.mixin.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.client.gui.components.CreativePackFilters;
import com.tacz.guns.client.gui.components.creative.CreativePackFilterHooks;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/CreativeModeInventoryScreenMixin.java}。
 *
 * <p>
 * 上游用途：给创造模式物品栏挂上「内容包筛选」侧栏（{@link CreativePackFilters}），在 init / selectTab /
 * refreshCurrentTabContents / render 四个时机刷新它，并接管 mouseScrolled。
 *
 * <p>
 * 1.7.10 目标类是 build/rfg/minecraft-src/java/net/minecraft/client/gui/inventory/GuiContainerCreative.java：
 * {@code init} TAIL → {@code initGui} TAIL（:258）；{@code selectTab} TAIL →
 * {@code setCurrentCreativeTab(CreativeTabs)} TAIL（:500）；{@code render} HEAD → {@code drawScreen(int,int,float)}
 * HEAD（:634）；widget 绘制 → {@code drawScreen} TAIL 自绘；点击派发 → {@code mouseClicked(int,int,int)}
 * HEAD（:440）自派发；{@code mouseScrolled} → {@code handleMouseInput()} HEAD（:596，1.7.10 从
 * {@code Mouse.getEventDWheel()} 读滚轮）；{@code selectedTab} 字段 → {@code private static int
 * selectedTabIndex}（:43，用 {@code CreativeTabs.creativeTabArray[index]} 反查）；{@code scrollOffs} →
 * {@code private float currentScroll}（:45）。
 *
 * <p>
 * <b>为什么不用 {@code buttonList}（别改回去）</b>：{@code @Shadow} 只在目标类自身声明的字段里查找、
 * 不走父类，写 {@code @Shadow List<GuiButton> buttonList} 会在 apply 阶段抛
 * {@code InvalidMixinException: @Shadow field buttonList was not located in the target class}，整个 client
 * mixin 配置加载失败（= 启动崩）。因此改为自持 {@code @Unique List<GuiButton> tacz$buttons} + 自绘 +
 * 自派发点击（{@link CreativePackFilters#init} 通过 {@link CreativePackFilterHooks#buttonAdder} 加按钮）。
 * 与上游只有两处差异且不影响功能：z 序在 {@code drawScreen} 末尾（侧栏在 {@code guiLeft - 28}，与 tooltip
 * 不重叠）、不走 Forge 的 {@code ActionPerformedEvent} / {@code MouseInputEvent}。
 *
 * <p>
 * <b>降级点：{@code refreshCurrentTabContents} 没有对应注入点。</b>承载该语义的
 * {@code updateFilteredItems(GuiContainerCreative$ContainerCreative)}（:370）形参是<b>包私有</b>内部类
 * （:1003），本包无法在回调签名里写出该类型；其作用由 {@code drawScreen} HEAD 的「每帧对账」覆盖
 * （{@link CreativePackFilters#updateTab} 会先比对页签 / 条目引用 / 枪包版本），最多推迟 1 帧。
 *
 * <p>
 * <b>为什么用 {@code implements Runnable}</b>：Mixin 类运行期不会成为目标类的父类，任何捕获 {@code this} 的
 * lambda / 匿名内部类都会在运行期把一个 {@code GuiContainerCreative} 当成
 * {@code CreativeModeInventoryScreenMixin} 用。把 {@link Runnable} 直接实现在 Mixin 类上（合并后即
 * {@code GuiContainerCreative implements Runnable}）再传出 {@code this} 才安全。
 *
 * <p>
 * <b>⚠ 本类里不允许出现任何嵌套 / 内部 / 匿名类（启动期硬崩）</b>：javac 只要类里含嵌套类型就会写
 * {@code NestMembers}，与 {@code mixins.tacz.json} 的 {@code "compatibilityLevel": "JAVA_8"} 冲突，启动报
 * {@code Unsupported mixin ... requires the following unsupported language features: [NESTING],
 * these features require compatibility level JAVA_11}。
 * 辅助逻辑一律放到普通包的顶层普通类（如 {@link CreativePackFilterHooks}），<b>不能</b>放在
 * {@code com.tacz.guns.mixin.**} 下（普通类会被拦截并抛 {@code IllegalClassLoadError}）；
 * {@code implements} 接口是安全的。
 *
 * <p>
 * 附：原先按 MCP 名反射 {@code itemList}/{@code scrollTo}，生产运行期是 SRG 名 → 取不到 → 「内容包筛选点了
 * 没反应」。现由 {@link CreativeContainerAccessor}（{@code @Accessor}/{@code @Invoker}，refmap 自动映射、
 * 不硬编码 SRG 名）经 {@link CreativePackFilterHooks#CONTAINER_ACCESS} 注入，钩子缺失时回退旧反射（只降级不崩）。
 */
@Mixin(GuiContainerCreative.class)
public abstract class CreativeModeInventoryScreenMixin implements Runnable {

    /** 1.7.10 用下标代替 {@code CreativeModeTab selectedTab}（GuiContainerCreative.java:43，目标类自有字段）。 */
    @Shadow
    private static int selectedTabIndex;

    /** 上游 {@code scrollOffs}（GuiContainerCreative.java:45，目标类自有字段）。 */
    @Shadow
    private float currentScroll;

    /**
     * 本 Mixin 自持的筛选按钮列表（不用 {@code GuiScreen#buttonList}，理由见类 javadoc）。
     */
    @Unique
    private final List<GuiButton> tacz$buttons = new ArrayList<>();

    @Unique
    private CreativePackFilters tacz$packFilters;

    /**
     * 等价上游 {@code () -> scrollOffs = 0}：筛选结果变化时把滚动条拉回顶部。
     * 真正的列表复位由 {@link CreativePackFilters} 反射调 {@code ContainerCreative#scrollTo(float)} 完成，
     * 这里补上 GUI 自己的滚动条位置。
     */
    @Override
    public void run() {
        this.currentScroll = 0.0F;
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void tacz$initPackFilters(CallbackInfo ci) {
        // initGui 在非创造模式会直接 displayGuiScreen(GuiInventory) 后返回，此时按钮不属于本 GUI
        if (!this.tacz$inCreativeMode()) {
            return;
        }
        // 装容器访问桥（幂等）：让 CreativePackFilters 用 @Accessor 而不是生产环境必然失败的 MCP 名反射
        if (CreativePackFilters.containerAccessProvider == null) {
            CreativePackFilters.containerAccessProvider = CreativePackFilterHooks.CONTAINER_ACCESS;
        }
        this.tacz$buttons.clear();
        if (this.tacz$packFilters == null) {
            this.tacz$packFilters = new CreativePackFilters((GuiContainerCreative) (Object) this, this);
        }
        this.tacz$packFilters.init(CreativePackFilterHooks.buttonAdder(this.tacz$buttons), this.tacz$selectedTab());
    }

    @Inject(method = "setCurrentCreativeTab", at = @At("TAIL"))
    private void tacz$selectPackFilterTab(CreativeTabs tab, CallbackInfo ci) {
        if (this.tacz$packFilters != null) {
            this.tacz$packFilters.updateTab(tab, true);
        }
    }

    /** 每帧对账（上游 {@code render} HEAD）。 */
    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void tacz$updatePackFilters(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.tacz$packFilters != null) {
            this.tacz$packFilters.updateTab(this.tacz$selectedTab(), false);
        }
    }

    /** 画筛选按钮（上游的 widget 层；{@code GuiButton#drawButton} 自己会判 {@code visible}）。 */
    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void tacz$drawPackFilterButtons(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.tacz$buttons.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        for (GuiButton button : this.tacz$buttons) {
            button.drawButton(mc, mouseX, mouseY);
        }
    }

    /**
     * 派发筛选按钮的点击（上游的 widget 点击）。
     *
     * <p>
     * 命中后播原版按钮音效（{@code GuiScreen#mouseClicked} 里对 {@code buttonList} 的按钮做的是
     * {@code actionPerformed} + {@code GuiButton#func_146113_a(SoundHandler)}，即
     * {@code gui.button.press}，见 {@code GuiButton.java:137-140}），然后 {@code ci.cancel()}
     * 阻止原版继续处理这次点击。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tacz$clickPackFilterButtons(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        // 上游 AbstractButton#onClick 只处理左键
        if (mouseButton != 0 || this.tacz$buttons.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        for (GuiButton button : this.tacz$buttons) {
            if (button.mousePressed(mc, mouseX, mouseY)) {
                mc.getSoundHandler()
                    .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
                ci.cancel();
                return;
            }
        }
    }

    /**
     * 1.7.10 没有 {@code mouseScrolled}；滚轮在 {@code GuiContainerCreative#handleMouseInput}（第 596 行）里处理。
     * 侧栏命中时取消整个方法，避免同一格滚轮既滚侧栏又滚物品列表。
     */
    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void tacz$scrollPackFilters(CallbackInfo ci) {
        if (this.tacz$packFilters == null) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return;
        }
        // 与 GuiScreen#handleMouseInput 的坐标换算保持一致（缩放后坐标，y 轴原点在左下）
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scaledWidth = resolution.getScaledWidth();
        int scaledHeight = resolution.getScaledHeight();
        double mouseX = (double) Mouse.getEventX() * (double) scaledWidth / (double) mc.displayWidth;
        double mouseY = (double) scaledHeight
            - (double) Mouse.getEventY() * (double) scaledHeight / (double) mc.displayHeight
            - 1.0D;
        if (this.tacz$packFilters.mouseScrolled(mouseX, mouseY, wheel)) {
            ci.cancel();
        }
    }

    /** 1.7.10 用 {@code CreativeTabs.creativeTabArray} 反查上游的 {@code selectedTab} 字段。 */
    @Unique
    private CreativeTabs tacz$selectedTab() {
        CreativeTabs[] tabs = CreativeTabs.creativeTabArray;
        int index = selectedTabIndex;
        return index >= 0 && index < tabs.length ? tabs[index] : null;
    }

    @Unique
    private boolean tacz$inCreativeMode() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.playerController != null && mc.playerController.isInCreativeMode();
    }

}
