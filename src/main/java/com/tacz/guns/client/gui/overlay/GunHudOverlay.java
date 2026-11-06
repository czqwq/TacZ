package com.tacz.guns.client.gui.overlay;

import java.text.DecimalFormat;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gameplay.ClientAmmoCount;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * 枪械 HUD（弹药数 / 备弹 / 开火模式 / 枪械图标）。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>1.7.10 没有 {@code IGuiOverlay} / {@code ForgeGui} / {@code RegisterGuiOverlaysEvent}，
 * HUD 由 {@code RenderGameOverlayEvent.Post} 触发。这里把入口改成
 * {@link #render(GuiGraphics, int, int)}，由 {@code client/event/**} 或
 * {@code client/init/ClientSetupEvent} 在事件里调用。</li>
 * <li>{@code Minecraft#player}→{@code thePlayer}，{@code font}→{@code fontRenderer}，
 * {@code LocalPlayer}→{@code EntityPlayerSP}，{@code getMainHandItem()}→{@code getHeldItem()}。</li>
 * <li>1.7.10 无旁观者模式，{@code player.isSpectator()} 判断删除。</li>
 * <li>{@code RenderSystem.setShader(GameRenderer::getPositionTexShader)} 无对应（固定管线），删除。</li>
 * <li>版本号：{@code SharedConstants.getCurrentVersion().getName()}→{@link Loader#MC_VERSION}；
 * {@code ModList.get().getModFileById(...).versionString()}→{@code Loader} 的 ModContainer 版本。</li>
 * </ul>
 */
public class GunHudOverlay {

    private static final ResourceLocation SEMI = new ResourceLocation(GunMod.MOD_ID, "textures/hud/fire_mode_semi.png");
    private static final ResourceLocation AUTO = new ResourceLocation(GunMod.MOD_ID, "textures/hud/fire_mode_auto.png");
    private static final ResourceLocation BURST = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/hud/fire_mode_burst.png");
    private static final ResourceLocation HEATBAR = new ResourceLocation(GunMod.MOD_ID, "textures/hud/heat_bar.png");
    private static final ResourceLocation HEATBASE = new ResourceLocation(GunMod.MOD_ID, "textures/hud/heat_base.png");

    private static final DecimalFormat CURRENT_AMMO_FORMAT = new DecimalFormat("000");
    private static final DecimalFormat CURRENT_AMMO_FORMAT_PERCENT = new DecimalFormat("000%");
    private static final DecimalFormat INVENTORY_AMMO_FORMAT = new DecimalFormat("0000");
    private static long checkAmmoTimestamp = -1L;
    private static int cacheMaxAmmoCount = 0;
    private static int cacheInventoryAmmoCount = 0;

    private static final int MAX_AMMO_COUNT = ClientAmmoCount.MAX_DISPLAY_COUNT;

    /** 对应 1.20 {@code IGuiOverlay#render(ForgeGui, GuiGraphics, float, int, int)} 的 1.7.10 入口。 */
    public void render(GuiGraphics graphics, int width, int height) {
        if (!RenderConfig.GUN_HUD_ENABLE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(stack);

        GunData gunData = TimelessAPI.getClientGunIndex(gunId)
            .map(ClientGunIndex::getGunData)
            .orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack)
            .orElse(null);
        if (gunData == null || display == null) {
            return;
        }

        // 是否背包直读
        boolean useInventoryAmmo = iGun.useInventoryAmmo(stack);
        // 是否使用虚拟备弹
        boolean useDummyAmmo = iGun.useDummyAmmo(stack);
        // 是否完全过热
        boolean overheatLocked = gunData.hasHeatData() && iGun.isOverheatLocked(stack);
        // 当前枪械弹药数
        int ammoCount = useInventoryAmmo
            ? cacheInventoryAmmoCount + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0)
            : iGun.getCurrentAmmoCount(stack)
                + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0);
        ammoCount = Math.min(ammoCount, MAX_AMMO_COUNT);
        // 弹药颜色
        int ammoCountColor;
        if (ammoCount < (cacheMaxAmmoCount * 0.25) && ammoCount < 10 || overheatLocked) {
            // 红色
            ammoCountColor = 0xFF5555;
        } else {
            // 如果背包直读并且使用虚拟备弹为青色，否则背包直读为黄色，其他为白色
            ammoCountColor = useInventoryAmmo && useDummyAmmo ? 0x55FFFF : useInventoryAmmo ? 0xFFFF55 : 0xFFFFFF;
        }
        // 备弹颜色
        int inventoryAmmoCountColor;
        if (!useInventoryAmmo && useDummyAmmo) {
            inventoryAmmoCountColor = 0x55FFFF;
        } else {
            inventoryAmmoCountColor = 0xAAAAAA;
        }

        // 当前枪械弹药数显示
        String currentAmmoCountText;
        if (display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
            // 百分比模式
            currentAmmoCountText = CURRENT_AMMO_FORMAT_PERCENT
                .format((float) ammoCount / (cacheMaxAmmoCount == 0 ? 1f : cacheMaxAmmoCount));
        } else {
            // 普通模式
            currentAmmoCountText = CURRENT_AMMO_FORMAT.format(ammoCount);
        }

        // 备弹数显示 (背包直读模式不显示备弹)
        String inventoryAmmoCountText = useInventoryAmmo ? "" : INVENTORY_AMMO_FORMAT.format(cacheInventoryAmmoCount);
        if (!useInventoryAmmo && gunData.getReloadData()
            .isInfinite()) {
            inventoryAmmoCountText = "∞";
        }

        // 计算弹药数
        handleCacheCount(player, stack, gunData, iGun);

        // 竖线
        graphics.fill(width - 75, height - 43, width - 74, height - 25, 0xFFFFFFFF);

        // 数字
        graphics.pose()
            .pushPose();
        graphics.pose()
            .scale(1.5F, 1.5F, 1.0F);
        graphics.drawString(
            mc.fontRenderer,
            currentAmmoCountText,
            (int) ((width - 70) / 1.5F),
            (int) ((height - 43) / 1.5F),
            ammoCountColor,
            false);
        graphics.pose()
            .popPose();

        graphics.pose()
            .pushPose();
        graphics.pose()
            .scale(0.8F, 0.8F, 1.0F);
        graphics.drawString(
            mc.fontRenderer,
            inventoryAmmoCountText,
            (int) ((width - 68 + mc.fontRenderer.getStringWidth(currentAmmoCountText) * 1.5F) / 0.8F),
            (int) ((height - 43) / 0.8F),
            inventoryAmmoCountColor,
            false);
        graphics.pose()
            .popPose();

        // 模组版本信息
        String debugInfo = String.format("%s-%s", Loader.MC_VERSION, modVersion());
        // 文本
        graphics.pose()
            .pushPose();
        graphics.pose()
            .scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(
            mc.fontRenderer,
            debugInfo,
            (int) ((width - 70) / 0.5F),
            (int) ((height - 29f) / 0.5F),
            0xffaaaaaa);
        graphics.pose()
            .popPose();

        // 图标渲染
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 获取图标
        ResourceLocation hudTexture = display.getHUDTexture();
        @Nullable
        ResourceLocation hudEmptyTexture = display.getHudEmptyTexture();

        if (ammoCount <= 0 || overheatLocked) {
            if (hudEmptyTexture == null) {
                RenderSystem.setShaderColor(1, 0.3f, 0.3f, 1);
            } else {
                hudTexture = hudEmptyTexture;
            }
        }
        // 渲染枪械图标
        graphics.blit(hudTexture, width - 117, height - 44, 0, 0, 39, 13, 39, 13);

        // 渲染开火模式图标
        FireMode fireMode = IGun.getMainHandFireMode(player);
        ResourceLocation fireModeTexture = switch (fireMode) {
            case AUTO -> AUTO;
            case BURST -> BURST;
            default -> SEMI;
        };
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(
            fireModeTexture,
            (int) (width - 68.5 + mc.fontRenderer.getStringWidth(currentAmmoCountText) * 1.5),
            height - 38,
            0,
            0,
            10,
            10,
            10,
            10);
    }

    /** 1.7.10 没有 {@code ModList}，改用 {@code Loader} 的 ModContainer。 */
    private static String modVersion() {
        ModContainer container = Loader.instance()
            .getIndexedModList()
            .get(GunMod.MOD_ID);
        return container == null ? "unknown" : container.getVersion();
    }

    private static void handleCacheCount(EntityPlayerSP player, ItemStack stack, GunData gunData, IGun iGun) {
        // 0.05 秒 (1 tick) 检查一次
        if (checkAmmoTimestamp < 0 || GunTime.hasElapsed(checkAmmoTimestamp, 50)) {
            checkAmmoTimestamp = GunTime.nowMillis();
            // 当前枪械的总弹药数
            cacheMaxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData);
            // 玩家背包弹药数
            cacheInventoryAmmoCount = ClientAmmoCount.getCached(player, stack, iGun);
        }
    }

    public static void resetTimestamp() {
        checkAmmoTimestamp = -1L;
        cacheMaxAmmoCount = 0;
        cacheInventoryAmmoCount = 0;
    }
}
