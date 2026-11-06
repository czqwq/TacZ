package com.tacz.guns.entity;

import static com.tacz.guns.api.GunProperties.RuntimeOnly.BULLET_FRICTION;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.BULLET_GRAVITY;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.BULLET_LIFE;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.EXPLODE_ENABLED;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.EXPLOSION_DAMAGE;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.EXPLOSION_DELAY;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.EXPLOSION_DESTROYS_BLOCK;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.EXPLOSION_KNOCKBACK;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.EXPLOSION_RADIUS;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.IGNITE_BLOCK;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.IGNITE_ENTITY;
import static com.tacz.guns.api.GunProperties.RuntimeOnly.IGNITE_ENTITY_TIME;
import static com.tacz.guns.api.event.common.GunDamageSourcePart.ARMOR_PIERCING;
import static com.tacz.guns.api.event.common.GunDamageSourcePart.NON_ARMOR_PIERCING;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.google.common.collect.Lists;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.api.entity.KnockBackModifier;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.init.ModDamageTypes;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageBulletHitBlock;
import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import com.tacz.guns.network.message.event.ServerMessageGunKill;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.DamageModifier;
import com.tacz.guns.resource.modifier.custom.ExplosionModifier;
import com.tacz.guns.resource.modifier.custom.IgniteModifier;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.Ignite;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.ExplodeUtil;
import com.tacz.guns.util.TacHitResult;
import com.tacz.guns.util.block.BlockRayTrace;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 搬运自上游 1.20.1 {@code entity/EntityKineticBullet.java}（动能武器子弹实体，子弹弹道与命中判定核心）。
 *
 * <p>
 * 1.7.10 适配（本类差异较多，逐项记录）：
 * <ul>
 * <li>{@code Projectile/EntityType} → {@link Entity} + {@code EntityRegistry} 注册（见 init/ModEntities）；
 * {@code tick()} → {@link #onUpdate()}；{@code discard()} → {@code setDead()}；</li>
 * <li>运动/位置：{@code getDeltaMovement/setDeltaMovement} → {@code motionX/Y/Z}；
 * {@code position()/setPos} → {@code Vec3.createVectorHelper(...)/setPosition}；
 * {@code getYRot/getXRot} → {@code rotationYaw/rotationPitch}；{@code xRotO/yRotO} → {@code prevRotationPitch/Yaw}；</li>
 * <li>方块射线：上游 {@code util/block/BlockRayTrace#rayTraceBlocks(Level, ClipContext)} →
 * 1.7.10 用同名等价实现 {@link com.tacz.guns.util.block.BlockRayTrace#rayTraceBlocks(World, Vec3, Vec3)}
 * （保留上游 DDA 算法 + {@code ClipContext.Block.COLLIDER}/{@code Fluid.NONE} 语义 +
 * {@code tacz:bullet_ignore} 标签的阻塞替代 + {@code AmmoBlockConfigRead#canPassThrough} 可穿透配置）；</li>
 * <li>伤害源：上游 {@code ModDamageTypes.Sources.bullet(...)}（1.20 自定义 damage_type 数据包）→
 * 1.7.10 无数据驱动伤害类型，改用 {@code init/ModDamageTypes.Sources#bullet(...)} 的
 * {@code BulletDamageSource}（{@code EntityDamageSourceIndirect} + {@code setDamageBypassesArmor()}），
 * 普通/穿甲两段伤害源分开（穿甲段真的无视护甲），死亡消息为 {
 * 
 * @code death.attack.tacz.bullet}；魔法伤害对末影人硬编码处理；</li>
 *       <li>{@code EntityHitResult/BlockHitResult} → {@link TacHitResult} + {@link MovingObjectPosition}；</li>
 *       <li>多段实体：上游 {@code MaybeMultipartEntity}（{@code PartEntity#getParent}）→ 本类私有
 *       {@code MaybeMultipartEntity}，1.7.10 只识别原版 {@link net.minecraft.entity.boss.EntityDragonPart}
 *       （{@code entityDragonObj} 为本体）；伤害施加 hitPart、无敌帧/击退/爆炸/事件用 core（见 {@code onHitEntity} 注释）；</li>
 *       <li>{@code EntityType 标签}（use_magic_damage_on 等 data/tacz/tags）→ 1.7.10 无实体标签，硬编码末影人；</li>
 *       <li>spawn 数据：{@code FriendlyByteBuf} → 1.7.10 {@code net.minecraft.network.PacketBuffer} +
 *       {@link ByteBufUtils}（Forge 的 {@link IEntityAdditionalSpawnData} 通道）；</li>
 *       <li>{@code getPersistentData()} → {@code getEntityData()}（1.7.10 Forge 的实体持久 NBT）；</li>
 *       <li>客户端弹道粒子（{@code AmmoParticleSpawner}）属 L2，见 {@code entity/EntityBulletClientHooks}。</li>
 *       </ul>
 *
 *       <p>
 *       <b>NBT / 同步字段</b>：spawn 数据字段顺序与上游保持一致（1.7.10 两侧都是本模组，格式自洽）。
 */
public class EntityKineticBullet extends Entity implements IEntityAdditionalSpawnData {

    public static final String TRACER_COLOR_OVERRIDER_KEY = GunMod.MOD_ID + ":tracer_override";
    public static final String TRACER_SIZE_OVERRIDER_KEY = GunMod.MOD_ID + ":tracer_size";

    private static final ExplosionData DEFAULT_EXPLOSION_DATA = new ExplosionData(false, 0, 0, false, 30, false);

    @Nullable
    private Entity owner;
    private ResourceLocation ammoId = DefaultAssets.EMPTY_AMMO_ID;
    private int life = 200;
    @Deprecated
    private float speed = 1;
    private float gravity = 0;
    private float friction = 0.01F;
    private LinkedList<DistanceDamagePair> damageAmount = Lists.newLinkedList();
    private float distanceAmount = 0;
    private float knockback = 0;
    private boolean explosion = false;
    private boolean igniteEntity = false;
    private boolean igniteBlock = false;
    private int igniteEntityTime = 2;
    private float explosionDamage = 3;
    private float explosionRadius = 3;
    private int explosionDelayCount = Integer.MAX_VALUE;
    private boolean explosionKnockback = false;
    private boolean explosionDestroyBlock = false;
    private float damageModifier = 1;
    private int pierce = 1;
    private Vec3 startPos;
    private boolean isTracerAmmo;
    // 客户端曳光弹数据
    private float cameraXRot;
    private float cameraYRot;
    private Vector3f firstPersonRenderOffset;
    private ResourceLocation gunId = DefaultAssets.EMPTY_GUN_ID;
    private ResourceLocation gunDisplayId = DefaultAssets.DEFAULT_GUN_DISPLAY_ID;
    private float armorIgnore;
    private float headShot;
    private float shotDamageMultiplier = 1f;

    public EntityKineticBullet(World worldIn) {
        super(worldIn);
        this.setSize(0.0625F, 0.0625F);
        this.noClip = true;
    }

    public EntityKineticBullet(World worldIn, double x, double y, double z) {
        this(worldIn);
        this.setPosition(x, y, z);
    }

    public EntityKineticBullet(World worldIn, EntityLivingBase throwerIn, ItemStack gunItem, ResourceLocation ammoId,
        ResourceLocation gunId, ResourceLocation gunDisplayId, boolean isTracerAmmo, GunData gunData,
        BulletData bulletData) {
        this(worldIn, throwerIn.posX, throwerIn.posY + (double) throwerIn.getEyeHeight() - 0.1D, throwerIn.posZ);
        this.owner = throwerIn;
        this.gunId = Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_GUN_ID);
        AttachmentCacheProperty cacheProperty = Objects.requireNonNull(
            IGunOperator.fromLivingEntity(throwerIn)
                .getCacheProperty());
        float armorIgnore = modifyProperty(
            GunProperties.ARMOR_IGNORE,
            Float.class,
            cacheProperty.getCache(GunProperties.ARMOR_IGNORE));
        float headshot = modifyProperty(
            GunProperties.HEADSHOT_MULTIPLIER,
            Float.class,
            cacheProperty.getCache(GunProperties.HEADSHOT_MULTIPLIER));
        float knockback = modifyProperty(
            GunProperties.KNOCKBACK,
            Float.class,
            cacheProperty.getCache(GunProperties.KNOCKBACK));
        this.armorIgnore = Mth.clamp(armorIgnore, 0f, 1f);
        this.headShot = Math.max(headshot, 0f);
        this.knockback = Math.max(knockback, 0f);
        this.ammoId = Objects.requireNonNullElse(ammoId, DefaultAssets.EMPTY_AMMO_ID);
        float lifeSecond = modifyProperty(BULLET_LIFE, Float.class, bulletData.getLifeSecond());
        this.life = Mth.clamp((int) (lifeSecond * 20), 1, Integer.MAX_VALUE);
        this.gravity = Mth
            .clamp(modifyProperty(BULLET_GRAVITY, Float.class, bulletData.getGravity()), 0f, Float.MAX_VALUE);
        this.friction = Mth
            .clamp(modifyProperty(BULLET_FRICTION, Float.class, bulletData.getFriction()), 0f, Float.MAX_VALUE);
        // 点燃
        Ignite ignite = cacheProperty.getCache(IgniteModifier.ID);
        this.igniteEntity = modifyProperty(
            IGNITE_ENTITY,
            Boolean.class,
            bulletData.getIgnite()
                .isIgniteEntity() || ignite.isIgniteEntity());
        this.igniteEntityTime = Math
            .max(modifyProperty(IGNITE_ENTITY_TIME, Integer.class, bulletData.getIgniteEntityTime()), 0);
        this.igniteBlock = modifyProperty(
            IGNITE_BLOCK,
            Boolean.class,
            bulletData.getIgnite()
                .isIgniteBlock() || ignite.isIgniteBlock());
        this.damageAmount = cacheProperty.getCache(DamageModifier.ID);
        this.distanceAmount = modifyProperty(
            GunProperties.EFFECTIVE_RANGE,
            Float.class,
            cacheProperty.getCache(GunProperties.EFFECTIVE_RANGE));
        int pierce = modifyProperty(GunProperties.PIERCE, Integer.class, cacheProperty.getCache(GunProperties.PIERCE));
        this.pierce = Mth.clamp(pierce, 1, Integer.MAX_VALUE);
        ExplosionData explosionData = Objects
            .requireNonNullElse(cacheProperty.getCache(ExplosionModifier.ID), DEFAULT_EXPLOSION_DATA);
        this.explosion = modifyProperty(EXPLODE_ENABLED, Boolean.class, explosionData.isExplode());
        if (this.explosion) {
            float explosionDamage = modifyProperty(EXPLOSION_DAMAGE, Float.class, explosionData.getDamage());
            float explosionRadius = modifyProperty(EXPLOSION_RADIUS, Float.class, explosionData.getRadius());
            this.explosionDamage = (float) Mth
                .clamp(explosionDamage * SyncConfig.DAMAGE_BASE_MULTIPLIER.get(), 0, Float.MAX_VALUE);
            this.explosionRadius = Mth.clamp(explosionRadius, 0, Float.MAX_VALUE);
            this.explosionKnockback = modifyProperty(EXPLOSION_KNOCKBACK, Boolean.class, explosionData.isKnockback());
            int delayTickCount = (int) (modifyProperty(EXPLOSION_DELAY, Float.class, explosionData.getDelay()) * 20);
            if (delayTickCount < 0) {
                delayTickCount = Integer.MAX_VALUE;
            }
            this.explosionDestroyBlock = AmmoConfig.EXPLOSIVE_AMMO_DESTROYS_BLOCK.get()
                && modifyProperty(EXPLOSION_DESTROYS_BLOCK, Boolean.class, explosionData.isDestroyBlock());
            this.explosionDelayCount = Math.max(delayTickCount, 1);
        }
        // 子弹初始位置重置（上游 EntityKineticBullet.java:218-221）：
        // 取「上一 tick 位置 + 当前 tick 位置」的中点 + 眼睛高度 —— 即射手在本 tick 移动的一半处开火。
        // 1.7.10 的等价字段是 lastTickPosX/Y/Z（World#updateEntity:2282-2284 在 onUpdate 前写入），
        // getEyeHeight() 与上游 getEyeY()-getY() 同义（相对 posY）。
        double posX = throwerIn.lastTickPosX + (throwerIn.posX - throwerIn.lastTickPosX) / 2.0;
        double posY = throwerIn.lastTickPosY + (throwerIn.posY - throwerIn.lastTickPosY) / 2.0
            + throwerIn.getEyeHeight();
        double posZ = throwerIn.lastTickPosZ + (throwerIn.posZ - throwerIn.lastTickPosZ) / 2.0;
        this.setPosition(posX, posY, posZ);
        this.startPos = Vec3.createVectorHelper(this.posX, this.posY, this.posZ);
        this.isTracerAmmo = isTracerAmmo;
        this.gunDisplayId = Objects.requireNonNullElse(gunDisplayId, DefaultAssets.DEFAULT_GUN_DISPLAY_ID);
    }

    public void applyShotgunDamageSpread(int bulletCount) {
        if (bulletCount > 1) {
            this.damageModifier = 1f / bulletCount;
        }
    }

    public void setShotDamageMultiplier(float multiplier) {
        this.shotDamageMultiplier = Math.max(multiplier, 0f);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.onBulletTick();
        if (this.worldObj.isRemote) {
            // L2 客户端弹道粒子：由客户端注入的钩子生成（common 侧不做任何客户端类引用）
            EntityBulletClientHooks.spawnAmmoParticle(this);
        }
        // 子弹模型的旋转与抛物线
        double x = this.motionX;
        double y = this.motionY;
        double z = this.motionZ;
        double horizontalDistance = Math.sqrt(x * x + z * z);
        this.rotationYaw = (float) Math.toDegrees(Mth.atan2(x, z));
        this.rotationPitch = (float) Math.toDegrees(Mth.atan2(y, horizontalDistance));
        if (this.prevRotationPitch == 0.0F && this.prevRotationYaw == 0.0F) {
            this.prevRotationYaw = this.rotationYaw;
            this.prevRotationPitch = this.rotationPitch;
        }
        this.rotationPitch = lerpRotation(this.prevRotationPitch, this.rotationPitch);
        this.rotationYaw = lerpRotation(this.prevRotationYaw, this.rotationYaw);
        // 子弹位置更新（上游 EntityKineticBullet.java:277-280 `this.setPos(nextPosX, nextPosY, nextPosZ)`）
        //
        // 1.7.10 依据：{@code Entity#onUpdate()} 只调用 {@code onEntityUpdate()}
        // （build/rfg/minecraft-src/java/net/minecraft/entity/Entity.java:384-387），
        // 而 {@code onEntityUpdate()} 仅做 prev 位置/角度的记录与火焰/传送门处理，
        // **不会**按 motion 移动实体 —— 位移必须由子类自己 {@code moveEntity} / {@code setPosition}
        // （对照原版 {@code EntityArrow#onUpdate}：{@code this.setPosition(this.posX, this.posY, this.posZ)}）。
        // 早期移植误以为基类会自动按 motion 移动，导致子弹永远停在枪口：
        // 每次 tick 都从同一原点做射线，打不中远处目标，也不飞到墙边。
        double nextPosX = this.posX + x;
        double nextPosY = this.posY + y;
        double nextPosZ = this.posZ + z;
        this.setPosition(nextPosX, nextPosY, nextPosZ);
        float friction = this.friction;
        float gravity = this.gravity;
        if (this.isInWater()) {
            // 上游 {@code tick()} :277-284 是 `this.level().addParticle(ParticleTypes.BUBBLE, ...)`：
            // 1.20 的 {@code Level#addParticle} 在服务端是**空实现**（{@code Level.java:404,407}），只有
            // {@code ClientLevel} 覆写后交给 {@code LevelRenderer}（{@code ClientLevel.java:597}）
            // ⇒ 气泡只在**各客户端本地**生成（每颗子弹在各客户端都会自己 tick）。
            // 1.7.10 的 {@code World#spawnParticle} 是同一套分流：客户端走 {@code WorldClient} 的
            // {@code RenderGlobal}（build/rfg/.../World.java:1457-1461 遍历 worldAccesses），服务端只到
            // {@code WorldManager}（无转发）。因此这里显式按 isRemote 分流，与上游逐位等价：
            // <b>不要</b>改成 {@code spawnServerParticles()} 广播 —— 那会让每个客户端「广播一份 + 本地一份」，
            // 比上游多一倍粒子。
            if (this.worldObj.isRemote) {
                for (int i = 0; i < 4; i++) {
                    this.worldObj.spawnParticle(
                        "bubble",
                        nextPosX - x * 0.25F,
                        nextPosY - y * 0.25F,
                        nextPosZ - z * 0.25F,
                        x,
                        y,
                        z);
                }
            }
            friction = 0.4F;
            gravity *= 0.6F;
        }
        this.motionX *= (1 - friction);
        this.motionY *= (1 - friction);
        this.motionZ *= (1 - friction);
        this.motionY -= gravity;
        if (this.ticksExisted >= this.life - 1) {
            this.setDead();
        }
    }

    protected void onBulletTick() {
        if (this.worldObj.isRemote) {
            return;
        }
        // 延迟爆炸判定
        if (this.explosion) {
            if (this.explosionDelayCount > 0) {
                this.explosionDelayCount--;
            } else {
                ExplodeUtil.createExplosion(
                    this.owner,
                    this,
                    this.explosionDamage,
                    this.explosionRadius,
                    this.explosionKnockback,
                    this.explosionDestroyBlock,
                    Vec3.createVectorHelper(this.posX, this.posY, this.posZ));
                this.setDead();
                return;
            }
        }
        Vec3 startVec = Vec3.createVectorHelper(this.posX, this.posY, this.posZ);
        Vec3 endVec = startVec.addVector(this.motionX, this.motionY, this.motionZ);
        // 子弹的方块碰撞检测
        // 上游：BlockRayTrace.rayTraceBlocks(level, new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, this))
        // （tmp/TACZ-1.20.1/.../EntityKineticBullet.java:314）
        // 1.7.10 的等价实现已经存在：{@link com.tacz.guns.util.block.BlockRayTrace#rayTraceBlocks}——它保留了上游
        // DDA 逐格算法、ClipContext.Block.COLLIDER 语义（只命中 getCollisionBoundingBoxFromPool != null 的方块）、
        // ClipContext.Fluid.NONE 语义，以及 tacz:bullet_ignore 标签的 1.7.10 退化（铁栏杆/栅栏/栅栏门/树叶）
        // 和 AmmoBlockConfigRead#canPassThrough 可穿透配置。
        // 早期移植直接调 World#rayTraceBlocks（凡非空气/液体都命中），会 ①被高草/花/火把等无碰撞箱方块挡住
        // ②打不穿铁栏杆/栅栏/树叶（上游可穿透）③忽略可穿透方块配置。
        MovingObjectPosition blockResult = BlockRayTrace.rayTraceBlocks(this.worldObj, startVec, endVec);
        if (blockResult != null && blockResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            endVec = blockResult.hitVec;
        }

        List<EntityResult> hitEntities = null;
        if (this.pierce <= 1 || this.explosion) {
            EntityResult entityResult = EntityUtil.findEntityOnPath(this, startVec, endVec);
            if (entityResult != null) {
                hitEntities = Collections.singletonList(entityResult);
            }
        } else {
            hitEntities = EntityUtil.findEntitiesOnPath(this, startVec, endVec);
        }
        if (hitEntities != null && !hitEntities.isEmpty()) {
            EntityResult[] hitEntityResult = hitEntities.toArray(new EntityResult[0]);
            // 按距离子弹发射位置的远近升序排序
            for (int i = 0; (i < this.pierce || i < 1) && i < (hitEntityResult.length - 1); i++) {
                int k = i;
                for (int j = i + 1; j < hitEntityResult.length; j++) {
                    if (hitEntityResult[j].getHitPos()
                        .distanceTo(startVec)
                        < hitEntityResult[k].getHitPos()
                            .distanceTo(startVec)) {
                        k = j;
                    }
                }
                EntityResult t = hitEntityResult[i];
                hitEntityResult[i] = hitEntityResult[k];
                hitEntityResult[k] = t;
            }
            for (EntityResult entityResult : hitEntityResult) {
                TacHitResult tacHitResult = new TacHitResult(entityResult);
                this.onHitEntity(tacHitResult, startVec, endVec);
                this.pierce--;
                if (this.pierce < 1 || this.explosion) {
                    this.setDead();
                    return;
                }
            }
        }
        if (blockResult != null) {
            this.onHitBlock(blockResult, startVec, endVec);
        }
    }

    public void shoot(double pitch, double yaw, float pVelocity, Vector2d vector2d) {
        Vector3d left = new Vector3d(vector2d.x, vector2d.y, 8);
        left.rotateX(pitch * Mth.DEG_TO_RAD);
        left.rotateY(-yaw * Mth.DEG_TO_RAD);
        Vector3d normalized = left.normalize();
        // vector2d 路径的散布已由 vector2d 自身携带（上游同位置也不加噪声）⇒ inaccuracy 传 0
        applyShoot(this, normalized.x, normalized.y, normalized.z, pVelocity, 0.0F);
    }

    public void shootFromRotation(Entity pShooter, float pX, float pY, float pZ, float pVelocity, Vector2d vector2d) {
        this.shoot(pX, pY, pVelocity, vector2d);
        this.motionX += pShooter.motionX;
        this.motionY += pShooter.onGround ? 0.0D : pShooter.motionY;
        this.motionZ += pShooter.motionZ;
    }

    public void shootFromRotation(Entity pShooter, float pX, float pY, float pZ, float pVelocity, float inaccuracy) {
        // 上游 1.20.1 {@code Projectile#shootFromRotation}(Projectile.java:133-139)：
        // f5 = -sin(yRot)·cos(xRot)、f6 = -sin((xRot + roll)·deg)、f7 = cos(yRot)·cos(xRot)，
        // 再交给 {@code Projectile#shoot}（归一化/噪声/缩放/朝向都在那里，见 applyShoot）。
        float f = -MathHelper.sin(pY * (float) Math.PI / 180.0F) * MathHelper.cos(pX * (float) Math.PI / 180.0F);
        float f1 = -MathHelper.sin((pX + pZ) * (float) Math.PI / 180.0F);
        float f2 = MathHelper.cos(pY * (float) Math.PI / 180.0F) * MathHelper.cos(pX * (float) Math.PI / 180.0F);
        applyShoot(this, f, f1, f2, pVelocity, inaccuracy);
        // 继承射击者本 tick 的速度（上游 Projectile.java:138-139）
        this.motionX += pShooter.motionX;
        this.motionY += pShooter.onGround ? 0.0D : pShooter.motionY;
        this.motionZ += pShooter.motionZ;
    }

    /**
     * 上游 {@code Projectile#shoot(double,double,double,float,float)}（{@code Projectile.java:120-131}）的
     * 1.7.10 等价实现：方向 → {@code Vec3#normalize}（长度 &lt; 1e-4 归零）→ 每轴加
     * {@code random.triangle(0, 0.0172275·inaccuracy)} → 乘初速 → 写 motion 与 rotation/prev。
     *
     * <p>
     * <b>噪声分布必须与上游一致</b>：1.20 用 {@code RandomSource#triangle}
     * （{@code RandomSource.java:55-57}：{@code mean + deviation * (nextDouble() - nextDouble())}），
     * 系数 {@code 0.0172275}。1.7.10 {@code EntityThrowable} 那套
     * {@code nextGaussian() * 0.0075 * inaccuracy} 是**另一种分布**（高斯，系数也差 2.3 倍）：
     * 曾经照搬在这里，于是「真实枪械路径」（{@code ModernKineticGunItem#doBulletSpread} → 本方法）与
     * 「api 默认路径」（{@code AbstractGunItem#doBulletSpread}，已按上游 triangle 复刻）散布不一致。
     * 两条路径现在用同一组公式与系数。
     *
     * @param target         被写入 motion/rotation 的子弹
     * @param dirX/dirY/dirZ 未归一化的方向（上游 {@code shoot} 的三个入参）
     * @param velocity       初速（上游 {@code f}）
     * @param inaccuracy     散布系数（上游 {@code f1}；0 = 无散布）
     */
    private static void applyShoot(EntityKineticBullet target, double dirX, double dirY, double dirZ, float velocity,
        float inaccuracy) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        // 对应 Vec3#normalize()：长度 < 1e-4 返回零向量（上游同样行为；直接除会得到 NaN）
        double vx;
        double vy;
        double vz;
        if (length < 1.0E-4D) {
            vx = 0.0D;
            vy = 0.0D;
            vz = 0.0D;
        } else {
            vx = dirX / length;
            vy = dirY / length;
            vz = dirZ / length;
        }
        // RandomSource#triangle(0, deviation) = deviation * (nextDouble() - nextDouble())；
        // 1.7.10 无 RandomSource，用世界随机源（World#rand），与 AmmoParticleSpawner 等处的替代一致。
        // deviation == 0 时直接跳过：既不产生噪声，也不白耗世界随机数（共用的 World#rand 会影响其它系统）。
        double deviation = 0.0172275D * (double) inaccuracy;
        if (deviation != 0.0D) {
            Random random = target.worldObj.rand;
            vx += deviation * (random.nextDouble() - random.nextDouble());
            vy += deviation * (random.nextDouble() - random.nextDouble());
            vz += deviation * (random.nextDouble() - random.nextDouble());
        }
        target.motionX = vx * velocity;
        target.motionY = vy * velocity;
        target.motionZ = vz * velocity;
        double horizontalDistance = Math.sqrt(target.motionX * target.motionX + target.motionZ * target.motionZ);
        target.rotationYaw = (float) Math.toDegrees(Mth.atan2(target.motionX, target.motionZ));
        target.rotationPitch = (float) Math.toDegrees(Mth.atan2(target.motionY, horizontalDistance));
        target.prevRotationYaw = target.rotationYaw;
        target.prevRotationPitch = target.rotationPitch;
    }

    protected void onHitEntity(TacHitResult result, Vec3 startVec, Vec3 endVec) {
        Entity hitEntity = result.entityHit;
        if (hitEntity == null || EntityUtil.isBulletDamageIgnored(hitEntity)) {
            return;
        }
        if (hitEntity instanceof ITargetEntity targetEntity) {
            DamageSource source = DamageSource.causeThrownDamage(this, this.owner);
            targetEntity.onProjectileHit(this, result, source, this.getDamage(result.hitVec));
            return;
        }
        Entity entity = hitEntity;
        Entity attackerEntity = this.owner;
        EntityLivingBase attacker = attackerEntity instanceof EntityLivingBase living ? living : null;
        boolean headshot = result.isHeadshot();
        float damage = this.getDamage(result.hitVec);
        float headShotMultiplier = Math.max(this.headShot, 0);
        Pair<DamageSource, DamageSource> sources = this.createDamageSources(entity);
        EntityHurtByGunEvent.Pre preEvent = new EntityHurtByGunEvent.Pre(
            this,
            entity,
            attacker,
            this.gunId,
            this.gunDisplayId,
            damage,
            sources,
            headshot,
            headShotMultiplier,
            Side.SERVER);
        boolean cancelled = MinecraftForge.EVENT_BUS.post(preEvent);
        if (cancelled) {
            return;
        }
        entity = preEvent.getHurtEntity();
        if (entity == null || EntityUtil.isBulletDamageIgnored(entity)) {
            return;
        }
        // 上游 :400-405 只对原始命中实体派发一次 ITargetEntity（在 Pre 事件之前），此处不再二次派发。
        // r2 修复（对照上游 MaybeMultipartEntity，1.7.10 多段实体只有原版 EntityDragonPart）：
        // 上游 :411/:427 把命中实体拆成 parts（hitPart, core）；后续「击退/清无敌帧/爆炸/Post/Kill 事件」
        // 一律用 core，只有 **伤害** 施加到 hitPart（:601/:605）。本移植此前全程把 part 当核心：
        // ① part 不是 EntityLivingBase → 打末影龙等部件实体时 Hurt/Kill 事件、击杀特效、ServerMessageGunHurt/Kill 全丢失；
        // ② 无敌帧（1.7.10 的 hurtResistantTime）只清在 part 上，龙本体的 20 tick 无敌帧仍在
        // （EntityLivingBase.java:854-872：hurtResistantTime > max/2 时只补 lastDamage 差额）→ 穿甲段被吞；
        // ③ 击退 strength 设置在 part 上（非 living）→ 无效果；
        // ④ 爆炸前清无敌帧同样清错对象。
        MaybeMultipartEntity parts = MaybeMultipartEntity.of(entity);
        attacker = preEvent.getAttacker();
        ResourceLocation newGunId = preEvent.getGunId();
        damage = preEvent.getBaseAmount();
        sources = Pair.of(preEvent.getDamageSource(NON_ARMOR_PIERCING), preEvent.getDamageSource(ARMOR_PIERCING));
        headshot = preEvent.isHeadShot();
        headShotMultiplier = preEvent.getHeadshotMultiplier();
        // 点燃（上游 :435-441 对 preEvent 后的 hurtEntity 本体生效，部件实体也照此）
        if (this.igniteEntity && AmmoConfig.IGNITE_ENTITY.get()) {
            entity.setFire(this.igniteEntityTime);
            if (!this.worldObj.isRemote) {
                // 上游 serverLevel.sendParticles(ParticleTypes.LAVA, ...) 是**服务端广播**；
                // 1.7.10 的 World#spawnParticle 在服务端只走 IWorldAccess（WorldManager 不转发粒子）⇒ 无效果，
                // 必须用 WorldServer#func_147487_a（= 1.7.10 的 sendParticles）才会下发给附近客户端。
                this.spawnServerParticles("lava", entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ, 1);
            }
        }
        if (headshot) {
            damage *= headShotMultiplier;
        }
        if (parts.getCore() instanceof EntityLivingBase livingCore) {
            KnockBackModifier modifier = KnockBackModifier.fromLivingEntity(livingCore);
            modifier.setKnockBackStrength(this.knockback);
            // 必须是 try/finally：击退强度是**实体上的持久状态**（默认 -1 = 用原版 0.4F），
            // 一旦 tacAttackEntity 抛异常（模组事件监听器、附魔/药水回调都可能抛）而漏了复位，
            // 这个值会永久留在玩家/生物身上 —— 之后**所有**伤害源的击退都会被它改写。
            // 注：r3 起 `mixin/common/LivingEntityKnockbackMixin` 会真的消费这个值
            // （注入 `EntityLivingBase#knockBack` 的 0.4F 常量），所以这条从「隐形」变成「可见错误」，
            // 必须保证成对。
            try {
                tacAttackEntity(parts, damage, sources);
            } finally {
                modifier.resetKnockBackStrength();
            }
        } else {
            tacAttackEntity(parts, damage, sources);
        }
        if (this.explosion) {
            // 上游 :463 清的是 core 的 invulnerableTime（不是 part）
            parts.getCore().hurtResistantTime = 0;
            ExplodeUtil.createExplosion(
                this.owner,
                this,
                this.explosionDamage,
                this.explosionRadius,
                this.explosionKnockback,
                this.explosionDestroyBlock,
                result.hitVec);
        }
        // 击杀/受击事件同步（仅服务端）——上游 :467-480 用 parts.core() 判 living
        if (parts.getCore() instanceof EntityLivingBase livingCore && !this.worldObj.isRemote) {
            int attackerId = attacker == null ? 0 : attacker.getEntityId();
            // 「致死」判定：上游 EntityKineticBullet 用 `living.isDeadOrDying()`（1.20 语义 = isDead || health<=0）。
            // 1.7.10 没有该方法，且 {@code Entity#isDead} 只在实体真正被移除（{@code setDead()}，死亡动画结束后）
            // 才置位 —— 致死一击的瞬间还是 false（见 EntityLivingBase.java:996 只置 {@code dead} 字段，
            // 20 tick 后才 {@code setDead()}，EntityLivingBase.java:356-374）。
            // 因此必须用 {@code !isEntityAlive()}（= isDead || getHealth()<=0），否则
            // EntityKillByGunEvent / ServerMessageGunKill 永远不会触发（击杀特效、击杀统计全失效）。
            if (!livingCore.isEntityAlive()) {
                MinecraftForge.EVENT_BUS.post(
                    new EntityKillByGunEvent(
                        this,
                        livingCore,
                        attacker,
                        newGunId,
                        gunDisplayId,
                        damage,
                        sources,
                        headshot,
                        headShotMultiplier,
                        Side.SERVER));
                NetworkHandler.sendToDimension(
                    new ServerMessageGunKill(
                        this.getEntityId(),
                        livingCore.getEntityId(),
                        attackerId,
                        newGunId,
                        gunDisplayId,
                        damage,
                        headshot,
                        headShotMultiplier),
                    livingCore);
            } else {
                MinecraftForge.EVENT_BUS.post(
                    new EntityHurtByGunEvent.Post(
                        this,
                        livingCore,
                        attacker,
                        newGunId,
                        gunDisplayId,
                        damage,
                        sources,
                        headshot,
                        headShotMultiplier,
                        Side.SERVER));
                NetworkHandler.sendToDimension(
                    new ServerMessageGunHurt(
                        this.getEntityId(),
                        livingCore.getEntityId(),
                        attackerId,
                        newGunId,
                        gunDisplayId,
                        damage,
                        headshot,
                        headShotMultiplier),
                    livingCore);
            }
        }
    }

    protected void onHitBlock(MovingObjectPosition result, Vec3 startVec, Vec3 endVec) {
        int blockX = result.blockX;
        int blockY = result.blockY;
        int blockZ = result.blockZ;
        Vec3 hitVec = result.hitVec;
        Block hitBlock = this.worldObj.getBlock(blockX, blockY, blockZ);
        // 仅当子弹实际打中该方块时才处理（MISS 或未命中已排除）
        AmmoHitBlockEvent event = new AmmoHitBlockEvent(this.worldObj, result, hitBlock, this);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return;
        }
        // 1.7.10 没有 Block#onProjectileHit：上游 super.onHitBlock(result) 间接触发的标靶方块逻辑
        // （红石强度/复位计时/TargetHitTrigger/提示文本）由 TargetBlock#onBulletHit 显式承担。
        // 契约见 block/TargetBlock 的交付说明（D 节）；返回值仅表示是否命中靶面，这里忽略。
        if (hitBlock instanceof TargetBlock targetBlock) {
            Entity owner = this.owner;
            EntityPlayer ownerPlayer = owner instanceof EntityPlayer player ? player : null;
            targetBlock.onBulletHit(this.worldObj, result, ownerPlayer, this.getDamage(result.hitVec));
        }
        if (this.explosion) {
            ExplodeUtil.createExplosion(
                this.owner,
                this,
                this.explosionDamage,
                this.explosionRadius,
                this.explosionKnockback,
                this.explosionDestroyBlock,
                hitVec);
            this.setDead();
            return;
        }
        // 弹孔与点燃特效（客户端弹孔由 L2 客户端处理；这里只做服务端点燃 + 通知）
        int maxDistance = SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.get();
        double maxDistanceSqr = (double) maxDistance * (double) maxDistance;
        ServerMessageBulletHitBlock message = new ServerMessageBulletHitBlock(
            hitVec,
            ForgeDirection.getOrientation(result.sideHit),
            blockX,
            blockY,
            blockZ,
            this.ammoId.toString(),
            this.gunId.toString(),
            this.gunDisplayId.toString(),
            this.igniteEntity);
        for (EntityPlayer player : this.worldObj.playerEntities) {
            if (!(player instanceof EntityPlayerMP serverPlayer)) {
                continue;
            }
            if (maxDistance > 0 && player != this.owner
                && player.getDistanceSq(hitVec.xCoord, hitVec.yCoord, hitVec.zCoord) > maxDistanceSqr) {
                continue;
            }
            NetworkHandler.sendToClientPlayer(message, serverPlayer);
        }
        if (this.igniteBlock) {
            this.spawnServerParticles("lava", hitVec.xCoord, hitVec.yCoord, hitVec.zCoord, 1);
        }
        if (this.igniteBlock && AmmoConfig.IGNITE_BLOCK.get()) {
            ForgeDirection direction = ForgeDirection.getOrientation(result.sideHit);
            int offsetX = blockX + direction.offsetX;
            int offsetY = blockY + direction.offsetY;
            int offsetZ = blockZ + direction.offsetZ;
            if (this.worldObj.isAirBlock(offsetX, offsetY, offsetZ)) {
                this.worldObj.setBlock(offsetX, offsetY, offsetZ, Blocks.fire);
                this.spawnServerParticles(
                    "lava",
                    hitVec.xCoord - 1.0 + this.worldObj.rand.nextDouble() * 2.0,
                    hitVec.yCoord,
                    hitVec.zCoord - 1.0 + this.worldObj.rand.nextDouble() * 2.0,
                    4);
            }
        }
        this.setDead();
    }

    /**
     * 服务端广播粒子（上游 {@code ServerLevel#sendParticles}，{@code EntityKineticBullet.java:439/:507/:515}）。
     *
     * <p>
     * 1.7.10 的 {@code World#spawnParticle} 在服务端不转发给客户端（{@code WorldServer} 不注册转发用的
     * {@code IWorldAccess}），只有 {@code WorldServer#func_147487_a} 会打包 {@code S2APacketParticles}
     * 发给 16 格内的玩家（{@code WorldServer.java:1121-1139}）。上游 sendParticles 的下发半径是 32 格，
     * 这里按 1.7.10 原版半径，属平台差异。
     */
    private void spawnServerParticles(String particleName, double x, double y, double z, int count) {
        if (this.worldObj instanceof WorldServer worldServer) {
            // (name, x, y, z, count, xOffset, yOffset, zOffset, speed)
            worldServer.func_147487_a(particleName, x, y, z, count, 0, 0, 0, 0);
        }
    }

    /** 根据距离进行伤害衰减设计 */
    public float getDamage(Vec3 hitVec) {
        float base = 0;
        double playerDistance = hitVec.distanceTo(this.startPos);
        for (DistanceDamagePair pair : this.damageAmount) {
            float effectiveDistance = this.damageAmount.get(0)
                .getDistance() == pair.getDistance() ? this.distanceAmount : pair.getDistance();
            if (playerDistance < effectiveDistance) {
                float damage = pair.getDamage();
                base = Math.max(damage * this.damageModifier, 0F);
                break;
            }
        }
        float modifiedDamage = modifyProperty(GunProperties.DAMAGE, Float.class, base);
        return Math.max(modifiedDamage * this.shotDamageMultiplier, 0F);
    }

    private <T> T modifyProperty(GunProperty<?> prop, Class<T> type, T original) {
        return modifyProperty(prop.name(), type, original);
    }

    private <T> T modifyProperty(String id, Class<T> type, T original) {
        if (this.owner instanceof EntityLivingBase shooter) {
            ItemStack gun = shooter.getHeldItem();
            if (gun != null && gun.getItem() instanceof AbstractGunItem gunInterface
                && Objects.equals(this.gunId, gunInterface.getGunId(gun))) {
                ShooterDataHolder dataHolder = IGunOperator.fromLivingEntity(shooter)
                    .getDataHolder();
                return gunInterface.modifyProperty(dataHolder, gun, shooter, id, type, original);
            }
        }
        return original;
    }

    /**
     * 1.7.10：无数据驱动 damage_type，改用 {@link com.tacz.guns.init.ModDamageTypes.Sources} 的
     * {@code BulletDamageSource}（{@code EntityDamageSourceIndirect} 子类 + {@code setDamageBypassesArmor} 标志）。
     *
     * <p>
     * 上游 {@code createDamageSources}（{@code tmp/TACZ-1.20.1/.../EntityKineticBullet.java:575-590}）返回
     * {@code Pair<非穿甲伤害源, 穿甲伤害源>}：普通 {@code Sources.bullet(..., false)}、穿甲
     * {@code Sources.bullet(..., true)}（后者 {@code ignoreArmor=true}）。本方法必须保持「两段伤害源不同」，
     * 否则 {@link #tacAttackEntity} 的 {@code armorIgnore} 拆分（普通比例 + 穿甲比例）会退化成
     * 「两段都被护甲减免」。同时 {@link com.tacz.guns.event.EntityDamageEvent} 依赖
     * {@code ModDamageTypes.isBulletDamage(source)}（= {@code instanceof BulletDamageSource}）来应用
     * {@code BULLET_RESISTANCE} 属性——用原版 {@code causeThrownDamage} 会让该监听器恒不命中。
     *
     * <p>
     * 末影人：上游 {@code USE_MAGIC_DAMAGE_ON} 实体标签的硬编码降级（1.7.10 无实体标签），
     * 仍用间接魔法伤害（末影人免疫弹射物，必须魔法伤害才能造成伤害）。
     */
    private Pair<DamageSource, DamageSource> createDamageSources(Entity hitEntity) {
        if (hitEntity instanceof EntityEnderman) {
            // 上游 USE_MAGIC_DAMAGE_ON 标签：末影人对投掷物免疫，必须用间接魔法伤害
            DamageSource source = DamageSource.causeIndirectMagicDamage(this, this.owner);
            return Pair.of(source, source);
        }
        // 上游默认分支：directCause = this（子弹）；PRETEND_MELEE_DAMAGE_ON 标签在 1.7.10 无法表达，
        // 目前所有枪械数据都不带该标签，按默认走。
        return Pair.of(
            ModDamageTypes.Sources.bullet(this, this.owner, false),
            ModDamageTypes.Sources.bullet(this, this.owner, true));
    }

    /**
     * 1.7.10 版 {@code MaybeMultipartEntity}（上游 {@code EntityKineticBullet.java:384-394,411,427}）。
     *
     * <p>
     * 1.7.10 没有 Forge 的 {@code PartEntity}/{@code PartEntity#getParent()}，多段实体只有原版
     * {@link net.minecraft.entity.boss.EntityDragonPart}（其 {@code entityDragonObj} 才是本体，
     * 见 {@code EntityDragonPart.java:11,46-49}，且 {@code canBeCollidedWith()==true} ⇒ 子弹会先命中部件）。
     * 本类把「命中部件 → 本体」的映射收口到一处；非多段实体 hitPart == core。
     */
    private static final class MaybeMultipartEntity {

        private final Entity hitPart;
        private final Entity core;

        private MaybeMultipartEntity(Entity hitPart, Entity core) {
            this.hitPart = hitPart;
            this.core = core;
        }

        static MaybeMultipartEntity of(Entity hitPart) {
            if (hitPart instanceof EntityDragonPart part && part.entityDragonObj instanceof Entity parent) {
                return new MaybeMultipartEntity(hitPart, parent);
            }
            return new MaybeMultipartEntity(hitPart, hitPart);
        }

        Entity getHitPart() {
            return this.hitPart;
        }

        Entity getCore() {
            return this.core;
        }
    }

    /**
     * 对命中实体施加「普通 + 穿甲」两段伤害。
     *
     * <p>
     * 上游 {@code tacAttackEntity(MaybeMultipartEntity, ...)}（{@code EntityKineticBullet.java:592-606}）：
     * <b>伤害施加到 {@code parts.hitPart().hurt(...)}</b>，而两次伤害前清的是
     * {@code parts.core().invulnerableTime = 0}（让本体不被无敌帧吞掉第二段）。1.7.10 的等价物是
     * {@code EntityLivingBase#hurtResistantTime}（{@code EntityLivingBase.java:854-872}：
     * {@code hurtResistantTime > maxHurtResistantTime/2} 时只补 {@code lastDamage} 差额，即使伤害更低也直接吞掉）。
     */
    private void tacAttackEntity(MaybeMultipartEntity parts, float damage, Pair<DamageSource, DamageSource> sources) {
        Entity core = parts.getCore();
        Entity hitPart = parts.getHitPart();
        float armorDamagePercent = Mth.clamp(this.armorIgnore, 0.0F, 1.0F);
        float normalDamagePercent = 1 - armorDamagePercent;
        // 1.7.10 的「取消无敌时间」在 hurtResistantTime（1.20 是 invulnerableTime，上游 :599/:603）
        core.hurtResistantTime = 0;
        hitPart.attackEntityFrom(sources.getLeft(), damage * normalDamagePercent);
        core.hurtResistantTime = 0;
        hitPart.attackEntityFrom(sources.getRight(), damage * armorDamagePercent);
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        PacketBuffer buf = buffer instanceof PacketBuffer packetBuffer ? packetBuffer : new PacketBuffer(buffer);
        buf.writeFloat(this.rotationPitch);
        buf.writeFloat(this.rotationYaw);
        buf.writeDouble(this.motionX);
        buf.writeDouble(this.motionY);
        buf.writeDouble(this.motionZ);
        buf.writeInt(this.owner != null ? this.owner.getEntityId() : 0);
        ByteBufUtils.writeUTF8String(
            buf,
            Objects.requireNonNullElse(this.ammoId, DefaultAssets.EMPTY_AMMO_ID)
                .toString());
        buf.writeFloat(this.gravity);
        buf.writeBoolean(this.explosion);
        buf.writeBoolean(this.igniteEntity);
        buf.writeBoolean(this.igniteBlock);
        buf.writeFloat(this.explosionRadius);
        buf.writeFloat(this.explosionDamage);
        buf.writeInt(this.life);
        buf.writeFloat(this.speed);
        buf.writeFloat(this.friction);
        buf.writeInt(this.pierce);
        buf.writeBoolean(this.isTracerAmmo);
        ByteBufUtils.writeUTF8String(
            buf,
            Objects.requireNonNullElse(this.gunId, DefaultAssets.EMPTY_GUN_ID)
                .toString());
        ByteBufUtils.writeUTF8String(
            buf,
            Objects.requireNonNullElse(this.gunDisplayId, DefaultAssets.DEFAULT_GUN_DISPLAY_ID)
                .toString());
    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {
        PacketBuffer buf = additionalData instanceof PacketBuffer packetBuffer ? packetBuffer
            : new PacketBuffer(additionalData);
        this.rotationPitch = buf.readFloat();
        this.rotationYaw = buf.readFloat();
        this.motionX = buf.readDouble();
        this.motionY = buf.readDouble();
        this.motionZ = buf.readDouble();
        int ownerId = buf.readInt();
        Entity entity = this.worldObj != null ? this.worldObj.getEntityByID(ownerId) : null;
        if (entity != null) {
            this.owner = entity;
        }
        this.ammoId = new ResourceLocation(ByteBufUtils.readUTF8String(buf));
        this.gravity = buf.readFloat();
        this.explosion = buf.readBoolean();
        this.igniteEntity = buf.readBoolean();
        this.igniteBlock = buf.readBoolean();
        this.explosionRadius = buf.readFloat();
        this.explosionDamage = buf.readFloat();
        this.life = buf.readInt();
        this.speed = buf.readFloat();
        this.friction = buf.readFloat();
        this.pierce = buf.readInt();
        this.isTracerAmmo = buf.readBoolean();
        this.gunId = new ResourceLocation(ByteBufUtils.readUTF8String(buf));
        this.gunDisplayId = new ResourceLocation(ByteBufUtils.readUTF8String(buf));
    }

    private static float lerpRotation(float oldRotation, float newRotation) {
        while (newRotation - oldRotation < -180.0F) {
            newRotation += 360.0F;
        }
        while (newRotation - oldRotation >= 180.0F) {
            newRotation -= 360.0F;
        }
        return oldRotation + 0.2F * (newRotation - oldRotation);
    }

    @Nullable
    public Entity getOwner() {
        return this.owner;
    }

    public void setOwner(@Nullable Entity owner) {
        this.owner = owner;
    }

    public ResourceLocation getAmmoId() {
        return ammoId;
    }

    public ResourceLocation getGunId() {
        return gunId;
    }

    public ResourceLocation getGunDisplayId() {
        return gunDisplayId;
    }

    public boolean isTracerAmmo() {
        return isTracerAmmo;
    }

    public float getCameraYRot() {
        return cameraYRot;
    }

    public void setCameraYRot(float cameraYRot) {
        this.cameraYRot = cameraYRot;
    }

    public float getCameraXRot() {
        return cameraXRot;
    }

    public void setCameraXRot(float cameraXRot) {
        this.cameraXRot = cameraXRot;
    }

    public Vector3f getFirstPersonRenderOffset() {
        return firstPersonRenderOffset;
    }

    public void setFirstPersonRenderOffset(Vector3f originRenderOffset) {
        this.firstPersonRenderOffset = originRenderOffset;
    }

    public Optional<float[]> getTracerColorOverride() {
        NBTTagCompound pd = this.getEntityData();
        if (!pd.hasKey(TRACER_COLOR_OVERRIDER_KEY, Constants.NBT.TAG_INT_ARRAY)) {
            return Optional.empty();
        }
        int[] ints = pd.getIntArray(TRACER_COLOR_OVERRIDER_KEY);
        switch (ints.length) {
            case 0:
                return Optional.empty();
            case 1: {
                float albedo = ints[0] / 255F;
                return Optional.of(new float[] { albedo, albedo, albedo, 1 });
            }
            case 2: {
                float albedo = ints[0] / 255F;
                float alpha = ints[1] / 255F;
                return Optional.of(new float[] { albedo, albedo, albedo, alpha });
            }
            case 3: {
                float r = ints[0] / 255F;
                float g = ints[1] / 255F;
                float b = ints[2] / 255F;
                return Optional.of(new float[] { r, g, b, 1 });
            }
            default: {
                float r = ints[0] / 255F;
                float g = ints[1] / 255F;
                float b = ints[2] / 255F;
                float a = ints[3] / 255F;
                return Optional.of(new float[] { r, g, b, a });
            }
        }
    }

    public float getTracerSizeOverride() {
        NBTTagCompound pd = this.getEntityData();
        return pd.hasKey(TRACER_SIZE_OVERRIDER_KEY, Constants.NBT.TAG_ANY_NUMERIC)
            ? pd.getFloat(TRACER_SIZE_OVERRIDER_KEY)
            : 1;
    }

    /**
     * 1.7.10 {@code Entity} 的抽象方法（同步部分）：上游 1.20 用 {@code SynchedEntityData} 在构造期注册同步字段；
     * 1.7.10 由 {@code DataWatcher} 承担，本实体不依赖 DataWatcher —— 子弹的全部状态用
     * {@link IEntityAdditionalSpawnData} 在生成时一次带全（字段顺序见 {@link #writeSpawnData}），
     * 语义与上游 spawn packet 一致，因此本类不覆写 {@code Entity#getDescriptionPacket}，这里也只留空实现。
     */
    @Override
    protected void entityInit() {}

    /**
     * 1.7.10 {@code Entity} 的抽象方法：子弹不持久化（对应上游 {@code EntityType.Builder#noSave()}，
     * 子弹生命周期只有几十 tick），空实现仅用于满足抽象契约。
     */
    @Override
    public void writeEntityToNBT(NBTTagCompound tagCompound) {}

    @Override
    public void readEntityFromNBT(NBTTagCompound tagCompound) {}

    /**
     * 1.7.10 的 {@code EntityType.Builder#noSave()} 等价物。
     *
     * <p>
     * 1.7.10 没有「不存档实体」的注册标志：{@code AnvilChunkLoader.java:371} 对区块里的每个实体都调
     * {@code writeToNBTOptional}，为真就写进区块 NBT。而本类只序列化基类的 id/Pos/Motion
     * （{@link #writeEntityToNBT} 为空，对应上游 {@code noSave()} 的「不写额外字段」），读档后会变成
     * **没有弹药/伤害数据、却仍在飞的幽灵子弹**。返回 {@code false} 即「本实体不进存档」，
     * 与上游 {@code noSave()} 语义一致。
     */
    @Override
    public boolean writeToNBTOptional(NBTTagCompound tagCompound) {
        return false;
    }

    public static class EntityResult {

        private final Entity entity;
        private final Vec3 hitVec;
        private final boolean headshot;

        public EntityResult(Entity entity, Vec3 hitVec, boolean headshot) {
            this.entity = entity;
            this.hitVec = hitVec;
            this.headshot = headshot;
        }

        public Entity getEntity() {
            return this.entity;
        }

        public Vec3 getHitPos() {
            return this.hitVec;
        }

        public boolean isHeadshot() {
            return this.headshot;
        }
    }
}
