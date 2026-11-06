package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.lwjgl.input.Keyboard;

import com.tacz.guns.client.gui.compat.ClothConfigScreen;
import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.init.CompatRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 打开 Cloth Config 配置界面（上游 {@code KeyModifier.ALT} + T）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/ConfigKey.java}。
 *
 * <p>
 * 平台替换：
 * <ul>
 * <li>{@code KeyMapping → KeyBinding}、{@code LocalPlayer → EntityPlayerSP}；</li>
 * <li>{@code ModList.get().isLoaded(CompatRegistry.CLOTH_CONFIG)} →
 * {@link MenuIntegration#LOADED}（{@code compat-stub} 定稿的 1.7.10 探测常量，Cloth Config 在
 * 1.7.10 不存在，恒 {@code false}）；</li>
 * <li>{@code Minecraft#setScreen(x)} → {@link Minecraft#displayGuiScreen(GuiScreen)}；</li>
 * <li>{@code Component}/{@code MutableComponent}/{@code Style}/{@code ClickEvent}/{@code HoverEvent} →
 * {@link ChatComponentTranslation} + {@link ChatStyle} + {@code net.minecraft.event.ClickEvent/HoverEvent}
 * （<b>注意包名</b>：1.7.10 的这两个类在 {@code net.minecraft.event} 下，不是 {@code net.minecraft.network.chat}）；</li>
 * <li>{@code player.sendSystemMessage(component)} → {@code player.addChatMessage(component)}。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 默认键位的降级（有据）</b>：上游默认是 <b>ALT + T</b>。1.7.10 的 {@code KeyBinding}
 * <b>没有修饰键概念</b>（无 {@code KeyModifier}/{@code KeyConflictContext}），而 {@code T} 是原版聊天键：
 * {@code KeyBinding} 在构造时把 {@code keyCode → binding} 写进 {@code IntHashMap}，
 * {@code Minecraft#runTick} 每次按键只对该 keyCode 对应的<b>唯一</b> binding 更新
 * {@code pressed}/{@code pressTime}（见 {@code Minecraft.java:1837-1844} 与
 * {@code KeyBinding.java:41-52}）。自定义绑定晚于原版创建，会顶掉同 keyCode 的原版绑定 →
 * **默认占 {@code T} 会让原版聊天键彻底失效**。
 * 因此这里把默认值降级为「未绑定」（{@link Keyboard#KEY_NONE}），由玩家在「控制」里自行绑定；
 * 修饰键（Alt）要求由 {@link GunInputHandler#MODIFIER_ALT} 在轮询时施加，语义与上游一致。
 */
@SideOnly(Side.CLIENT)
public class ConfigKey {

    /** 对应上游的 {@code GLFW_KEY_T}；1.7.10 降级为未绑定，原因见类 javadoc。 */
    public static final KeyBinding OPEN_CONFIG_KEY = new KeyBinding(
        "key.tacz.open_config.desc",
        Keyboard.KEY_NONE,
        "key.category.tacz");

    private ConfigKey() {}

    public static void onOpenConfig() {
        if (!isInGame()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return;
        }
        if (!MenuIntegration.LOADED) {
            ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, ClothConfigScreen.CLOTH_CONFIG_URL);
            HoverEvent hoverEvent = new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ChatComponentTranslation("gui.tacz.cloth_config_warning.download"));
            ChatStyle style = new ChatStyle();
            style.setColor(EnumChatFormatting.BLUE);
            style.setUnderlined(Boolean.TRUE);
            style.setChatClickEvent(clickEvent);
            style.setChatHoverEvent(hoverEvent);
            IChatComponent component = new ChatComponentTranslation("gui.tacz.cloth_config_warning.tips");
            component.setChatStyle(style);
            player.addChatMessage(component);
        } else {
            CompatRegistry.checkModLoad(
                CompatRegistry.CLOTH_CONFIG,
                () -> mc.displayGuiScreen(MenuIntegration.getConfigScreen(mc.currentScreen)));
        }
    }
}
