package com.tacz.guns.client.event;

import java.io.File;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.tacz.guns.resource.PackConvertor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 进入世界时提示「旧版枪包可转换」（{@code /tacz convert}）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/PlayerEnterWorld.java}
 * （{@code @OnlyIn(Dist.CLIENT)} + {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)}）。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent} → FML 的
 * {@link PlayerEvent.PlayerLoggedInEvent}（1.7.10 的 Forge 侧 <b>没有</b> PlayerLoggedInEvent，
 * 它属于 {@code cpw.mods.fml.common.gameevent}，注册在 {@code FMLCommonHandler.instance().bus()}）；</li>
 * <li>{@code @OnlyIn(Dist.CLIENT)} → {@link SideOnly} + {@link Side#CLIENT}；</li>
 * <li>{@code event.getEntity()} → 字段 {@code player}；{@code sendSystemMessage(Component)} →
 * {@link net.minecraft.entity.player.EntityPlayer#addChatMessage(IChatComponent)}（1.7.10 没有「系统消息」
 * 独立通道，全部走聊天栏，与 {@code sendSystemMessage} 的可见效果一致）；</li>
 * <li>{@code Component.translatable} → {@link ChatComponentTranslation}；{@code Component#append} →
 * {@link IChatComponent#appendSibling(IChatComponent)}；</li>
 * <li>{@code Style/ClickEvent/HoverEvent/ChatFormatting} → {@link ChatStyle}/{@link ClickEvent}/
 * {@link HoverEvent}/{@link EnumChatFormatting}（1.7.10 的 {@code ClickEvent}/{@code HoverEvent} 在
 * {@code net.minecraft.event} 包下）。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（{@code cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115}）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在事件派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法(...)} 在这里改为 {@code public void 处理方法(...)}
 * （类名 / 方法名 / 参数 / 语义一律不变；非处理方法的静态工具方法与字段仍保持 {@code static}）。
 * 本类也因为「{@code EventBus#register(Class)} 在 1.7.10 无效」而提供 public 无参构造，
 * 由 {@code client/ClientProxy} 反射 {@code new} 后注册到正确的总线。
 */
@SideOnly(Side.CLIENT)
public class PlayerEnterWorld {

    @SubscribeEvent
    public void onPlayerEnterWorld(PlayerEvent.PlayerLoggedInEvent event) {
        File[] files = PackConvertor.FOLDER.toFile()
            .listFiles();
        if (files != null && files.length > 0) {
            event.player.addChatMessage(pre(new ChatComponentTranslation("message.tacz.convert_from_legacy.intro")));
            event.player.addChatMessage(pre(new ChatComponentTranslation("message.tacz.convert_from_legacy.intro2")));
            IChatComponent component = new ChatComponentTranslation("message.tacz.convert_from_legacy").appendSibling(
                new ChatComponentTranslation("message.tacz.convert_from_legacy.button").setChatStyle(
                    new ChatStyle().setColor(EnumChatFormatting.GREEN)
                        .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tacz convert"))
                        .setChatHoverEvent(
                            new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                new ChatComponentTranslation("message.tacz.convert_from_legacy.hover")))));
            event.player.addChatMessage(pre(component));
            event.player.addChatMessage(pre(new ChatComponentTranslation("message.tacz.convert_from_legacy.hint")));
            event.player.addChatMessage(pre(new ChatComponentTranslation("message.tacz.convert_from_legacy.hide")));
        }
    }

    private static IChatComponent pre(IChatComponent component) {
        return new ChatComponentTranslation("message.tacz.pre").appendSibling(component);
    }
}
