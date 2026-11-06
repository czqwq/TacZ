package com.tacz.guns.network;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.network.message.ClientMessageAmmoStorageClick;
import com.tacz.guns.network.message.ClientMessageAmmoStorageInteract;
import com.tacz.guns.network.message.ClientMessageCraft;
import com.tacz.guns.network.message.ClientMessageInstallVirtualAttachment;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import com.tacz.guns.network.message.ClientMessagePlayerAim;
import com.tacz.guns.network.message.ClientMessagePlayerBoltGun;
import com.tacz.guns.network.message.ClientMessagePlayerCancelReload;
import com.tacz.guns.network.message.ClientMessagePlayerCrawl;
import com.tacz.guns.network.message.ClientMessagePlayerDrawGun;
import com.tacz.guns.network.message.ClientMessagePlayerFireSelect;
import com.tacz.guns.network.message.ClientMessagePlayerMelee;
import com.tacz.guns.network.message.ClientMessagePlayerReloadGun;
import com.tacz.guns.network.message.ClientMessagePlayerShoot;
import com.tacz.guns.network.message.ClientMessagePlayerUnloadGun;
import com.tacz.guns.network.message.ClientMessagePlayerZoom;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageSetAttachmentVariant;
import com.tacz.guns.network.message.ClientMessageSetSlotAdapter;
import com.tacz.guns.network.message.ClientMessageSyncBaseTimestamp;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.network.message.ServerMessageAmmoStorageContents;
import com.tacz.guns.network.message.ServerMessageBulletHitBlock;
import com.tacz.guns.network.message.ServerMessageCraft;
import com.tacz.guns.network.message.ServerMessageGunUnload;
import com.tacz.guns.network.message.ServerMessageLevelUp;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import com.tacz.guns.network.message.ServerMessageSound;
import com.tacz.guns.network.message.ServerMessageSwapItem;
import com.tacz.guns.network.message.ServerMessageSyncBaseTimestamp;
import com.tacz.guns.network.message.ServerMessageSyncConfig;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.network.message.ServerMessageUpdateEntityData;
import com.tacz.guns.network.message.event.ServerMessageGunDraw;
import com.tacz.guns.network.message.event.ServerMessageGunFinishReload;
import com.tacz.guns.network.message.event.ServerMessageGunFire;
import com.tacz.guns.network.message.event.ServerMessageGunFirePost;
import com.tacz.guns.network.message.event.ServerMessageGunFireSelect;
import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import com.tacz.guns.network.message.event.ServerMessageGunKill;
import com.tacz.guns.network.message.event.ServerMessageGunMelee;
import com.tacz.guns.network.message.event.ServerMessageGunReload;
import com.tacz.guns.network.message.event.ServerMessageGunShoot;
import com.tacz.guns.network.message.handshake.Acknowledge;
import com.tacz.guns.network.message.handshake.ServerMessageSyncedEntityDataMapping;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 网络层入口。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/network/NetworkHandler.java}
 *
 * <h2>1.20.1 → 1.7.10 的机制替换</h2>
 * <ul>
 * <li>{@code SimpleChannel} → {@link SimpleNetworkWrapper}；1.7.10 没有协议版本协商，
 * 上游的 {@code VERSION = "1.0.14"} 只剩注释意义（保留常量便于对照）。</li>
 * <li>{@code registerMessage(id, class, encoder, decoder, handler, Optional<NetworkDirection>)} →
 * {@code registerMessage(IMessageHandler, class, discriminator, Side)}，一包一行，
 * <b>discriminator 顺序与上游完全一致</b>（见 {@link #init()} 的编号注释）。</li>
 * <li>{@code PacketDistributor.PLAYER/ALL/TRACKING_ENTITY[_AND_SELF]/DIMENSION} →
 * {@code sendTo/sendToAll/sendToDimension} + 1.7.10 的 {@code EntityTracker}。</li>
 * </ul>
 *
 * <h2>FML 1.7.10 的硬限制：pipeline handler 名不能重复</h2>
 * {@code SimpleNetworkWrapper#addServerHandlerAfter} 用 {@code messageHandler.getClass().getName()}
 * 作为 netty pipeline 里的 handler 名，pipeline 不允许重名。因此：
 * <b>每个注册点都必须写 {@code new SimpleMessageDispatcher<Xxx>(Xxx::handle) {}}</b>
 * —— 不同源码位置的匿名子类会得到不同类名（{@code NetworkHandler$1}、{@code $2}…），天然唯一。
 * 若改成共享同一个 {@code SimpleMessageDispatcher} 类，第二个注册就会抛
 * {@code IllegalArgumentException: Duplicate handler name: ...SimpleMessageDispatcher}（实测崩溃点）。
 *
 * <h2>channel 名</h2>
 * 主 channel {@code "tacz"}（上游 {@code tacz:network}，1.7.10 惯例直接用 modid），
 * 握手 channel {@code "tacz:handshake"}（与上游同名）。
 */
public class NetworkHandler {

    private static final String VERSION = "1.0.14";

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("tacz");
    public static final SimpleNetworkWrapper HANDSHAKE_CHANNEL = NetworkRegistry.INSTANCE
        .newSimpleChannel("tacz:handshake");

    private static int id = 1;
    private static int handshakeId = 1;
    private static boolean initialized = false;

    private NetworkHandler() {}

    /**
     * 对应上游 {@code NetworkHandler#init()}（在 {@code FMLCommonSetupEvent} 里被 {@code enqueueWork} 调用）。
     * <p>
     * 注册顺序 = 上游 discriminator 顺序，行首注释里的数字就是 discriminator。
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // 1 ClientMessagePlayerShoot PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerShoot>(ClientMessagePlayerShoot::handle) {},
            ClientMessagePlayerShoot.class,
            Side.SERVER);
        // 2 ClientMessagePlayerReloadGun PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerReloadGun>(ClientMessagePlayerReloadGun::handle) {},
            ClientMessagePlayerReloadGun.class,
            Side.SERVER);
        // 3 ClientMessagePlayerCancelReload PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerCancelReload>(ClientMessagePlayerCancelReload::handle) {},
            ClientMessagePlayerCancelReload.class,
            Side.SERVER);
        // 4 ClientMessagePlayerFireSelect PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerFireSelect>(ClientMessagePlayerFireSelect::handle) {},
            ClientMessagePlayerFireSelect.class,
            Side.SERVER);
        // 5 ClientMessagePlayerAim PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerAim>(ClientMessagePlayerAim::handle) {},
            ClientMessagePlayerAim.class,
            Side.SERVER);
        // 6 ClientMessagePlayerCrawl PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerCrawl>(ClientMessagePlayerCrawl::handle) {},
            ClientMessagePlayerCrawl.class,
            Side.SERVER);
        // 7 ClientMessagePlayerDrawGun PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerDrawGun>(ClientMessagePlayerDrawGun::handle) {},
            ClientMessagePlayerDrawGun.class,
            Side.SERVER);
        // 8 ServerMessageSound PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageSound>(ServerMessageSound::handle) {},
            ServerMessageSound.class,
            Side.CLIENT);
        // 9 ServerMessageBulletHitBlock PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageBulletHitBlock>(ServerMessageBulletHitBlock::handle) {},
            ServerMessageBulletHitBlock.class,
            Side.CLIENT);
        // 10 ClientMessageCraft PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageCraft>(ClientMessageCraft::handle) {},
            ClientMessageCraft.class,
            Side.SERVER);
        // 11 ServerMessageCraft PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageCraft>(ServerMessageCraft::handle) {},
            ServerMessageCraft.class,
            Side.CLIENT);
        // 12 ClientMessagePlayerZoom PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerZoom>(ClientMessagePlayerZoom::handle) {},
            ClientMessagePlayerZoom.class,
            Side.SERVER);
        // 13 ClientMessageRefitGun PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageRefitGun>(ClientMessageRefitGun::handle) {},
            ClientMessageRefitGun.class,
            Side.SERVER);
        // 14 ClientMessageInstallVirtualAttachment PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageInstallVirtualAttachment>(
                ClientMessageInstallVirtualAttachment::handle) {},
            ClientMessageInstallVirtualAttachment.class,
            Side.SERVER);
        // 15 ServerMessageRefreshRefitScreen PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageRefreshRefitScreen>(ServerMessageRefreshRefitScreen::handle) {},
            ServerMessageRefreshRefitScreen.class,
            Side.CLIENT);
        // 16 ClientMessageUnloadAttachment PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageUnloadAttachment>(ClientMessageUnloadAttachment::handle) {},
            ClientMessageUnloadAttachment.class,
            Side.SERVER);
        // 17 ServerMessageSwapItem PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageSwapItem>(ServerMessageSwapItem::handle) {},
            ServerMessageSwapItem.class,
            Side.CLIENT);
        // 18 ClientMessagePlayerBoltGun PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerBoltGun>(ClientMessagePlayerBoltGun::handle) {},
            ClientMessagePlayerBoltGun.class,
            Side.SERVER);
        // 19 ServerMessageLevelUp PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageLevelUp>(ServerMessageLevelUp::handle) {},
            ServerMessageLevelUp.class,
            Side.CLIENT);
        // 20 ServerMessageGunHurt PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunHurt>(ServerMessageGunHurt::handle) {},
            ServerMessageGunHurt.class,
            Side.CLIENT);
        // 21 ServerMessageGunKill PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunKill>(ServerMessageGunKill::handle) {},
            ServerMessageGunKill.class,
            Side.CLIENT);
        // 22 ServerMessageUpdateEntityData PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageUpdateEntityData>(ServerMessageUpdateEntityData::handle) {},
            ServerMessageUpdateEntityData.class,
            Side.CLIENT);
        // 23 ServerMessageSyncGunPack PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageSyncGunPack>(ServerMessageSyncGunPack::handle) {},
            ServerMessageSyncGunPack.class,
            Side.CLIENT);
        // 24 ClientMessagePlayerMelee PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerMelee>(ClientMessagePlayerMelee::handle) {},
            ClientMessagePlayerMelee.class,
            Side.SERVER);
        // 25 ServerMessageGunDraw PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunDraw>(ServerMessageGunDraw::handle) {},
            ServerMessageGunDraw.class,
            Side.CLIENT);
        // 26 ServerMessageGunFire PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunFire>(ServerMessageGunFire::handle) {},
            ServerMessageGunFire.class,
            Side.CLIENT);
        // 27 ServerMessageGunFireSelect PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunFireSelect>(ServerMessageGunFireSelect::handle) {},
            ServerMessageGunFireSelect.class,
            Side.CLIENT);
        // 28 ServerMessageGunMelee PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunMelee>(ServerMessageGunMelee::handle) {},
            ServerMessageGunMelee.class,
            Side.CLIENT);
        // 29 ServerMessageGunReload PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunReload>(ServerMessageGunReload::handle) {},
            ServerMessageGunReload.class,
            Side.CLIENT);
        // 30 ServerMessageGunShoot PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunShoot>(ServerMessageGunShoot::handle) {},
            ServerMessageGunShoot.class,
            Side.CLIENT);
        // 31 ServerMessageSyncBaseTimestamp PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageSyncBaseTimestamp>(ServerMessageSyncBaseTimestamp::handle) {},
            ServerMessageSyncBaseTimestamp.class,
            Side.CLIENT);
        // 32 ClientMessageSyncBaseTimestamp PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageSyncBaseTimestamp>(ClientMessageSyncBaseTimestamp::handle) {},
            ClientMessageSyncBaseTimestamp.class,
            Side.SERVER);
        // 33 ClientMessageLaserColor PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageLaserColor>(ClientMessageLaserColor::handle) {},
            ClientMessageLaserColor.class,
            Side.SERVER);
        // 34 ClientMessageAmmoStorageInteract PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageAmmoStorageInteract>(ClientMessageAmmoStorageInteract::handle) {},
            ClientMessageAmmoStorageInteract.class,
            Side.SERVER);
        // 35 ClientMessageSetAttachmentVariant PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageSetAttachmentVariant>(
                ClientMessageSetAttachmentVariant::handle) {},
            ClientMessageSetAttachmentVariant.class,
            Side.SERVER);
        // 36 ClientMessageSetSlotAdapter PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageSetSlotAdapter>(ClientMessageSetSlotAdapter::handle) {},
            ClientMessageSetSlotAdapter.class,
            Side.SERVER);
        // 37 ClientMessageAmmoStorageClick PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessageAmmoStorageClick>(ClientMessageAmmoStorageClick::handle) {},
            ClientMessageAmmoStorageClick.class,
            Side.SERVER);
        // 38 ServerMessageAmmoStorageContents PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageAmmoStorageContents>(ServerMessageAmmoStorageContents::handle) {},
            ServerMessageAmmoStorageContents.class,
            Side.CLIENT);
        // 39 ServerMessageGunFinishReload PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunFinishReload>(ServerMessageGunFinishReload::handle) {},
            ServerMessageGunFinishReload.class,
            Side.CLIENT);
        // 40 ServerMessageGunFirePost PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunFirePost>(ServerMessageGunFirePost::handle) {},
            ServerMessageGunFirePost.class,
            Side.CLIENT);
        // 41 ClientMessagePlayerUnloadGun PLAY_TO_SERVER
        register(
            new SimpleMessageDispatcher<ClientMessagePlayerUnloadGun>(ClientMessagePlayerUnloadGun::handle) {},
            ClientMessagePlayerUnloadGun.class,
            Side.SERVER);
        // 42 ServerMessageGunUnload PLAY_TO_CLIENT
        register(
            new SimpleMessageDispatcher<ServerMessageGunUnload>(ServerMessageGunUnload::handle) {},
            ServerMessageGunUnload.class,
            Side.CLIENT);
        // 43 ServerMessageSyncConfig PLAY_TO_CLIENT —— 移植特有（1.7.10 无 SERVER 配置自动同步）
        register(
            new SimpleMessageDispatcher<ServerMessageSyncConfig>(ServerMessageSyncConfig::handle) {},
            ServerMessageSyncConfig.class,
            Side.CLIENT);

        // handshake channel：上游 discriminator 从 1 开始，顺序 Acknowledge → SyncedEntityDataMapping
        HANDSHAKE_CHANNEL.registerMessage(
            new SimpleMessageDispatcher<Acknowledge>(Acknowledge::handle) {},
            Acknowledge.class,
            handshakeId++,
            Side.SERVER);
        HANDSHAKE_CHANNEL.registerMessage(
            new SimpleMessageDispatcher<ServerMessageSyncedEntityDataMapping>(
                ServerMessageSyncedEntityDataMapping::handle) {},
            ServerMessageSyncedEntityDataMapping.class,
            handshakeId++,
            Side.CLIENT);

        // 弹药库「条目列表」的下发通道。
        //
        // 上游 `inventory/AmmoStorageMenu.java:113` 在 `broadcastChanges` 里直接
        // `sendToClientPlayer(new ServerMessageAmmoStorageContents(...), player)`。
        // 1.7.10 的 `Container` 只有 `sendSlotContents` / `sendProgressBarUpdate`（后者只传两个
        // short 截断的 int），装不下 `List<AmmoStorageEntry>`（元素含 ItemStack），因此
        // `AmmoStorageMenu` 留了 `ContentSender` 静态钩子，**由本方法注入**。
        //
        // 不注入的后果（玩家可见）：弹药库 GUI 只能看到「已用/容量」两根进度条，
        // **弹药列表恒空**、无法查看/取出具体弹药（`AmmoStorageScreen:82/163` 全依赖
        // `menu.getEntries()`）；`ServerMessageAmmoStorageContents` 在此之前全仓无生产者。
        AmmoStorageMenu.setContentSender(
            (player, windowId, capacityStacks, usedStacks, entries) -> sendToClientPlayer(
                new ServerMessageAmmoStorageContents(windowId, capacityStacks, usedStacks, entries),
                player));
    }

    /**
     * 注册一个消息处理器。
     *
     * <p>
     * <b>每个调用点必须是独立的匿名子类</b>：1.7.10 的
     * {@code SimpleNetworkWrapper#registerMessage(IMessageHandler, Class, int, Side)} 用
     * {@code messageHandler.getClass().getName()} 作为 netty pipeline 的 handler 名，pipeline 不允许重名。
     * 同源码位置的 {@code new SimpleMessageDispatcher<X>(X::handle) {}} 会产生独立匿名类（类名唯一），
     * 共享同一个实现类则第二个注册即抛 {@code Duplicate handler name}（实测崩溃点）。
     */
    private static <REQ extends IMessage> void register(SimpleMessageDispatcher<REQ> handler, Class<REQ> type,
        Side receptionSide) {
        CHANNEL.registerMessage(handler, type, id++, receptionSide);
    }

    /**
     * 发送给指定玩家。上游 {@code PacketDistributor.PLAYER.with(() -> (ServerPlayer) player)}。
     */
    public static void sendToClientPlayer(IMessage message, EntityPlayer player) {
        if (message != null && player instanceof EntityPlayerMP) {
            CHANNEL.sendTo(message, (EntityPlayerMP) player);
        }
    }

    /**
     * 发送给所有监听此实体的玩家 + 实体自己（若实体是玩家）。
     * <p>
     * 上游 {@code PacketDistributor.TRACKING_ENTITY_AND_SELF}；1.7.10 用
     * {@code WorldServer#getEntityTracker().getTrackingPlayers(entity)}。
     */
    public static void sendToTrackingEntityAndSelf(Entity centerEntity, IMessage message) {
        if (centerEntity == null || message == null) return;
        Set<EntityPlayerMP> receivers = new LinkedHashSet<>(getTrackingPlayers(centerEntity));
        if (centerEntity instanceof EntityPlayerMP) receivers.add((EntityPlayerMP) centerEntity);
        for (EntityPlayerMP player : receivers) {
            CHANNEL.sendTo(message, player);
        }
    }

    public static void sendToAllPlayers(IMessage message) {
        if (message != null) CHANNEL.sendToAll(message);
    }

    /**
     * 发送给所有监听此实体的玩家（不含自己）。上游 {@code PacketDistributor.TRACKING_ENTITY}。
     */
    public static void sendToTrackingEntity(IMessage message, Entity centerEntity) {
        if (centerEntity == null || message == null) return;
        for (EntityPlayerMP player : getTrackingPlayers(centerEntity)) {
            CHANNEL.sendTo(message, player);
        }
    }

    /**
     * 发送给实体所在维度的所有玩家。上游 {@code PacketDistributor.DIMENSION.with(() -> dimension)}。
     * <p>
     * 1.7.10 的维度 id 是 int（{@code worldObj.provider.dimensionId}）。
     */
    public static void sendToDimension(IMessage message, Entity centerEntity) {
        if (centerEntity == null || message == null || centerEntity.worldObj == null) return;
        CHANNEL.sendToDimension(message, centerEntity.worldObj.provider.dimensionId);
    }

    private static Set<EntityPlayerMP> getTrackingPlayers(Entity centerEntity) {
        if (!(centerEntity.worldObj instanceof WorldServer)) return Collections.emptySet();
        WorldServer world = (WorldServer) centerEntity.worldObj;
        Set<?> tracking = world.getEntityTracker()
            .getTrackingPlayers(centerEntity);
        if (tracking == null || tracking.isEmpty()) return Collections.emptySet();
        Set<EntityPlayerMP> players = new LinkedHashSet<>();
        for (Object candidate : tracking) {
            if (candidate instanceof EntityPlayerMP) players.add((EntityPlayerMP) candidate);
        }
        return players;
    }

    /**
     * 当前在线玩家（1.7.10 {@code ServerConfigurationManager#playerEntityList}）。
     */
    @SuppressWarnings("unchecked")
    public static List<EntityPlayerMP> getOnlinePlayers() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return Collections.emptyList();
        return server.getConfigurationManager().playerEntityList;
    }

    /**
     * 兼容上游保留常量（1.7.10 无协议版本协商，仅用于日志对照）。
     */
    public static String getVersion() {
        return VERSION;
    }
}
