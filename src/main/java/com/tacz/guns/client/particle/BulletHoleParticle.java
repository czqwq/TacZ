package com.tacz.guns.client.particle;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.particles.BulletHoleOption;

/**
 * 弹孔粒子（贴在被打中的方块面上、逐渐变暗并淡出）。
 *
 * <p>
 * Author: Forked from MrCrayfish, continued by Timeless devs
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/particle/BulletHoleParticle.java}
 *
 * <h2>1.7.10 基类替换</h2>
 * 上游继承 {@code net.minecraft.client.particle.TextureSheetParticle}（1.14+ 才有）。
 * 1.7.10 的粒子体系是 {@link EntityFX} + {@code EffectRenderer#addEffect(EntityFX)}，自定义粒子只能继承
 * {@link EntityFX} 并覆写
 * {@link EntityFX#renderParticle(Tessellator, float, float, float, float, float, float)}；1.7.10 没有
 * {@code ParticleProvider} / {@code ParticleOptions} / {@code ParticleEngine}。字段对应关系（同一语义换名字）：
 *
 * <pre>
 *   上游 TextureSheetParticle      →  1.7.10 EntityFX
 *   lifetime                       →  particleMaxAge
 *   age                            →  particleAge
 *   tick()                         →  onUpdate()
 *   remove()                       →  setDead()
 *   alpha                          →  particleAlpha
 *   rCol / gCol / bCol             →  particleRed / particleGreen / particleBlue
 *   hasPhysics = false             →  noClip = true
 *   gravity                        →  particleGravity
 *   sprite (TextureAtlasSprite)    →  particleIcon (IIcon)
 *   getRenderType() = TERRAIN_SHEET→  getFXLayer() = 1（1 = 方块图集，见 EffectRenderer#renderParticles）
 *   render(VertexConsumer, Camera, partialTicks)
 *                                  →  renderParticle(Tessellator, partialTicks, rotX, rotXZ, rotZ, rotYZ, rotXY)
 * </pre>
 *
 * <h2>降级点（3 处，均已在方法处标注 TODO(L2)）</h2>
 * <ul>
 * <li>{@code Direction#getRotation()} → 自建 {@link #rotationForDirection(ForgeDirection)}；</li>
 * <li>{@code BlockModelShaper#getTexture(BlockState, Level, BlockPos)} → {@code Block#getIcon(side, meta)}；</li>
 * <li>{@code Camera#getPosition()} → {@code renderViewEntity} 插值位置。</li>
 * </ul>
 */
public class BulletHoleParticle extends EntityFX {

    private final ForgeDirection direction;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private int uOffset;
    private int vOffset;
    private float textureDensity;
    private boolean validAttachment = true;
    /** 上游 {@code TextureSheetParticle#quadSize}：四边形半边长（格）。1.7.10 的 {@link EntityFX} 没有这个字段。 */
    private float quadSize = 0.05F;

    /**
     * 上游 {@code BulletHoleParticle(ClientLevel, double, double, double, Direction, BlockPos, String, String,
     * String, boolean)}。
     *
     * @param world                客户端世界（上游 {@code ClientLevel}）
     * @param direction            命中面朝向（上游 {@code Direction}）
     * @param blockX/blockY/blockZ 被命中方块坐标（上游 {@code BlockPos}）
     */
    public BulletHoleParticle(World world, double x, double y, double z, ForgeDirection direction, int blockX,
        int blockY, int blockZ, String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        super(world, x, y, z);
        this.direction = direction;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        IIcon sprite = this.getSprite(blockX, blockY, blockZ);
        if (sprite != null) {
            this.setSprite(sprite);
        }
        this.particleMaxAge = this.getLifetimeFromConfig(world);
        // 上游 hasPhysics = false / gravity = 0
        this.noClip = true;
        this.particleGravity = 0.0F;
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;

        Block block = world.getBlock(blockX, blockY, blockZ);
        if (block == ModBlocks.TARGET || shouldRemove() || !isBulletHoleEnabled(ammoId, gunId, gunDisplayId)) {
            this.validAttachment = false;
            this.particleMaxAge = 1;
            this.particleAlpha = 0.0F;
        }
        TimelessAPI.getGunDisplay(new ResourceLocation(gunDisplayId), new ResourceLocation(gunId))
            .ifPresent(gunIndex -> {
                float[] gunTracerColor = gunIndex.getTracerColor();
                if (gunTracerColor != null) {
                    this.particleRed = gunTracerColor[0];
                    this.particleGreen = gunTracerColor[1];
                    this.particleBlue = gunTracerColor[2];
                } else {
                    TimelessAPI.getClientAmmoIndex(new ResourceLocation(ammoId))
                        .ifPresent(ammoIndex -> {
                            float[] ammoTracerColor = ammoIndex.getTracerColor();
                            this.particleRed = ammoTracerColor[0];
                            this.particleGreen = ammoTracerColor[1];
                            this.particleBlue = ammoTracerColor[2];
                        });
                }
            });
        this.particleAlpha = 0.9F;
    }

    public static boolean isBulletHoleEnabled(String ammoId, String gunId, String gunDisplayId) {
        try {
            var gunDisplay = TimelessAPI.getGunDisplay(new ResourceLocation(gunDisplayId), new ResourceLocation(gunId));
            if (gunDisplay.isPresent() && gunDisplay.get()
                .getBulletHoleEnabled() != null) {
                return gunDisplay.get()
                    .getBulletHoleEnabled();
            }
            return TimelessAPI.getClientAmmoIndex(new ResourceLocation(ammoId))
                .map(ammoIndex -> ammoIndex.isBulletHoleEnabled())
                .orElse(true);
        } catch (Exception ignored) {
            return true;
        }
    }

    private int getLifetimeFromConfig(World world) {
        int configLife = RenderConfig.BULLET_HOLE_PARTICLE_LIFE.get();
        if (configLife <= 1) {
            return configLife;
        }
        return configLife + world.rand.nextInt(configLife / 2);
    }

    /**
     * 上游 {@code setSprite(TextureAtlasSprite)} 的 1.7.10 形态（{@link IIcon}）。
     * 逻辑完全一致：随机挑 16×16 网格里的一个子格，按图标尺寸算密度。
     */
    private void setSprite(IIcon sprite) {
        this.particleIcon = sprite;
        this.uOffset = this.rand.nextInt(16);
        this.vOffset = this.rand.nextInt(16);
        // 材质应该都是方形
        this.textureDensity = (sprite.getMaxU() - sprite.getMinU()) / 16.0F;
    }

    /**
     * 上游 {@code getSprite(BlockPos)} 的 1.7.10 形态。
     *
     * <p>
     * TODO(L2): 上游 BulletHoleParticle.java:114 用
     * {@code Minecraft#getBlockRenderer().getBlockModelShaper().getTexture(BlockState, Level, BlockPos)}
     * （按 1.20 的方块模型烘焙结果取贴图，能正确处理朝向与连接纹理）；1.7.10 没有方块模型烘焙器，
     * 也没有 {@code MissingTextureAtlasSprite}，此处改用 {@code Block#getIcon(side, metadata)} 近似：
     * side 取命中面序号，方块为空气时返回 {@code null}（此时粒子本就不可见）。
     */
    private IIcon getSprite(int blockX, int blockY, int blockZ) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return null;
        }
        Block block = world.getBlock(blockX, blockY, blockZ);
        if (block == null || block.isAir(world, blockX, blockY, blockZ)) {
            return null;
        }
        int metadata = world.getBlockMetadata(blockX, blockY, blockZ);
        // 1.7.10 的 side 序号：0=-Y 1=+Y 2=-Z 3=+Z 4=-X 5=+X（与 ForgeDirection 的 ordinal 一致）
        int side = this.direction == null ? 1 : this.direction.ordinal();
        if (side < 0 || side > 5) {
            side = 1;
        }
        return block.getIcon(side, metadata);
    }

    /** 上游 {@code getU0()}：图标 U0 + 随机子格偏移。 */
    private float getU0() {
        return this.particleIcon == null ? 0.0F : this.particleIcon.getMinU() + this.uOffset * this.textureDensity;
    }

    /** 上游 {@code getV0()}。 */
    private float getV0() {
        return this.particleIcon == null ? 0.0F : this.particleIcon.getMinV() + this.vOffset * this.textureDensity;
    }

    /** 上游 {@code getU1()}。 */
    private float getU1() {
        return this.getU0() + this.textureDensity;
    }

    /** 上游 {@code getV1()}。 */
    private float getV1() {
        return this.getV0() + this.textureDensity;
    }

    /**
     * 上游 {@code tick()}。1.7.10 的 {@link EntityFX#onUpdate()} 会做重力 / 位移 / 阻尼，
     * 而弹孔是原地贴花，所以这里只推进年龄并做存活判定，<b>不</b>调用 {@code super.onUpdate()}。
     */
    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (!this.validAttachment) {
            this.setDead();
            return;
        }
        if (++this.particleAge >= this.particleMaxAge) {
            this.setDead();
            return;
        }
        if (shouldRemove()) {
            this.setDead();
        }
    }

    /**
     * 上游 {@code render(VertexConsumer, Camera, partialTicks)} 的 1.7.10 形态：用 {@link Tessellator}
     * 直接写 4 个顶点。
     */
    @Override
    public void renderParticle(Tessellator tessellator, float partialTicks, float rotX, float rotXZ, float rotZ,
        float rotYZ, float rotXY) {
        // particleMaxAge <= 0 时下面的淡出公式会出现 0/0；上游同样没有防护，这里补一条纯安全判定
        if (!this.validAttachment || this.particleIcon == null || this.particleMaxAge <= 0) {
            return;
        }
        double[] camera = cameraPosition(partialTicks);
        float particleX = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) partialTicks - camera[0]);
        float particleY = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) partialTicks - camera[1]);
        float particleZ = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) partialTicks - camera[2]);

        // 上游 direction.getRotation()：把局部 +Y 轴转到命中面法线（四边形平铺在局部 XZ 平面、y 抬 0.01）
        Quaternionf quaternion = rotationForDirection(this.direction);
        Vector3f[] points = new Vector3f[] {
            // Y 值稍微大一点点，防止 z-fight
            new Vector3f(-1.0F, 0.01F, -1.0F), new Vector3f(-1.0F, 0.01F, 1.0F), new Vector3f(1.0F, 0.01F, 1.0F),
            new Vector3f(1.0F, 0.01F, -1.0F) };
        float scale = this.quadSize;

        for (int i = 0; i < 4; ++i) {
            Vector3f vector3f = points[i];
            vector3f.rotate(quaternion);
            vector3f.mul(scale);
            vector3f.add(particleX, particleY, particleZ);
        }

        // UV 坐标
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();

        // 0 - 30 tick 内，从 15 亮度到 0 亮度
        int light = Math.max(15 - this.particleAge / 2, 0);
        int lightColor = LightTexture.pack(light, light);

        // 颜色，逐渐渐变到 0 0 0，也就是黑色
        float colorPercent = light / 15.0f;
        float red = this.particleRed * colorPercent;
        float green = this.particleGreen * colorPercent;
        float blue = this.particleBlue * colorPercent;

        // 透明度，逐渐变成 0，也就是透明
        double threshold = RenderConfig.BULLET_HOLE_PARTICLE_FADE_THRESHOLD.get() * this.particleMaxAge;
        float fade = 1.0f - (float) (Math.max(this.particleAge - threshold, 0) / (this.particleMaxAge - threshold));
        float alphaFade = this.particleAlpha * fade;

        // 上游 buffer.vertex(...).uv(...).color(...).uv2(lightColor).endVertex()
        tessellator.setBrightness(lightColor);
        tessellator.setColorRGBA_F(red, green, blue, alphaFade);
        tessellator.addVertexWithUV(points[0].x(), points[0].y(), points[0].z(), u1, v1);
        tessellator.addVertexWithUV(points[1].x(), points[1].y(), points[1].z(), u1, v0);
        tessellator.addVertexWithUV(points[2].x(), points[2].y(), points[2].z(), u0, v0);
        tessellator.addVertexWithUV(points[3].x(), points[3].y(), points[3].z(), u0, v1);
    }

    /**
     * 上游 {@code getRenderType() = ParticleRenderType.TERRAIN_SHEET} → 1.7.10 的
     * {@code getFXLayer() == 1}（{@code EffectRenderer#renderParticles} 的 case 1 绑定方块图集）。
     */
    @Override
    public int getFXLayer() {
        return 1;
    }

    private boolean shouldRemove() {
        Block block = this.worldObj.getBlock(this.blockX, this.blockY, this.blockZ);
        if (block == null || block.isAir(this.worldObj, this.blockX, this.blockY, this.blockZ)) {
            return true;
        }
        // 阻止弹孔在与方块不构成有效附着时继续渲染
        block.setBlockBoundsBasedOnState(this.worldObj, this.blockX, this.blockY, this.blockZ);
        AxisAlignedBB blockBoundingBox = block
            .getCollisionBoundingBoxFromPool(this.worldObj, this.blockX, this.blockY, this.blockZ);
        if (blockBoundingBox == null) {
            return true;
        }
        boolean intersects = blockBoundingBox.intersectsWith(
            AxisAlignedBB.getBoundingBox(
                this.posX - 0.1,
                this.posY - 0.1,
                this.posZ - 0.1,
                this.posX + 0.1,
                this.posY + 0.1,
                this.posZ + 0.1));
        return !intersects;
    }

    /**
     * 1.7.10 自建的 {@code Direction#getRotation()} 等价物。
     *
     * <p>
     * 上游四边形是「平铺在局部 XZ 平面、沿局部 +Y 抬 0.01」的贴花，因此旋转必须把局部 +Y 轴转到命中面法线，
     * 才能让贴花贴在方块面上并向外抬 0.01 防 z-fight。本方法按这个几何要求逐方向构造四元数：
     *
     * <pre>
     *   UP    (0,1,0)  → 单位阵
     *   DOWN  (0,-1,0) → rotX(π)
     *   NORTH (0,0,-1) → rotX(-π/2)
     *   SOUTH (0,0,1)  → rotX(π/2)
     *   WEST  (-1,0,0) → rotZ(π/2)
     *   EAST  (1,0,0)  → rotZ(-π/2)
     * </pre>
     *
     * TODO(L2): 上游 BulletHoleParticle.java:160 用 {@code Direction#getRotation()}（1.20.1 原版方法，
     * 返回 {@code Quaternionf}）；1.7.10 的 {@link ForgeDirection} 无此方法，此处按「局部 +Y → 面法线」的
     * 几何要求自建等价旋转。纹理的绕轴滚转角与上游可能相差 90°，弹孔是各向同性污渍，视觉上无差别。
     */
    private static Quaternionf rotationForDirection(ForgeDirection direction) {
        if (direction == null) {
            return new Quaternionf();
        }
        switch (direction) {
            case DOWN:
                return new Quaternionf().rotationX((float) Math.PI);
            case NORTH:
                return new Quaternionf().rotationX(-(float) Math.PI / 2.0F);
            case SOUTH:
                return new Quaternionf().rotationX((float) Math.PI / 2.0F);
            case WEST:
                return new Quaternionf().rotationZ((float) Math.PI / 2.0F);
            case EAST:
                return new Quaternionf().rotationZ(-(float) Math.PI / 2.0F);
            case UP:
            default:
                return new Quaternionf();
        }
    }

    /**
     * 相机世界坐标（按 partialTicks 插值）。
     *
     * <p>
     * TODO(L2): 上游 BulletHoleParticle.java:156 从 {@code Camera#getPosition()} 读相机位置；1.7.10 的
     * 等价量是 {@code ActiveRenderInfo.objectX/Y/Z}，但那是实体渲染阶段才更新的，粒子 pass 里可能滞后一帧。
     * 此处改用 {@code renderViewEntity} 的插值位置——与 {@code EffectRenderer#renderParticles} 写
     * {@code EntityFX.interpPos*} 的公式完全相同。
     */
    static double[] cameraPosition(float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity view = minecraft.renderViewEntity;
        if (view == null) {
            return new double[] { EntityFX.interpPosX, EntityFX.interpPosY, EntityFX.interpPosZ };
        }
        return new double[] { view.lastTickPosX + (view.posX - view.lastTickPosX) * (double) partialTicks,
            view.lastTickPosY + (view.posY - view.lastTickPosY) * (double) partialTicks,
            view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * (double) partialTicks };
    }

    /**
     * 上游的 {@code BulletHoleParticle.Provider implements ParticleProvider<BulletHoleOption>}。
     *
     * <p>
     * 1.7.10 没有粒子 provider 注册表（{@code RegisterParticleProvidersEvent} 是 1.14+ 的），粒子一律直接
     * {@code new} 出来交给 {@code EffectRenderer#addEffect}。保留本类是为了让上游形状的调用点
     * （{@code client/init/ParticleFactoryRegistry}）仍能 {@code new BulletHoleParticle.Provider()}，
     * 并提供「由 {@link BulletHoleOption} 造粒子」的入口。
     */
    public static class Provider {

        public Provider() {}

        /**
         * 上游 {@code createParticle(BulletHoleOption, ClientLevel, double, double, double, double, double,
         * double)} 的 1.7.10 形态：1.7.10 的粒子不接受初速度（弹孔本来也是静止贴花），故去掉速度参数。
         */
        public BulletHoleParticle createParticle(BulletHoleOption option, World world, double x, double y, double z) {
            return new BulletHoleParticle(
                world,
                x,
                y,
                z,
                option.getDirection(),
                option.getX(),
                option.getY(),
                option.getZ(),
                option.getAmmoId(),
                option.getGunId(),
                option.getGunDisplayId(),
                option.isIncendiary());
        }
    }
}
