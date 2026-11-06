package com.tacz.guns.client.renderer.scope;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.ClientTickHelper;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.BufferBuilder;
import com.tacz.guns.mcshim.BufferUploader;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 瞄具镜片的屏幕空间 pass（畸变 / 色散 / 暗角 / 镀膜染色 / 夜视 / 热成像调色板）。
 *
 * <p>
 * 1.20 → 1.7.10 要点：{@code RegisterShadersEvent} + {@code ShaderInstance} → {@link #init()} +
 * {@link ScopeLensProgram}（GL20 编译 GLSL 120）；主渲染目标 → {@code Minecraft#getFramebuffer()}
 * （{@link Framebuffer}）+ 同尺寸 copy 纹理（{@code glCopyTexSubImage2D}）；
 * {@code BufferUploader#drawWithShader} → {@link BufferUploader#draw(BufferBuilder.RenderedBuffer)}；
 * {@code RenderSystem} 的矩阵/模板/时间调用 → {@code glOrtho} / {@code GL11.glStencil*} /
 * {@code System.currentTimeMillis()}。
 *
 * <p>
 * <b>stencil 降级（r6，实机大黑框的根因之一）</b>：上游 {@code capture()} 要求主 framebuffer 有 stencil 附件，
 * 否则直接返回 {@code false}（上游 ScopeLensRenderer.java:82-83）。1.7.10 的 {@link Framebuffer} 只有在 Forge
 * 为窗口申请到 stencil bits 时才把深度建成 depth-stencil 并挂 {@code STENCIL_ATTACHMENT}
 * （build/rfg/minecraft-src/java/net/minecraft/client/shader/Framebuffer.java:117-130；
 * bits 默认 0，见 ForgeHooksClient.java:317-341）⇒ 默认没有 stencil，模板测试恒通过。
 * 本移植的处理：<b>镜片圆盘照画</b>（圆盘本身不需要模板裁剪，热成像/光学效果靠它生效），
 * 但<b>不登记/不绘制 reticle（division 分割面）</b>，并由 {@code BedrockAttachmentModel} 跳过所有
 * 「靠模板裁剪的黑遮罩/分割面」——它们未裁剪时会画成整块黑矩形
 * （实机证据 docs/review/r6/screenshots/{b,c,d}*.png）。
 * {@link ScopeLensContour} 与 {@link ScopeThermalRenderer} 的 Ready 标志/数据源限制见各自类注释。
 *
 * <p>
 * <b>有 stencil 时的延迟 pass（与上游 Capture 等价）</b>：上游 {@code capture()} 把主 framebuffer 的
 * 模板缓冲 blit 进每个 lens 自己的 capture target，帧末再把主颜色纹理挂到该 target 上绘制 —— 因为枪体
 * 渲染结束时会清空主模板缓冲（{@code BedrockGunModel#renderUnaccelerated}，与上游同款），帧末直接查主模板
 * 只会全部失败。本移植同样在 {@link #capture} 时把模板 blit 进自建 FBO（{@link CaptureStencil}），
 * 绘制时临时把 {@code main.framebufferTexture} 挂到该 FBO 的 {@code COLOR_ATTACHMENT0}，像素仍写回主画面。
 *
 * <p>
 * <b>线程规则（曾导致 JVM abort，务必先读）</b>：<b>任何 {@code GL11/GL12/GL14/GL20/OpenGlHelper} 调用都必须在
 * 渲染线程且上下文有效</b>，违反时 LWJGL2 直接 native abort 整个 JVM（{@code FATAL ERROR in native method: ...
 * No context is current}），不是 Java 异常（实测：断线的 {@code ClientDisconnectionFromServerEvent} 在 Netty IO
 * 线程触发，其处理器调 {@link #close()} → 崩服）。因此入口分两类：<b>线程无关</b>（{@link #close()} 只写
 * volatile 标记，不碰 GL、也不改渲染线程正在遍历的集合）；<b>仅渲染线程</b>（{@link #init()} / {@link #capture} /
 * {@link #render()} 与 {@link #drainDeletes()} 里真正的 {@code glDelete*}），由 {@link #render()} 开头 +
 * {@link #registerDeferredDeleteTicker()} 双冗余排空。断线/网络/模组加载回调一律不可直接调 GL，
 * 需要立刻释放就用「打标记 + 渲染线程排空」，或 {@code Minecraft.getMinecraft().func_152344_a(Runnable)}。
 *
 * <p>
 * <b>Angelica / core-profile 下已知失效</b>：Angelica 开 shader pack 时强制 GL 3.3 core 并接管
 * {@code Tessellator#draw()}，compatibility 三件套全部不成立 —— ① {@code glBindAttribLocation(0, "Position")}
 * 依赖 {@code glVertexPointer} → attribute 0 的别名（镜片不显示）；② {@code glGetFloat(GL_MODELVIEW_MATRIX/
 * GL_PROJECTION_MATRIX)} 返回恒等或垃圾（椭圆中心/半径算错）；③ {@code glMatrixMode/glPushMatrix/
 * glLoadMatrix/glPopMatrix} 报 {@code GL_INVALID_OPERATION}（镜片不落屏幕空间、热掩膜与深度对不齐）。
 * 对策：{@link #isCoreProfile()} 为真时<b>不编译着色器</b>、{@link #capture} 直接返回 false → 回退原版镜片并打 WARN。
 * 彻底解决需改成自建 VBO + VAO 并放弃固定管线矩阵查询（docs/ANGELICA_COMPAT.md 的可选方案，实测确认后再决定）。
 */
@SideOnly(Side.CLIENT)
public final class ScopeLensRenderer {

    private static final int SEGMENTS = 90;

    /**
     * 上游 {@code ScopeLensRenderer#SURFACE_Z}（:37）：镜片 surface 的 z。与 {@code BedrockAttachmentModel}
     * 的 INVERT 圆同值 —— 两边是**同一份视空间圆**（上游 :273-284 注释原文
     * "Exactly the same view-space circle used by BedrockAttachmentModel's stencil inversion"）。
     */
    private static final float SURFACE_Z = -90.0F;

    private static final List<Lens> LENSES = new ArrayList<>();
    /**
     * 镜片 GLSL 程序。着色器是「注册一次、活到进程结束」的静态资源（上游 {@code RegisterShadersEvent} 同语义），
     * 断线只释放每世界/每尺寸的 target，<b>不销毁程序</b> —— 否则一次断线后整局游戏都没有镜片效果。
     */
    private static volatile ScopeLensProgram program;

    /** 与主 framebuffer 同尺寸的「场景快照」纹理；采样它再写回主 framebuffer，避免读写同一纹理。 */
    private static int copyTexture = -1;
    private static int copyTextureWidth = -1;
    private static int copyTextureHeight = -1;
    /**
     * 本帧是否已经在「手部/枪械绘制之前」把世界画面拷进 {@link #copyTexture}（r6-2）。
     *
     * <p>
     * 为什么需要提前拷贝：没有 stencil 时模型侧的遮罩全部失效，手部渲染会把镜身/目镜画满整个准星区；
     * 等到 {@link #render()}（手部渲染之后）才拷贝，镜片圆盘采到的就是镜身而不是世界（实机截图
     * docs/review/r6/screenshots/e）。改由 {@code ClientSetupEvent#onRenderWorldLast} 在世界画完、
     * 手部未画时调用 {@link #prepareWorldCopy()}，镜片圆盘就一定能采到干净的世界画面。
     */
    private static boolean worldCopyValid;
    /** 每个 lens 一份「capture 时刻的模板缓冲」副本（上游 {@code Capture#target#enableStencil} 的等价物）。 */
    private static final List<CaptureStencil> CAPTURE_STENCILS = new ArrayList<>();

    // ================================================================================================
    // 跨线程释放（线程规则见类 javadoc）
    // ================================================================================================

    /** {@link #close()} 打过「有 GL 对象待删除」的标记；任何线程可写，只有渲染线程读并清除。 */
    private static volatile boolean deletePending;
    /** {@link #close()} 请求「本帧渲染线程释放每世界/每尺寸的 GPU 资源」；程序本身不销毁。 */
    private static volatile boolean closeRequested;
    /** 待删除的 GL 对象 id；{@link #close()} 入队，{@link #drainDeletes()} 在渲染线程删除。 */
    private static final Queue<Integer> PENDING_FRAMEBUFFERS = new ConcurrentLinkedQueue<>();
    private static final Queue<Integer> PENDING_RENDERBUFFERS = new ConcurrentLinkedQueue<>();
    private static final Queue<Integer> PENDING_TEXTURES = new ConcurrentLinkedQueue<>();
    /** {@link #registerDeferredDeleteTicker()} 只注册一次。 */
    private static boolean tickerRegistered;
    /** {@code GL_CONTEXT_PROFILE_MASK}（GL 3.2；LWJGL2 未导出该枚举）。 */
    private static final int GL_CONTEXT_PROFILE_MASK = 0x9126;
    /** {@code GL_CONTEXT_CORE_PROFILE_BIT}（同上）。 */
    private static final int GL_CONTEXT_CORE_PROFILE_BIT = 0x00000001;
    /** {@link #isCoreProfile()} 的缓存；同一进程内上下文 profile 不会变。 */
    private static volatile Boolean coreProfile;

    // ================================================================================================
    // 一次性诊断（r6：实机「大黑框」定位用；只打一条，不刷屏）
    // ================================================================================================

    /** 「热成像瞄具但 ThermalReady=0」只 WARN 一次。 */
    private static boolean thermalNotReadyWarned;
    /** 「镜片 pass 已实际出图」只 INFO 一次（与上面的 WARN 配对判读）。 */
    private static boolean lensPassLogged;
    /** 第一条 lens 的 NDC 圆盘参数只 INFO 一次（用于判读「镜片位置/尺寸对不对」）。 */
    private static boolean lensGeometryLogged;
    /** 镜片 pass 里检测到 GL 错误只 WARN 一次（用于判读「黑块是不是 copy/采样失败」）。 */
    private static boolean glErrorWarned;
    /** 「stencil 路径的 capture 目标创建/模板 blit 失败」只 WARN 一次（失败时该帧退回无模板圆盘）。 */
    private static boolean stencilCaptureWarned;
    /** r7 实机复核⑤：按 key 只 WARN 一次的集合（capture/INVERT 失败原因诊断用；见 {@link #warnOnce}）。 */
    private static final Set<String> WARNED_ONCE = ConcurrentHashMap.newKeySet();
    /**
     * 只读断言（spec §8.2 断言 B：capture 时 proj.m11 = f、modelView = I）的**上次状态**；
     * 复核⑨：状态翻转（首次 / true↔false）时打一条，保证「所有帧 modelViewIsIdentity=true」可验证。
     */
    private static @Nullable Boolean lastCaptureModelViewIdentity;
    /** 只读诊断（复核⑧）：镜片采样源（capture 目标自己的颜色纹理 vs 回退旧快照）只打一条。 */
    private static boolean snapshotSourceLogged;
    /**
     * 只读诊断（复核⑬）：最近一次 {@link #capture} 失败/跳过原因的简短字符串（每次调用都覆盖，不刷屏）。
     * 由 {@code BedrockAttachmentModel} 的「稳定开镜帧」诊断读出，用于定位「某个瞄具为何没有镜片」。
     */
    private static volatile String lastCaptureFailure = "capture-not-called";
    /** 只读诊断（复核⑧）：镜片 config（tint/vision/thermal）只打一条。 */
    private static boolean lensConfigLogged;
    /** 「stencil 延迟 pass 真正生效」只 INFO 一次（实机验证 -Dforge.forceDisplayStencil=true 时判读）。 */
    private static boolean stencilPathLogged;
    /** 「镜片 pass 实际在无 stencil 降级模式下运行」只 WARN 一次（运行期补挂失败时判读）。 */
    private static boolean degradedWarned;
    /**
     * 复核⑩：**稳定开镜帧**（{@code currentAimProgress() >= 0.99}）的每-lens 一次性诊断集合（按 {@code lens.stencil} 去重）。
     *
     * <p>
     * 原复核③的 probe 在「lens pass 第一次执行」时采样，但 lens pass 每帧都跑，首帧往往是抬枪过渡
     * （aim≈0.53~0.60，视空间圆在屏幕外/半径小，属正常）⇒ 无法判断完全开镜后的实际结果。
     * 现在 before/after/reticle/stats 只在稳定帧首次采样；日志字段不变。
     */
    private static final Set<Integer> STABLE_PROBED_LENSES = ConcurrentHashMap.newKeySet();
    /** 复核⑩：稳定帧的 shader uniform 快照只打一条。 */
    private static boolean lensUniformLogged;
    /** r7 实机复核③：before-probe 记下的镜片中心 5 点颜色和（用于判读镜片到底有没有写进主画面）。 */
    private static long lensProbeColorBefore = -1L;
    /** r7 实机复核③：镜片圆盘画完、reticle 未画时的中心 5 点颜色和。 */
    private static long lensProbeColorAfterSurface = -1L;
    /** r7 实机复核③：reticle 顶点数/包围盒/模板 ref 的一次性诊断。 */
    private static boolean reticleDrawProbeLogged;

    /**
     * 无 stencil 附件时是否仍画镜片圆盘（r6 单点回滚开关）。
     *
     * <p>
     * {@code true}（默认）：圆盘照画 —— 热成像/光学效果在没有模板裁剪的环境下也能生效，
     * 只是没有 reticle（准星面靠模板裁剪，未裁剪会盖满视野）。
     * {@code false}：无 stencil 时连圆盘也不画，完全回退「原版镜片」（= 什么都不叠加）。
     * 若实机发现圆盘位置/尺寸异常（例如采到黑纹理或被画到镜筒外），把这里置 {@code false} 即可单点回退。
     */
    public static boolean ALLOW_LENS_WITHOUT_STENCIL = true;

    private ScopeLensRenderer() {}

    /**
     * 编译镜片着色器。上游入口是 {@code RegisterShadersEvent}（1.20 mod 总线事件）；1.7.10 无该事件，由
     * {@code client/init/ClientSetupEvent} 调用本方法。
     */
    public static void init() {
        // 先把上一轮（例如上一次进服）在别的线程上攒下的删除清掉，再重建。
        drainDeletes();
        destroyCopyTexture();
        thermalNotReadyWarned = false;
        lensPassLogged = false;
        lensGeometryLogged = false;
        glErrorWarned = false;
        stencilCaptureWarned = false;
        stencilPathLogged = false;
        degradedWarned = false;
        STABLE_PROBED_LENSES.clear();
        lensUniformLogged = false;
        reticleDrawProbeLogged = false;
        lensProbeColorBefore = -1L;
        lensProbeColorAfterSurface = -1L;
        // core profile 下整条镜片链路都不成立（见类注释「Angelica / core-profile 下已知失效」），
        // 这时**不要编译着色器**，让 capture()/render() 全部早退，避免画出错的东西。
        if (isCoreProfile()) {
            program = null;
            GunMod.LOGGER.warn(
                "TACZ: OpenGL core profile detected (Angelica/Iris). The experimental scope lens pass needs "
                    + "the compatibility profile (glVertexPointer->attribute 0 aliasing, GL_MODELVIEW_MATRIX "
                    + "queries, glPushAttrib), so scope lens rendering is DISABLED; scopes fall back to "
                    + "vanilla lens rendering.");
        } else {
            program = ScopeLensProgram.compile();
        }
        // 1.7.10 默认 Forge stencilBits=0，但本工程会在运行期给主 framebuffer 补 depth-stencil
        // （MainFramebufferStencil，等价于 Forge 1.20.1 RenderTarget#enableStencil），因此这里只打一条
        // INFO 说明「马上尝试补挂」（下面的 beginFrame() 会立即执行一次）；补挂失败时由
        // MainFramebufferStencil / render() 打一次性 WARN 并走既有降级。
        if (program != null && !isStencilAvailable()) {
            GunMod.LOGGER.debug(
                "TACZ: main framebuffer has no stencil attachment yet (Forge stencilBits=0); the runtime "
                    + "depth-stencil patch is attempted immediately (see MainFramebufferStencil). Until then / if "
                    + "it fails, scope lens runs in the no-stencil degraded mode.");
        }
        ScopeThermalRenderer.init();
        ScopeLensContour.init();
        beginFrame();
        registerDeferredDeleteTicker();
    }

    /**
     * 主 framebuffer 当前是否<b>可用模板裁剪</b>。
     *
     * <p>
     * 两个来源（任一成立即 {@code true}）：
     * <ol>
     * <li>Forge 按 {@code -Dforge.forceDisplayStencil=true} 在创建时就把深度建成 depth-stencil
     * （{@code ForgeHooksClient.java:317-341} / {@code Framebuffer.java:117-130}）；</li>
     * <li>本工程运行期补挂成功（{@link MainFramebufferStencil}，等价于 Forge 1.20.1
     * {@code RenderTarget#enableStencil}）。</li>
     * </ol>
     *
     * <p>
     * 无 GL 调用，因此渲染路径任意位置都可安全调用（{@code BedrockAttachmentModel} 的模板/降级分支靠它）。
     * 与 {@link #stencilAvailable()} 的分工：本方法回答「现在能不能用模板」，后者回答「当前绑定的 FBO 上到底
     * 挂没挂 STENCIL_ATTACHMENT」（Angelica 等接管 framebuffer 时以它为准）。
     */
    public static boolean isStencilAvailable() {
        return MinecraftForgeClient.getStencilBits() > 0 || MainFramebufferStencil.isReady();
    }

    /**
     * 当前 GL 上下文是否是 <b>core profile</b>（Angelica / Iris 的 shader pack 模式）。此时本类的三处
     * compatibility 依赖全部失效（见类注释），必须<b>整体跳过</b>镜片而不是「画错」。
     *
     * <p>
     * TODO(L2): 靠 {@code GL_CONTEXT_PROFILE_MASK}（GL 3.2）是否含 {@code GL_CONTEXT_CORE_PROFILE_BIT} 检测；
     * 查询不可用（GL &lt; 3.2 / 驱动不实现）时判定为「不是 core profile」，保持 1.7.10 原版行为。
     * 上游 1.20 本身就运行在 core profile 上、靠 {@code RenderSystem/PoseStack/VBO} 表达一切，没有此问题。
     */
    public static boolean isCoreProfile() {
        Boolean cached = coreProfile;
        if (cached != null) {
            return cached;
        }
        boolean result;
        try {
            ContextCapabilities capabilities = GLContext.getCapabilities();
            if (!capabilities.OpenGL32) {
                result = false;
            } else {
                int mask = GL11.glGetInteger(GL_CONTEXT_PROFILE_MASK);
                result = (mask & GL_CONTEXT_CORE_PROFILE_BIT) != 0;
            }
        } catch (RuntimeException unsupported) {
            result = false;
        }
        coreProfile = result;
        return result;
    }

    /**
     * 把 {@link #drainDeletes()} 挂到渲染线程的每帧 tick 上：{@link #close()} 可能在 Netty IO 线程被调用（断线），
     * 而 GL 删除只能在渲染线程做；{@link #render()} 开头虽然也排空，但其注入点（{@code GameRendererMixin}）可能尚未
     * 启用，那样待删对象就永远清不掉。
     *
     * <p>
     * 注意 1.7.10 的三条硬约束：<b>必须注册实例</b>（{@code EventBus#register(Class)} 是空操作）、
     * <b>{@code @SubscribeEvent} 必须是实例方法</b>（{@code ASMEventHandler} 固定发 {@code INVOKEVIRTUAL}，
     * 静态会 {@code IncompatibleClassChangeError}）、且 {@code TickEvent} 在 <b>FML 总线</b>上而不在
     * {@code MinecraftForge.EVENT_BUS}。
     */
    private static void registerDeferredDeleteTicker() {
        if (tickerRegistered) {
            return;
        }
        tickerRegistered = true;
        FMLCommonHandler.instance()
            .bus()
            .register(new DeferredDeleteTicker());
    }

    /** 见 {@link #registerDeferredDeleteTicker()}。 */
    public static final class DeferredDeleteTicker {

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                ScopeLensRenderer.drainDeletes();
            }
        }
    }

    /** 丢弃未完成的绘制（切枪、改窗口大小、渲染中途异常都会走到这里）。 */
    public static void beginFrame() {
        // 每帧第一件事：确保主 framebuffer 有 stencil 附件（运行期补挂 / resize 后重挂；见 MainFramebufferStencil）。
        // 调用点是 renderWorld HEAD（主 FBO 已绑定、尚未开始画），因此本帧的手部/瞄具渲染就能用上模板裁剪。
        MainFramebufferStencil.ensure();
        ScopeThermalRenderer.beginFrame();
        worldCopyValid = false;
        for (Lens lens : LENSES) {
            for (Draw draw : lens.reticle) {
                if (draw.vertices != null) {
                    draw.vertices.release();
                }
            }
        }
        LENSES.clear();
    }

    /**
     * r6-2：在「世界已画完、手部/枪械尚未画」的时机把主 framebuffer 颜色拷进 {@link #copyTexture}。
     *
     * <p>
     * 调用点：{@code client/init/ClientSetupEvent#onRenderWorldLast}（{@code RenderWorldLastEvent}，
     * 1.7.10 里等于 1.20 的「AFTER_PARTICLES」时机；见 {@code EntityRenderer.java:1429-1437}）。
     * 1.20 上游也在这个时机做镜片 pass，所以拷贝内容与上游一致（世界 + 手部之前的画面）。
     *
     * <p>
     * 必须在渲染线程调用（直接碰 GL）。失败时静默，{@link #render()} 会自动退回「手部之后再拷贝」。
     */
    public static void prepareWorldCopy() {
        if (program == null || !RenderConfig.EXPERIMENTAL_SCOPE_LENS.get() || worldCopyValid) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // 只有第一人称、非 hideGUI 才会画手持枪械/镜片（与 FirstPersonRenderHandler 的闸门一致），
        // 其他情况省掉这次全屏拷贝。
        if (mc.gameSettings == null || mc.gameSettings.thirdPersonView != 0 || mc.gameSettings.hideGUI) {
            return;
        }
        // 没在开镜就不用镜片 pass（capture 也以 radius>0.001 早退），省掉每帧一次全屏拷贝。
        // r6-3：ADS 判定用布尔状态 {#isAimingNow()}，避免「玩家已开镜但进度读数异常为 0」时漏拷 →
        // 镜片圆盘会退回「手部之后」的拷贝（采到镜身/黑框）。
        if (!isAimingNow() && currentAimProgress() <= 0.001F) {
            return;
        }
        Framebuffer main = mc.getFramebuffer();
        if (main == null) {
            return;
        }
        int width = main.framebufferTextureWidth;
        int height = main.framebufferTextureHeight;
        if (width <= 0 || height <= 0) {
            return;
        }
        // copyScreen / ensureCopyTexture 会改写当前纹理单元绑定与 FBO 绑定：用与镜片 pass 相同的状态快照包住。
        try (RenderState ignored = new RenderState()) {
            ensureCopyTexture(width, height);
            if (copyTexture < 0) {
                return;
            }
            copyScreen(main, width, height);
        }
        worldCopyValid = true;
    }

    /**
     * 用当前镜筒几何与位姿登记一次镜片绘制（本方法<b>不</b>画任何东西）。与上游同签名：{@code ocular} 会被喂一个
     * 只做投影统计的 {@link VertexConsumer}，{@code reticle} 会被喂一个按 {@link RenderType} 分桶的录制用
     * {@link MultiBufferSource}。
     *
     * <p>
     * <b>r6 修复（大黑框）</b>：把 capture 时刻的 {@code GL_PROJECTION}/{@code GL_MODELVIEW} 连同 reticle 一起存进
     * {@link Lens}，{@link #render()} 画 reticle 前必须还原它们。修复前 reticle 顶点（capture 时已烘进相机空间）
     * 被用 identity 投影直接当 NDC 画出来 ⇒ 一块跟着瞄具走的巨型黑四边形
     * （实机证据 docs/review/r6/screenshots/b,d）。
     *
     * <p>
     * 无 stencil 附件时（1.7.10 默认，见 {@link #isStencilAvailable()}）：<b>不登记 reticle</b>（division 面靠模板
     * 裁剪才正确，未裁剪会盖满视野），镜片圆盘仍登记，保证热成像/光学效果可用。
     *
     * @return 是否登记成功（{@code false} 时调用方走原版镜片绘制）
     */
    public static boolean capture(float centerX, float centerY, float radius, int stencil, ScopeLensConfig config,
        Consumer<VertexConsumer> ocular, Consumer<MultiBufferSource> reticle) {
        if (program == null) {
            lastCaptureFailure = "shader-program-null";
            warnOnce(
                "capture-no-program",
                "TACZ: scope lens capture skipped: the lens shader program was not compiled (see the shader error above).");
            return false;
        }
        if (!RenderConfig.EXPERIMENTAL_SCOPE_LENS.get() || radius <= 0.001f || OculusCompat.isRenderShadow()) {
            lastCaptureFailure = "config-or-radius(radius=" + radius
                + ",experimental="
                + RenderConfig.EXPERIMENTAL_SCOPE_LENS.get()
                + ",shadow="
                + OculusCompat.isRenderShadow()
                + ")";
            return false;
        }
        Framebuffer main = Minecraft.getMinecraft()
            .getFramebuffer();
        if (main == null || main.framebufferTextureWidth <= 0 || main.framebufferTextureHeight <= 0) {
            lastCaptureFailure = "main-fbo-unavailable";
            warnOnce(
                "capture-no-main-fbo",
                "TACZ: scope lens capture skipped: the main framebuffer is unavailable / has no colour texture.");
            return false;
        }
        // 上游 capture() 要求「当前 draw framebuffer 是主目标且 viewport 铺满」（ScopeLensRenderer.java:80-89），
        // 否则捕获的掩膜像素网格与最终画面不一致；1.7.10 同语义：不满足就回退原版绘制。
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        if (viewport.get(0) != 0 || viewport.get(1) != 0
            || viewport.get(2) != main.framebufferTextureWidth
            || viewport.get(3) != main.framebufferTextureHeight) {
            lastCaptureFailure = "viewport-mismatch(" + viewport.get(0)
                + ","
                + viewport.get(1)
                + ","
                + viewport.get(2)
                + ","
                + viewport.get(3)
                + " vs "
                + main.framebufferTextureWidth
                + "x"
                + main.framebufferTextureHeight
                + ")";
            warnOnce(
                "capture-viewport",
                "TACZ: scope lens capture skipped: the viewport {}x{}+{}+{} does not cover the {}x{} main framebuffer.",
                viewport.get(2),
                viewport.get(3),
                viewport.get(0),
                viewport.get(1),
                main.framebufferTextureWidth,
                main.framebufferTextureHeight);
            return false;
        }

        float[] projection = new float[16];
        float[] modelView = new float[16];
        readMatrices(projection, modelView);
        // 只读断言（spec §8.2 断言 B）：capture 时上游 ModelViewMat 恒为单位矩阵、ProjMat.m11 = f
        // = 1/tan(ITEM_MODEL_FOV/2)（手部投影）。越界说明手部 pass 的 GL 矩阵被别的东西改过
        // （受伤摇晃写在 GL_MODELVIEW / 拖尾 / shader 模组）—— 属 r7-fp 域，不在镜片代码里兜底。
        //
        // 复核⑨（r7-fp 已把「手部 pass GL_MODELVIEW 恒为单位矩阵」修好）：断言改成**状态翻转时打一条**
        // —— 原「整局只打一条」无法验证后续帧；现在首次取值、以及 true↔false 每次翻转都会打一条，
        // 这样「第一次 false」与「恢复正常」都能在日志里看到。
        boolean modelViewIdentity = isIdentityMatrix(modelView);
        if (lastCaptureModelViewIdentity == null || lastCaptureModelViewIdentity != modelViewIdentity) {
            Boolean previous = lastCaptureModelViewIdentity;
            lastCaptureModelViewIdentity = modelViewIdentity;
            float expectedF = (float) (1.0 / Math.tan(Math.toRadians(CameraSetupEvent.ITEM_MODEL_FOV) / 2.0));
            GunMod.LOGGER.debug(
                "TACZ: scope capture matrices (modelViewIsIdentity FLIP {} -> {}): proj.m00={}, proj.m11={}, "
                    + "expectedF={} (ITEM_MODEL_FOV={})",
                previous == null ? "<first>" : previous,
                modelViewIdentity,
                fmt(projection[0]),
                fmt(projection[5]),
                fmt(expectedF),
                fmt(CameraSetupEvent.ITEM_MODEL_FOV));
        }
        Bounds bounds = new Bounds(projection, modelView);
        ocular.accept(bounds);
        if (!bounds.isValid()) {
            // r7 实机复核⑥：回退到复核④行为 —— bounds 无效（含「有顶点跨到相机平面后面」）就返回 false，
            // 不登记镜片、不用整屏包围盒替代（那会改变 lens 中心/半径 → 镜中采样错乱，复核⑤全局回归的根因之一）。
            // 这里只保留诊断：说明是哪个原因（behindCamera / 退化），方便后续按上游规格重做。
            lastCaptureFailure = "bounds-invalid(behindCamera=" + bounds
                .isBehindCamera() + ",centerX=" + centerX + ",centerY=" + centerY + ")";
            warnOnce(
                "capture-bounds-invalid",
                "TACZ: scope lens capture skipped: the eye-piece projection bounds are invalid "
                    + "(behindCamera={}, all vertices behind the camera or degenerate). Falling back to the "
                    + "no-lens rendering for this scope (review-4 behaviour).",
                bounds.isBehindCamera());
            return false;
        }

        // 上游 capture:99-104,128：blit 模板前保存并关闭 scissor —— scissor 开着时 glBlitFramebuffer 会被裁掉，
        // 模板副本残缺 ⇒ 镜片/准星局部消失（spec §3.3-① / §7.3-D）。
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissorEnabled) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        try {
            // stencil 附件决定 reticle 能不能画：上游 capture() 在无 stencil 附件时直接 false
            // （上游 ScopeLensRenderer.java:82-83）；本移植把「镜片圆盘」与「reticle」拆开：
            // 圆盘不需要模板裁剪（= 保留热成像效果），reticle 没有模板就会盖满视野（= 必须跳过）。
            boolean stencilValid = stencilAvailable();
            CaptureStencil captureStencil = null;
            if (stencilValid) {
                // 上游把主 framebuffer 的模板缓冲 blit 进 capture target；枪体渲染结束时主模板会被清空，
                // 帧末的延迟 pass 必须查这份副本，否则模板测试恒失败（见类注释「有 stencil 时的延迟 pass」）。
                captureStencil = captureStencil(
                    LENSES.size(),
                    main,
                    main.framebufferTextureWidth,
                    main.framebufferTextureHeight);
                stencilValid = captureStencil != null;
            }
            if (!stencilValid && isStencilAvailable()) {
                // 模型侧按 Forge stencilBits 走了模板路径（会把 division 交给本方法延迟绘制），但当前 GL
                // 无法建立模板副本（GL2.1 / capture 目标失败）。返回 false 让调用方立即画 division，
                // 否则 reticle 会被「延迟」到一个永远画不出来的 pass 里而丢失。
                lastCaptureFailure = "stencil-capture-target-failed";
                return false;
            }
            if (!stencilValid && !ALLOW_LENS_WITHOUT_STENCIL) {
                // 单点回滚：无模板附件时整体回退「什么都不叠加」（原版镜片）。
                lastCaptureFailure = "no-stencil-and-lens-without-stencil-disabled";
                return false;
            }
            List<Draw> reticleDraws = List.of();
            float @Nullable [] reticleBbox = null;
            if (stencilValid) {
                ReticleBuffers reticleBuffers = new ReticleBuffers();
                try {
                    reticle.accept(reticleBuffers);
                } catch (RuntimeException failure) {
                    reticleBuffers.discard();
                    throw failure;
                }
                reticleBbox = reticleBuffers.bbox();
                reticleDraws = reticleBuffers.finish();
            }
            LENSES.add(
                new Lens(
                    stencil,
                    stencilValid,
                    config,
                    bounds,
                    reticleDraws,
                    reticleBbox,
                    centerX,
                    centerY,
                    radius,
                    projection,
                    modelView,
                    captureStencil));
        } finally {
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
        lastCaptureFailure = "ok";
        return true;
    }

    /** 只读诊断（复核⑬）：最近一次 {@link #capture} 失败/跳过原因。 */
    public static String lastCaptureFailureReason() {
        return lastCaptureFailure;
    }

    /**
     * 只读诊断（复核⑬）：用当前 GL 矩阵投影该目镜几何，返回 {@link Bounds} 的可用性描述
     * （{@code valid} / {@code invalid(behindCamera=..)} + UV 包围盒）。用于定位「某个瞄具为何没有镜片」。
     */
    public static String describeOcularBounds(Consumer<VertexConsumer> ocular) {
        float[] projection = new float[16];
        float[] modelView = new float[16];
        readMatrices(projection, modelView);
        Bounds bounds = new Bounds(projection, modelView);
        ocular.accept(bounds);
        return (bounds.isValid() ? "valid" : "invalid(behindCamera=" + bounds.isBehindCamera() + ")")
            + ",modelViewIsIdentity="
            + isIdentityMatrix(modelView)
            + ",bounds="
            + fmt(bounds.minU)
            + ","
            + fmt(bounds.minV)
            + ".."
            + fmt(bounds.maxU)
            + ","
            + fmt(bounds.maxV);
    }

    /**
     * 把「当前 draw framebuffer」的模板缓冲拷进本 lens 的 {@link CaptureStencil}。
     *
     * @return 成功返回 target；GL 不支持 / FBO 不完整 / blit 失败返回 {@code null}（该 lens 退回无模板圆盘）
     */
    private static @Nullable CaptureStencil captureStencil(int slot, Framebuffer main, int width, int height) {
        int source = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (source == 0 && main.framebufferObject >= 0) {
            source = main.framebufferObject;
        }
        if (source <= 0) {
            warnStencilCapture("the draw framebuffer has no stencil source (binding=" + source + ")");
            return null;
        }
        CaptureStencil target;
        if (slot < CAPTURE_STENCILS.size()) {
            target = CAPTURE_STENCILS.get(slot);
        } else {
            target = new CaptureStencil();
            CAPTURE_STENCILS.add(target);
        }
        if (!target.ensure(width, height)) {
            warnStencilCapture("could not create a " + width + "x" + height + " depth-stencil capture target");
            return null;
        }
        if (!target.copyStencilFrom(source, width, height)) {
            warnStencilCapture("stencil blit from framebuffer " + source + " failed");
            return null;
        }
        return target;
    }

    /** stencil 捕获失败只 WARN 一次（每帧都失败时不刷屏）。 */
    private static void warnStencilCapture(String reason) {
        if (stencilCaptureWarned) {
            return;
        }
        stencilCaptureWarned = true;
        GunMod.LOGGER.warn(
            "TACZ: scope stencil capture DISABLED ({}); reticles/lens clipping fall back to the no-stencil path.",
            reason);
    }

    /**
     * 按 key 只 WARN 一次（r7 实机复核⑤的诊断用：capture/INVERT 的每种失败原因各报一条，便于实机定位，
     * 又不会每帧刷屏）。
     */
    private static void warnOnce(String key, String format, Object... args) {
        if (!WARNED_ONCE.add(key)) {
            return;
        }
        GunMod.LOGGER.warn(format, args);
    }

    /** 主渲染阶段末尾调用：把本帧登记的所有镜片画进主 framebuffer。 */
    public static void render() {
        // 渲染线程每帧的第一件事：执行 close() 攒下的 GL 删除（必须在下面的早退之前，
        // 否则「镜片列表为空」时待删对象永远清不掉）。
        drainDeletes();
        if (LENSES.isEmpty() || program == null) {
            return;
        }
        Framebuffer main = Minecraft.getMinecraft()
            .getFramebuffer();
        if (main == null) {
            beginFrame();
            return;
        }
        int width = main.framebufferTextureWidth;
        int height = main.framebufferTextureHeight;
        if (width <= 0 || height <= 0) {
            beginFrame();
            return;
        }
        try (RenderState ignored = new RenderState()) {
            ensureCopyTexture(width, height);
            if (copyTexture < 0) {
                GunMod.LOGGER.warn(
                    "TACZ: scope lens copy texture allocation failed ({}x{}); skipping lens pass.",
                    width,
                    height);
                beginFrame();
                return;
            }
            // 1) 场景快照：优先用 prepareWorldCopy() 在世界渲染末尾拷好的（干净世界，不含手部/枪械）。
            // 没有预拷贝时退回「现在再拷」（此时画面里已有手部，镜片可能采到枪体——仅兜底）。
            if (!worldCopyValid) {
                copyScreen(main, width, height);
            }
            // r6 诊断：拷贝失败（FBO/驱动问题）会让圆盘采到黑纹理 = 黑块。只报一次，不打断渲染。
            if (!glErrorWarned) {
                int error = GL11.glGetError();
                if (error != GL11.GL_NO_ERROR) {
                    glErrorWarned = true;
                    GunMod.LOGGER.warn(
                        "TACZ: GL error 0x{} right after scope lens copyScreen ({}x{}); the lens disc may sample "
                            + "an uninitialized/black texture.",
                        Integer.toHexString(error),
                        width,
                        height);
                }
            }
            // 2) 画镜片。stencil 路径把主颜色纹理挂到 capture FBO 上（模板裁剪照常生效、像素仍写回主画面）；
            // 无 stencil 路径直接画到主 framebuffer，圆盘用 capture 时刻目镜几何的屏幕 AABB 椭圆。
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            try {
                GL11.glViewport(0, 0, width, height);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glColorMask(true, true, true, true);
                if (!lensPassLogged) {
                    lensPassLogged = true;
                    GunMod.LOGGER.debug(
                        "TACZ: scope lens pass ACTIVE: {} lens(es), stencil={}, thermal={}, size={}x{}",
                        LENSES.size(),
                        isStencilAvailable(),
                        ScopeThermalRenderer.isReady(width, height),
                        width,
                        height);
                }
                if (!isStencilAvailable() && !degradedWarned) {
                    degradedWarned = true;
                    GunMod.LOGGER.warn(
                        "TACZ: scope lens pass runs in the NO-STENCIL degraded mode: reticles / eye-piece masks / "
                            + "scope-body clipping are skipped and the lens disc is an AABB ellipse. See the "
                            + "MainFramebufferStencil log line above for why the runtime stencil patch is unavailable.");
                }
                for (Lens lens : LENSES) {
                    // 只读诊断（spec §8.1）：只在「完全开镜」时打一条视空间圆的 NDC 落点（与
                    // BedrockAttachmentModel 的 INVERT 圆同一份参数；surfaceNdc 应与 invertNdc 完全相等）。
                    float aimProgress = currentAimProgress();
                    if (!lensGeometryLogged && aimProgress >= 0.99F) {
                        lensGeometryLogged = true;
                        // 视空间圆 (16*90*cx, 16*90*cy, -90) → NDC = (m00*centerX/90, m11*centerY/90)
                        // （spec §1.3；lens.centerX/centerY 已是 16*90*cx/cy）。
                        float centerX = lens.projection[0] * lens.centerX / 90.0F;
                        float centerY = lens.projection[5] * lens.centerY / 90.0F;
                        float radiusX = lens.projection[0] * lens.radius / 90.0F;
                        float radiusY = lens.projection[5] * lens.radius / 90.0F;
                        GunMod.LOGGER.debug(
                            "TACZ: scope lens surface (view-space circle, NDC): center=({}, {}), radius=({}, {}), "
                                + "stencilValid={}, reticles={}, vision={}, aim={}",
                            fmt(centerX),
                            fmt(centerY),
                            fmt(radiusX),
                            fmt(radiusY),
                            lens.stencilValid,
                            lens.reticle.size(),
                            lens.config.getVisionMode(),
                            String.format("%.2f", aimProgress));
                    }
                    if (lens.stencilValid && lens.captureStencil != null && main.framebufferTexture > 0) {
                        drawLensWithStencil(lens, width, height, main.framebufferTexture);
                    } else {
                        // 混用（同一帧既有 stencil lens 又有降级 lens）时不能把上一个 lens 的模板测试留下来。
                        GL11.glDisable(GL11.GL_STENCIL_TEST);
                        loadIdentityMatrices();
                        drawLens(lens, width, height, copyTexture, false);
                    }
                }
                GL20.glUseProgram(0);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            } finally {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
        } finally {
            beginFrame();
        }
    }

    /**
     * stencil 路径的镜片绘制（上游 {@code drawLens} + capture target 的等价物）：
     * <ul>
     * <li>把 capture 时刻的模板缓冲所在 FBO 设为 DRAW framebuffer；</li>
     * <li>把主 framebuffer 的颜色纹理临时挂到该 FBO 的 {@code COLOR_ATTACHMENT0}，像素仍写回主画面；</li>
     * <li><b>上游 {@code drawLens:226-231} 的 snapshot 语义</b>：capture target 自己的颜色纹理此时没挂在 FBO 上，
     * 先把「本帧最终主画面」{@code glCopyTexSubImage2D} 进去，镜片 shader 的 Sampler0/Sampler2 采这份快照
     * （r7 第二轮之前采的是 {@code prepareWorldCopy()} 手部之前的旧快照，与上游不一致）；</li>
     * <li>圆盘几何 = 上游 {@code buildSurface:273-284} 的**视空间圆**（{@link #drawViewSpaceDisc}，
     * 与 {@code BedrockAttachmentModel} 的 INVERT 圆同一份参数），ProjMat/ModelViewMat = capture 矩阵；</li>
     * <li>模板 {@code GL_EQUAL} 到该 lens 的 stencil 值，于是圆盘/准星被裁到「目镜 ∩ 瞄准圆」内。</li>
     * </ul>
     * 结束必须把颜色附件摘回 dummy 纹理（保证 capture FBO 完整）并恢复读/写 framebuffer 绑定。
     */
    private static void drawLensWithStencil(Lens lens, int width, int height, int mainColorTexture) {
        CaptureStencil stencil = lens.captureStencil;
        if (!stencilPathLogged) {
            stencilPathLogged = true;
            GunMod.LOGGER.debug(
                "TACZ: scope lens stencil path ACTIVE: captureTarget={}x{}, stencilRef={}, reticles={}, mainColorTexture={}",
                stencil.width,
                stencil.height,
                lens.stencil,
                lens.reticle.size(),
                mainColorTexture);
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // 与上游 target.bindWrite(false) 同语义：读写 framebuffer 都指向 capture target，再把主颜色纹理挂上去。
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, stencil.framebuffer);
        // r7 实机复核⑪：必须挂到 GL_FRAMEBUFFER（READ+DRAW 都挂），与上游 `_glFramebufferTexture2D(
        // GL_FRAMEBUFFER, ...)` 一致。只挂 DRAW 时 READ 侧仍是 capture target 自己的颜色纹理，
        // 紧接着的 glCopyTexSubImage2D（源 = READ 颜色附件、目标 = 同一张纹理）就变成自拷贝/未定义内容，
        // 镜片 shader 采到的是残帧/垃圾 ⇒ 镜中出现「纯黑/单色/上一帧残影」（截图 g/h/i）。
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            mainColorTexture,
            0);
        try {
            // 颜色附件换成主颜色纹理后确认 FBO 仍完整（失败时 finally 会还原，绘制直接跳过）。
            int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                warnStencilCapture(
                    "the capture target is incomplete after attaching the main colour texture (status=" + status + ")");
                return;
            }
            // 上游 drawLens:226-231：把本帧最终主画面拷进 capture target 自己的颜色纹理（此刻它没挂 FBO：
            // 读 READ framebuffer 的颜色附件 = 主颜色纹理，写自己的纹理 ⇒ 不存在自采样）。
            // 失败时退回手部之前的世界快照 copyTexture（r6 行为），不阻断绘制。
            int snapshotTexture = copyMainColorIntoCaptureSnapshot(stencil, width, height);
            boolean snapshotOk = snapshotTexture >= 0;
            if (!snapshotOk) {
                snapshotTexture = copyTexture;
            }
            // 只读诊断（复核⑧）：镜片采样源是「本帧主画面快照」还是回退的旧世界快照。
            // 若 snapshotOk=false ⇒ 采到的是手部渲染前的旧画面（没有本帧枪模/世界变化），
            // 镜中内容会明显不对（黑/绿/错位）。
            if (!snapshotSourceLogged) {
                snapshotSourceLogged = true;
                GunMod.LOGGER.debug(
                    "TACZ: scope lens snapshot: captureTargetOwnColorTexture={} (textureId={}), "
                        + "fallbackCopyTextureId={}, size={}x{}, glError=0x{}",
                    snapshotOk,
                    snapshotOk ? stencil.dummyColorTexture : -1,
                    copyTexture,
                    width,
                    height,
                    Integer.toHexString(GL11.glGetError()));
            }
            GL11.glViewport(0, 0, width, height);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_EQUAL, lens.stencil, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glColorMask(true, true, true, true);
            loadMatrices(lens.projection, lens.modelView);
            // 复核⑩：**稳定开镜帧**（aim>=0.99）每个 lens 只采样一次。lens pass 每帧都跑，首帧往往是
            // 抬枪过渡（aim≈0.53~0.60，视空间圆在屏幕外/半径小，属正常），在那里采样无法判断最终结果。
            boolean stableProbe = currentAimProgress() >= 0.99F && STABLE_PROBED_LENSES.add(lens.stencil);
            if (stableProbe) {
                probeStencilLensBeforeDraw(lens, width, height);
                probeLensPassStats(lens, snapshotOk);
            }
            // 上游 drawLens:224-271：镜片 surface 用 ProjMat/ModelViewMat（= capture 矩阵）画视空间圆。
            drawLens(lens, width, height, snapshotTexture, true);
            // 只读观测：先测「镜片画完」（reticle 之前），再测「reticle 画完」（spec §8.1；不得触发兜底）。
            if (stableProbe) {
                probeStencilLensAfterSurface(lens, width, height);
            }
            // reticle（division 分割面）的顶点是 capture 时烘进相机空间的固定管线坐标，
            // 用同一份 capture 矩阵 + 同一个 stencil 画（上游顺序：先镜片后 reticle）。
            if (!lens.reticle.isEmpty()) {
                drawReticle(lens, stencil, mainColorTexture);
            }
            if (stableProbe) {
                probeStencilReticleAfter(lens, width, height);
            }
        } finally {
            // 与挂载对称：GL_FRAMEBUFFER（READ+DRAW）一起还原成 capture target 自己的颜色纹理。
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                stencil.dummyColorTexture,
                0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    /**
     * 上游 {@code drawLens:226-231}：capture target 的 color attachment 已被主颜色纹理替换，它自己的颜色纹理
     * 此时空着 ⇒ 先把「当前主画面」拷进去当 screen texture，再让镜片 shader 采它。
     *
     * <p>
     * <b>前提</b>：调用方必须把主颜色纹理挂到 capture target 的 <b>GL_FRAMEBUFFER</b>（READ+DRAW 都挂，上游
     * {@code _glFramebufferTexture2D(GL_FRAMEBUFFER, ...)} 即如此）。若只挂 DRAW，READ 侧仍是 capture target
     * 自己的颜色纹理（= 本方法的拷贝目标 dummy），{@code glCopyTexSubImage2D} 就变成同一张纹理的自拷贝，
     * 结果是未定义内容/残帧 ⇒ 镜片黑屏或单色（r7 实机复核⑪ 截图 g/h/i 的根因）。
     *
     * <p>
     * 注意 {@code glCopyTexSubImage2D} 读的是 <b>READ framebuffer 的颜色附件</b>（此时 = 主颜色纹理），写的是
     * 「绑定在活动单元上的目标纹理」（= capture target 自己的颜色纹理）——两者不是同一纹理，无自采样。
     *
     * @return 快照纹理 id；拷贝失败返回 {@code -1}（调用方退回 {@link #copyTexture}）
     */
    private static int copyMainColorIntoCaptureSnapshot(CaptureStencil stencil, int width, int height) {
        if (stencil.dummyColorTexture < 0) {
            return -1;
        }
        int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, stencil.dummyColorTexture);
            drainGlErrors();
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                warnStencilCapture(
                    "glCopyTexSubImage2D into the capture snapshot failed (0x" + Integer.toHexString(error) + ")");
                return -1;
            }
            return stencil.dummyColorTexture;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActive);
        }
    }

    /** 把之前遗留的 GL 错误排空，保证随后读到的错误确实来自紧跟的那条命令。 */
    private static void drainGlErrors() {
        for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            // drain
        }
    }

    /**
     * 只读诊断（镜片画之前；复核⑩改为**稳定开镜帧** aim>=0.99 首次采样）：capture FBO 状态 /
     * 视空间圆心模板值 + 稳定帧多点模板采样（{@code stencilProbe5}）/ 快照中心颜色（{@code snapshotCenterRgb}，
     * 3x3 平均）/ 视空间圆参数与它经 capture 投影后的 NDC。
     *
     * <p>
     * 上游 {@code ScopeLensRenderer#drawLens:236-238} 用 {@code Bounds} 只算 shader 的
     * {@code LensCenter/LensRadius}；几何来自 {@code buildSurface:273-284} 的视空间圆。这里把两者都打出来，
     * 便于实机核对 `surfaceNdc` 是否与 {@code BedrockAttachmentModel} 的 INVERT 圆一致。
     */
    private static void probeStencilLensBeforeDraw(Lens lens, int width, int height) {
        int[] centerPixel = viewSpaceCenterPixel(lens, width, height);
        int pixelX = centerPixel[0];
        int pixelY = centerPixel[1];
        int colorBefore = readColorPixel(pixelX, pixelY);
        int snapshotRgb = sampleColorAverage3x3(pixelX, pixelY, width, height);
        lensProbeColorBefore = sampleColorAround(pixelX, pixelY, width, height);
        int stencilAtCenter = readStencilPixel(pixelX, pixelY);
        // 视空间圆 → NDC：上游 buildSurface 的 (16*90*cx, 16*90*cy, -90) 经透视除法后
        // = (proj.m00*16*cx, proj.m11*16*cy)，半径 = (proj.m00*r/90, proj.m11*r/90)（spec §1.3）。
        float m00 = lens.projection[0];
        float m11 = lens.projection[5];
        float ndcCenterX = m00 * lens.centerX / 90.0F;
        float ndcCenterY = m11 * lens.centerY / 90.0F;
        float ndcRadiusX = m00 * lens.radius / 90.0F;
        float ndcRadiusY = m11 * lens.radius / 90.0F;
        // 稳定帧多点模板采样：视空间圆心 + (0, ±0.1, ±0.25) NDC。
        // 期望：镜片中心 = 254（INVERT 后的值）、圆半径 NDC_y≈2.2~2.8 时 5 点全部 = 254。
        int stencilUp010 = readStencilAtNdc(ndcCenterX, ndcCenterY + 0.10F, width, height);
        int stencilDown010 = readStencilAtNdc(ndcCenterX, ndcCenterY - 0.10F, width, height);
        int stencilUp025 = readStencilAtNdc(ndcCenterX, ndcCenterY + 0.25F, width, height);
        int stencilDown025 = readStencilAtNdc(ndcCenterX, ndcCenterY - 0.25F, width, height);
        drainGlErrors();
        GunMod.LOGGER.debug(
            "TACZ: scope lens stencil probe (before draw, stable ADS): pixel=({},{}), colorBefore=0x{}, "
                + "snapshotCenterRgb=#{}, stencilAtCenter={}, stencilProbe5=[{},{},{},{},{}], stencilRef={}, "
                + "surfaceViewSpace=(c={},{},r={}), surfaceNdc=(center={},{}, radius={},{}), "
                + "modelViewIsIdentity={}, fboStatus={}, aim={}",
            pixelX,
            pixelY,
            String.format("%08X", colorBefore),
            snapshotRgb < 0 ? "unreadable" : String.format("%06X", snapshotRgb & 0xFFFFFF),
            stencilAtCenter,
            stencilAtCenter,
            stencilUp010,
            stencilDown010,
            stencilUp025,
            stencilDown025,
            lens.stencil,
            fmt(lens.centerX),
            fmt(lens.centerY),
            fmt(lens.radius),
            fmt(ndcCenterX),
            fmt(ndcCenterY),
            fmt(ndcRadiusX),
            fmt(ndcRadiusY),
            isIdentityMatrix(lens.modelView),
            GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER),
            String.format("%.2f", currentAimProgress()));
    }

    /** 只读诊断（镜片圆盘画完、reticle 未画）：记录中心 5 像素采样（**不触发任何兜底**，spec §8.1）。 */
    private static void probeStencilLensAfterSurface(Lens lens, int width, int height) {
        int[] centerPixel = viewSpaceCenterPixel(lens, width, height);
        lensProbeColorAfterSurface = sampleColorAround(centerPixel[0], centerPixel[1], width, height);
    }

    /** 矩阵是否为单位矩阵（spec §8.2 断言 B：capture 时 {@code ModelViewMat} 必须是单位矩阵）。 */
    private static boolean isIdentityMatrix(float[] matrix) {
        for (int i = 0; i < 16; i++) {
            float expected = (i % 5 == 0) ? 1.0F : 0.0F;
            if (Math.abs(matrix[i] - expected) > 1.0E-4F) {
                return false;
            }
        }
        return true;
    }

    /** 实机复核③一次性诊断（reticle 画完之后）：区分「镜片画了没有」与「reticle 画了没有」。 */
    private static void probeStencilReticleAfter(Lens lens, int width, int height) {
        int[] centerPixel = viewSpaceCenterPixel(lens, width, height);
        int pixelX = centerPixel[0];
        int pixelY = centerPixel[1];
        long sampleAfter = sampleColorAround(pixelX, pixelY, width, height);
        Boolean lensDrew = lensProbeColorBefore < 0 || lensProbeColorAfterSurface < 0 ? null
            : lensProbeColorBefore != lensProbeColorAfterSurface;
        Boolean reticleDrew = lensProbeColorAfterSurface < 0 || sampleAfter < 0 ? null
            : lensProbeColorAfterSurface != sampleAfter;
        int error = GL11.glGetError();
        GunMod.LOGGER.debug(
            "TACZ: scope lens stencil probe (after draw): pixel=({},{}), sampleBefore={}, sampleAfterSurface={}, "
                + "sampleAfterReticle={}, lensDrew={}, reticleDrew={}, glError=0x{}",
            pixelX,
            pixelY,
            lensProbeColorBefore,
            lensProbeColorAfterSurface,
            sampleAfter,
            lensDrew,
            reticleDrew,
            Integer.toHexString(error));
    }

    /** 只读诊断（复核⑩稳定帧）：lens pass 统计（每个 lens 首次 aim>=0.99 时一条）。 */
    private static void probeLensPassStats(Lens lens, boolean snapshotOk) {
        GunMod.LOGGER.debug(
            "TACZ: scope lens pass stats (stable ADS): lensCount={}, stencil={}, stencilValid={}, reticles={}, "
                + "snapshotIsCaptureTargetColorTexture={}, aim={}",
            LENSES.size(),
            lens.stencil,
            lens.stencilValid,
            lens.reticle.size(),
            snapshotOk,
            String.format("%.2f", currentAimProgress()));
    }

    /** 读 NDC 坐标处的 capture stencil 值（诊断用；读不到返回 -1）。 */
    private static int readStencilAtNdc(float ndcX, float ndcY, int width, int height) {
        int pixelX = Math.max(0, Math.min(width - 1, Math.round((ndcX * 0.5F + 0.5F) * width)));
        int pixelY = Math.max(0, Math.min(height - 1, Math.round((0.5F - ndcY * 0.5F) * height)));
        return readStencilPixel(pixelX, pixelY);
    }

    /** 3x3 平均颜色（RGB；读不到返回 -1）。仅诊断用。 */
    private static int sampleColorAverage3x3(int pixelX, int pixelY, int width, int height) {
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = Math.max(0, Math.min(width - 1, pixelX + dx));
                int y = Math.max(0, Math.min(height - 1, pixelY + dy));
                int rgb = readColorPixel(x, y);
                if (rgb < 0) {
                    return -1;
                }
                red += (rgb >> 16) & 0xFF;
                green += (rgb >> 8) & 0xFF;
                blue += rgb & 0xFF;
            }
        }
        return ((int) (red / 9) << 16) | ((int) (green / 9) << 8) | (int) (blue / 9);
    }

    /**
     * 围绕给定像素做 5 点（中心 + 上下左右各 8px）颜色和采样，降低「镜片画了但中心恰好同色」的误判概率。
     * 读不到时返回 {@code -1}。仅诊断用。
     */
    private static long sampleColorAround(int pixelX, int pixelY, int width, int height) {
        int[][] offsets = { { 0, 0 }, { 8, 0 }, { -8, 0 }, { 0, 8 }, { 0, -8 } };
        long sum = 0;
        for (int[] offset : offsets) {
            int x = Math.max(0, Math.min(width - 1, pixelX + offset[0]));
            int y = Math.max(0, Math.min(height - 1, pixelY + offset[1]));
            int rgb = readColorPixel(x, y);
            if (rgb < 0) {
                return -1;
            }
            sum += (rgb & 0xFF) + ((rgb >> 8) & 0xFF) + ((rgb >> 16) & 0xFF);
        }
        return sum;
    }

    private static String fmt(float value) {
        return String.format("%.3f", value);
    }

    /**
     * 视空间圆心的屏幕像素（诊断采样点）。spec §1.3：视空间圆经透视除法后
     * {@code ndcCenter = (proj.m00*16*cx, proj.m11*16*cy)}；OpenGL 像素原点在左下。
     */
    private static int[] viewSpaceCenterPixel(Lens lens, int width, int height) {
        // lens.centerX/centerY 已是上游 buildSurface 的视空间坐标 (16*90*cx)，透视除法后 NDC = m00*centerX/90。
        float ndcX = lens.projection[0] * lens.centerX / 90.0F;
        float ndcY = lens.projection[5] * lens.centerY / 90.0F;
        int pixelX = Math.max(0, Math.min(width - 1, Math.round((ndcX * 0.5F + 0.5F) * width)));
        int pixelY = Math.max(0, Math.min(height - 1, Math.round((0.5F - ndcY * 0.5F) * height)));
        return new int[] { pixelX, pixelY };
    }

    /** 读当前 READ framebuffer 的一个颜色像素（RGBA8，返回 0xRRGGBB；失败返回 -1）。仅诊断用。 */
    private static int readColorPixel(int pixelX, int pixelY) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        try {
            GL11.glReadPixels(pixelX, pixelY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                return -1;
            }
            return ((buffer.get(0) & 0xFF) << 16) | ((buffer.get(1) & 0xFF) << 8) | (buffer.get(2) & 0xFF);
        } catch (RuntimeException unsupported) {
            return -1;
        }
    }

    /**
     * 读当前 READ framebuffer 的一个模板值（GL3.0 {@code GL_DEPTH_STENCIL + GL_UNSIGNED_INT_24_8}；
     * 驱动不支持时返回 -1）。仅诊断用。
     */
    private static int readStencilPixel(int pixelX, int pixelY) {
        IntBuffer buffer = BufferUtils.createIntBuffer(1);
        try {
            GL11.glReadPixels(pixelX, pixelY, 1, 1, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, buffer);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                return -1;
            }
            // GL_UNSIGNED_INT_24_8 打包：高 24 位深度、低 8 位模板（GL 3.0 规范 4.4.4）。
            return buffer.get(0) & 0xFF;
        } catch (RuntimeException unsupported) {
            return -1;
        }
    }

    /**
     * 列主序 4×4：{@code P * M * v}，返回 NDC {@code {x, y}}；{@code w <= 0}（相机后面）返回 {@code null}。
     */
    private static float @Nullable [] projectPointNdc(float[] projection, float[] modelView, float x, float y,
        float z) {
        float mvX = modelView[0] * x + modelView[4] * y + modelView[8] * z + modelView[12];
        float mvY = modelView[1] * x + modelView[5] * y + modelView[9] * z + modelView[13];
        float mvZ = modelView[2] * x + modelView[6] * y + modelView[10] * z + modelView[14];
        float mvW = modelView[3] * x + modelView[7] * y + modelView[11] * z + modelView[15];
        float clipX = projection[0] * mvX + projection[4] * mvY + projection[8] * mvZ + projection[12] * mvW;
        float clipY = projection[1] * mvX + projection[5] * mvY + projection[9] * mvZ + projection[13] * mvW;
        float clipW = projection[3] * mvX + projection[7] * mvY + projection[11] * mvZ + projection[15] * mvW;
        if (clipW <= 0.0F || !Float.isFinite(clipW)) {
            return null;
        }
        return new float[] { clipX / clipW, clipY / clipW };
    }

    /**
     * 请求释放镜片管线的每世界/每尺寸 GL 资源。<b>可从任意线程调用</b>（断线事件在 Netty IO 线程上触发，
     * 见类注释「线程规则」）：本方法只写 volatile 标记，<b>不碰 GL、也不改渲染线程正在遍历的集合</b>；
     * 真正的清理（清 lens 列表、删 copy 纹理与 capture target）由渲染线程上的 {@link #drainDeletes()} 执行。
     *
     * <p>
     * <b>不销毁着色器程序</b>：程序是注册一次的静态资源（上游 {@code RegisterShadersEvent} 同语义），
     * 且 {@code init()} 只在客户端初始化时调用一次 —— 若在这里销毁，一次断线后整局游戏都没有镜片/热成像效果。
     */
    public static void close() {
        ScopeThermalRenderer.close();
        ScopeLensContour.close();
        closeRequested = true;
        deletePending = true;
    }

    /**
     * 渲染线程每帧调用：执行 {@link #close()} 攒下的清理与 GL 删除。幂等且便宜（一次 volatile boolean 读），
     * 可以从 {@link #render()} 开头与 {@code RenderTickEvent.START} 两处重复调用。
     */
    public static void drainDeletes() {
        if (!deletePending) {
            return;
        }
        deletePending = false;
        if (closeRequested) {
            closeRequested = false;
            beginFrame();
            destroyCopyTexture();
            for (CaptureStencil stencil : CAPTURE_STENCILS) {
                stencil.enqueueDeletion();
            }
            CAPTURE_STENCILS.clear();
        }
        Integer framebuffer;
        while ((framebuffer = PENDING_FRAMEBUFFERS.poll()) != null) {
            GL30.glDeleteFramebuffers(framebuffer);
        }
        Integer renderbuffer;
        while ((renderbuffer = PENDING_RENDERBUFFERS.poll()) != null) {
            GL30.glDeleteRenderbuffers(renderbuffer);
        }
        Integer texture;
        while ((texture = PENDING_TEXTURES.poll()) != null) {
            GL11.glDeleteTextures(texture);
        }
        ScopeThermalRenderer.drainDeletes();
    }

    // ================================================================================================
    // 内部实现
    // ================================================================================================

    /**
     * 画一次镜片（上游 {@code ScopeLensRenderer#drawLens:224-271}）。
     *
     * <p>
     * 几何 = 上游 {@code buildSurface:273-284} 的**视空间圆**（与 {@code BedrockAttachmentModel} 的
     * INVERT 圆逐参数相同）：顶点 {@code (centerX, centerY, SURFACE_Z)}、半径 {@code radius}，
     * 由 {@code ProjMat/ModelViewMat}（= capture 时刻的 {@code GL_PROJECTION/GL_MODELVIEW}）变换。
     * 上游 {@code RenderSystem} 在 capture 时 {@code ModelViewMat} 恒为单位矩阵，本移植
     * {@code FirstPersonRenderHandler:463-465,599-616} 同构（手部投影 + {@code glLoadIdentity}），
     * 因此直接喂 {@code lens.projection/lens.modelView}。
     *
     * <p>
     * 无 stencil 的平台降级路径（{@code MainFramebufferStencil} 补挂失败时）没有模板裁剪，
     * 视空间圆会盖满屏幕，因此仍用 NDC 椭圆（spec §9-⑩ 白名单）；有模板时必须用视空间圆。
     *
     * @param screenTexture Sampler0 / Sampler2 采样的「屏幕画面」纹理：stencil 路径传 capture target 自己的
     *                      颜色纹理（上游 {@code drawLens:226-234}：先拷当前主画面再采样）；无 stencil 路径传
     *                      手部之前拷好的 {@link #copyTexture}
     * @param viewSpace     {@code true} = 上游视空间圆（stencil 路径）；{@code false} = 无 stencil 降级用 NDC 椭圆
     */
    private static void drawLens(Lens lens, int width, int height, int screenTexture, boolean viewSpace) {
        program.use();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ScopeThermalRenderer.textureOr(screenTexture, width, height));
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        program.set1i("Sampler0", 0);
        program.set1i("Sampler1", 1);
        program.set1i("Sampler2", 2);

        program.set2f("ScreenSize", width, height);
        program.set2f("LensCenter", lens.centerU, lens.centerV);
        program.set2f("LensRadius", lens.radiusU, lens.radiusV);
        // 轮廓通道不可用（原因见 ScopeLensContour），走上游的椭圆兜底分支。
        program.set1i("ContourReady", 0);
        program.set4f("ContourRect", 0.0F, 0.0F, 1.0F, 1.0F);
        program.set2f("ContourSize", 1.0F, 1.0F);
        ScopeLensConfig config = lens.config;
        program.set1f("Distortion", config.getDistortion());
        program.set1f("Aberration", config.getAberration());
        program.set1f("EdgeWidth", config.getEdgeWidth());
        program.set1f("VignetteStrength", config.getVignetteStrength());
        program.set1f("VignetteWidth", config.getVignetteWidth());
        int tint = config.getTint();
        float strength = config.getTintStrength();
        program.set3f(
            "LensTint",
            1 + (((tint >> 16) & 0xFF) / 255f - 1) * strength,
            1 + (((tint >> 8) & 0xFF) / 255f - 1) * strength,
            1 + ((tint & 0xFF) / 255f - 1) * strength);
        ScopeVisionMode mode = config.getVisionMode();
        if (mode.isThermal() && !RenderConfig.SCOPE_THERMAL_ENABLE.get()) {
            mode = ScopeVisionMode.NORMAL;
        }
        // r6 诊断：热成像瞄具但热掩膜没准备好时，着色器会退化成「只有染色/暗角」的普通画面
        // （实机表现为整片均匀紫色/普通画面）。这里只 WARN 一条，并给出具体原因。
        boolean thermalReady = ScopeThermalRenderer.isReady(width, height);
        if (mode.isThermal() && !thermalReady && !thermalNotReadyWarned) {
            thermalNotReadyWarned = true;
            GunMod.LOGGER.warn(
                "TACZ: thermal scope lens is active but the thermal mask is NOT ready (ThermalReady=0): {}. "
                    + "The lens will show plain optical tint instead of the heat palette.",
                ScopeThermalRenderer.describeNotReady(width, height));
        }
        program.set1i("VisionMode", mode.ordinal());
        program.set1i("ThermalReady", thermalReady ? 1 : 0);
        program.set1f("VisionGain", config.getVisionGain());
        program.set1f("VisionNoise", config.getVisionNoise());
        program.set1f("SensorTime", (System.currentTimeMillis() % 60000L) / 1000f);
        // 只读诊断（复核⑧）：镜片 config（tint/vision）——「整片半透明绿色」若来自 config 的
        // tint/vision_mode，这里会直接暴露（tint 用 #RRGGBB 打印，vision 打印实际生效值）。
        if (!lensConfigLogged) {
            lensConfigLogged = true;
            GunMod.LOGGER.debug(
                "TACZ: scope lens config: tint=#{}, tintStrength={}, vision={} (raw={}), thermalEnabled={}, "
                    + "thermalReady={}, distortion={}, edgeWidth={}, vignette={}/{}",
                String.format("%06X", tint & 0xFFFFFF),
                fmt(strength),
                mode,
                config.getVisionMode(),
                RenderConfig.SCOPE_THERMAL_ENABLE.get(),
                thermalReady,
                fmt(config.getDistortion()),
                fmt(config.getEdgeWidth()),
                fmt(config.getVignetteStrength()),
                fmt(config.getVignetteWidth()));
        }
        // 只读诊断（复核⑩稳定帧）：shader uniform 快照（若稳定帧 stencil=254、lensDrew=true、快照=世界色，
        // 但仍黑/绿 ⇒ 问题在 shader 采样/输出，用这条判读 uniform 是否合理）。
        if (!lensUniformLogged && currentAimProgress() >= 0.99F) {
            lensUniformLogged = true;
            GunMod.LOGGER.debug(
                "TACZ: scope lens uniforms (stable ADS): ScreenSize=({},{}), LensCenter=({},{}), "
                    + "LensRadius=({},{}), LensTint=({},{},{}), VisionMode={}, ThermalReady={}, ContourReady=0, "
                    + "Distortion={}, Aberration={}, EdgeWidth={}, Vignette={}/{}, aim={}",
                width,
                height,
                fmt(lens.centerU),
                fmt(lens.centerV),
                fmt(lens.radiusU),
                fmt(lens.radiusV),
                fmt(1 + (((tint >> 16) & 0xFF) / 255f - 1) * strength),
                fmt(1 + (((tint >> 8) & 0xFF) / 255f - 1) * strength),
                fmt(1 + ((tint & 0xFF) / 255f - 1) * strength),
                mode.ordinal(),
                thermalReady,
                fmt(config.getDistortion()),
                fmt(config.getAberration()),
                fmt(config.getEdgeWidth()),
                fmt(config.getVignetteStrength()),
                fmt(config.getVignetteWidth()),
                String.format("%.2f", currentAimProgress()));
        }
        if (viewSpace) {
            // 上游 :184-193：ProjMat = capture 投影（手部 FOV）、ModelViewMat = capture 模型视图（单位矩阵）；
            // 几何 = buildSurface 视空间圆（与 INVERT 圆同一份参数）。
            program.setMatrix4f("ProjMat", lens.projection);
            program.setMatrix4f("ModelViewMat", lens.modelView);
            drawViewSpaceDisc(lens.centerX, lens.centerY, lens.radius);
        } else {
            // 平台降级（无 stencil）：没有模板裁剪，视空间圆会盖满屏幕 ⇒ 用 NDC 椭圆（spec §9-⑩）。
            float[] identity = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
            program.setMatrix4f("ProjMat", identity);
            program.setMatrix4f("ModelViewMat", identity);
            drawNdcDisc(
                lens.centerU * 2.0F - 1.0F,
                lens.centerV * 2.0F - 1.0F,
                lens.radiusU * 2.0F,
                lens.radiusV * 2.0F);
        }
        program.release();
    }

    /**
     * 上游 {@code ScopeLensRenderer#buildSurface:273-284}：视空间圆（TRIANGLE_FAN，91 顶点，{@code SURFACE_Z=-90}）。
     *
     * <p>
     * 该函数在 {@code program.use()} 生效期间调用 ⇒ 顶点变换完全由 {@code ProjMat*ModelViewMat} uniform 决定
     * （等价上游 {@code BufferUploader.drawWithShader}）；{@code attribute vec3 Position} 通过
     * {@code glVertexPointer} → generic attribute 0 的别名读取。
     */
    private static void drawViewSpaceDisc(float centerX, float centerY, float radius) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_TRIANGLE_FAN);
        tessellator.addVertex(centerX, centerY, SURFACE_Z);
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i * Math.PI * 2 / SEGMENTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            tessellator.addVertex(centerX + cos * radius, centerY + sin * radius, SURFACE_Z);
        }
        tessellator.draw();
    }

    /**
     * 画镜片几何：NDC 椭圆（capture 时刻目镜几何投影出的 {@code Bounds}）。
     *
     * <p>
     * 用 1.7.10 的 {@link Tessellator} 立即绘制：着色器的 {@code attribute vec3 Position} 固定绑在
     * generic attribute 0 上，而 {@code glVertexPointer} 正好别名到该槽位（compatibility profile），
     * 因此不需要额外建 VBO。
     */
    private static void drawNdcDisc(float centerX, float centerY, float radiusX, float radiusY) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_TRIANGLE_FAN);
        tessellator.addVertex(centerX, centerY, 0.0D);
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i * Math.PI * 2 / SEGMENTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            tessellator.addVertex(centerX + cos * radiusX, centerY + sin * radiusY, 0.0D);
        }
        tessellator.draw();
    }

    /**
     * 画 reticle（division 分割面）。上游 {@code render():196-209}：每个 reticle 在
     * {@code draw.type.setupRenderState()} 之后<b>重新 {@code target.bindWrite(false)}</b>（RenderType 的输出状态
     * 可能绑定别的 target）、关深度测试/深度写，再 {@code BufferUploader.drawWithShader(vertices)}。
     *
     * <p>
     * 1.7.10 的 mcshim {@code RenderType#setupRenderState()} 不改 FBO 绑定，但仍按上游补上重绑 + 重设模板状态：
     * 这是「reticles=1 但屏幕上看不到」的第一嫌疑点（r7 实机复核③）。
     */
    private static void drawReticle(Lens lens, CaptureStencil stencil, int mainColorTexture) {
        boolean probe = !reticleDrawProbeLogged;
        if (probe) {
            reticleDrawProbeLogged = true;
        }
        int draws = 0;
        int verticesTotal = 0;
        int errorBefore = GL11.glGetError();
        for (Draw draw : lens.reticle) {
            draws++;
            BufferBuilder.RenderedBuffer vertices = draw.vertices;
            draw.vertices = null;
            if (vertices == null) {
                continue;
            }
            verticesTotal += vertices.vertexCount();
            // 上游 target.bindWrite(false)：保证 RenderType setup 之后仍然画到 capture target（= 主画面）。
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, stencil.framebuffer);
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                mainColorTexture,
                0);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_EQUAL, lens.stencil, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            draw.type.setupRenderState();
            try {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                BufferUploader.draw(vertices);
            } finally {
                draw.type.clearRenderState();
            }
        }
        if (probe) {
            drainGlErrors();
            float[] bbox = lens.reticleBbox;
            String camera;
            String ndc;
            if (bbox == null || verticesTotal <= 0) {
                camera = "camera=<empty>";
                ndc = "ndc=<none>";
            } else {
                camera = String.format(
                    "camera=[%.3f,%.3f,%.3f..%.3f,%.3f,%.3f]",
                    bbox[0],
                    bbox[1],
                    bbox[2],
                    bbox[3],
                    bbox[4],
                    bbox[5]);
                float ndcMinX = Float.POSITIVE_INFINITY;
                float ndcMinY = Float.POSITIVE_INFINITY;
                float ndcMaxX = Float.NEGATIVE_INFINITY;
                float ndcMaxY = Float.NEGATIVE_INFINITY;
                for (int corner = 0; corner < 8; corner++) {
                    float x = (corner & 1) == 0 ? bbox[0] : bbox[3];
                    float y = (corner & 2) == 0 ? bbox[1] : bbox[4];
                    float z = (corner & 4) == 0 ? bbox[2] : bbox[5];
                    float[] point = projectPointNdc(lens.projection, lens.modelView, x, y, z);
                    if (point == null) {
                        continue;
                    }
                    ndcMinX = Math.min(ndcMinX, point[0]);
                    ndcMinY = Math.min(ndcMinY, point[1]);
                    ndcMaxX = Math.max(ndcMaxX, point[0]);
                    ndcMaxY = Math.max(ndcMaxY, point[1]);
                }
                ndc = ndcMinX <= ndcMaxX
                    ? String.format("ndc=[%.3f,%.3f..%.3f,%.3f]", ndcMinX, ndcMinY, ndcMaxX, ndcMaxY)
                    : "ndc=<behind camera>";
            }
            GunMod.LOGGER.debug(
                "TACZ: scope reticle probe: draws={}, vertices={}, {}, {}, stencilRef={}, glErrorBefore=0x{}, "
                    + "glErrorAfter=0x{}",
                draws,
                verticesTotal,
                camera,
                ndc,
                lens.stencil,
                Integer.toHexString(errorBefore),
                Integer.toHexString(GL11.glGetError()));
        }
    }

    private static void ensureCopyTexture(int width, int height) {
        if (copyTexture >= 0 && copyTextureWidth == width && copyTextureHeight == height) {
            return;
        }
        destroyCopyTexture();
        // 只在 unit 0 上建/绑纹理：调用方的 RenderState 会还原 unit 0 的绑定与活动单元，
        // 不碰 unit 1（1.7.10 的 lightmap 单元）。
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        copyTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            width,
            height,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            (ByteBuffer) null);
        copyTextureWidth = width;
        copyTextureHeight = height;
    }

    private static void destroyCopyTexture() {
        if (copyTexture >= 0) {
            GL11.glDeleteTextures(copyTexture);
        }
        copyTexture = -1;
        copyTextureWidth = -1;
        copyTextureHeight = -1;
    }

    /**
     * 把主 framebuffer 的颜色拷进 {@link #copyTexture}。上游用 {@code GlStateManager#_glCopyTexSubImage2D}，
     * 1.7.10 直接调 {@code GL11.glCopyTexSubImage2D}；本方法自己绑一次主 framebuffer，避免依赖调用方顺序。
     */
    private static void copyScreen(Framebuffer main, int width, int height) {
        main.bindFramebuffer(false);
        // 只改 unit 0 的绑定（活动单元由调用方的 RenderState 还原）。
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTexture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    /**
     * 主 framebuffer 是否有模板附件：1.7.10 的 {@link Framebuffer} 只在「窗口申请了 stencil bits」时才把深度缓冲建成
     * depth-stencil 并挂成 {@code STENCIL_ATTACHMENT}（Framebuffer.java:117-130）。这里查一次 FBO 附件类型，需要
     * GL 3.0 的 {@code glGetFramebufferAttachmentParameteri}，拿不到就当没有。
     */
    private static boolean stencilAvailable() {
        ContextCapabilities capabilities = GLContext.getCapabilities();
        if (!capabilities.OpenGL30) {
            return false;
        }
        try {
            int bound = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            if (bound == 0) {
                return false;
            }
            int type = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            return type != GL11.GL_NONE;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 当前开镜进度 0..1（只用于诊断日志的触发时机；拿不到时按 1 处理）。 */
    private static float currentAimProgress() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return 1.0F;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) {
            return 1.0F;
        }
        // 上游 Minecraft#getFrameTime() → ClientTickHelper#getPartialTick()（同一帧内常量）。
        float progress = operator.getClientAimingProgress(ClientTickHelper.getPartialTick());
        return Float.isFinite(progress) ? Math.max(0.0F, Math.min(1.0F, progress)) : 1.0F;
    }

    /**
     * r6-3：玩家是否处于 ADS（按下 ADS 即 true 的客户端布尔状态）。
     *
     * <p>
     * 与 {@link #currentAimProgress()} 的区别：进度是插值量、异常读数会让「已开镜」被判成 0（HAMR 黑框的
     * 触发条件之一：已经隐藏镜身却没有镜片画面，或者反过来把 division 大黑面画出来）。这里只读布尔状态，
     * 拿不到（MixIn 未应用等）时返回 {@code false}，行为退回本轮之前的路径。
     */
    public static boolean isAimingNow() {
        try {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return false;
            }
            return IClientPlayerGunOperator.fromLocalPlayer(player)
                .isAim();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /** 读当前 GL 的 projection / modelview（上游读 {@code RenderSystem} 的两个缓存矩阵）。 */
    private static void readMatrices(float[] projection, float[] modelView) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, buffer);
        buffer.rewind();
        buffer.get(projection);
        buffer.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        buffer.rewind();
        buffer.get(modelView);
    }

    /** 把 GL_PROJECTION / GL_MODELVIEW 都置成 identity（镜片圆盘直接给 NDC 坐标）。 */
    private static void loadIdentityMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
    }

    /**
     * 还原 capture 时刻的 GL_PROJECTION / GL_MODELVIEW（画 deferred reticle 用）。
     *
     * <p>
     * 修复前没有这一步：reticle 顶点是 capture 时烘进相机空间的坐标，却用 identity 投影画 ⇒ 被当成 NDC ⇒
     * 一整块巨型黑四边形（实测截图 docs/review/r6/screenshots/b,d）。上游对每个 lens 调
     * {@code RenderSystem.setProjectionMatrix(lens.projection)} + {@code applyModelViewMatrix(lens.modelView)}
     * （上游 ScopeLensRenderer.java:184-187），这里用固定管线等价物。
     */
    private static void loadMatrices(float[] projection, float[] modelView) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(toFloatBuffer(projection));
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadMatrix(toFloatBuffer(modelView));
    }

    private static FloatBuffer toFloatBuffer(float[] values) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(values);
        buffer.flip();
        return buffer;
    }

    /** 上游 {@code Bounds}：把镜筒顶点投影到屏幕 UV，取包围盒当椭圆近似用。 */
    private static final class Bounds implements VertexConsumer {

        private final Matrix4f projection;
        private final Vector4f point = new Vector4f();
        private float minU = Float.POSITIVE_INFINITY;
        private float minV = Float.POSITIVE_INFINITY;
        private float maxU = Float.NEGATIVE_INFINITY;
        private float maxV = Float.NEGATIVE_INFINITY;
        private boolean behindCamera;

        Bounds(float[] projection, float[] modelView) {
            this.projection = new Matrix4f().set(toFloatBuffer(projection))
                .mul(new Matrix4f().set(toFloatBuffer(modelView)));
        }

        /**
         * 投影包围盒是否可用（上游 {@code Bounds#isValid} 同语义）。
         *
         * <p>
         * <b>用途仅限 shader uniform</b> {@code LensCenter/LensRadius/Contour*}（上游 :236-243）——
         * 镜片/模板圆几何来自 {@code buildSurface} 的视空间圆，**不来自 Bounds**（spec §0-1/§1.3）。
         * 有任何顶点在相机平面后面（{@code w <= 0}）即判无效，{@link #capture} 返回 false
         * （= 上游行为；该 scope 走「division 立即画」的局部降级）。
         */
        boolean isValid() {
            return !behindCamera && Float.isFinite(minU)
                && Float.isFinite(minV)
                && Float.isFinite(maxU)
                && Float.isFinite(maxV)
                && maxU - minU > 0.00001f
                && maxV - minV > 0.00001f;
        }

        /** 诊断用：是否有顶点跨到相机平面后面（仅用于失败原因日志，不改变行为）。 */
        boolean isBehindCamera() {
            return behindCamera;
        }

        float centerU() {
            return (minU + maxU) * 0.5f;
        }

        float centerV() {
            return (minV + maxV) * 0.5f;
        }

        float radiusU() {
            return (maxU - minU) * 0.5f;
        }

        float radiusV() {
            return (maxV - minV) * 0.5f;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            point.set((float) x, (float) y, (float) z, 1)
                .mul(projection);
            if (point.w <= 0.0001f || !Float.isFinite(point.w)) {
                behindCamera = true;
                return this;
            }
            float u = point.x / point.w * 0.5f + 0.5f;
            float v = point.y / point.w * 0.5f + 0.5f;
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(float red, float green, float blue, float alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {}
    }

    /** 上游 {@code Draw}：一个 {@link RenderType} + 已结束的顶点缓冲。 */
    private static final class Draw {

        final RenderType type;
        BufferBuilder.RenderedBuffer vertices;

        Draw(RenderType type, BufferBuilder.RenderedBuffer vertices) {
            this.type = type;
            this.vertices = vertices;
        }
    }

    /** 上游 {@code ReticleBuffers}：按 {@link RenderType} 分桶录制的 {@link MultiBufferSource}。 */
    private static final class ReticleBuffers implements MultiBufferSource {

        private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        private final Map<RenderType, BufferBuilder> active = new LinkedHashMap<>();
        /** r7 实机复核③诊断：录制时顺带统计顶点包围盒（相机空间），用于判读 reticle 是否被投影到屏幕外。 */
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            BufferBuilder builder = active.computeIfAbsent(type, key -> {
                BufferBuilder created = builders
                    .computeIfAbsent(key, value -> new BufferBuilder(Math.max(256, value.bufferSize())));
                created.begin(key.mode(), key.format());
                return created;
            });
            // 顶点仍写进同一个 BufferBuilder，只是多包一层统计位置（不影响绘制语义）。
            return new BboxConsumer(builder, this);
        }

        void trackVertex(double x, double y, double z) {
            minX = Math.min(minX, (float) x);
            minY = Math.min(minY, (float) y);
            minZ = Math.min(minZ, (float) z);
            maxX = Math.max(maxX, (float) x);
            maxY = Math.max(maxY, (float) y);
            maxZ = Math.max(maxZ, (float) z);
        }

        /** {@code {minX,minY,minZ,maxX,maxY,maxZ}}；没有任何顶点时返回 {@code null}。 */
        float @Nullable [] bbox() {
            if (minX > maxX) {
                return null;
            }
            return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
        }

        List<Draw> finish() {
            List<Draw> draws = new ArrayList<>();
            active.forEach((type, builder) -> draws.add(new Draw(type, builder.end())));
            active.clear();
            return draws;
        }

        void discard() {
            for (BufferBuilder builder : active.values()) {
                BufferBuilder.RenderedBuffer buffer = builder.end();
                if (buffer != null) {
                    buffer.release();
                }
            }
            active.clear();
        }

        /** 只统计顶点位置的转发层（其余属性原样交给 {@link BufferBuilder}）。 */
        private static final class BboxConsumer implements VertexConsumer {

            private final VertexConsumer delegate;
            private final ReticleBuffers owner;

            BboxConsumer(VertexConsumer delegate, ReticleBuffers owner) {
                this.delegate = delegate;
                this.owner = owner;
            }

            @Override
            public VertexConsumer vertex(double x, double y, double z) {
                owner.trackVertex(x, y, z);
                return delegate.vertex(x, y, z);
            }

            @Override
            public VertexConsumer color(float red, float green, float blue, float alpha) {
                return delegate.color(red, green, blue, alpha);
            }

            @Override
            public VertexConsumer color(int red, int green, int blue, int alpha) {
                return delegate.color(red, green, blue, alpha);
            }

            @Override
            public VertexConsumer uv(float u, float v) {
                return delegate.uv(u, v);
            }

            @Override
            public VertexConsumer overlayCoords(int u, int v) {
                return delegate.overlayCoords(u, v);
            }

            @Override
            public VertexConsumer uv2(int u, int v) {
                return delegate.uv2(u, v);
            }

            @Override
            public VertexConsumer normal(float x, float y, float z) {
                return delegate.normal(x, y, z);
            }

            @Override
            public void endVertex() {
                delegate.endVertex();
            }
        }
    }

    /** 上游 {@code Lens}：一次登记下来的镜片绘制数据。 */
    private static final class Lens {

        final int stencil;
        /** capture 时主 framebuffer 是否真有模板附件（且模板副本 blit 成功）；false 时不画 reticle。 */
        final boolean stencilValid;
        final ScopeLensConfig config;
        /** {@code Bounds} 的屏幕 UV 包围盒：**只用于 shader uniform** {@code LensCenter/LensRadius/Contour*}（上游 :236-243）。 */
        final float centerU;
        final float centerV;
        final float radiusU;
        final float radiusV;
        /**
         * 上游几何参数（{@code ScopeLensRenderer#buildSurface:273-284} / {@code BedrockAttachmentModel:290-299}）：
         * {@code centerX/centerY} = pose space 目镜中心（格），{@code radius} = {@code 80*modifier*aimProgress}。
         * 镜片 surface 与模板 INVERT 圆都用这三个值画**同一份视空间圆**（顶点 {@code (16*90*cx, 16*90*cy, -90)}）。
         */
        final float centerX;
        final float centerY;
        final float radius;
        final List<Draw> reticle;
        /** r7 诊断：reticle 顶点的相机空间包围盒 {@code {minX,minY,minZ,maxX,maxY,maxZ}}；无顶点时 null。 */
        final float @Nullable [] reticleBbox;
        /** capture 时刻的 GL_PROJECTION / GL_MODELVIEW（列主序 16 float）：镜片 surface 的 uniform + reticle 的固定管线矩阵。 */
        final float[] projection;
        final float[] modelView;
        /** capture 时刻的模板缓冲副本；stencil 路径的绘制目标（见 {@link #drawLensWithStencil}）。 */
        final @Nullable CaptureStencil captureStencil;

        Lens(int stencil, boolean stencilValid, ScopeLensConfig config, Bounds bounds, List<Draw> reticle,
            float @Nullable [] reticleBbox, float centerX, float centerY, float radius, float[] projection,
            float[] modelView, @Nullable CaptureStencil captureStencil) {
            this.stencil = stencil;
            this.stencilValid = stencilValid;
            this.config = config;
            this.reticle = reticle;
            this.reticleBbox = reticleBbox;
            this.centerU = bounds.centerU();
            this.centerV = bounds.centerV();
            this.radiusU = bounds.radiusU();
            this.radiusV = bounds.radiusV();
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.projection = projection;
            this.modelView = modelView;
            this.captureStencil = captureStencil;
        }
    }

    /**
     * 每个 lens 一份的「capture 时刻模板缓冲」离屏目标（上游 {@code Capture#target#enableStencil()} 的等价物）。
     *
     * <p>
     * 只用于帧末的模板测试：绘制时把主 framebuffer 的颜色纹理临时挂成 {@code COLOR_ATTACHMENT0}，像素仍写回
     * 主画面。平时挂一个与目标同尺寸的 dummy 颜色纹理保证 FBO 完整（用户 FBO 的默认 draw buffer 是
     * {@code COLOR_ATTACHMENT0}；与颜色/深度附件同尺寸也能避免驱动对尺寸不一致的挑剔）。
     * 渲染线程独占；{@link #enqueueDeletion()} 可从任意线程调用。
     */
    private static final class CaptureStencil {

        private int framebuffer = -1;
        private int renderbuffer = -1;
        private int dummyColorTexture = -1;
        private int width = -1;
        private int height = -1;

        /** 创建/复用目标；失败返回 {@code false}（调用方退回无模板圆盘）。仅渲染线程。 */
        boolean ensure(int targetWidth, int targetHeight) {
            if (framebuffer >= 0 && width == targetWidth && height == targetHeight) {
                return true;
            }
            enqueueDeletion();
            // capture 发生在模型渲染中途，必须只动 unit 0 / renderbuffer 绑定并原样还原，
            // 否则会踩到 1.7.10 的 lightmap 单元（unit 1）或后续 renderbuffer 操作。
            int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            framebuffer = GL30.glGenFramebuffers();
            renderbuffer = GL30.glGenRenderbuffers();
            dummyColorTexture = GL11.glGenTextures();
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, dummyColorTexture);
                // 上游 prepareCapture:151 `target.setFilterMode(GL_LINEAR)`（capture 自己的颜色纹理 = 镜片采样源），
                // 以及 RenderTarget#createBuffers:114-115 的 GL_CLAMP_TO_EDGE（spec §3.3-②③）。
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    targetWidth,
                    targetHeight,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
                GL30.glRenderbufferStorage(
                    GL30.GL_RENDERBUFFER,
                    EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT,
                    targetWidth,
                    targetHeight);
                GL30.glFramebufferRenderbuffer(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_RENDERBUFFER,
                    renderbuffer);
                GL30.glFramebufferRenderbuffer(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER,
                    renderbuffer);
                GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    dummyColorTexture,
                    0);
                if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    return false;
                }
                width = targetWidth;
                height = targetHeight;
                return true;
            } finally {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                GL13.glActiveTexture(activeTexture);
            }
        }

        /** 把源 framebuffer 的模板缓冲 blit 进来；失败返回 {@code false}。仅渲染线程。 */
        boolean copyStencilFrom(int sourceFramebuffer, int targetWidth, int targetHeight) {
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            // 先排空此前遗留的 GL 错误，保证下面读到的错误确实来自这次 blit。
            for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
                // drain
            }
            GL30.glBlitFramebuffer(
                0,
                0,
                targetWidth,
                targetHeight,
                0,
                0,
                targetWidth,
                targetHeight,
                GL11.GL_STENCIL_BUFFER_BIT,
                GL11.GL_NEAREST);
            int error = GL11.glGetError();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            return error == GL11.GL_NO_ERROR;
        }

        /** 把 GL 对象 id 挪进「渲染线程待删」队列（可从任意线程调用）。 */
        void enqueueDeletion() {
            if (framebuffer >= 0) {
                PENDING_FRAMEBUFFERS.add(framebuffer);
                framebuffer = -1;
            }
            if (renderbuffer >= 0) {
                PENDING_RENDERBUFFERS.add(renderbuffer);
                renderbuffer = -1;
            }
            if (dummyColorTexture >= 0) {
                PENDING_TEXTURES.add(dummyColorTexture);
                dummyColorTexture = -1;
            }
            width = -1;
            height = -1;
        }
    }

    /**
     * 上游 {@code RenderState}：把镜片/热掩膜 pass 会碰的 GL 状态存一份，结束时原样恢复。
     *
     * <p>
     * 必须<b>按 unit 0/1/2 全量快照 + 还原</b>，并把 {@code GL_ACTIVE_TEXTURE} 也还原回进入时的值：
     * {@link #drawLens} 依次把 unit 0/1/2 绑成镜片纹理（copyTexture / 热成像纹理 / copyTexture）后只把活动单元切回 0，
     * unit 1、unit 2 的绑定被永久改掉。而 1.7.10 的 <b>unit 1 就是 lightmap 单元</b>
     * （{@code OpenGlHelper.lightmapTexUnit}，见 {@code EntityRenderer#enableLightmap}）⇒ 同帧后续任何「开 lightmap
     * + 逐顶点 lightmap 坐标」的绘制（第一人称枪体、粒子、实体）都会采错图（整片变亮/变暗/花屏，而 unit 0 的贴图是
     * 对的，很难定位）。{@link ScopeThermalRenderer} 的 {@code ensureTarget} 也会在当前活动单元上 bind 自己的 FBO
     * 颜色纹理。
     *
     * <p>
     * 另外快照本类会改的固定管线状态：viewport / scissor / colorMask / depth / blend / cull / stencil /
     * polygon offset / clear color+depth / 读-写 framebuffer 绑定（上游 RenderState 同款；漏还原会把
     * 「热掩膜用的黑清屏色 / 深度函数 / 偏移」泄漏给后续 GUI/手部渲染）。
     */
    static final class RenderState implements AutoCloseable {

        private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
        private final IntBuffer scissorBox = BufferUtils.createIntBuffer(16);
        private final ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        private final FloatBuffer clearColor = BufferUtils.createFloatBuffer(4);
        private final int program;
        private final int texture0;
        private final int texture1;
        private final int texture2;
        /** 进入时的活动纹理单元（{@code GL_ACTIVE_TEXTURE}），结束时原样还原。 */
        private final int activeTexture;
        private final boolean depthTest;
        private final boolean depthMask;
        private final int depthFunc;
        private final boolean blend;
        private final boolean cull;
        private final boolean stencil;
        private final int stencilFunc;
        private final int stencilRef;
        private final int stencilValueMask;
        private final int stencilFail;
        private final int stencilDepthFail;
        private final int stencilPass;
        private final boolean scissor;
        private final boolean polygonOffset;
        private final float polygonOffsetFactor;
        private final float polygonOffsetUnits;
        private final float clearDepth;
        /** GL 3.0 才有分离的读/写 framebuffer 绑定与 {@code glGetFramebufferAttachmentParameteri}。 */
        private final boolean gl30;
        private final int readFramebuffer;
        private final int drawFramebuffer;

        RenderState() {
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissorBox);
            GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
            GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            this.program = GL11.glGetInteger(GL20_PROGRAM_BINDING);
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            this.activeTexture = active;
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            this.texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            this.texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            this.texture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(active);
            this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            this.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            this.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            this.stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            this.stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
            this.stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
            this.stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
            this.stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
            this.stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
            this.stencilPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
            this.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            this.polygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
            this.polygonOffsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
            this.polygonOffsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
            this.clearDepth = GL11.glGetFloat(GL11.GL_DEPTH_CLEAR_VALUE);
            ContextCapabilities capabilities = GLContext.getCapabilities();
            this.gl30 = capabilities.OpenGL30;
            if (gl30) {
                this.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
                this.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            } else {
                this.readFramebuffer = -1;
                this.drawFramebuffer = -1;
            }
        }

        @Override
        public void close() {
            GL20.glUseProgram(program);
            // 三个纹理单元的绑定全部还原（unit 1 = 1.7.10 的 lightmap 单元，见类 javadoc）。
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2);
            // 活动单元还原成进入时的值（不是硬留在 TEXTURE0）。
            GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            if (depthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            if (blend) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (cull) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
            GL11.glStencilOp(stencilFail, stencilDepthFail, stencilPass);
            if (stencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            GL11.glScissor(scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3));
            if (scissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glColorMask(
                colorMask.get(0) != 0,
                colorMask.get(1) != 0,
                colorMask.get(2) != 0,
                colorMask.get(3) != 0);
            GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
            if (polygonOffset) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            } else {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            GL11.glClearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
            GL11.glClearDepth(clearDepth);
            if (gl30) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            }
        }

        /** {@code GL_PROGRAM_BINDING_ARB} 的常量值；LWJGL2 的 GL20 里没有导出该查询枚举。 */
        private static final int GL20_PROGRAM_BINDING = 0x8B8D;
    }
}
