package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;

import org.lwjgl.opengl.GL11;

import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.mcshim.RenderType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 枪械挂板的<b>槽位高亮</b>：瞄准某格时高亮该格（空=绿），潜行时高亮所有已占用格（红）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/GunPegboardHighlightEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT)}，监听 {@code RenderHighlightEvent.Block}）。
 *
 * <p>
 * 平台替换与降级逐条同 {@link GunDisplayCaseHighlightEvent}（两者上游代码结构完全对称）：
 * {@code RenderHighlightEvent.Block} → {@link DrawBlockHighlightEvent}；
 * {@code BlockState#getValue(...)} → metadata + {@code GunPegboardBlock} 的静态判定；
 * {@code LevelRenderer#renderLineBox} → {@link RenderGlobal#drawOutlinedBoundingBox(AxisAlignedBB, int)}；
 * 上游内嵌 {@code RenderStateShard} 子类 → {@code mcshim.RenderType.lines()} + 显式 GL 开关。
 *
 * <p>
 * 注意：挂板没有「锁定」属性（上游也不判），因此这里比展示柜少一个 LOCKED 判断。
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
public class GunPegboardHighlightEvent {

    @SubscribeEvent
    public void onRenderBlockHighlight(DrawBlockHighlightEvent event) {
        MovingObjectPosition target = event.target;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        int x = target.blockX;
        int y = target.blockY;
        int z = target.blockZ;
        // 上游 event.getCamera().getEntity() → 1.7.10 的渲染视角实体
        Entity cameraEntity = Minecraft.getMinecraft().renderViewEntity;
        if (cameraEntity == null) {
            return;
        }
        World level = cameraEntity.worldObj;
        if (level == null) {
            return;
        }
        if (!(level.getBlock(x, y, z) instanceof GunPegboardBlock)) {
            return;
        }
        int metadata = level.getBlockMetadata(x, y, z);
        TileEntity blockEntity = level.getTileEntity(x, y, z);
        GunPegboardBlockEntity pegboard = blockEntity instanceof GunPegboardBlockEntity value ? value : null;
        if (cameraEntity.isSneaking()) {
            renderOccupiedSlots(event, metadata, x, y, z, pegboard);
            return;
        }
        renderAimedSlot(event, metadata, x, y, z, target, pegboard);
    }

    private static void renderAimedSlot(DrawBlockHighlightEvent event, int metadata, int x, int y, int z,
        MovingObjectPosition target, GunPegboardBlockEntity pegboard) {
        int slot = GunPegboardSlotHelper.getSlot(metadata, x, y, z, target.hitVec);
        if (slot < 0) {
            return;
        }
        boolean occupied = pegboard != null && pegboard.getItem(slot) != null;
        renderSlotBox(event, metadata, x, y, z, slot, occupied ? 1.0F : 1.0F, occupied ? 0.0F : 1.0F, 0.0F);
    }

    private static void renderOccupiedSlots(DrawBlockHighlightEvent event, int metadata, int x, int y, int z,
        GunPegboardBlockEntity pegboard) {
        if (pegboard == null) {
            return;
        }
        for (int slot = 0; slot < GunPegboardBlockEntity.SLOT_COUNT; slot++) {
            if (pegboard.getItem(slot) != null) {
                renderSlotBox(event, metadata, x, y, z, slot, 1.0F, 0.0F, 0.0F);
            }
        }
    }

    private static void renderSlotBox(DrawBlockHighlightEvent event, int metadata, int x, int y, int z, int slot,
        float red, float green, float blue) {
        AxisAlignedBB aabb = GunPegboardSlotHelper.getSlotAabb(metadata, x, y, z, slot);
        // 上游：event.getCamera().getPosition()。1.7.10 按原版 drawSelectionBox 的约定，用渲染玩家的插值坐标
        Entity player = event.player;
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) event.partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) event.partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) event.partialTicks;
        AxisAlignedBB offset = aabb.getOffsetBoundingBox(-camX, -camY, -camZ);

        RenderType lines = PegboardHighlightRenderType.getLines();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            lines.setupRenderState();
            // 上游 RenderType 的 NO_DEPTH_TEST / NO_CULL / COLOR_WRITE：1.7.10 直接开关 GL
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDepthMask(false);
            GL11.glColor4f(red, green, blue, 1.0F);
            RenderGlobal.drawOutlinedBoundingBox(offset, -1);
        } finally {
            lines.clearRenderState();
            GL11.glPopAttrib();
        }
    }

    /**
     * 上游内嵌 {@code RenderStateShard} 子类，仅提供「线条」这一种 {@code RenderType}。
     *
     * <p>
     * TODO(L2): 上游 {@code tmp/TACZ-1.20.1/.../GunPegboardHighlightEvent.java:84-109} 用
     * {@code RenderType.create(..., CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
     * .setLineState(DEFAULT_LINE).setLayeringState(VIEW_OFFSET_Z_LAYERING)
     * .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
     * .createCompositeState(false))}；1.7.10 无 {@code RenderStateShard}/{@code CompositeState}，
     * 降级为 {@code mcshim.RenderType.lines()} + 绘制处显式 GL 开关
     * （VIEW_OFFSET_Z_LAYERING 的深度偏移可用 {@code GL_POLYGON_OFFSET} 近似，这里未启用）。
     */
    private static class PegboardHighlightRenderType {

        private static final RenderType LINES_NO_DEPTH = RenderType.lines();

        private static RenderType getLines() {
            return LINES_NO_DEPTH;
        }
    }
}
