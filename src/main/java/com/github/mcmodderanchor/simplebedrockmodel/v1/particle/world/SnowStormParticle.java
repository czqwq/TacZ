// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/world/SnowStormParticle.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.github.mcmodderanchor.simplebedrockmodel.port.Camera;
import com.github.mcmodderanchor.simplebedrockmodel.port.Vec3;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceLighting;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleExpireIfInBlocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleExpireIfNotInBlocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.motion.ParticleMotionCollision;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.motion.ParticleMotionDynamic;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.CameraStateCache;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.util.math.MathUtil;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

/**
 * Molang 世界粒子（SnowStorm 语义）在 1.7.10 的实现。
 *
 * <h2>移植要点</h2>
 * 上游继承 1.20 的 {@code TextureSheetParticle}，1.7.10 对应的粒子基类是
 * {@link EntityFX}（{@code EffectRenderer} 管理其 tick 与渲染）。字段名映射（1.20 → 1.7.10）：
 * {@code xd/yd/zd → motionX/Y/Z}、{@code rCol/gCol/bCol/alpha → particleRed/Green/Blue/Alpha}、
 * {@code lifetime → particleMaxAge}、{@code age → particleAge}、{@code gravity → particleGravity}、
 * {@code level → worldObj}、{@code remove() → setDead()}、{@code getLightColor(float) →
 * getBrightnessForRender(float)}。{@code roll/oRoll} 在 1.7.10 的 EntityFX 上不存在，本类自带同名字段。
 *
 * <h2>降级点</h2>
 * <ul>
 * <li><b>渲染不由 {@code EffectRenderer} 负责</b>：1.20 的粒子通过
 * {@code getRenderType()} 选择任意 {@code RenderType}（自带纹理/混合），由 {@code ParticleEngine}
 * 按 RenderType 分组绘制。1.7.10 的 {@link net.minecraft.client.particle.EffectRenderer} 只有 4 个
 * <b>固定图层</b>，且同一图层内只开一次 {@code Tessellator} 会话——中途切换纹理不会生效。
 * 因此本类的 {@link #getFXLayer()} 返回 3（自定义图层）且 {@link #renderParticle} 为空，
 * 真正的绘制交给 {@link WorldEmitterManager#renderWorldParticles(float)}：它按
 * {@link RenderType} 分组、逐纹理绑定后用 mcshim {@code MultiBufferSource} 批量提交。
 * 上游的 {@link #render(VertexConsumer, Camera, float)} 因此可以逐行保留。</li>
 * <li>{@code hasPhysics}（1.20 {@code Particle} 的字段）在 1.7.10 无对应，改为本类的私有标记。</li>
 * <li>碰撞用 1.7.10 的 {@code AxisAlignedBB.calculateXOffset/YOffset/ZOffset} +
 * {@code World#getCollidingBoundingBoxes} 实现（等价于 1.20 的 {@code Entity.collideBoundingBox}），
 * 保留上游的 {@code coefficientOfRestitution} / {@code collisionDrag} 语义。</li>
 * <li>方块过期检测：1.20 用 {@code BuiltInRegistries.BLOCK.getKey(block)}，
 * 1.7.10 用 {@code Block.blockRegistry.getNameForObject(block)}（同为 {@code namespace:path}）。</li>
 * <li>Sodium 快速路径 {@code SodiumParticleVertexWriter.tryRender} 在 1.7.10 恒返回 false（见该类注释）。</li>
 * </ul>
 */
public class SnowStormParticle extends EntityFX {

    private final ParticleInstance particleData;
    private final ParticleEffectDefinition definition;
    private final ParticleEmitterInstance emitter;
    private final boolean fovCompensatedWorldParticle;
    private final float birthFovScale;
    @Nullable
    private final Matrix3f birthMotionMap;
    @Nullable
    private final ParticleMotionDynamic dynamicMotion;

    // 锚点跟随：发射器启用 followAnchor 时，粒子保存锚点局部坐标，
    // 渲染时用发射器当前 worldTransform（含旋转+平移）重新求世界坐标——位置与朝向均跟随锚点
    private final boolean followAnchor;
    private final float localX;
    private final float localY;
    private final float localZ;

    // 锚点跟随渲染用的临时世界坐标
    private static final Vector3f FOLLOW_POS = new Vector3f();

    // billboard 朝向模式
    private final ParticleAppearanceBillboard.FaceCameraMode faceCameraMode;

    // 碰撞参数
    @Nullable
    private final ParticleMotionCollision collisionComponent;
    private boolean hasCollision;
    /** TODO(L2): 1.20 {@code Particle#hasPhysics} 在 1.7.10 无对应字段，改为本类私有标记。 */
    private boolean hasPhysics;
    private final float collisionDrag;
    private final float coefficientOfRestitution;
    private final boolean expireOnContact;

    // 光照
    private final boolean environmentLighting;

    // 方块过期组件
    @Nullable
    private final ParticleExpireIfInBlocks expireIfInBlocks;
    @Nullable
    private final ParticleExpireIfNotInBlocks expireIfNotInBlocks;

    // 自定义 RenderType（按纹理+材质缓存）
    private final RenderType renderType;

    // 1.7.10 EntityFX 没有 roll/oRoll 字段（1.12 才有），本类自带同名字段
    private float roll;
    private float oRoll;

    public SnowStormParticle(WorldClient level, ParticleInstance particleData, ParticleEffectDefinition definition,
        ParticleEmitterInstance emitter) {
        // TODO(L2): 1.20 {@code TextureSheetParticle(WorldClient, double, double, double)} →
        // 1.7.10 {@link EntityFX#EntityFX(World, double, double, double)}（protected，同参数语义）。
        super(level, particleData.x, particleData.y, particleData.z);
        this.particleData = particleData;
        this.definition = definition;
        this.emitter = emitter;
        float birthScale = emitter.getWorldParticleScale();
        this.fovCompensatedWorldParticle = emitter.isFovCompensatedWorldParticles() && Float.isFinite(birthScale)
            && birthScale > 1.0e-4f;
        this.birthFovScale = birthScale;
        this.birthMotionMap = fovCompensatedWorldParticle ? createBirthMotionMap(birthScale) : null;
        this.dynamicMotion = fovCompensatedWorldParticle ? definition.findComponent(ParticleMotionDynamic.class) : null;

        // 初始速度（blocks/tick）
        this.motionX = particleData.vx / 20f;
        this.motionY = particleData.vy / 20f;
        this.motionZ = particleData.vz / 20f;

        // 颜色
        this.particleRed = particleData.r;
        this.particleGreen = particleData.g;
        this.particleBlue = particleData.b;
        this.particleAlpha = particleData.a;

        // 生命周期（tick 为单位）
        this.particleMaxAge = (int) (particleData.maxLifetime * 20);
        this.particleAge = 0;

        // 不使用原版的重力和摩擦
        this.particleGravity = 0;

        // billboard 朝向模式
        ParticleAppearanceBillboard billboard = definition.findComponent(ParticleAppearanceBillboard.class);
        this.faceCameraMode = billboard != null ? billboard.faceCameraMode()
            : ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ;

        // 碰撞
        ParticleMotionCollision collision = definition.findComponent(ParticleMotionCollision.class);
        this.collisionComponent = collision;
        if (collision != null) {
            // enabled 初始值：如果有 enabled 表达式则求值，否则默认启用
            if (collision.enabled() != null) {
                this.hasCollision = collision.enabled()
                    .evaluate(
                        emitter.getMolang()
                            .getContext())
                    != 0;
            } else {
                this.hasCollision = true;
            }
            this.collisionDrag = collision.collisionDrag();
            this.coefficientOfRestitution = collision.coefficientOfRestitution();
            this.expireOnContact = collision.expireOnContact();
            this.hasPhysics = true;
            float radius = collision.collisionRadius();
            // TODO(L2): 1.20 {@code AABB#inflate(r)} → 1.7.10 {@code AxisAlignedBB#expand(r,r,r)}（同名语义）。
            this.setAabb(this.boundingBox.expand(radius, radius, radius));
        } else {
            this.hasCollision = false;
            this.collisionDrag = 0;
            this.coefficientOfRestitution = 1;
            this.expireOnContact = false;
        }

        // 光照：组件存在时使用世界光照，否则全亮
        this.environmentLighting = definition.findComponent(ParticleAppearanceLighting.class) != null;

        // 方块过期组件
        this.expireIfInBlocks = definition.findComponent(ParticleExpireIfInBlocks.class);
        this.expireIfNotInBlocks = definition.findComponent(ParticleExpireIfNotInBlocks.class);

        // RenderType
        ParticleDescription desc = definition.getDescription();
        this.renderType = MolangWorldParticleRenderType.get(desc.getMaterial(), desc.getTexture());

        // 锚点跟随：记录出生锚点（发射器 worldTransform），并把当前世界坐标反算为锚点局部坐标；
        // 渲染时用当前 worldTransform 重新变换回世界坐标，位置与朝向均跟随锚点
        this.followAnchor = emitter.isFollowAnchor();
        if (followAnchor) {
            Matrix4f spawnTransform = new Matrix4f(emitter.getWorldTransform()).invert();
            Vector4f local = new Vector4f(particleData.x, particleData.y, particleData.z, 1.0f);
            spawnTransform.transform(local);
            this.localX = local.x;
            this.localY = local.y;
            this.localZ = local.z;
        } else {
            this.localX = 0;
            this.localY = 0;
            this.localZ = 0;
        }
    }

    /**
     * 1.7.10 的 {@code boundingBox} 由 {@code Entity} 持有且以 {@code setBounds} 原地修改，
     * 这里封装成上游的 {@code setBoundingBox(AABB)} 语义。
     */
    private void setAabb(AxisAlignedBB box) {
        this.boundingBox.setBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        this.syncPosFromAabb();
    }

    /** 与 1.20 的 {@code setLocationFromBoundingbox()} 等价：用包围盒回写实体坐标。 */
    private void syncPosFromAabb() {
        this.posX = (this.boundingBox.minX + this.boundingBox.maxX) / 2.0D;
        this.posY = this.boundingBox.minY;
        this.posZ = (this.boundingBox.minZ + this.boundingBox.maxZ) / 2.0D;
    }

    /**
     * TODO(L2): 1.7.10 的 {@code Entity#boundingBox} 是 final 字段（{@code AxisAlignedBB} 可变），
     * 因此「平移包围盒」要靠 {@code setBounds} 原地改，不能像 1.20 那样换成新对象。
     */
    private void offsetAabb(double dx, double dy, double dz) {
        this.boundingBox.setBounds(
            this.boundingBox.minX + dx,
            this.boundingBox.minY + dy,
            this.boundingBox.minZ + dz,
            this.boundingBox.maxX + dx,
            this.boundingBox.maxY + dy,
            this.boundingBox.maxZ + dz);
    }

    private static Matrix3f createBirthMotionMap(float scale) {
        Camera camera = Camera.getMainCamera();
        Matrix4f worldToCamera = new Matrix4f().rotationX((float) Math.toRadians(camera.getXRot()))
            .rotateY((float) Math.toRadians(camera.getYRot() + 180f))
            .rotateZ(CameraStateCache.getCameraRollRadians());
        Matrix4f cameraToWorld = new Matrix4f(worldToCamera).invert();
        return new Matrix3f(
            cameraToWorld.scale(scale, scale, 1.0f)
                .mul(worldToCamera));
    }

    @Override
    public void onUpdate() {
        // TODO(L2): 1.20 的 {@code Particle#tick()} → 1.7.10 {@link EntityFX#onUpdate()}
        // （EntityFX.onUpdate 会先做粒子自身的年龄/重力/移动；本类完全接管移动逻辑，故不复用基类实现）。
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.oRoll = this.roll;

        float dt = 1f / 20f;

        // 每帧更新 collision.enabled
        if (collisionComponent != null && collisionComponent.enabled() != null) {
            this.hasCollision = collisionComponent.enabled()
                .evaluate(
                    emitter.getMolang()
                        .getContext())
                != 0;
        }

        float savedX = particleData.x, savedY = particleData.y, savedZ = particleData.z;

        emitter.updateSingleParticle(particleData, dt);
        applyFovAcceleration(dt);

        particleData.x = savedX;
        particleData.y = savedY;
        particleData.z = savedZ;

        this.motionX = particleData.vx / 20f;
        this.motionY = particleData.vy / 20f;
        this.motionZ = particleData.vz / 20f;

        this.move(this.motionX, this.motionY, this.motionZ);

        particleData.vx = (float) this.motionX * 20f;
        particleData.vy = (float) this.motionY * 20f;
        particleData.vz = (float) this.motionZ * 20f;

        particleData.x = (float) this.posX;
        particleData.y = (float) this.posY;
        particleData.z = (float) this.posZ;

        this.roll = (float) Math.toRadians(particleData.rotation);

        this.particleRed = particleData.r;
        this.particleGreen = particleData.g;
        this.particleBlue = particleData.b;
        this.particleAlpha = particleData.a;

        // 方块过期检测
        if (particleData.alive) {
            checkBlockExpiration();
        }

        if (!particleData.alive) {
            emitter.fireParticleExpirationEvents(particleData);
            this.setDead();
        }

        this.particleAge++;
    }

    private void applyFovAcceleration(float dt) {
        if (birthMotionMap == null || dynamicMotion == null || dynamicMotion.linearAcceleration() == null) {
            return;
        }
        MolangExpression[] acceleration = dynamicMotion.linearAcceleration();
        var context = emitter.getMolang()
            .getContext();
        float ax = (float) acceleration[0].evaluate(context);
        float ay = (float) acceleration[1].evaluate(context);
        float az = (float) acceleration[2].evaluate(context);
        float mx = birthMotionMap.m00() * ax + birthMotionMap.m10() * ay + birthMotionMap.m20() * az;
        float my = birthMotionMap.m01() * ax + birthMotionMap.m11() * ay + birthMotionMap.m21() * az;
        float mz = birthMotionMap.m02() * ax + birthMotionMap.m12() * ay + birthMotionMap.m22() * az;
        float drag = dynamicMotion.linearDragCoefficient() == null ? 1.0f
            : Math.max(
                0.0f,
                1.0f - (float) dynamicMotion.linearDragCoefficient()
                    .evaluate(context) * dt);
        particleData.vx += (mx - ax) * drag * dt;
        particleData.vy += (my - ay) * drag * dt;
        particleData.vz += (mz - az) * drag * dt;
    }

    /**
     * 对应 1.20 {@code Particle#move(double, double, double)}。
     * <p>
     * TODO(L2): 1.20 用 {@code Entity.collideBoundingBox(...)} 做扫掠碰撞；1.7.10 没有这个静态方法，
     * 此处用 1.7.10 {@code Entity#moveEntity} 内部的同一套算法：
     * {@code World#getCollidingBoundingBoxes} + {@code AxisAlignedBB#calculateXOffset/YOffset/ZOffset}，
     * 碰撞后的 {@code collisionDrag / coefficientOfRestitution} 反弹语义与上游一致。
     */
    public void move(double x, double y, double z) {
        if (!hasCollision) {
            if (x != 0.0 || y != 0.0 || z != 0.0) {
                this.setAabb(this.boundingBox.getOffsetBoundingBox(x, y, z));
            }
            return;
        }

        double origX = x, origY = y, origZ = z;

        // 速度上限
        double velSqr = x * x + y * y + z * z;
        if (this.hasPhysics && (x != 0.0 || y != 0.0 || z != 0.0) && velSqr < 10000.0) {
            // 三轴分别解算并各自应用一次，顺序同 1.7.10 {@code Entity#moveEntity}（Y → X → Z）。
            // 上游 {@code Entity.collideBoundingBox} 是纯扫掠、不改包围盒，位移在解算后统一应用一次；
            // 因此这里不能既 offsetAabb 又在末尾再套一次 getOffsetBoundingBox(x,y,z)。
            AxisAlignedBB box = this.boundingBox.getOffsetBoundingBox(x, y, z);
            List<?> collisions = this.worldObj.getCollidingBoundingBoxes(this, box);
            for (Object o : collisions) {
                y = ((AxisAlignedBB) o).calculateYOffset(this.boundingBox, y);
            }
            this.offsetAabb(0.0, y, 0.0);

            box = this.boundingBox.getOffsetBoundingBox(x, 0.0, z);
            collisions = this.worldObj.getCollidingBoundingBoxes(this, box);
            for (Object o : collisions) {
                x = ((AxisAlignedBB) o).calculateXOffset(this.boundingBox, x);
            }
            this.offsetAabb(x, 0.0, 0.0);

            box = this.boundingBox.getOffsetBoundingBox(0.0, 0.0, z);
            collisions = this.worldObj.getCollidingBoundingBoxes(this, box);
            for (Object o : collisions) {
                z = ((AxisAlignedBB) o).calculateZOffset(this.boundingBox, z);
            }
            this.offsetAabb(0.0, 0.0, z);

            if (origX != x) {
                this.motionX = -Mth.sign(motionX) * (Math.abs(motionX) - collisionDrag / 20f)
                    * coefficientOfRestitution;
            }
            if (origY != y) {
                this.motionY *= -coefficientOfRestitution;
            }
            if (origZ != z) {
                this.motionZ = -Mth.sign(motionZ) * (Math.abs(motionZ) - collisionDrag / 20f)
                    * coefficientOfRestitution;
            }
        } else if (x != 0.0 || y != 0.0 || z != 0.0) {
            // 未走碰撞解算（速度超限）：整段位移一次应用。
            this.offsetAabb(x, y, z);
        }

        this.syncPosFromAabb();

        // 检测碰撞发生
        boolean collided = origX != x || origY != y || origZ != z;
        if (collided) {
            this.onGround = origY != y && origY < 0.0;

            // 触发碰撞事件
            float speed = (float) Math.sqrt(
                particleData.vx * particleData.vx + particleData.vy * particleData.vy
                    + particleData.vz * particleData.vz);
            emitter.fireCollisionEvents(particleData, speed);

            if (expireOnContact) {
                this.setDead();
            }
        }
    }

    /**
     * TODO(L2): 1.20 的 {@code ParticleEngine} 会回调 {@code render(VertexConsumer, Camera, partialTicks)}；
     * 1.7.10 的 {@code EffectRenderer} 只回调
     * {@code renderParticle(Tessellator, partialTicks, rotX, rotZ, rotYZ, rotXY, rotXZ)}，
     * 而同一图层的所有粒子共享一次 Tessellator 会话、无法逐粒子换纹理（见类注释）。因此这里留空，
     * 实际绘制由 {@link WorldEmitterManager#renderWorldParticles(float)} 用
     * {@link #render(VertexConsumer, Camera, float)} 完成。
     */
    @Override
    public void renderParticle(Tessellator tessellator, float partialTicks, float rotX, float rotZ, float rotYZ,
        float rotXY, float rotXZ) {
        // no-op：见方法注释
    }

    /**
     * 1.7.10 {@code EffectRenderer} 的图层 3（自定义图层）：本类不由原版管线绘制。
     */
    @Override
    public int getFXLayer() {
        return 3;
    }

    private static final Quaternionf QUATERNION = new Quaternionf();
    private static final Vector3f TEMP_VEC = new Vector3f();
    private static final Vector3f TEMP_VEC2 = new Vector3f();
    private static final Vector3f TEMP_VEC3 = new Vector3f();
    private static final Vector3f AXIS_X = new Vector3f();
    private static final Vector3f AXIS_Y = new Vector3f();
    private static final Vector4f TEMP_VEC4 = new Vector4f();
    private static final Matrix4f TEMP_MAT = new Matrix4f();
    private static final Matrix3f TEMP_MAT3 = new Matrix3f();

    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 camPos = camera.getPosition();
        float cx, cy, cz;
        if (followAnchor) {
            // 锚点跟随：用发射器当前 worldTransform（含旋转+平移）把局部坐标变换回世界坐标
            emitter.getWorldTransform()
                .transformPosition(localX, localY, localZ, FOLLOW_POS);
            cx = FOLLOW_POS.x - (float) camPos.x();
            cy = FOLLOW_POS.y - (float) camPos.y();
            cz = FOLLOW_POS.z - (float) camPos.z();
        } else {
            cx = (float) (Mth.lerp(partialTicks, this.prevPosX, this.posX) - camPos.x());
            cy = (float) (Mth.lerp(partialTicks, this.prevPosY, this.posY) - camPos.y());
            cz = (float) (Mth.lerp(partialTicks, this.prevPosZ, this.posZ) - camPos.z());
        }

        // 构建朝向四元数
        QUATERNION.identity();
        applyFacingMode(QUATERNION, camera);

        // 应用粒子自旋
        float currentRoll = Mth.lerp(partialTicks, this.oRoll, this.roll);
        if (currentRoll != 0) {
            QUATERNION.rotateZ(currentRoll);
        }

        // 粒子尺寸：第一人称世界粒子在绘制时按当前 FOV 补偿，避免旧/新粒子尺寸混杂。
        float scale = particleData.spawnScale;
        if (fovCompensatedWorldParticle) {
            scale *= CameraStateCache.getWorldParticleScale() / birthFovScale;
        }
        float hw = particleData.width * scale;
        float hh = particleData.height * scale;

        // UV
        float u0 = particleData.u0;
        float v0 = particleData.v0;
        float u1 = particleData.u1;
        float v1 = particleData.v1;

        int light = getBrightnessForRender(partialTicks);

        AXIS_X.set(1, 0, 0)
            .rotate(QUATERNION)
            .mul(hw);
        AXIS_Y.set(0, 1, 0)
            .rotate(QUATERNION)
            .mul(hh);

        float ax = AXIS_X.x(), ay = AXIS_X.y(), az = AXIS_X.z();
        float bx = AXIS_Y.x(), by = AXIS_Y.y(), bz = AXIS_Y.z();

        float x0 = cx - ax - bx;
        float y0 = cy - ay - by;
        float z0 = cz - az - bz;
        float x1 = cx - ax + bx;
        float y1 = cy - ay + by;
        float z1 = cz - az + bz;
        float x2 = cx + ax + bx;
        float y2 = cy + ay + by;
        float z2 = cz + az + bz;
        float x3 = cx + ax - bx;
        float y3 = cy + ay - by;
        float z3 = cz + az - bz;

        // TODO(L2): 上游此处先尝试 Sodium 的 VertexBufferWriter 快速路径；
        // 1.7.10 无 Sodium，SodiumParticleVertexWriter.tryRender 恒返回 false，故该分支已移除。

        renderVertex(buffer, x0, y0, z0, u0, v1, light);
        renderVertex(buffer, x1, y1, z1, u0, v0, light);
        renderVertex(buffer, x2, y2, z2, u1, v0, light);
        renderVertex(buffer, x3, y3, z3, u1, v1, light);
    }

    private void renderVertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z)
            .uv(u, v)
            .color(particleRed, particleGreen, particleBlue, particleAlpha)
            // TODO(L2): 上游 {@code uv2(int)} → mcshim {@code lightmap(int)}（同一语义，见 VertexConsumer 注释）。
            .lightmap(light)
            .endVertex();
    }

    // 应用朝向模式
    private void applyFacingMode(Quaternionf q, Camera camera) {
        switch (faceCameraMode) {
            case ROTATE_XYZ -> q.set(camera.rotation());
            case ROTATE_Y -> {
                Quaternionf camRot = camera.rotation();
                q.set(0, camRot.y, 0, camRot.w)
                    .normalize();
            }
            case LOOKAT_XYZ -> {
                // 真正的 lookat：从粒子位置看向摄像机
                Vec3 camPos = camera.getPosition();
                TEMP_VEC
                    .set((float) (camPos.x - this.posX), (float) (camPos.y - this.posY), (float) (camPos.z - this.posZ))
                    .normalize(); // toCamera
                TEMP_VEC2.set(0, 1, 0); // up
                TEMP_VEC2.cross(TEMP_VEC, TEMP_VEC3); // right = up × toCamera
                TEMP_VEC3.normalize();
                TEMP_VEC.cross(TEMP_VEC3, TEMP_VEC2); // correctedUp = toCamera × right
                q.setFromNormalized(
                    TEMP_MAT3
                        .set(
                            TEMP_VEC3.x,
                            TEMP_VEC2.x,
                            TEMP_VEC.x,
                            TEMP_VEC3.y,
                            TEMP_VEC2.y,
                            TEMP_VEC.y,
                            TEMP_VEC3.z,
                            TEMP_VEC2.z,
                            TEMP_VEC.z)
                        .invert());
            }
            case LOOKAT_Y -> {
                // lookat 但只保留 Y 轴旋转分量
                Vec3 camPos = camera.getPosition();
                TEMP_VEC
                    .set((float) (camPos.x - this.posX), (float) (camPos.y - this.posY), (float) (camPos.z - this.posZ))
                    .normalize(); // toCamera
                TEMP_VEC2.set(0, 1, 0); // up
                TEMP_VEC2.cross(TEMP_VEC, TEMP_VEC3); // right = up × toCamera
                TEMP_VEC3.normalize();
                TEMP_VEC.cross(TEMP_VEC3, TEMP_VEC2); // correctedUp = toCamera × right
                q.setFromNormalized(
                    TEMP_MAT3
                        .set(
                            TEMP_VEC3.x,
                            TEMP_VEC2.x,
                            TEMP_VEC.x,
                            TEMP_VEC3.y,
                            TEMP_VEC2.y,
                            TEMP_VEC.y,
                            TEMP_VEC3.z,
                            TEMP_VEC2.z,
                            TEMP_VEC.z)
                        .invert());
                q.x = 0;
                q.z = 0;
                q.normalize();
            }
            case LOOKAT_DIRECTION -> {
                // X 轴沿速度方向（facingDirection），然后绕 X 轴旋转使面片尽量朝向摄像机
                TEMP_VEC.set(particleData.vx, particleData.vy, particleData.vz);
                float len = TEMP_VEC.length();
                if (len > 0.0001f) {
                    TEMP_VEC.normalize();
                    // 从 X 轴 (1,0,0) 旋转到速度方向
                    TEMP_VEC2.set(1, 0, 0);
                    MathUtil.setFromUnitVectors(TEMP_VEC2, TEMP_VEC, q);
                    // 把摄像机方向变换到粒子局部空间，计算绕 X 轴的旋转使面片朝向摄像机
                    Vec3 camPos = camera.getPosition();
                    TEMP_VEC4
                        .set(
                            (float) (camPos.x - this.posX),
                            (float) (camPos.y - this.posY),
                            (float) (camPos.z - this.posZ),
                            0)
                        .mul(
                            TEMP_MAT.rotation(q)
                                .invert());
                    q.rotateX((float) Mth.atan2(-TEMP_VEC4.y, TEMP_VEC4.z));
                } else {
                    q.set(camera.rotation());
                }
            }
            // TODO(L2): mcshim Mth 未提供 PI/HALF_PI 常量，直接用 float 常量（数值同 1.20 Mth）。
            case DIRECTION_X -> q.rotationXYZ(0, (float) (Math.PI / 2.0), 0);
            case DIRECTION_Y -> q.rotationXYZ((float) (Math.PI / 2.0), (float) Math.PI, 0);
            case DIRECTION_Z -> q.identity();
            default -> q.set(camera.rotation());
        }
    }

    /**
     * 粒子使用的 RenderType（由 {@link WorldEmitterManager} 分组渲染用）。
     * TODO(L2): 1.20 是 {@code @Override Particle#getRenderType()}；1.7.10 无该接口方法，
     * 这里改成普通 getter，方法名保持上游一致。
     */
    public RenderType getRenderType() {
        return renderType;
    }

    /**
     * 获取关联的 ParticleInstance 数据。
     */
    public ParticleInstance getParticleData() {
        return particleData;
    }

    /**
     * 获取关联的发射器实例。
     */
    public ParticleEmitterInstance getEmitter() {
        return emitter;
    }

    /**
     * TODO(L2): 1.20 覆写 {@code Particle#getLightColor(float)}；1.7.10 对应物是
     * {@code Entity#getBrightnessForRender(float)}（返回打包的 lightmap 坐标）。
     */
    @Override
    public int getBrightnessForRender(float partialTick) {
        return environmentLighting ? super.getBrightnessForRender(partialTick) : LightTexture.FULL_BRIGHT;
    }

    /**
     * 检查粒子是否因所在方块而过期。
     */
    private void checkBlockExpiration() {
        if (expireIfInBlocks == null && expireIfNotInBlocks == null) return;

        // TODO(L2): 1.20 的 {@code BlockPos.containing} + {@code BuiltInRegistries.BLOCK.getKey} →
        // 1.7.10 的整数坐标 + {@code Block.blockRegistry.getNameForObject}。
        int bx = MathHelper.floor_double(this.posX);
        int by = MathHelper.floor_double(this.posY);
        int bz = MathHelper.floor_double(this.posZ);
        Block block = this.worldObj.getBlock(bx, by, bz);
        String blockId = String.valueOf(Block.blockRegistry.getNameForObject(block));

        if (expireIfInBlocks != null && expireIfInBlocks.blocks()
            .contains(blockId)) {
            particleData.alive = false;
        }
        if (expireIfNotInBlocks != null && !expireIfNotInBlocks.blocks()
            .contains(blockId)) {
            particleData.alive = false;
        }
    }
}
