package com.tacz.debug;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 开发用游戏内调试桥（TACZ dev harness），<b>仅开发环境启用</b>。
 *
 * <p>
 * 启动条件：JVM 系统属性 {@code -Dtacz.devbridge.port=<port>}（或 {@code -Dtacz.devbridge.port=true} 用默认 25566）。
 * 生产环境不设置该属性时，本类不会被加载（{@code ClientProxy} 里有惰性门控），对模组行为零影响。
 *
 * <p>
 * 协议：TCP 127.0.0.1 + 按行 JSON（NDJSON）。每条请求形如
 * {@code {"cmd":"status","args":{},"phase":"tick|render","timeoutMs":15000}}；
 * 响应 {@code {"ok":true,"result":{...},"ms":3}} 或 {@code {"ok":false,"error":"..."}}。
 *
 * <p>
 * 线程模型：连接线程只负责收发；游戏状态相关命令一律入队，由客户端主线程在
 * {@link TickEvent.ClientTickEvent}（START）与 {@link TickEvent.RenderTickEvent}（END）里出队执行。
 * 这样 CLI 可以直接读写 Minecraft 对象，无需截图/模拟键鼠。
 *
 * <p>
 * 渲染类命令（{@code render.frame} 等）必须用 {@code "phase":"render"}：它们在当前帧渲染完成、
 * 主 framebuffer 仍绑定时执行，可安全 {@code glGetTexImage} 读帧缓冲并存 PNG。
 */
@SideOnly(Side.CLIENT)
public final class DevBridge {

    public static final String PROP_PORT = "tacz.devbridge.port";
    public static final String VERSION = "1.0";
    public static final int DEFAULT_PORT = 25566;

    private static final Logger LOGGER = LogManager.getLogger("TACZ-DEVBRIDGE");
    private static final int CHAT_LOG_LIMIT = 300;

    private static DevBridge INSTANCE;
    private static Field TIMER_FIELD;

    private final int port;
    private final ServerSocket server;
    private final Gson gson = new GsonBuilder().serializeNulls()
        .create();
    private final ConcurrentLinkedQueue<Call> tickQueue = new ConcurrentLinkedQueue<Call>();
    private final ConcurrentLinkedQueue<Call> renderQueue = new ConcurrentLinkedQueue<Call>();
    private final AtomicInteger pendingTicks = new AtomicInteger(0);
    private final AtomicLong tickCount = new AtomicLong(0L);
    private final AtomicLong renderCount = new AtomicLong(0L);
    private final long startedAtMs = System.currentTimeMillis();
    private final Deque<String> chatLog = new ArrayDeque<String>();

    private volatile int fps;
    private int fpsFrames;
    private long fpsLastMs = System.currentTimeMillis();
    private volatile boolean accepting = true;
    private static volatile boolean overlaySuppressed = false;

    private DevBridge(int port) throws IOException {
        this.port = port;
        this.server = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"));
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                acceptLoop();
            }
        }, "tacz-devbridge-accept");
        thread.setDaemon(true);
        thread.start();
    }

    /** 由 {@code ClientProxy.init} 在客户端初始化阶段调用；未设置系统属性时什么也不做。 */
    public static synchronized void startIfEnabled() {
        if (INSTANCE != null) {
            return;
        }
        String raw = System.getProperty(PROP_PORT);
        if (raw == null) {
            return;
        }
        raw = raw.trim();
        if (raw.isEmpty() || "false".equalsIgnoreCase(raw)) {
            return;
        }
        int port = DEFAULT_PORT;
        if (!"true".equalsIgnoreCase(raw)) {
            try {
                port = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                LOGGER.error("[TACZ-DEVBRIDGE] invalid port: {}", raw);
                return;
            }
        }
        try {
            INSTANCE = new DevBridge(port);
            FMLCommonHandler.instance()
                .bus()
                .register(INSTANCE);
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.gameSettings != null) {
                    // 失焦不自动暂停，便于无人值守的自动化（1.7.10 该字段就是原版行为开关）
                    mc.gameSettings.pauseOnLostFocus = false;
                }
            } catch (Throwable ignored) {
                // 调试增强，失败无害
            }
            LOGGER.info("[TACZ-DEVBRIDGE] v{} listening on 127.0.0.1:{}", VERSION, port);
        } catch (Throwable t) {
            LOGGER.error("[TACZ-DEVBRIDGE] failed to start on port " + port, t);
        }
    }

    public static DevBridge get() {
        return INSTANCE;
    }

    public static boolean isOverlaySuppressed() {
        return overlaySuppressed;
    }

    public static void setOverlaySuppressed(boolean suppressed) {
        overlaySuppressed = suppressed;
    }

    public static long tick() {
        DevBridge b = INSTANCE;
        return b == null ? -1L : b.tickCount.get();
    }

    public static long renderTick() {
        DevBridge b = INSTANCE;
        return b == null ? -1L : b.renderCount.get();
    }

    public static int fps() {
        DevBridge b = INSTANCE;
        return b == null ? -1 : b.fps;
    }

    /** 请求快进 N 个客户端 tick（在 tick 事件里把 {@code Timer.elapsedTicks} 加高，让主循环多跑几圈）。 */
    public static int requestTicks(int count) {
        DevBridge b = INSTANCE;
        if (b == null || count <= 0) {
            return 0;
        }
        int capped = Math.min(count, 1000);
        b.pendingTicks.addAndGet(capped);
        return capped;
    }

    /** 跳过 {@code Minecraft#timer} 的私有可见性，取到 Timer（快进 tick / 改 timerSpeed 用）。 */
    public static Timer timer() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
                return null;
            }
            if (TIMER_FIELD == null) {
                Field field = Minecraft.class.getDeclaredField("timer");
                field.setAccessible(true);
                TIMER_FIELD = field;
            }
            return (Timer) TIMER_FIELD.get(mc);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void addChatLine(String line) {
        DevBridge b = INSTANCE;
        if (b == null || line == null) {
            return;
        }
        synchronized (b.chatLog) {
            b.chatLog.addLast(line);
            while (b.chatLog.size() > CHAT_LOG_LIMIT) {
                b.chatLog.removeFirst();
            }
        }
    }

    public static List<String> chatTail(int count) {
        DevBridge b = INSTANCE;
        List<String> out = new ArrayList<String>();
        if (b == null) {
            return out;
        }
        synchronized (b.chatLog) {
            int n = Math.max(0, Math.min(count, b.chatLog.size()));
            int skip = b.chatLog.size() - n;
            int i = 0;
            for (String line : b.chatLog) {
                if (i++ >= skip) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    public int port() {
        return port;
    }

    public long uptimeMs() {
        return System.currentTimeMillis() - startedAtMs;
    }

    public int pendingTickRequest() {
        return pendingTicks.get();
    }

    private JsonObject ping() {
        JsonObject r = new JsonObject();
        r.addProperty("version", VERSION);
        r.addProperty("port", port);
        r.addProperty("uptimeMs", uptimeMs());
        r.addProperty("tick", tickCount.get());
        r.addProperty("render", renderCount.get());
        r.addProperty("fps", fps);
        r.addProperty("pendingTicks", pendingTicks.get());
        r.addProperty("tickQueue", tickQueue.size());
        r.addProperty("renderQueue", renderQueue.size());
        r.addProperty("pid", pid());
        return r;
    }

    private static long pid() {
        try {
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean()
                .getName();
            return Long.parseLong(name.split("@")[0]);
        } catch (Throwable t) {
            return -1L;
        }
    }

    private void acceptLoop() {
        while (accepting) {
            Socket socket = null;
            try {
                socket = server.accept();
                Thread thread = new Thread(new Connection(socket), "tacz-devbridge-conn");
                thread.setDaemon(true);
                thread.start();
            } catch (SocketException e) {
                if (!accepting) {
                    return;
                }
                LOGGER.warn("[TACZ-DEVBRIDGE] accept failed", e);
            } catch (Throwable t) {
                LOGGER.warn("[TACZ-DEVBRIDGE] accept failed", t);
                close(socket);
            }
        }
    }

    private static void close(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private String handleLine(String line) {
        long start = System.currentTimeMillis();
        JsonObject resp = new JsonObject();
        try {
            JsonObject req = new JsonParser().parse(line)
                .getAsJsonObject();
            String cmd = str(req, "cmd", "");
            JsonObject args = req.has("args") && req.get("args")
                .isJsonObject() ? req.getAsJsonObject("args") : new JsonObject();
            long timeoutMs = num(req, "timeoutMs", 15000L);
            if ("ping".equals(cmd)) {
                resp.addProperty("ok", true);
                resp.add("result", ping());
            } else {
                boolean renderPhase = "render".equalsIgnoreCase(str(req, "phase", "tick"));
                Call call = new Call(cmd, args, renderPhase);
                (renderPhase ? renderQueue : tickQueue).add(call);
                boolean done = call.latch.await(timeoutMs, TimeUnit.MILLISECONDS);
                if (!done) {
                    call.fail(
                        "timeout after " + timeoutMs
                            + "ms: client thread did not run the command"
                            + " (game still loading/frozen?)");
                }
                if (call.error != null) {
                    resp.addProperty("ok", false);
                    resp.addProperty("error", call.error);
                } else {
                    resp.addProperty("ok", true);
                    resp.add("result", call.result == null ? new JsonObject() : call.result);
                }
            }
        } catch (Throwable t) {
            resp.addProperty("ok", false);
            resp.addProperty(
                "error",
                t.getClass()
                    .getSimpleName() + ": "
                    + String.valueOf(t.getMessage()));
        }
        resp.addProperty("ms", System.currentTimeMillis() - start);
        return gson.toJson(resp);
    }

    private static String str(JsonObject object, String key, String def) {
        try {
            return object.has(key) && !object.get(key)
                .isJsonNull() ? object.get(key)
                    .getAsString() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static long num(JsonObject object, String key, long def) {
        try {
            return object.has(key) ? object.get(key)
                .getAsLong() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private void drain(ConcurrentLinkedQueue<Call> queue) {
        Call call;
        while ((call = queue.poll()) != null) {
            call.execute();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        tickCount.incrementAndGet();
        applyFastForward();
        drain(tickQueue);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        renderCount.incrementAndGet();
        fpsFrames++;
        long now = System.currentTimeMillis();
        if (now - fpsLastMs >= 1000L) {
            fps = fpsFrames;
            fpsFrames = 0;
            fpsLastMs = now;
        }
        drain(renderQueue);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        try {
            String text = event.message == null ? "" : event.message.getUnformattedText();
            addChatLine(text);
        } catch (Throwable ignored) {
            // ignore
        }
    }

    @SubscribeEvent
    public void onRenderOverlayPre(RenderGameOverlayEvent.Pre event) {
        // 冻结屏（currentScreen != null）下 1.7.10 仍会渲染 HUD，这里直接取消整个 overlay
        if (overlaySuppressed && event.type == RenderGameOverlayEvent.ElementType.ALL) {
            event.setCanceled(true);
        }
    }

    private void applyFastForward() {
        int extra = pendingTicks.getAndSet(0);
        if (extra <= 0) {
            return;
        }
        Timer timer = timer();
        if (timer == null) {
            pendingTicks.addAndGet(extra);
            return;
        }
        // runGameLoop 的 for 循环每次迭代都会重新读 elapsedTicks，因此运行期加高即可多跑 tick。
        timer.elapsedTicks = Math.min(2000, timer.elapsedTicks + extra);
    }

    private final class Connection implements Runnable {

        private final Socket socket;

        Connection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                socket.setTcpNoDelay(true);
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                String line;
                while (accepting && (line = reader.readLine()) != null) {
                    if (line.trim()
                        .isEmpty()) {
                        continue;
                    }
                    String response = handleLine(line);
                    writer.write(response);
                    writer.newLine();
                    writer.flush();
                }
            } catch (Throwable t) {
                if (!(t instanceof SocketException)) {
                    LOGGER.warn("[TACZ-DEVBRIDGE] connection error: {}", String.valueOf(t));
                }
            } finally {
                close(socket);
            }
        }
    }

    private final class Call {

        private final String cmd;
        private final JsonObject args;
        private final boolean renderPhase;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile JsonObject result;
        private volatile String error;

        Call(String cmd, JsonObject args, boolean renderPhase) {
            this.cmd = cmd;
            this.args = args;
            this.renderPhase = renderPhase;
        }

        void execute() {
            try {
                result = DevCommands.execute(cmd, args, renderPhase);
            } catch (Throwable t) {
                error = t.getClass()
                    .getSimpleName() + ": "
                    + String.valueOf(t.getMessage());
                LOGGER.warn("[TACZ-DEVBRIDGE] command '{}' failed: {}", cmd, error);
            } finally {
                latch.countDown();
            }
        }

        void fail(String message) {
            if (latch.getCount() > 0L) {
                error = message;
                latch.countDown();
            }
        }
    }
}
