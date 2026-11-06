package com.tacz.guns.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.tacz.guns.GunMod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * 按逻辑侧排队的主任务队列 —— 1.7.10 版 {@code NetworkEvent.Context#enqueueWork} 的替代品。
 *
 * <p>
 * 上游（1.20.1）消息处理器在 {@code handle} 里写：
 * 
 * <pre>
 * context.enqueueWork(() -&gt; { ... 改世界/玩家状态 ... });
 * context.setPacketHandled(true);
 * </pre>
 * 
 * 1.7.10 的 simpleimpl 已经把包体交给逻辑线程：普通包在 Netty 解码后进入
 * {@code NetworkManager#receivedPacketsQueue}，由主线程 {@code processReceivedPackets()} 调
 * {@code Packet#processPacket}（{@code build/rfg/minecraft-src/java/net/minecraft/network/NetworkManager.java:118-130}
 * 与 {@code :236-242}），再用 {@code EmbeddedChannel#writeInbound} 走到
 * {@code FMLIndexedMessageToMessageCodec#decode} + {@code SimpleChannelHandlerWrapper#channelRead0}。
 * 因此本类<b>不承担线程切换</b>，只做「延后到本站 tick 末尾执行」——与上游 {@code enqueueWork}
 * 的「处理器返回后再执行」语义一致，代价是至多 1 tick（50ms）延迟。
 *
 * <p>
 * <b>为什么按侧分成两个队列</b>：单人/局域网内嵌服务端下，客户端线程与服务端线程<b>各自</b>跑 tick，
 * 而 {@link TickEvent.ServerTickEvent} 与 {@link TickEvent.ClientTickEvent} 在本类里各自排空一次。
 * 若只用一个队列，C2S 包排入的任务可能被客户端 tick 取走、S2C 包的任务可能被服务端 tick 取走，
 * 就等于在<b>错误的线程</b>改世界状态（数据竞争）。因此入队时必须带上接收侧
 * （即消息处理器里的 {@code ctx.side}），排空时只取自己那一侧。
 *
 * <p>
 * <b>1.7.10 差异（必须知道）</b>：{@link TickEvent} 属于 <b>FML 事件总线</b>（{@code cpw.mods.fml.common.gameevent}），
 * 由 {@code FMLCommonHandler#onPostServerTick/onPostClientTick} 用 {@code FMLCommonHandler#bus()} 发出
 * （{@code build/rfg/minecraft-src/java/cpw/mods/fml/common/FMLCommonHandler.java:245-248}、{@code :328-331}），
 * 而 {@code bus()} 返回的是本类实例私有的 {@code new EventBus()}
 * （同文件 {@code :90}、{@code :97}），<b>不是</b> {@code MinecraftForge.EVENT_BUS}。
 * 只注册到 {@code MinecraftForge.EVENT_BUS} 会导致两个 tick 处理器永远不触发，任务永远不执行（网络层整体失效）。
 * 因此这里在构造器里兜底注册到 FML 总线；{@code EventBus#register} 对同一实例是幂等的
 * （{@code EventBus.java:47-50}），调用方在 FML 总线上再注册一次也不会冲突。
 */
public final class NetworkTaskQueue {

    /** 服务端侧任务（C2S 包），由 {@link TickEvent.ServerTickEvent} 排空。 */
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<>();

    /** 客户端侧任务（S2C 包），由 {@link TickEvent.ClientTickEvent} 排空。 */
    private static final Queue<Runnable> CLIENT_TASKS = new ConcurrentLinkedQueue<>();

    /**
     * 需要实例注册到事件总线（{@code @SubscribeEvent} 方法是实例方法），因此构造器必须是 public
     * —— 由 {@code CommonProxy#preInit} 调用。
     */
    public NetworkTaskQueue() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    /**
     * 排入「由 {@code side} 的逻辑线程」执行的任务。
     *
     * @param side 接收侧，直接传消息处理器里的 {@code ctx.side}
     */
    public static void enqueue(Side side, Runnable task) {
        if (task == null) {
            return;
        }
        queueOf(side).add(task);
    }

    /** 排空指定侧的任务；只应在该侧的 tick 里调用。 */
    public static void runTasks(Side side) {
        Queue<Runnable> queue = queueOf(side);
        Runnable task;
        while ((task = queue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                GunMod.LOGGER.error("Error while running a queued network task", t);
            }
        }
    }

    private static Queue<Runnable> queueOf(Side side) {
        return side == Side.SERVER ? SERVER_TASKS : CLIENT_TASKS;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            runTasks(Side.SERVER);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            runTasks(Side.CLIENT);
        }
    }
}
