package com.tacz.guns.sound;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSound;

/**
 * 搬运自上游 1.20.1 {@code sound/SoundManager.java}。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>音效名仍是 {@code String} 常量（1.7.10 声音由 {@code sounds.json} + 字符串名寻址，没有 SoundEvent 注册表），
 * 与上游常量名/值保持一致，供枪包音效索引引用；</li>
 * <li>{@code sendSoundToNearby}：上游用 {@code ServerLevel#getChunkSource().chunkMap.getPlayers(...)} +
 * {@code PacketDistributor.PLAYER}；1.7.10 改为遍历 {@link WorldServer#playerEntities}，
 * 按距离平方过滤后走 {@link NetworkHandler#sendToClientPlayer}；</li>
 * <li>{@code LivingEntity} → {@link EntityLivingBase}；{@code ServerPlayer} → {@link EntityPlayerMP}。</li>
 * </ul>
 */
public class SoundManager {

    /** 射击音效，自己能听见 */
    public static String SHOOT_SOUND = "shoot";
    /** 其他玩家听到的枪声 */
    public static String SHOOT_3P_SOUND = "shoot_3p";
    /** 消音器音效 */
    public static String SILENCE_SOUND = "silence";
    /** 其他玩家听到的消音器枪声 */
    public static String SILENCE_3P_SOUND = "silence_3p";
    /** 近战刺刀音效 */
    public static String MELEE_BAYONET = "melee_bayonet";
    /** 近战推人音效 */
    public static String MELEE_PUSH = "melee_push";
    /** 近战枪托砸人音效 */
    public static String MELEE_STOCK = "melee_stock";
    /** 没有子弹时，空击的声音 */
    public static String DRY_FIRE_SOUND = "dry_fire";
    /** 空仓换弹声音 */
    public static String RELOAD_EMPTY_SOUND = "reload_empty";
    /** 战术换弹声音 */
    public static String RELOAD_TACTICAL_SOUND = "reload_tactical";
    /** 空仓检视声音 */
    public static String INSPECT_EMPTY_SOUND = "inspect_empty";
    /** 普通检视声音 */
    public static String INSPECT_SOUND = "inspect";
    /** 切枪切入声音 */
    public static String DRAW_SOUND = "draw";
    /** 切枪切出的声音 */
    public static String PUT_AWAY_SOUND = "put_away";
    /** 拉栓声音 */
    public static String BOLT_SOUND = "bolt";
    /** 枪械主动卸弹成功音效 */
    public static String UNLOAD_SOUND = "unload";
    /** 切换开关模式的声音 */
    public static String FIRE_SELECT = "fire_select";
    /** 爆头击中声音 */
    public static String HEAD_HIT_SOUND = "head_hit";
    /** 普通击中声音 */
    public static String FLESH_HIT_SOUND = "flesh_hit";
    /** 击杀的声音 */
    public static String KILL_SOUND = "kill";
    /** 卸载配件的声音，用于配件的 */
    public static String UNINSTALL_SOUND = "uninstall";
    /** 装载配件的声音，用于配件的 */
    public static String INSTALL_SOUND = "install";

    public static void sendSoundToNearby(EntityLivingBase sourceEntity, int distance, ResourceLocation gunId,
        ResourceLocation gunDisplayId, String soundName, float volume, float pitch) {
        if (!(sourceEntity.worldObj instanceof WorldServer worldServer)) {
            return;
        }
        ServerMessageSound soundMessage = new ServerMessageSound(
            sourceEntity.getEntityId(),
            gunId,
            gunDisplayId,
            soundName,
            volume,
            pitch,
            distance);
        List<EntityPlayer> players = worldServer.playerEntities;
        double distanceSq = (double) distance * distance;
        for (EntityPlayer player : players) {
            if (!(player instanceof EntityPlayerMP serverPlayer)) {
                continue;
            }
            if (serverPlayer.getEntityId() == sourceEntity.getEntityId()) {
                continue;
            }
            if (serverPlayer.getDistanceSq(sourceEntity.posX, sourceEntity.posY, sourceEntity.posZ) < distanceSq) {
                NetworkHandler.sendToClientPlayer(soundMessage, serverPlayer);
            }
        }
    }
}
