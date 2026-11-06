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

    @Nullable
    private GameProfile gameProfile = null;

    public TargetMinecart(World world) {
        super(world);
        // 上游 TargetMinecart.java:41 `.sized(0.75F, 2.4F)`：1.7.10 的 EntityType 尺寸没有注册表，
        // 只能在构造器里 setSize。EntityMinecart 构造器默认给 0.98×0.7
        // （build/rfg/minecraft-src/java/net/minecraft/entity/item/EntityMinecart.java:70），
        // 与上游标靶（细而高的靶面）差异明显，且直接影响子弹命中的 boundingBox。
        // yOffset 依 EntityMinecart.java:71 的约定取 height/2。
        this.setSize(0.75F, 2.4F);
        this.yOffset = this.height / 2.0F;
    }

    public TargetMinecart(World world, double x, double y, double z) {
        super(world, x, y, z);
        // r2 修复：物品放置（TargetMinecartItem#onItemUse）走的是这个 3 参构造器，
        // 上面 1 参构造器里的 setSize/setOffset **不会**执行 → 放置出来的标靶车退回原版矿车的
        // 0.98×0.7 命中盒，2.4 高的靶面判定（上游 TargetMinecart.java:41 `.sized(0.75F, 2.4F)`）失效。
        // 两个构造器必须同样设置（尺寸与 yOffset 约定见 1 参构造器注释）。
        this.setSize(0.75F, 2.4F);
        this.yOffset = this.height / 2.0F;
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
