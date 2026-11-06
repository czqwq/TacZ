package com.tacz.guns.client.renderer.entity;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.DefaultVertexFormat;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 子弹实体渲染器：弹体模型 + 曳光弹发光。
 *
 * <h2>1.7.10 平台替换（逐个方法对照）</h2>
 * <ul>
 * <li>上游 {@code extends EntityRenderer<EntityKineticBullet>}（1.17+ 的泛型实体渲染器 +
 * {@code EntityRendererProvider.Context} 注入）→ 1.7.10 {@link Render}
 * （{@code doRender(Entity, double x, double y, double z, float entityYaw, float partialTicks)}）。
 * 上游 {@code render(bullet, entityYaw, partialTicks, poseStack, buffer, packedLight)} 是本类的核心，
 * 原样保留为核心方法 {@link #render(EntityKineticBullet, float, float, PoseStack, MultiBufferSource, int)}，
 * 由 {@link #doRender} 负责「把世界坐标 x/y/z 转成 PoseStack 平移」这一步。</li>
 * <li>上游 {@code EntityRenderer#shouldRender(entity, Frustum, camX, camY, camZ)} 在 1.7.10
 * <b>不是</b>渲染器钩子（{@code RenderManager} 只用 {@code Entity#isInRangeToRender3d} + 视锥裁剪
 * 对 {@code boundingBox} 做），因此本方法保留为公开工具方法，参数类型换成 1.7.10 的
 * {@link Frustrum}（注意 1.7.10 类名就是双 r 的 {@code Frustrum}），由 {@link #doRender} 自己调用。</li>
 * <li>上游 {@code getBlockLightLevel(entity, blockPos)} 返回 15（子弹不受方块光照影响）→ 1.7.10
 * 没有这个钩子，改为把亮度值<b>强制成 block=15</b>（保留天空光，见
 * {@link #fullBlockBrightness(int)}），效果等价。</li>
 * <li>上游 {@code RenderType.energySwirl(texture, 15, 15)}：1.20 用 {@code OffsetTexturingStateShard}
 * 在 shader 里把 UV 平移常量 {@code (15,15)}（与时间无关）。1.7.10 没有该 shader，改用<b>固定管线的
 * 纹理矩阵</b>（{@code GL_TEXTURE} / {@code glTranslatef}）实现同一常量偏移，
 * 见 {@link #pushEnergySwirlTextureMatrix()} / {@link #popEnergySwirlTextureMatrix()}。
 * 唯一差异：纹理矩阵只作用于本批次绘制期间（上游是 shader 内的常量 UV 偏移），因此必须成对调用。</li>
 * </ul>
 */
public class EntityBulletRenderer extends Render {

    /**
     * 1.20 {@code RenderType.energySwirl(ResourceLocation, float, float)} 的 1.7.10 等价状态包：
     * 加色混合（{@code SRC_ALPHA / ONE}）、不裁剪、不写深度、关闭 alpha 测试。
     *
     * <p>
     * 与上游的差异只有一条：1.20 的 {@code ENERGY_SWIRL_SHADER} 会按 {@code xOffset/yOffset} +
     * 时间做 UV 滚动，本状态包不含 shader，UV 滚动改由 {@code GL_TEXTURE} 矩阵承担。
     */
    private static final RenderType ENERGY_SWIRL = createEnergySwirlType(InternalAssetLoader.DEFAULT_BULLET_TEXTURE);

    public EntityBulletRenderer() {
        super();
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.DEFAULT_BULLET_MODEL);
    }

    /**
     * 上游 {@code EntityRenderer#render(EntityKineticBullet, float entityYaw, float partialTicks,
     * PoseStack, MultiBufferSource, int packedLight)}。
     */
    public void render(EntityKineticBullet bullet, float entityYaw, float partialTicks, PoseStack poseStack,
        MultiBufferSource buffer, int packedLight) {
        ResourceLocation gunId = bullet.getGunId();
        ResourceLocation gunDisplayId = bullet.getGunDisplayId();
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(gunDisplayId, gunId);
        if (!display.isPresent()) {
            return;
        }
        float[] tracerColor = bullet.getTracerColorOverride()
            .orElse(
                display.get()
                    .getTracerColor());
        ResourceLocation ammoId = bullet.getAmmoId();
        Optional<ClientAmmoIndex> ammoIndexOpt = TimelessAPI.getClientAmmoIndex(ammoId);
        if (!ammoIndexOpt.isPresent()) {
            return;
        }
        ClientAmmoIndex ammoIndex = ammoIndexOpt.get();
        BedrockAmmoModel ammoEntityModel = ammoIndex.getAmmoEntityModel();
        ResourceLocation textureLocation = ammoIndex.getAmmoEntityTextureLocation();
        if (ammoEntityModel != null && textureLocation != null) {
            poseStack.mulPose(
                Axis.YP.rotationDegrees(Mth.lerp(partialTicks, bullet.prevRotationYaw, bullet.rotationYaw) - 180.0F));
            poseStack.mulPose(
                Axis.XP.rotationDegrees(Mth.lerp(partialTicks, bullet.prevRotationPitch, bullet.rotationPitch)));
            poseStack.pushPose();
            poseStack.translate(0, 1.5, 0);
            poseStack.scale(-1, -1, 1);
            ammoEntityModel.render(
                poseStack,
                ItemDisplayContext.GROUND,
                RenderType.entityTranslucentCull(textureLocation),
                packedLight,
                OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        // 曳光弹发光
        if (bullet.isTracerAmmo()) {
            float[] actualTracerColor = Objects.requireNonNullElse(tracerColor, ammoIndex.getTracerColor());
            renderTracerAmmo(bullet, actualTracerColor, partialTicks, poseStack, packedLight);
        }
    }

    public void renderTracerAmmo(EntityKineticBullet bullet, float[] tracerColor, float partialTicks,
        PoseStack poseStack, int packedLight) {
        Optional<BedrockModel> modelOpt = getModel();
        if (!modelOpt.isPresent()) {
            return;
        }
        BedrockModel model = modelOpt.get();
        Entity shooter = bullet.getOwner();
        if (shooter == null) {
            return;
        }
        boolean isFirstPerson = this.renderManager.options.thirdPersonView == 0
            && shooter == Minecraft.getMinecraft().thePlayer;
        if (isFirstPerson && !RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get()) {
            return;
        }
        poseStack.pushPose();
        {
            float width = 0.005f;
            Vec3 bulletPosition = interpolatedPosition(bullet, partialTicks);
            double trailLength = 0.85 * Math.sqrt(
                bullet.motionX * bullet.motionX + bullet.motionY * bullet.motionY + bullet.motionZ * bullet.motionZ);
            double disToEye = bulletPosition.distanceTo(eyePosition(shooter, partialTicks));
            trailLength = Math.min(trailLength, disToEye * 0.8);

            if (isFirstPerson) {
                // 第一人称渲染自己的曳光弹的时候需要应用偏移
                Vector3f offset = bullet.getFirstPersonRenderOffset();
                if (offset == null) {
                    offset = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                    // 上游 Camera#getXRot/getYRot：Camera#setup 用 entity.getViewXRot/getViewYRot(partialTicks)，
                    // 即两 tick 间插值（1.7.10 对应 EntityRenderer#orientCamera:641-642）。
                    bullet.setCameraXRot(cameraPitch(partialTicks));
                    bullet.setCameraYRot(cameraYaw(partialTicks));
                    bullet.setFirstPersonRenderOffset(offset);
                }
                // 按照生存时间减少曳光弹的偏移，避免渲染位置距离落点太远
                double offsetReducer = Math.max(0, (50 - disToEye)) / 50;
                // 摄像机旋转
                poseStack.mulPose(Axis.YN.rotationDegrees(bullet.getCameraYRot() + 180f));
                poseStack.mulPose(Axis.XN.rotationDegrees(bullet.getCameraXRot()));
                // 应用偏移
                poseStack.translate(offset.x * offsetReducer, offset.y * offsetReducer, offset.z * offsetReducer);
                // 逆转摄像机旋转
                poseStack.mulPose(Axis.XP.rotationDegrees(bullet.getCameraXRot()));
                poseStack.mulPose(Axis.YP.rotationDegrees(bullet.getCameraYRot() + 180f));
            }
            // 说是 override 其实默认值是 1
            // 所以这里直接乘也没关系
            width *= bullet.getTracerSizeOverride();
            width *= (float) Math.max(1.0, disToEye / 3.5);
            poseStack.mulPose(
                Axis.YP.rotationDegrees(Mth.lerp(partialTicks, bullet.prevRotationYaw, bullet.rotationYaw) - 180.0F));
            poseStack.mulPose(
                Axis.XP.rotationDegrees(Mth.lerp(partialTicks, bullet.prevRotationPitch, bullet.rotationPitch)));
            poseStack.translate(0, isFirstPerson ? 0 : -0.2, trailLength / 2.0);
            poseStack.scale(width, width, (float) trailLength);
            // 距离两格外才渲染，只在前 5 tick 判定。上游是 shooter.getEyePosition()（不插值，取当前 tick 位置），
            // 而非 renderTracerAmmo 里那个带 partialTicks 的 eyePosition。
            double bulletDistance = bulletPosition.distanceTo(eyePosition(shooter, 1.0F));
            if (bullet.ticksExisted >= 5 || bulletDistance > 2) {
                // 上游 RenderType.energySwirl(..., 15, 15) 的常量纹理矩阵，由 GL_TEXTURE 矩阵承担（见类注释）。
                pushEnergySwirlTextureMatrix();
                try {
                    model.render(
                        poseStack,
                        ItemDisplayContext.NONE,
                        ENERGY_SWIRL,
                        fullBlockBrightness(packedLight),
                        OverlayTexture.NO_OVERLAY,
                        tracerColor[0],
                        tracerColor[1],
                        tracerColor[2],
                        1);
                } finally {
                    popEnergySwirlTextureMatrix();
                }
            }
        }
        poseStack.popPose();
    }

    // ================================================================================================
    // 1.7.10 Render 入口
    // ================================================================================================

    /**
     * 1.7.10 的实体渲染入口。
     *
     * <p>
     * 上游由 {@code EntityRendererDispatcher} 直接把「实体局部坐标 + PoseStack」交给渲染器；
     * 1.7.10 的 {@link Render#doRender} 收到的是<b>摄像机相对</b>的世界坐标，因此这里
     * 先把 {@code x/y/z} 平移进 PoseStack，再交给 {@link #render}。
     */
    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (!(entity instanceof EntityKineticBullet bullet)) {
            return;
        }
        Frustrum frustrum = new Frustrum();
        double camX = ActiveRenderInfo.objectX;
        double camY = ActiveRenderInfo.objectY;
        double camZ = ActiveRenderInfo.objectZ;
        frustrum.setPosition(camX, camY, camZ);
        if (!shouldRender(bullet, frustrum, camX, camY, camZ)) {
            return;
        }
        int packedLight = bullet.getBrightnessForRender(partialTicks);
        PoseStack poseStack = new PoseStack();
        poseStack.translate(x, y, z);
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate();
        try {
            render(bullet, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
        } finally {
            bufferSource.endBatch();
        }
    }

    /**
     * 上游 {@code EntityRenderer#shouldRender(EntityKineticBullet, Frustum, double, double, double)}。
     *
     * <p>
     * 上游用 {@code bullet.getBoundingBoxForCulling().inflate(0.5)}（子弹的包围盒很小，
     * 稍放大以免贴边时被裁掉），并为「NaN / 零尺寸」的退化包围盒兜底成 4×4×4。
     * 1.7.10 的 {@code AxisAlignedBB} 没有 {@code inflate}（用 {@code expand}）、
     * 也没有 {@code getSize()}，尺寸由本类自己算；视锥判断用
     * {@link Frustrum#isBoundingBoxInFrustum(AxisAlignedBB)}。
     */
    public boolean shouldRender(EntityKineticBullet bullet, Frustrum camera, double camX, double camY, double camZ) {
        AxisAlignedBB aabb = cullingBox(bullet);
        if (camera == null) {
            return true;
        }
        return camera.isBoundingBoxInFrustum(aabb);
    }

    /**
     * 上游的 {@code EntityRenderer#getTextureLocation}：子弹本体不走 EntityRenderer 的贴图绑定路径
     * （模型自带贴图），返回 {@code null} 等价于上游返回未使用值。
     */
    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    // ================================================================================================
    // 1.7.10 侧辅助
    // ================================================================================================

    private static AxisAlignedBB cullingBox(EntityKineticBullet bullet) {
        AxisAlignedBB raw = bullet.boundingBox;
        AxisAlignedBB aabb = raw == null ? null : raw.expand(0.5, 0.5, 0.5);
        boolean degenerate = aabb == null || Double.isNaN(aabb.minX)
            || Double.isNaN(aabb.maxX)
            || Double.isNaN(aabb.minY)
            || Double.isNaN(aabb.maxY)
            || Double.isNaN(aabb.minZ)
            || Double.isNaN(aabb.maxZ)
            || aabb.maxX - aabb.minX == 0.0
            || aabb.maxY - aabb.minY == 0.0
            || aabb.maxZ - aabb.minZ == 0.0;
        if (degenerate) {
            // 上游同样在这里兜底成 ±2 的立方体（见 EntityBulletRenderer#shouldRender）。
            aabb = AxisAlignedBB.getBoundingBox(
                bullet.posX - 2.0,
                bullet.posY - 2.0,
                bullet.posZ - 2.0,
                bullet.posX + 2.0,
                bullet.posY + 2.0,
                bullet.posZ + 2.0);
        }
        return aabb;
    }

    /** 上游 {@code Entity#getPosition(float)}（1.8+）；1.7.10 只有 {@code getPositionVector()}。 */
    private static Vec3 interpolatedPosition(Entity entity, float partialTicks) {
        double dx = entity.posX - entity.prevPosX;
        double dy = entity.posY - entity.prevPosY;
        double dz = entity.posZ - entity.prevPosZ;
        return Vec3.createVectorHelper(
            entity.prevPosX + dx * partialTicks,
            entity.prevPosY + dy * partialTicks,
            entity.prevPosZ + dz * partialTicks);
    }

    /** 上游 {@code Entity#getEyePosition(float)}（1.8+）。 */
    private static Vec3 eyePosition(Entity entity, float partialTicks) {
        Vec3 base = interpolatedPosition(entity, partialTicks);
        return Vec3.createVectorHelper(base.xCoord, base.yCoord + entity.getEyeHeight(), base.zCoord);
    }

    /**
     * 上游 {@code getBlockLightLevel(...) == 15} 的等价：把亮度值里的**方块光**强制成 15，
     * 保留原天空光。亮度打包布局见 {@link LightTexture}（1.7.10 是 {@code sky << 20 | block << 4}）。
     */
    private static int fullBlockBrightness(int packedLight) {
        return LightTexture.pack(15, LightTexture.sky(packedLight));
    }

    /**
     * 1.7.10 相机偏航角（对应上游 {@code Camera#getYRot()}）。
     * 1.7.10 的相机朝向就是 {@code renderViewEntity} 的插值 rotation，见
     * {@code EntityRenderer#orientCamera:641-642}；不能直接读当前 tick 的 {@code rotationYaw}。
     */
    private static float cameraYaw(float partialTicks) {
        Entity view = Minecraft.getMinecraft().renderViewEntity;
        if (view == null) {
            return 0.0F;
        }
        return view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * partialTicks;
    }

    /** 1.7.10 相机俯仰角（对应上游 {@code Camera#getXRot()}），插值依据同 {@link #cameraYaw(float)}。 */
    private static float cameraPitch(float partialTicks) {
        Entity view = Minecraft.getMinecraft().renderViewEntity;
        if (view == null) {
            return 0.0F;
        }
        return view.prevRotationPitch + (view.rotationPitch - view.prevRotationPitch) * partialTicks;
    }

    private static RenderType createEnergySwirlType(ResourceLocation texture) {
        RenderType.State state = new RenderType.State();
        state.texture = texture;
        state.translucent = true;
        // 上游用 ADDITIVE_TRANSPARENCY：SRC_ALPHA → ONE
        state.blendSrc = GL11.GL_SRC_ALPHA;
        state.blendDst = GL11.GL_ONE;
        state.cull = false;
        state.depthWrite = false;
        state.depthTest = true;
        state.alphaTest = false;
        state.lightmap = true;
        return RenderType
            .create("energy_swirl", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, state);
    }

    /**
     * 用固定管线的纹理矩阵复刻 1.20 {@code RenderType.energySwirl(tex, 15, 15)} 的纹理矩阵。
     *
     * <p>
     * 上游 {@code OffsetTexturingStateShard(15, 15)} 在每次 setup 时把 {@code TextureMat} 设为
     * <b>常量</b> {@code translation(15,15,0)}（{@code RenderStateShard.java:554-556}），shader 里
     * {@code texCoord0 = (TextureMat * vec4(UV0,0,1)).xy}，与时间无关。1.7.10 侧在 {@code Tessellator}
     * 绘制期间对 {@code GL_TEXTURE} 栈施加同样的平移（GL_REPEAT 下与上游一致），成对调用。
     */
    private static void pushEnergySwirlTextureMatrix() {
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(15.0F, 15.0F, 0.0F);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static void popEnergySwirlTextureMatrix() {
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }
}
