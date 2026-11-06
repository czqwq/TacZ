package com.tacz.guns.client.renderer.scope;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.tacz.guns.GunMod;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.util.RenderHelper;

/**
 * 运行期给 1.7.10 主 framebuffer 补 stencil 附件。
 *
 * <p>
 * <b>为什么需要</b>：上游 1.20.1 的瞄具/镜片/热掩膜全部依赖主渲染目标的模板缓冲；Forge 给
 * {@code RenderTarget} 打了 {@code enableStencil()} 补丁，在运行期把深度附件换成
 * {@code GL_DEPTH32F_STENCIL8}（{@code tmp/MinecraftForge-1.20.1/patches/minecraft/com/mojang/blaze3d/
 * pipeline/RenderTarget.java.patch:34-55}），上游 {@code RenderHelper#enableItemEntityStencilTest} 与
 * {@code ScopeLensRenderer.Capture} 都靠它。1.7.10 的 {@link Framebuffer} 只在
 * {@code MinecraftForgeClient.getStencilBits() > 0}（JVM 参数 {@code -Dforge.forceDisplayStencil=true}）时才建
 * depth-stencil（{@code build/rfg/minecraft-src/java/net/minecraft/client/shader/Framebuffer.java:117-130}），
 * 默认没有 ⇒ 模板测试恒通过、上游的模板裁剪路径整组失效。
 *
 * <p>
 * <b>做法</b>（与 Forge 1.20.1 补丁同构，只是目标从 depth texture 换成 1.7.10 的 depth renderbuffer）：
 * 生成一个 {@code GL_DEPTH24_STENCIL8} renderbuffer，同时挂到主 FBO 的 {@code DEPTH_ATTACHMENT} 与
 * {@code STENCIL_ATTACHMENT}，{@code glCheckFramebufferStatus} 通过后替换 {@link Framebuffer#depthBuffer}
 * 并删掉旧 renderbuffer；失败则还原旧深度附件并永久回退（不崩、不刷屏）。主 FBO 由
 * {@code Minecraft#getFramebuffer()} 持有，其字段在 1.7.10 是 public
 * （{@code Framebuffer.java:14-23}），且 {@code depthBuffer} 只被 Framebuffer 自己使用
 * （{@code Framebuffer.java:69-72,108,119-129}），运行期替换是安全的。
 *
 * <p>
 * <b>resize / 全屏切换</b>：1.7.10 的 {@code Framebuffer#createBindFramebuffer} 会
 * {@code deleteFramebuffer()}（连同我们补的 renderbuffer）后重建一个只有 {@code DEPTH_COMPONENT24} 的新 FBO，
 * 因此每帧在 {@link ScopeLensRenderer#beginFrame()}（{@code renderWorld} HEAD，主 FBO 已绑定）检测一次
 * 「主 FBO 当前有没有 stencil 附件」，没有就重新补挂。检测用
 * {@code glGetFramebufferAttachmentParameteri(STENCIL_ATTACHMENT, OBJECT_TYPE)}：无论模板是 Forge 建的、
 * 我们上一帧补的、还是其它模组补的，都能识别；只有真的没有时才动 FBO。
 *
 * <p>
 * <b>环境边界</b>（不满足就永久/定期回退到既有「无 stencil 降级」，见 {@code ScopeLensRenderer} 类注释）：
 * <ul>
 * <li>GL &lt; 3.0（没有非 EXT 的 FBO 查询 API）→ 永久回退；</li>
 * <li>core profile（Angelica/Iris 光影）→ 永久回退（镜片整条链路本来就不成立）；</li>
 * <li>光影包正在使用（{@link OculusCompat#isUsingRenderPack()}）→ 定期重试（用户可能关掉光影）；</li>
 * <li>OptiFine 已安装 → 永久回退（它自己管理 framebuffer/shader 管线）；</li>
 * <li>主 FBO 的深度附件不是 renderbuffer（已被光影模组换成 depth texture）→ 永久回退，绝不覆盖。</li>
 * </ul>
 *
 * <p>
 * 只在渲染线程调用（{@link ScopeLensRenderer#beginFrame()} 的三个调用点都是渲染线程）。
 */
final class MainFramebufferStencil {

    /** 临时不支持（如光影包开着）时，隔多少帧重试一次。 */
    private static final int RETRY_INTERVAL_FRAMES = 100;

    /** 主 FBO 当前确有 stencil 附件（Forge 建的或本类补的）。 */
    private static boolean ready;
    /** 永久失败：不再尝试，走既有降级路径。 */
    private static boolean failed;
    /** 成功/失败/延后日志各只打一条。 */
    private static boolean readyLogged;
    private static boolean failedLogged;
    private static boolean deferredLogged;
    /** 临时不支持时的重试倒计时。 */
    private static int retryCountdown;

    private MainFramebufferStencil() {}

    static boolean isReady() {
        return ready;
    }

    /**
     * 每帧调用（{@link ScopeLensRenderer#beginFrame()}，{@code renderWorld} HEAD）。
     * 已就绪时只有一次 GL 附件查询；未就绪时按环境边界决定补挂 / 延后 / 永久回退。
     */
    static void ensure() {
        if (failed) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        Framebuffer main = mc.getFramebuffer();
        if (main == null || !main.useDepth || main.framebufferObject < 0) {
            ready = false;
            return;
        }
        if (MinecraftForgeClient.getStencilBits() > 0) {
            // Forge 已按 -Dforge.forceDisplayStencil=true 建好 depth-stencil，无需补挂。
            markReady("Forge stencilBits>0");
            return;
        }
        if (retryCountdown > 0) {
            --retryCountdown;
            return;
        }
        if (!GLContext.getCapabilities().OpenGL30) {
            fail("current GL context does not expose OpenGL 3.0 (cannot attach a depth-stencil renderbuffer)");
            return;
        }
        try {
            if (hasStencilAttachment(main)) {
                markReady("existing stencil attachment");
                return;
            }
        } catch (RuntimeException unsupported) {
            fail("glGetFramebufferAttachmentParameteri is not available: " + unsupported.getMessage());
            return;
        }
        ready = false;
        if (ScopeLensRenderer.isCoreProfile()) {
            fail("OpenGL core profile (Angelica/Iris)");
            return;
        }
        if (OculusCompat.isUsingRenderPack()) {
            defer();
            return;
        }
        if (RenderHelper.isOptifineInstalled()) {
            fail("OptiFine is installed (it manages the main framebuffer)");
            return;
        }
        patch(main);
    }

    /** 主 FBO 当前是否有 stencil 附件（查询前临时绑定主 FBO，结束恢复）。 */
    private static boolean hasStencilAttachment(Framebuffer main) {
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean rebind = previousFramebuffer != main.framebufferObject;
        if (rebind) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.framebufferObject);
        }
        try {
            return GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_NONE;
        } finally {
            if (rebind) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            }
        }
    }

    /**
     * 给主 FBO 补挂 {@code GL_DEPTH24_STENCIL8} renderbuffer（与 Forge 1.20.1 的 {@code enableStencil()}
     * 同语义）。失败时还原旧深度附件并永久回退。
     */
    private static void patch(Framebuffer main) {
        int width = main.framebufferTextureWidth;
        int height = main.framebufferTextureHeight;
        if (width <= 0 || height <= 0) {
            return;
        }
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        int previousStencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        int previousClearStencil = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);
        int renderbuffer = -1;
        boolean handedOver = false;
        try {
            if (previousFramebuffer != main.framebufferObject) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.framebufferObject);
            }
            // 二次确认：这期间可能有别的模组补过；已有 stencil 就不要再动。
            int stencilType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (stencilType != GL11.GL_NONE) {
                markReady("existing stencil attachment");
                return;
            }
            // 深度必须是普通 renderbuffer；depth texture 说明被光影模组接管了，绝不覆盖。
            int depthType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (depthType != GL30.GL_RENDERBUFFER) {
                fail(
                    "main framebuffer depth is not a renderbuffer (type=" + depthType
                        + ", managed by a rendering/shader mod)");
                return;
            }
            renderbuffer = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            GL30.glRenderbufferStorage(
                GL30.GL_RENDERBUFFER,
                EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT,
                width,
                height);
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
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                // 回退：摘掉 stencil、恢复旧深度附件（新 renderbuffer 由 finally 删除）。
                GL30.glFramebufferRenderbuffer(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER,
                    0);
                if (main.depthBuffer > 0) {
                    GL30.glFramebufferRenderbuffer(
                        GL30.GL_FRAMEBUFFER,
                        GL30.GL_DEPTH_ATTACHMENT,
                        GL30.GL_RENDERBUFFER,
                        main.depthBuffer);
                }
                fail("attaching a GL_DEPTH24_STENCIL8 renderbuffer failed (status=" + status + ")");
                return;
            }
            int oldDepthBuffer = main.depthBuffer;
            main.depthBuffer = renderbuffer;
            // 所有权已交给 Framebuffer：resize 时 deleteFramebuffer 会负责删除它。
            handedOver = true;
            renderbuffer = -1;
            if (oldDepthBuffer > 0) {
                GL30.glDeleteRenderbuffers(oldDepthBuffer);
            }
            // 新附件内容未定义：清一次 stencil，保证首帧读到 0（之后由瞄具模型路径按需清）。
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            markReady("runtime GL_DEPTH24_STENCIL8 renderbuffer " + width + "x" + height);
        } finally {
            if (!handedOver && renderbuffer >= 0) {
                GL30.glDeleteRenderbuffers(renderbuffer);
            }
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            GL11.glStencilMask(previousStencilWriteMask);
            GL11.glClearStencil(previousClearStencil);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    private static void markReady(String reason) {
        if (ready) {
            return;
        }
        ready = true;
        if (!readyLogged) {
            readyLogged = true;
            GunMod.LOGGER.debug(
                "TACZ: main framebuffer stencil ENABLED ({}); scope masks / reticles / scope-body clipping and "
                    + "thermal stencil use the full path (equivalent to Forge 1.20.1 RenderTarget#enableStencil).",
                reason);
        }
    }

    private static void fail(String reason) {
        ready = false;
        if (failed) {
            return;
        }
        failed = true;
        if (!failedLogged) {
            failedLogged = true;
            GunMod.LOGGER.warn(
                "TACZ: main framebuffer stencil patch DISABLED ({}). Scope masks/reticles and the scope-body "
                    + "stencil clip fall back to the no-stencil degraded path.",
                reason);
        }
    }

    private static void defer() {
        ready = false;
        retryCountdown = RETRY_INTERVAL_FRAMES;
        if (deferredLogged) {
            return;
        }
        deferredLogged = true;
        GunMod.LOGGER.debug(
            "TACZ: main framebuffer stencil patch deferred (a shader pack is in use, Angelica/Iris manages the "
                + "main framebuffer depth); will retry every {} frames.",
            RETRY_INTERVAL_FRAMES);
    }
}
