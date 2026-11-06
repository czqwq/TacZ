package com.tacz.guns.entity;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.advancement.TargetHitTrigger;
import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import com.tacz.guns.util.TacHitResult;

import cpw.mods.fml.relauncher.Side;

/**
 * 搬运自上游 1.20.1 {@code entity/TargetMinecart.java}（extends AbstractMinecart implements ITargetEntity）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code AbstractMinecart} → {@link EntityMinecart}；{@code EntityType} 静态注册表 → {@code EntityRegistry}
 * （见 init/ModEntities）；</li>
 * <li>{@code onProjectileHit(Entity, EntityHitResult, DamageSource, float)} →
 * {@link ITargetEntity#onProjectileHit(Entity, TacHitResult, DamageSource, float)}
 * （1.7.10 无 EntityHitResult，用等价物 {@link TacHitResult}）；</li>
 * <li>{@code player.displayClientMessage(Component, true)}（1.20 actionbar）→ 1.7.10 无 actionbar，
 * 降级为聊天栏 {@link ChatComponentTranslation}（记录差异）；</li>
 * <li>{@code level().playSound(null, this, SoundEvent, SoundSource, ...)} → 1.7.10
 * {@code World#playSoundEffect(x,y,z,String name,...)}，声音名 {@code tacz:target_block_hit}；</li>
 * <li>{@code isInvulnerableTo}/{@code destroy}/{@code getPickResult}/{@code getDefaultDisplayBlockState}
 * 等 1.8+ API 无对应：改为 {@link #attackEntityFrom} 忽略爆炸伤害 + {@link #killMinecart} 掉落物品。</li>
 * </ul>
 */
public class TargetMinecart extends EntityMinecart implements ITargetEntity {

    /** 上游 {@code TargetMinecart.java:41} 的 {@code .sized(0.75F, 2.4F)}（1.7.10 无 EntityType 尺寸表）。 */
    private static final float TARGET_WIDTH = 0.75F;
    private static final float TARGET_HEIGHT = 2.4F;

    /**
     * AABB 锚点偏移：1.7.10 {@code Entity#setPosition} 用 {@code posY - yOffset} 当盒子底面
     * （{@code build/rfg/minecraft-src/java/net/minecraft/entity/Entity.java:352}）。
     *
     * <p>
     * 上游 1.20 把 posY 当盒子底面（{@code EntityDimensions#makeBoundingBox} ⇒ 盒子 = {@code [posY, posY+2.4]}），
     * 平直轨道上 posY = 轨道方块 y + 0.0625（{@code AbstractMinecart.java:521} 的 {@code setPos(d0,d1,d2)} 与
     * {@code :640} 的 {@code j + 0.0625}）；1.7.10 平直轨道上 posY = 轨道方块 y + 0.5
     * （{@code EntityMinecart#func_145821_a:669} → {@code func_70489_a:815} 返回 {@code j + 0.5}）。
     * 故取 {@code yOffset = 0.5 - 0.0625 = 0.4375}，盒子底面才与上游一样落在 {@code 轨道 y + 0.0625}。
     *
     * <p>
     * 不能沿用 {@code EntityMinecart} 构造器的 {@code height / 2}（= 1.2）：那会把盒子压到
     * {@code [轨道 y - 0.7, 轨道 y + 1.7]}，而模型占 {@code 轨道 y ~ 轨道 y + 2.43}
     * （见 {@code TargetMinecartRenderer#renderMinecartContents}）⇒ 靶面上半截整体在盒外，
     * 表现就是「只有下半截打得中」。
     */
    private static final float Y_OFFSET = 0.4375F;

    @Nullable
    private GameProfile gameProfile = null;

    public TargetMinecart(World world) {
        super(world);
        this.setSize(TARGET_WIDTH, TARGET_HEIGHT);
        this.yOffset = Y_OFFSET;
    }

    public TargetMinecart(World world, double x, double y, double z) {
        super(world, x, y, z);
        // 物品放置（TargetMinecartItem#onItemUse）走这个 3 参构造器，而 super(world,x,y,z) 内部的
        // setPosition 用的是 1 参构造器已设好的尺寸，这里必须再设一次以保证两个入口一致。
        this.setSize(TARGET_WIDTH, TARGET_HEIGHT);
        this.yOffset = Y_OFFSET;
    }

    /**
     * 1.7.10 {@code EntityMinecart#getMinecartType()} 是抽象方法（0=普通/1=箱子/2=熔炉/3=TNT/5=漏斗）。
     * 上游 1.20 的 {@code AbstractMinecart.Type.RIDEABLE} → 这里返回 0（普通矿车模型；
     * 实际渲染由 L2 客户端自定义 Renderer 接管）。
     */
    @Override
    public int getMinecartType() {
        return 0;
    }

    /**
     * 1.7.10 的「矿车默认装载方块」，对应上游 {@code TargetMinecart#getDefaultDisplayBlockState()}
     * （{@code tmp/TACZ-1.20.1/.../entity/TargetMinecart.java:144-148}，返回 {@code ModBlocks.TARGET}）。
     *
     * <p>
     * 这个方块<b>本身不会被画</b>：客户端由 {@code TargetMinecartRenderer#func_147910_a} 接管装载物渲染。
     * 它只负责过 1.7.10 的 gate {@code RenderMinecart.java:103 block.getRenderType() != -1}
     * （对应 1.20 的 {@code getRenderShape() != INVISIBLE}，{@code MinecartRenderer.java:84}）。
     * 不过 gate ⇒ {@code func_147910_a} 永不执行 ⇒ 只有阴影、没有模型。
     *
     * <p>
     * 上游返回 TARGET 方块，但本版的 {@code TargetBlock#getRenderType()} 是 -1
     * （{@code ENTITYBLOCK_ANIMATED} → TESR，TargetBlock.java:505-507）：1.20 的 gate 放行
     * ENTITYBLOCK_ANIMATED，1.7.10 的 -1 不放行，故改用 {@link Blocks#stone}（renderType 0）当触发器。
     *
     * <p>
     * 副作用面：{@code func_145820_n()} 在 1.7.10 只有三个使用点 —— {@code RenderMinecart.java:100}
     * （本 gate）、{@code EntityMinecart#writeEntityToNBT:889}（仅 {@code hasDisplayTile()} 为真时写盘，
     * 本类从不调 {@code setDisplayTile*} ⇒ 不写任何 NBT）、{@code EntityMinecart#setDisplayTileData:1132}
     * （本类不调用）。装载物偏移沿用默认 6（{@code EntityMinecart.java:1119-1122}），与 1.20
     * {@code getDefaultDisplayOffset()==6} 一致。
     */
    @Override
    public Block func_145817_o() {
        return Blocks.stone;
    }

    @Override
    public void onProjectileHit(Entity entity, TacHitResult result, DamageSource source, float damage) {
        if (this.worldObj.isRemote || this.isDead) {
            return;
        }
        if (!(entity instanceof EntityKineticBullet projectile)) {
            return;
        }
        Entity sourceEntity = source.getEntity();
        if (!(sourceEntity instanceof EntityPlayer player)) {
            return;
        }
        this.setRollingDirection(-1);
        this.setRollingAmplitude(10);
        this.velocityChanged = true;
        this.setDamage(10);
        double dis = this.getDistance(player.posX, player.posY, player.posZ);
        // 1.7.10 无 actionbar（displayClientMessage(..., true)），降级为聊天栏提示
        player.addChatMessage(
            new ChatComponentTranslation(
                "message.tacz.target_minecart.hit",
                String.format("%.1f", damage),
                String.format("%.2f", dis)));
        if (player instanceof EntityPlayerMP serverPlayer) {
            TargetHitTrigger.INSTANCE.trigger(serverPlayer, dis);
        }
        // 原版的声音传播距离由 volume 决定：当声音大于 1 时，距离 = 16 * volume
        float volume = OtherConfig.TARGET_SOUND_DISTANCE.get() / 16.0f;
        volume = Math.max(volume, 0);
        this.worldObj.playSoundEffect(
            this.posX,
            this.posY,
            this.posZ,
            "tacz:target_block_hit",
            volume,
            this.worldObj.rand.nextFloat() * 0.1F + 0.9F);

        boolean isHeadshot = false;
        float headshotMultiplier = 1;
        MinecraftForge.EVENT_BUS.post(
            new EntityHurtByGunEvent.Post(
                projectile,
                this,
                player,
                projectile.getGunId(),
                projectile.getGunDisplayId(),
                damage,
                Pair.of(source, source),
                isHeadshot,
                headshotMultiplier,
                Side.SERVER));
        NetworkHandler.sendToDimension(
            new ServerMessageGunHurt(
                projectile.getEntityId(),
                this.getEntityId(),
                player.getEntityId(),
                projectile.getGunId(),
                projectile.getGunDisplayId(),
                damage,
                isHeadshot,
                headshotMultiplier),
            this);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        // 1.20 上游 isInvulnerableTo 对爆炸伤害免疫；1.7.10 用 attackEntityFrom 拦截
        if (source.isExplosion()) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public void killMinecart(DamageSource source) {
        this.setDead();
        if (!this.worldObj.isRemote) {
            ItemStack itemStack = new ItemStack(ModItems.TARGET_MINECART);
            String customName = this.func_95999_t();
            if (customName != null && !customName.isEmpty()) {
                itemStack.setStackDisplayName(customName);
            }
            this.entityDropItem(itemStack, 0.0F);
        }
    }

    @Nullable
    public GameProfile getGameProfile() {
        String customName = this.func_95999_t();
        if (this.gameProfile == null && customName != null && !customName.isEmpty()) {
            this.gameProfile = new GameProfile(null, customName);
        }
        return gameProfile;
    }

    /**
     * 1.7.10 客户端渲染距离（对应上游 shouldRenderAtSqrDistance 的服务端可达距离判定）。
     * 1.7.10 实际的渲染距离由 EntityRenderer 决定，这里保留配置读取供 L2 客户端使用。
     */
    public static double getRenderDistanceSqr() {
        double size = 1.0;
        size *= RenderConfig.TARGET_RENDER_DISTANCE.get();
        return size * size;
    }
}
