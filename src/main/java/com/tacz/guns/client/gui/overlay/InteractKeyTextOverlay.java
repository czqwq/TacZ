package com.tacz.guns.client.gui.overlay;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.AmmoStorageBlock;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 「按 O 交互」提示 HUD。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>入口改为 {@link #render(GuiGraphics, int, int)}（1.7.10 没有 {@code IGuiOverlay}）。</li>
 * <li>射线命中：{@code HitResult/BlockHitResult/EntityHitResult} → {@link MovingObjectPosition}
 * （{@code typeOfHit}、{@code blockX/Y/Z}、{@code entityHit}）。</li>
 * <li>方块：{@code BlockState} → {@link Block} + metadata（{@code World#getBlock/getBlockMetadata}）。</li>
 * <li>键名：{@code KeyMapping#getTranslatedKeyMessage().getString()} →
 * {@code Keyboard.getKeyName(KeyBinding#getKeyCode())}。</li>
 * <li>{@code GunDisplayCaseSlotHelper#getSlot(BlockState, BlockPos, Vec3)} →
 * {@code GunDisplayCaseSlotHelper.getSlot(metadata, x, y, z, hitVec)}（1.7.10 用 metadata 表达方块属性、
 * 用三个 int 表达 BlockPos）。</li>
 * </ul>
 */
public class InteractKeyTextOverlay {

    public void render(GuiGraphics graphics, int width, int height) {
        if (RenderConfig.DISABLE_INTERACT_HUD_TEXT.get()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return;
        }
        MovingObjectPosition hitResult = mc.objectMouseOver;
        if (hitResult == null) {
            return;
        }
        if (hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            renderBlockText(graphics, width, height, hitResult, player, mc);
            return;
        }
        if (hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            renderEntityText(graphics, width, height, hitResult, mc);
        }
    }

    private static void renderBlockText(GuiGraphics graphics, int width, int height, MovingObjectPosition hitResult,
        EntityPlayerSP player, Minecraft mc) {
        int x = hitResult.blockX;
        int y = hitResult.blockY;
        int z = hitResult.blockZ;
        Block block = player.worldObj == null ? null : player.worldObj.getBlock(x, y, z);
        if (block == null || block == Blocks.air) {
            return;
        }
        int metadata = player.worldObj.getBlockMetadata(x, y, z);
        if (block instanceof GunDisplayCaseBlock) {
            renderDisplayCaseText(graphics, width, height, player, mc, metadata, x, y, z, hitResult.hitVec);
            return;
        }
        if (block instanceof AmmoStorageBlock && IGun.mainHandHoldGun(player)) {
            renderText(
                graphics,
                width,
                height,
                mc.fontRenderer,
                interactKeyName(),
                new ChatComponentTranslation("gui.tacz.interact_key.text.ammo_storage_pickup"));
            return;
        }
        if (InteractKeyConfigRead.canInteractBlock(block, metadata)) {
            boolean mainHandHoldGun = IGun.mainHandHoldGun(player);
            boolean hasGunSmithTableFilterItem = hasGunSmithTableFilterItem(block, player);
            boolean willFilterByHand = RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get()
                && hasGunSmithTableFilterItem;
            IChatComponent filterHint = willFilterByHand
                ? new ChatComponentTranslation("gui.tacz.interact_key.text.gun_smith_table_filter")
                : null;
            if (mainHandHoldGun) {
                renderText(graphics, width, height, mc.fontRenderer, interactKeyName(), filterHint);
            } else if (hasGunSmithTableFilterItem) {
                renderText(
                    graphics,
                    width,
                    height,
                    mc.fontRenderer,
                    Keyboard.getKeyName(mc.gameSettings.keyBindUseItem.getKeyCode()),
                    filterHint);
            }
        }
    }

    private static void renderDisplayCaseText(GuiGraphics graphics, int width, int height, EntityPlayerSP player,
        Minecraft mc, int metadata, int x, int y, int z, @Nullable Vec3 hitVec) {
        // 上游 InteractKeyTextOverlay.java:83：手上不是枪、或展示柜已上锁 → 直接不提示
        // （上锁时按 F 只会返回 false，提示反而误导）。
        if (!IGun.mainHandHoldGun(player) || GunDisplayCaseBlock.isLocked(metadata)) {
            return;
        }
        // 上游 :86：命中格必须落在某一格上（1.7.10 用「方块坐标 + 绝对命中向量」的等价重载）。
        int slot = hitVec == null ? -1 : GunDisplayCaseSlotHelper.getSlot(metadata, x, y, z, hitVec);
        if (slot < 0) {
            return;
        }
        TileEntity tileEntity = player.worldObj == null ? null : player.worldObj.getTileEntity(x, y, z);
        if (!(tileEntity instanceof GunDisplayCaseBlockEntity displayCase)) {
            return;
        }
        // 上游 :90-94：格子里**已有枪**（此时即使手上拿不了东西也能取出/旋转）或手上物品可放入 → 提示。
        ItemStack stored = displayCase.getItem(slot);
        boolean occupied = stored != null && stored.getItem() != null;
        boolean canPlaceHeld = GunPegboardItemConfigRead.canPlace(player.getHeldItem());
        if (occupied || canPlaceHeld) {
            renderText(graphics, width, height, mc.fontRenderer, interactKeyName(), null);
        }
    }

    private static void renderEntityText(GuiGraphics graphics, int width, int height, MovingObjectPosition hitResult,
        Minecraft mc) {
        if (mc.thePlayer == null || !IGun.mainHandHoldGun(mc.thePlayer)) {
            return;
        }
        if (hitResult.entityHit == null) {
            return;
        }
        if (InteractKeyConfigRead.canInteractEntity(hitResult.entityHit)) {
            renderText(graphics, width, height, mc.fontRenderer, interactKeyName(), null);
        }
    }

    private static boolean hasGunSmithTableFilterItem(Block block, EntityPlayerSP player) {
        if (!(block instanceof AbstractGunSmithTableBlock)) {
            return false;
        }
        ItemStack stack = player.getHeldItem();
        Item item = stack == null ? null : stack.getItem();
        return item instanceof IGun || item instanceof IAttachment || item instanceof IAmmo;
    }

    /**
     * 1.20 {@code KeyMapping#getTranslatedKeyMessage().getString()} 的 1.7.10 等价物。
     *
     * <p>
     * {@code InteractKey.INTERACT_KEY} 在 1.7.10 是 {@code KeyBinding}。
     */
    private static String interactKeyName() {
        return Keyboard.getKeyName(InteractKey.INTERACT_KEY.getKeyCode());
    }

    private static void renderText(GuiGraphics graphics, int width, int height, FontRenderer font, String keyName,
        IChatComponent subtitle) {
        IChatComponent title = new ChatComponentTranslation(
            "gui.tacz.interact_key.text.desc",
            StringUtils.capitalize(keyName));
        graphics.drawString(
            font,
            title,
            (int) ((width - font.getStringWidth(title.getFormattedText())) / 2.0f),
            (int) (height / 2.0f - 25),
            0xFFFF55,
            false);
        if (subtitle != null) {
            graphics.drawString(
                font,
                subtitle,
                (int) ((width - font.getStringWidth(subtitle.getFormattedText())) / 2.0f),
                (int) (height / 2.0f - 14),
                0xAAAAAA,
                false);
        }
    }
}
