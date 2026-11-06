package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.config.sync.SyncConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 趴下（crawl）键。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/CrawlKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}（{@code GLFW_KEY_C → Keyboard.KEY_C}）、
 * {@code LocalPlayer → EntityPlayerSP}、{@code isPassenger() → isRiding()}、
 * {@code KeyMapping#isDown() → GunInputHandler#isKeyDown}。
 */
@SideOnly(Side.CLIENT)
public class CrawlKey {

    /** 供事件总线按实例注册。 */
    public static final CrawlKey INSTANCE = new CrawlKey();

    public static final KeyBinding CRAWL_KEY = new KeyBinding(
        "key.tacz.crawl.desc",
        Keyboard.KEY_C,
        "key.category.tacz");
    private static boolean controllerCrawlDown;

    private CrawlKey() {}

    public static boolean onCrawlPress() {
        return onCrawlInput(true);
    }

    public static boolean onCrawlControllerPress(boolean isPress) {
        controllerCrawlDown = isPress;
        return onCrawlInput(isPress);
    }

    public static void setControllerCrawlDown(boolean isDown) {
        controllerCrawlDown = isDown;
    }

    @SubscribeEvent
    public void onCrawlHoldingInput(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame()) {
            controllerCrawlDown = false;
        }
        if (KeyConfig.HOLD_TO_CRAWL.get()) {
            // 持续检查也能处理先松开修饰键，以及冷却期间松开主键的情况。
            onCrawlInput(false);
        }
    }

    private static boolean onCrawlInput(boolean isPress) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            return false;
        }
        boolean holdToCrawl = KeyConfig.HOLD_TO_CRAWL.get();
        IGun iGun = IGun.getIGunOrNull(player.getHeldItem());
        boolean inputEnabled = isInGame() && SyncConfig.ENABLE_CRAWL.get();
        // 1.7.10 无旁观者模式，只剩骑乘一项
        boolean playerAllowed = inputEnabled && !player.isRiding();
        boolean crawlAllowed = playerAllowed
            && (iGun == null ? SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get() : iGun.isCanCrawl(player.getHeldItem()));
        if (!crawlAllowed) {
            if (holdToCrawl && operator.isCrawl()) {
                operator.crawl(false);
            }
            return false;
        }
        if (!isPress && !holdToCrawl) {
            return false;
        }
        boolean crawl = holdToCrawl ? GunInputHandler.isKeyDown(CRAWL_KEY) || controllerCrawlDown : !operator.isCrawl();
        if (operator.isCrawl() != crawl) {
            operator.crawl(crawl);
        }
        return true;
    }
}
