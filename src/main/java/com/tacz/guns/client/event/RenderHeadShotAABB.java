package com.tacz.guns.client.event;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;

import org.lwjgl.opengl.GL11;

import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.util.EntityIdHelper;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 爆头判定箱调试显示（F3+B 命中箱 + {@code RenderConfig.HEAD_SHOT_DEBUG_HITBOX}）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/RenderHeadShotAABB.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT)}，监听 {@code RenderLivingEvent.Post}）。
 *
 * <p>
 * 平台替换 / 降级（相对上游）：
 * <ul>
 * <li>{@code RenderLivingEvent.Post<?, ?>} → 1.7.10 的 {@link RenderLivingEvent.Post}
 * （字段 {@code entity}/{@code renderer}/{@code x,y,z}，<b>不带</b> PoseStack/MultiBufferSource）；
 * 该事件在 {@code RendererLivingEntity#doRender} 的 {@code glPopMatrix} <b>之后</b>投递
 * （{@code RendererLivingEntity.java:295/297}），此时 GL_MODELVIEW 是<b>相机帧</b>，不是实体局部帧
 * → 绘制前必须自己 {@code glTranslatef(event.x, event.y, event.z)} 再画局部坐标 AABB；</li>
 * <li>{@code LevelRenderer#renderLineBox(PoseStack, VertexConsumer, AABB, r,g,b,a)} →
 * {@link RenderGlobal#drawOutlinedBoundingBox(AxisAlignedBB, int)}（{@code -1} = 沿用当前
 * {@code glColor}），GL 状态手工开关；{@code AABB#inflate} → {@link AxisAlignedBB#expand};
 * {@code Entity#getBbWidth()} → 字段 {@code width}；</li>
 * <li>{@code minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()} →
 * {@link RenderManager#debugBoundingBox}（1.7.10 的 F3+B 开关，public static 字段）；</li>
 * <li>{@code ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())} → {@link EntityIdHelper#getModernId}
 * （统一把 1.7.10 的注册名转成 1.20 风格 id：原版 {@code "Zombie"} → {@code minecraft:zombie}、
 * FML 模组实体 {@code "<modid>.<name>"} → {@code "<modid>:<name>"}，见 {@code util/EntityIdHelper}）；</li>
 * <li><b>降级</b>：{@code minecraft.showOnlyReducedInfo()}（1.20 的「减少调试信息」）在 1.7.10
 * 没有对应 API，删除该守卫（{@code // TODO(L2)} 在调用点）。</li>
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
public class RenderHeadShotAABB {

    @SubscribeEvent
    public void onRenderEntity(RenderLivingEvent.Post event) {
        // 上游 minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()
        boolean canRender = RenderManager.debugBoundingBox;
        // TODO(L2): 上游还有 minecraft.showOnlyReducedInfo() 守卫；1.7.10 没有该接口，无法表达。
        if (!canRender) {
            return;
        }
        if (!RenderConfig.HEAD_SHOT_DEBUG_HITBOX.get()) {
            return;
        }
        EntityLivingBase entity = event.entity;
        ResourceLocation entityId = EntityIdHelper.getModernId(entity);
        if (entityId == null) {
            return;
        }
        AxisAlignedBB aabb = HeadShotAABBConfigRead.getAABB(entityId);
        if (aabb == null) {
            float width = entity.width;
            float eyeHeight = entity.getEyeHeight();
            // 扩张 0.01，避免和原版显示重合
            aabb = AxisAlignedBB
                .getBoundingBox(-width / 2, eyeHeight - 0.25, -width / 2, width / 2, eyeHeight + 0.25, width / 2)
                .expand(0.01, 0.01, 0.01);
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            // RenderLivingEvent.Post 在 RendererLivingEntity#doRender 的 glPopMatrix 之后投递
            // （RendererLivingEntity.java:295/297），矩阵停在相机帧；event.x/y/z 已是「实体位置 - renderPos」，
            // 平移后再画实体局部坐标 AABB 即等价上游「事件 PoseStack 停在实体位置帧」。
            GL11.glTranslatef((float) event.x, (float) event.y, (float) event.z);
            // GL 状态对齐上游 RenderType.lines()：深度测试/写入开、混合开、剔除关、线宽默认 1。
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glLineWidth(1.0F);
            GL11.glColor4f(1.0F, 1.0F, 0.0F, 1.0F);
            RenderGlobal.drawOutlinedBoundingBox(aabb, -1);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
