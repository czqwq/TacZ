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

import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.mcshim.RenderType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 枪械展示柜的<b>槽位高亮</b>：瞄准某格时高亮该格（空=绿），潜行时高亮所有已占用格（红）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/GunDisplayCaseHighlightEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT)}，监听 {@code RenderHighlightEvent.Block}）。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code RenderHighlightEvent.Block} → 1.7.10 的 {@link DrawBlockHighlightEvent}：同样在
 * 「方块选择框」绘制点触发（{@code EntityRenderer#renderWorld} → {@code ForgeHooksClient#onDrawBlockHighlight}），
 * 且紧跟其后就是原版 {@code RenderGlobal#drawSelectionBox}（所以我们画的线框与原版选择框共享同一套
 * 相机平移约定）。<b>不</b> cancel 该事件：上游只是「追加」高亮框，原版选择框仍然要画；</li>
 * <li>{@code event.getCamera().getEntity()/getPosition()} → 世界/相机信息改从
 * {@code Minecraft#renderViewEntity} 与 {@code event.player} 取；相机平移量按原版
 * {@code drawSelectionBox} 的做法用 {@code event.player} 的插值坐标（{@code partialTicks}），
 * 这样在任何视角模式下都与原版选择框对齐；</li>
 * <li>{@code BlockPos} → 三个 int；{@code BlockState#getValue(GunDisplayCaseBlock.LOCKED)} →
 * {@code world.getBlockMetadata} + {@link GunDisplayCaseBlock#isLocked(int)}；
 * {@code Level#getBlockEntity(pos)} → {@code World#getTileEntity(x,y,z)}；</li>
 * <li>{@code GunDisplayCaseSlotHelper#getSlot(BlockState,BlockPos,Vec3)} →
 * {@link GunDisplayCaseSlotHelper#getSlot(int, int, int, int, net.minecraft.util.Vec3)}（metadata 版）；
 * {@code getSlotAabb(...).move(-camera)} → {@code getOffsetBoundingBox(-camX,-camY,-camZ)}；</li>
 * <li>{@code LevelRenderer#renderLineBox(PoseStack, VertexConsumer, AABB, r,g,b,a)} → 1.7.10
 * {@link RenderGlobal#drawOutlinedBoundingBox(AxisAlignedBB, int)}（{@code -1} = 沿用当前
 * {@code glColor}），GL 状态由 {@link #renderSlotBox} 手工开关；</li>
 * <li><b>降级</b>：内嵌类 {@code DisplayCaseHighlightRenderType} 上游 extends {@code RenderStateShard}
 * 并用 {@code RenderType.CompositeState.builder()} 组「线条 + NO_DEPTH_TEST + NO_CULL + COLOR_WRITE」。
 * 1.7.10 没有 {@code RenderStateShard}/{@code CompositeState}（见 {@code mcshim.RenderType} javadoc），
 * 改为返回 {@code mcshim.RenderType.lines()} 这个「GL 状态包」，"关闭深度测试" 用显式
 * {@code GL11.glDisable(GL11.GL_DEPTH_TEST)} 表达（{@code // TODO(L2)} 见内嵌类注释）。</li>
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
public class GunDisplayCaseHighlightEvent {

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
        if (!(level.getBlock(x, y, z) instanceof GunDisplayCaseBlock)) {
            return;
        }
        int metadata = level.getBlockMetadata(x, y, z);
        if (GunDisplayCaseBlock.isLocked(metadata)) {
            return;
        }
        TileEntity blockEntity = level.getTileEntity(x, y, z);
        GunDisplayCaseBlockEntity displayCase = blockEntity instanceof GunDisplayCaseBlockEntity value ? value : null;
        if (cameraEntity.isSneaking()) {
            renderOccupiedSlots(event, metadata, x, y, z, displayCase);
            return;
        }
        renderAimedSlot(event, metadata, x, y, z, target, displayCase);
    }

    private static void renderAimedSlot(DrawBlockHighlightEvent event, int metadata, int x, int y, int z,
        MovingObjectPosition target, GunDisplayCaseBlockEntity displayCase) {
        int slot = GunDisplayCaseSlotHelper.getSlot(metadata, x, y, z, target.hitVec);
        if (slot < 0) {
            return;
        }
        boolean occupied = displayCase != null && displayCase.getItem(slot) != null;
        renderSlotBox(event, metadata, x, y, z, slot, occupied ? 1.0F : 1.0F, occupied ? 0.0F : 1.0F, 0.0F);
    }

    private static void renderOccupiedSlots(DrawBlockHighlightEvent event, int metadata, int x, int y, int z,
        GunDisplayCaseBlockEntity displayCase) {
        if (displayCase == null) {
            return;
        }
        for (int slot = 0; slot < GunDisplayCaseBlockEntity.SLOT_COUNT; slot++) {
            if (displayCase.getItem(slot) != null) {
                renderSlotBox(event, metadata, x, y, z, slot, 1.0F, 0.0F, 0.0F);
            }
        }
    }

    private static void renderSlotBox(DrawBlockHighlightEvent event, int metadata, int x, int y, int z, int slot,
        float red, float green, float blue) {
        AxisAlignedBB aabb = GunDisplayCaseSlotHelper.getSlotAabb(metadata, x, y, z, slot);
        // 上游：event.getCamera().getPosition()。1.7.10 按原版 drawSelectionBox 的约定，用渲染玩家的插值坐标
        Entity player = event.player;
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) event.partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) event.partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) event.partialTicks;
        AxisAlignedBB offset = aabb.getOffsetBoundingBox(-camX, -camY, -camZ);

        RenderType lines = DisplayCaseHighlightRenderType.getLines();
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
     * TODO(L2): 上游 {@code tmp/TACZ-1.20.1/.../GunDisplayCaseHighlightEvent.java:84-109} 用
     * {@code RenderType.create(name, POSITION_COLOR_NORMAL, LINES, 256, false, false,
     * CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(DEFAULT_LINE)
     * .setLayeringState(VIEW_OFFSET_Z_LAYERING).setDepthTestState(NO_DEPTH_TEST)
     * .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false))}；
     * 1.7.10 无 {@code RenderStateShard}/{@code CompositeState}/{@code ShaderStateShard}，
     * 故降级为 {@code mcshim.RenderType.lines()} + 绘制处的显式 GL 开关
     * （VIEW_OFFSET_Z_LAYERING 的深度偏移用 {@code GL_POLYGON_OFFSET} 可近似，这里未启用）。
     */
    private static class DisplayCaseHighlightRenderType {

        private static final RenderType LINES_NO_DEPTH = RenderType.lines();

        private static RenderType getLines() {
            return LINES_NO_DEPTH;
        }
    }
}
