package com.tacz.guns.client.renderer.scope;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.ClientTickHelper;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.BufferBuilder;
import com.tacz.guns.mcshim.BufferUploader;
import com.tacz.guns.mcshim.DefaultVertexFormat;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.mcshim.VertexFormat;
import com.tacz.guns.util.ThermalImagingHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 热成像瞄具的「热源掩膜」通道：本帧把热源实体录成白色几何画进自己的离屏目标，供
 * {@code scope_lens.fsh} 的 {@code Sampler1} 采样（{@code ThermalReady=1} 时启用热成像调色板）。
 *
 * <p>
 * <b>r6 重写（原实现不生效的原因）</b>：上游靠 {@code MultiBufferSource} 顶点流把实体剪影复制进 mask；
 * 1.7.10 的实体渲染是固定管线 {@code Tessellator} 直接提交、<b>没有可插入的顶点流</b>，且
 * {@link #wrap(MultiBufferSource, Entity)} 在全仓没有调用点 ⇒ 遮罩缓冲恒空 ⇒ 热成像只有均匀调色板、
 * 没有热源高亮（实机截图 docs/review/r6/screenshots/b 的整片紫色）。现改为在实体渲染汇聚点
 * （{@code RenderManager#func_147939_a} 的 HEAD，由 {@code EntityRenderDispatcherMixin} 调用
 * {@link #beginEntity}）<b>直接生成热源包围盒</b>；与上游差异：轮廓由「精确剪影」退化为「盒子」。
 *
 * <p>
 * 1.20 → 1.7.10 要点：{@code TextureTarget} → 自建 FBO（{@code glGenFramebuffers} + RGBA8 颜色纹理挂
 * {@code COLOR_ATTACHMENT0} + 自建 depth texture 挂 {@code DEPTH_ATTACHMENT}；1.7.10 没有
 * {@code TextureTarget}）；场景深度用 {@code glBlitFramebuffer} 把主 framebuffer 的深度
 * （renderbuffer，见 build/rfg/minecraft-src/java/net/minecraft/client/shader/Framebuffer.java:108/117-124）
 * 每帧拷进自己的 depth texture（{@link #blitSceneDepth}；blit 失败才退回旧的「借用附件」路径
 * {@link #resolveSceneDepth}/{@link #attachSceneDepth}）；{@code ShaderInstance scope_thermal_mask} →
 * {@link ScopeLensProgram#compileRaw}（GLSL 120，源码见 {@link #MASK_VERTEX_SOURCE} /
 * {@link #MASK_FRAGMENT_SOURCE}）；{@code RenderLevelStageEvent.Stage.AFTER_PARTICLES} → 无该事件，由
 * {@code RenderWorldLastEvent} 调用 {@link #render()}。
 *
 * <p>
 * 前提：需要 GL 3.0（{@code glBlitFramebuffer}/{@code glCheckFramebufferStatus}/非 EXT 版 FBO API）。
 * 拿不到时 {@link #isReady} 恒 {@code false}，镜片着色器自动走「无热成像数据」分支（只做光学效果），不会崩。
 *
 * <p>
 * Angelica（1.7.10 的 Iris）差异：① 开 shader pack 时主深度从 renderbuffer 换成 depth texture
 * （A/.../shaders/MixinFramebuffer.java:63-91）；深度 blit 用「读/写 framebuffer 绑定」而不是查询附件类型，
 * 两种形态都能拷，且<b>不硬编码 0 号 FBO</b>（用读/写绑定查询并在结束时恢复）。
 * ② core profile 下整条 pass 不成立（重放依赖 {@code glVertexPointer}→attribute 0 别名、{@code glLoadMatrix}、
 * {@code glPushAttrib} 式状态快照，三者都是 compatibility profile 专属）⇒ 由
 * {@link ScopeLensRenderer#isCoreProfile()} 统一跳过（{@code ThermalReady} 恒 0），本类 {@link #render()} 也会在
 * {@link #enabled()} 里早退。
 * ③ 镜片插入点假设会变：Angelica 开 shader pack 时 {@code renderHand} 里的 {@code renderItemInFirstPerson} 被跳过、
 * 手部改由 Iris {@code HandRenderer} 在关卡 pass 内重绘（A/.../shaders/MixinEntityRenderer.java:83-87、
 * A/net/coderbot/iris/pipeline/HandRenderer.java:127-181）⇒「世界已画完、手未画」在 Angelica 下不成立。
 * TODO(L2): 上游 ScopeThermalRenderer.java:142-234 只有「AFTER_PARTICLES」一个插入点、没有 Iris 的手部迁移问题；
 * 1.7.10 + Angelica 下需改成在 Iris 的 final/composite 阶段之前调用，需实测确认后由 mixin-port 调整。
 */
@SideOnly(Side.CLIENT)
public class ScopeThermalRenderer {

    /** 遮罩深度偏移：一格深度斜率盖住亚像素抖动，单位偏移盖住深度取整误差（与上游同值）。 */
    static final float DEPTH_BIAS_FACTOR = -1.0f;
    static final float DEPTH_BIAS_UNITS = -2.0f;

    /** 与上游 {@code shaders/core/scope_thermal_mask.vsh} 对应，降级到 GLSL 120。 */
    static final String MASK_VERTEX_SOURCE = """
        #version 120

        attribute vec3 Position;
        attribute vec4 Color;
        attribute vec2 UV0;

        uniform mat4 ModelViewMat;
        uniform mat4 ProjMat;

        varying vec2 texCoord;
        varying vec4 vertexColor;

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            texCoord = UV0;
            vertexColor = Color;
        }
        """;

    /**
     * 与上游 {@code shaders/core/scope_thermal_mask.fsh} 对应，`texture` → `texture2D`、`out` → `gl_FragColor`。
     *
     * <p>
     * <b>r6 改写</b>：上游用 {@code texture(Sampler0, uv).a < 0.1} 做 cutout 丢弃、并写顶点色。1.7.10 的实体渲染
     * 没有可插入的顶点流（见 {@link #beginEntity}），热源几何改由本类<b>按包围盒直接生成</b>，
     * 顶点 UV / 顶点色与掩膜语义无关 ⇒ 固定输出白色（= 热源）：这样既不依赖 Tessellator 是否真的开启了
     * color array，也保证 {@code Sampler1.r} 读到 1。上游「精确剪影 + 顶点色」因此退化为「白色盒子」，
     * 属平台降级，不是遗漏。
     */
    static final String MASK_FRAGMENT_SOURCE = """
        #version 120

        void main() {
            gl_FragColor = vec4(1.0);
        }
        """;

    private static final Map<VertexFormat, BufferBuilder> BUILDERS = new LinkedHashMap<>();
    private static final Map<VertexFormat, BufferBuilder> ACTIVE = new LinkedHashMap<>();

    private static int program;
    private static int framebuffer = -1;
    private static int colorTexture = -1;
    /**
     * 本类 FBO 自己的深度纹理（r6-2）。场景深度从主 framebuffer blit 过来（见 {@link #blitSceneDepth}）。
     * 主 framebuffer 的深度是 renderbuffer、不能直接采样/挂到第二个 FBO 采样，但 {@code glBlitFramebuffer}
     * 允许 renderbuffer→texture 的深度拷贝。
     */
    private static int depthTexture = -1;
    /** 本帧是否临时借用了主 framebuffer 的深度附件（{@link #blitSceneDepth} 失败时的兜底），结束时必须摘掉。 */
    private static boolean depthBorrowed;
    private static int targetWidth = -1;
    private static int targetHeight = -1;

    // ================================================================================================
    // 跨线程释放（线程规则见类 javadoc）
    // ================================================================================================

    /** {@link #close()} 打过「有 GL 对象待删除」的标记；任何线程可写，只有渲染线程读并清除。 */
    private static volatile boolean deletePending;
    /** {@link #close()} 请求渲染线程释放 FBO/纹理；着色器程序不销毁（与上游 RegisterShadersEvent 同语义）。 */
    private static volatile boolean closeRequested;
    private static int pendingFramebuffer = -1;
    private static int pendingColorTexture = -1;
    private static int pendingDepthTexture = -1;
    private static float[] projection;
    private static float[] modelView;
    private static boolean collecting;
    private static boolean ready;
    /** 每帧只解析一次「热成像开镜 + 手持热成像瞄具」的结论；{@code null} = 本帧还没算过。 */
    private static Boolean frameEnabled;
    private static Boolean thermalScope;

    // ================================================================================================
    // 一次性诊断（r6：热成像没生效时一眼定位；只打一条，不刷屏）
    // ================================================================================================

    /** 本帧/上次热掩膜没跑成的原因（给 {@link #describeNotReady} 用；纯诊断，不影响逻辑）。 */
    private static String notReadyReason;
    private static boolean gl30Warned;
    private static boolean depthResolveWarned;
    private static boolean depthBlitWarned;
    private static boolean incompleteWarned;
    private static boolean targetFailedWarned;
    private static boolean activeLogged;

    private ScopeThermalRenderer() {}

    /**
     * 编译热源遮罩着色器。上游入口是 {@code RegisterShadersEvent}，1.7.10 由客户端初始化调用。
     *
     * <p>
     * <b>只在渲染线程调</b>（本方法直接 {@code glDeleteProgram}）。
     */
    public static void init() {
        // 先把上一轮在别的线程上攒下的删除清掉（例如上一次断线时 close() 打的标记），再重建。
        drainDeletes();
        gl30Warned = false;
        depthResolveWarned = false;
        depthBlitWarned = false;
        incompleteWarned = false;
        targetFailedWarned = false;
        activeLogged = false;
        notReadyReason = null;
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        program = ScopeLensProgram.compileRaw(
            MASK_VERTEX_SOURCE,
            MASK_FRAGMENT_SOURCE,
            new String[] { "Position", "Color", "UV0" },
            new int[] { 0, 3, 8 });
    }

    public static void beginFrame() {
        for (BufferBuilder builder : ACTIVE.values()) {
            BufferBuilder.RenderedBuffer buffer = builder.end();
            if (buffer != null) {
                buffer.release();
            }
        }
        ACTIVE.clear();
        projection = null;
        modelView = null;
        collecting = true;
        ready = false;
        thermalScope = null;
        frameEnabled = null;
    }

    /**
     * 每帧结论缓存版 {@code enabled()}：{@link #beginEntity} 每帧每实体都会调，
     * 必须做到「一次 Boolean 判空」级别的早退，所以整帧只解析一次。
     */
    public static boolean enabled() {
        Boolean cached = frameEnabled;
        if (cached != null) {
            return cached;
        }
        boolean result = computeEnabled();
        frameEnabled = result;
        return result;
    }

    private static boolean computeEnabled() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (program == 0) {
            notReadyReason = "thermal mask shader failed to compile (see earlier GLSL error log)";
            return false;
        }
        if (!RenderConfig.EXPERIMENTAL_SCOPE_LENS.get()) {
            notReadyReason = "config EnableExperimentalScopeLens=false";
            return false;
        }
        if (!RenderConfig.SCOPE_THERMAL_ENABLE.get()) {
            notReadyReason = "config EnableScopeThermal=false";
            return false;
        }
        if (mc.theWorld == null || player == null || mc.renderViewEntity != player) {
            notReadyReason = "no local world/player (or camera entity is not the local player)";
            return false;
        }
        if (mc.gameSettings.thirdPersonView != 0 || mc.gameSettings.hideGUI) {
            notReadyReason = "third-person view or hideGUI (F1)";
            return false;
        }
        if (OculusCompat.isRenderShadow()) {
            notReadyReason = "Oculus/Iris shadow pass";
            return false;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null || operator.getClientAimingProgress(ClientTickHelper.getPartialTick()) <= 0.001f) {
            notReadyReason = "not aiming (aimingProgress<=0.001)";
            return false;
        }
        // 每帧只解析一次配件 NBT / 变体，而不是每个实体绘制都解析一次。
        if (thermalScope == null) {
            thermalScope = hasThermalScope(player.getHeldItem());
        }
        if (!thermalScope) {
            notReadyReason = "held gun's scope is not a thermal scope (hasThermalScope=false)";
        } else {
            notReadyReason = null;
        }
        return thermalScope;
    }

    /** {@link #notReadyReason} 的只读诊断文本（供 {@code ScopeLensRenderer} 打 WARN 用）。 */
    public static String describeNotReady(int width, int height) {
        if (program == 0) {
            return "thermal mask shader not compiled";
        }
        if (notReadyReason != null) {
            return notReadyReason;
        }
        if (!ready) {
            return "thermal mask pass did not run this frame (see next thermal WARN line for the exact early return)";
        }
        if (targetWidth != width || targetHeight != height) {
            return "thermal target size mismatch: target=" + targetWidth
                + "x"
                + targetHeight
                + " main="
                + width
                + "x"
                + height;
        }
        return "thermal mask ready but texture missing";
    }

    // ================================================================================================
    // 热源遮罩几何收集（由 mixin/client/EntityRenderDispatcherMixin 在
    // RenderManager#func_147939_a 的 HEAD / RETURN 分别调用）
    // ================================================================================================

    /**
     * 对应上游 {@code EntityRenderDispatcherMixin} 在 {@code EntityRenderDispatcher#render} HEAD 处的钩子：
     * 把「该实体是热源」这件事录进遮罩缓冲。
     *
     * <p>
     * <b>上游 vs 1.7.10（r6 改写）</b>：上游用 {@code @WrapOperation} 把 {@code MultiBufferSource} 换成
     * {@link #wrap(MultiBufferSource, Entity)}，实体顶点顺路复制进 mask 缓冲。1.7.10 的实体渲染是固定管线
     * {@code Tessellator} 直接提交，<b>没有可插入的顶点流</b>；旧实现试图「把实体再画一遍进 mask FBO」，
     * 但 vanilla 实体渲染根本不经过 mcshim {@code MultiBufferSource} ⇒ {@link #ACTIVE} 恒为空 ⇒ 热掩膜
     * 永远全 0（实机表现：热成像只有均匀调色板、没有热源高亮）。r6 改为<b>直接生成热源包围盒</b>
     * （见 {@link #appendHeatBox}）：遮罩里每个热源是一个白色盒子，经深度测试后被场景几何正确遮挡。
     * 与上游的差异：轮廓从「实体精确剪影」退化为「包围盒」——这是 1.7.10 无顶点流注入点的平台降级，
     * 记录在此，不是遗漏。
     *
     * <p>
     * <b>零开销早退</b>（每帧每实体都会调）：{@code collecting} 布尔 → 整帧缓存的 {@link #enabled()} →
     * 非相机实体 → 活且可见 → {@link ThermalImagingHelper#isHeatSource}。没开热成像瞄具时成本 ≈ 2 次字段读。
     *
     * <p>
     * 参数类型与上游一致取 {@link Entity}（上游 {@code wrap(source, Entity)}）：热源判定本身会处理
     * 「活着的生物 or 白名单实体」，非生物白名单实体也可能需要热源盒子。
     *
     * @param x 相机相对坐标（{@code RenderManager#func_147939_a} 的入参，见
     *          build/rfg/minecraft-src/java/net/minecraft/client/renderer/entity/RenderManager.java:286-348）
     */
    public static void beginEntity(Entity entity, double x, double y, double z) {
        // ---- 零开销早退（顺序即成本顺序）----
        if (!collecting || entity == null) {
            return;
        }
        if (!enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (entity == mc.renderViewEntity || entity == mc.thePlayer) {
            return;
        }
        if (!entity.isEntityAlive() || entity.isInvisible()) {
            return;
        }
        if (!ThermalImagingHelper.isHeatSource(entity)) {
            return;
        }
        appendHeatBox(entity, x, y, z);
    }

    /** 对应 mixin 的 RETURN 钩子：新实现只写 Java 顶点缓冲，不改 GL 状态，因此无需收尾。 */
    public static void endEntity() {}

    /**
     * 把一个热源包围盒（6 面 × 4 顶点）写进遮罩缓冲。
     *
     * <p>
     * 顶点坐标是<b>相机位置相对的世界轴向坐标</b>（{@code func_147939_a} 的 x/y/z 就是
     * {@code renderPosX/Y/Z} 相减后的值），相机的<b>旋转</b>在 GL_MODELVIEW 上，因此第一条遮罩几何出现时
     * 必须把当时的 {@code GL_PROJECTION}/{@code GL_MODELVIEW} 一起抓下来，render() 重放时才和实体对得上。
     */
    private static void appendHeatBox(Entity entity, double x, double y, double z) {
        if (projection == null) {
            // 第一条遮罩几何：抓当前世界投影 + 相机旋转矩阵（render() 里原样重放）。
            float[] proj = new float[16];
            float[] mv = new float[16];
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, buffer);
            buffer.rewind();
            buffer.get(proj);
            buffer.rewind();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
            buffer.rewind();
            buffer.get(mv);
            projection = proj;
            modelView = mv;
        }
        AxisAlignedBB box = entity.boundingBox;
        if (box == null) {
            return;
        }
        double halfWidth = (box.maxX - box.minX) * 0.5;
        double halfDepth = (box.maxZ - box.minZ) * 0.5;
        float minX = (float) (x - halfWidth);
        float maxX = (float) (x + halfWidth);
        float minZ = (float) (z - halfDepth);
        float maxZ = (float) (z + halfDepth);
        float minY = (float) (y + (box.minY - entity.posY));
        float maxY = (float) (y + (box.maxY - entity.posY));
        BufferBuilder builder = maskBuilder();
        // 下 / 上
        quad(builder, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
        quad(builder, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        // 北 / 南
        quad(builder, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ);
        quad(builder, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        // 西 / 东
        quad(builder, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
        quad(builder, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ);
    }

    /** 取（或按需重启）遮罩顶点缓冲；与上游 mask 顶点格式一致（POSITION_COLOR_TEX）。 */
    private static BufferBuilder maskBuilder() {
        BufferBuilder builder = ACTIVE
            .computeIfAbsent(DefaultVertexFormat.POSITION_COLOR_TEX, key -> BUILDERS.computeIfAbsent(key, unused -> {
                BufferBuilder buffer = new BufferBuilder(4096);
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
                return buffer;
            }));
        if (!builder.building()) {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        }
        return builder;
    }

    private static void quad(BufferBuilder builder, float x0, float y0, float z0, float x1, float y1, float z1,
        float x2, float y2, float z2, float x3, float y3, float z3) {
        builder.vertex(x0, y0, z0)
            .color(255, 255, 255, 255)
            .uv(0.0F, 0.0F)
            .endVertex();
        builder.vertex(x1, y1, z1)
            .color(255, 255, 255, 255)
            .uv(0.0F, 0.0F)
            .endVertex();
        builder.vertex(x2, y2, z2)
            .color(255, 255, 255, 255)
            .uv(0.0F, 0.0F)
            .endVertex();
        builder.vertex(x3, y3, z3)
            .color(255, 255, 255, 255)
            .uv(0.0F, 0.0F)
            .endVertex();
    }

    private static boolean hasThermalScope(ItemStack gunItem) {
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) {
            return false;
        }
        ItemStack scope = gun.getAttachment(gunItem, AttachmentType.SCOPE);
        if (scope == null) {
            scope = gun.getBuiltinAttachment(gunItem, AttachmentType.SCOPE);
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
        if (attachment == null) {
            return false;
        }
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(scope))
            .orElse(null);
        if (index == null) {
            return false;
        }
        ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(scope)
            .orElse(null);
        // 与 AttachmentRender 的「变体替换/叠加」选择保持一致，含模型不可用时的回退。
        if (variant != null && variant.getAttachmentModel() != null && variant.getModelTexture() != null) {
            boolean variantThermal = variant.isScope() && variant.getScopeLens()
                .getVisionMode()
                .isThermal();
            if (variant.isReplaceMode()) {
                return variantThermal;
            }
            if (variantThermal) {
                return true;
            }
        }
        return index.isScope() && index.getScopeLens()
            .getVisionMode()
            .isThermal();
    }

    public static MultiBufferSource wrap(MultiBufferSource source, Entity entity) {
        // 嵌套实体渲染各自判定，不继承父级的热掩膜；只解开本类的包装，其它模组的包装保持不动。
        while (source instanceof ThermalBuffers thermal) {
            source = thermal.source();
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!collecting || !enabled()
            || entity == null
            || !entity.isEntityAlive()
            || entity.isInvisible()
            || entity == mc.renderViewEntity
            || !ThermalImagingHelper.isHeatSource(entity)) {
            return source;
        }
        return new ThermalBuffers(source);
    }

    /**
     * 1.7.10 的「粒子之后、世界渲染末尾」入口（对应上游
     * {@code RenderLevelStageEvent.Stage.AFTER_PARTICLES}）。
     */
    public static void render() {
        if (OculusCompat.isRenderShadow()) {
            return;
        }
        collecting = false;
        if (!enabled()) {
            return;
        }
        ContextCapabilities capabilities = GLContext.getCapabilities();
        if (!capabilities.OpenGL30) {
            // 见类注释「前提条件」：拿不到 GL3.0 就不做热掩膜，镜片退回纯光学效果。
            if (!gl30Warned) {
                gl30Warned = true;
                GunMod.LOGGER.warn(
                    "TACZ: thermal mask DISABLED: current GL context does not expose GL 3.0 "
                        + "(glFramebufferRenderbuffer/glCheckFramebufferStatus needed); thermal scopes fall back "
                        + "to optical-only lens.");
            }
            notReadyReason = "GL context lacks OpenGL 3.0 (cannot attach/borrow the scene depth attachment)";
            return;
        }
        Framebuffer main = Minecraft.getMinecraft()
            .getFramebuffer();
        if (main == null) {
            notReadyReason = "main framebuffer is null";
            return;
        }
        int width = main.framebufferTextureWidth;
        int height = main.framebufferTextureHeight;
        if (width <= 0 || height <= 0) {
            notReadyReason = "main framebuffer has no colour texture yet";
            return;
        }
        // r6-2（方案 a）：主 framebuffer 的深度在 1.7.10 是 renderbuffer（Framebuffer.java:108-130），
        // 既不能直接采样、也不该长期挂到第二个 FBO（Angelica/驱动行为不可控）。改为每帧用
        // glBlitFramebuffer 把场景深度拷进本类自己的 depth texture（renderbuffer→texture 的深度 blit
        // 是 GL3.0 核心功能），这样遮罩盒子的深度测试与场景严格对齐。blit 失败时仍然继续渲染
        // （遮罩无遮挡、可接受的降级），不静默整条 pass 失效。
        boolean matricesPushed = false;
        try (ScopeLensRenderer.RenderState ignored = new ScopeLensRenderer.RenderState()) {
            // 记住当前绑定的 FBO（Angelica 可能把主 framebuffer 绑在 0 以外的 id 上），
            // 结束时恢复它，而不是硬编码回 0。
            int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            ensureTarget(width, height);
            if (framebuffer < 0) {
                notReadyReason = "thermal mask framebuffer/texture creation failed";
                if (!targetFailedWarned) {
                    targetFailedWarned = true;
                    GunMod.LOGGER.warn(
                        "TACZ: thermal mask DISABLED: failed to create the {}x{} offscreen framebuffer; thermal "
                            + "scopes fall back to optical-only lens.",
                        width,
                        height);
                }
                return;
            }
            // 主 framebuffer 的深度附件探测必须在绑定本类 FBO **之前**做：glGetFramebufferAttachmentParameteri
            // 查询的是当前绑定的 DRAW framebuffer，绑定后再查会查到自己（旧实现的兜底因此永远借不到主深度）。
            DepthAttachment mainDepth = resolveSceneDepth(main);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            // 遮罩目标没有 stencil 附件；上游同样在遮罩 pass 开头显式关掉模板测试。
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            // 深度也要清：blit 失败时 depth texture 不能是未定义内容，否则热源盒子会被随机遮挡。
            GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            // 场景深度：优先 方案 a（blit 到自己的 depth texture）；失败再退回「临时借用主 FBO 深度附件」。
            boolean depthBlitted = blitSceneDepth(main, width, height);
            DepthAttachment borrowedDepth = null;
            depthBorrowed = false;
            if (!depthBlitted && mainDepth != null) {
                borrowedDepth = mainDepth;
                attachSceneDepth(borrowedDepth.type(), borrowedDepth.object(), borrowedDepth.level());
                depthBorrowed = true;
                depthBlitted = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
                if (!depthResolveWarned) {
                    depthResolveWarned = true;
                    GunMod.LOGGER.warn(
                        "TACZ: thermal mask scene depth: glBlitFramebuffer path failed, fell back to borrowing "
                            + "the main framebuffer depth attachment (type={}, object={}, complete={}).",
                        borrowedDepth.type(),
                        borrowedDepth.object(),
                        depthBlitted);
                }
            }
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                notReadyReason = "thermal mask framebuffer is not complete (status=" + GL30
                    .glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) + ", depthTexture=" + depthTexture + ")";
                if (!incompleteWarned) {
                    incompleteWarned = true;
                    GunMod.LOGGER.warn(
                        "TACZ: thermal mask DISABLED: {}; thermal scopes fall back to optical-only lens.",
                        notReadyReason);
                }
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousDrawFramebuffer);
                return;
            }
            // 用录制时的投影/模型视图重放一遍，保证遮罩与场景深度对齐。
            // 先成对 push（PROJECTION + MODELVIEW），再 load，保证任何异常/提前 return 都能在
            // finally 里成对 pop 回来（见下方 finally 的 matricesPushed 分支）。
            if (projection != null && modelView != null) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                matricesPushed = true;
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glLoadMatrix(floatsToBuffer(projection));
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glLoadMatrix(floatsToBuffer(modelView));
            }
            GL20.glUseProgram(program);
            // r6：遮罩顶点着色器用 uniform ProjMat/ModelViewMat（与上游 ShaderInstance 一致）。
            // 旧实现**从未设置这两个 uniform** ⇒ gl_Position 恒为 0 ⇒ 遮罩永远是空的（热成像因此只剩
            // 均匀调色板）。这里把 capture 到的矩阵显式灌进去。
            if (projection != null && modelView != null) {
                setMatrix4(program, "ProjMat", projection);
                setMatrix4(program, "ModelViewMat", modelView);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            // 只给遮罩加深度偏移：保留 cutout 轮廓与场景遮挡。
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(DEPTH_BIAS_FACTOR, DEPTH_BIAS_UNITS);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColorMask(true, true, true, true);

            for (Map.Entry<VertexFormat, BufferBuilder> entry : ACTIVE.entrySet()) {
                BufferBuilder.RenderedBuffer vertices = entry.getValue()
                    .end();
                if (vertices != null) {
                    BufferUploader.draw(vertices);
                }
            }
            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousDrawFramebuffer);
            ready = true;
            notReadyReason = null;
            if (!activeLogged) {
                activeLogged = true;
                GunMod.LOGGER.debug(
                    "TACZ: thermal mask pass ACTIVE: {}x{}, sceneDepth={}, maskBuffers={}",
                    width,
                    height,
                    depthBlitted ? "blit-ok" : "blit-FAILED (no occlusion)",
                    ACTIVE.size());
            }
        } finally {
            // 矩阵栈必须成对还原（进入时已 push 两张），否则会把「录制的场景矩阵」留给后续绘制。
            // 顺序与 ScopeLensRenderer.java:399-404 一致：先 PROJECTION 后 MODELVIEW。
            if (matricesPushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            // 借用过主 framebuffer 的深度附件时必须摘掉，否则本类 FBO 会一直引着别人的 renderbuffer
            // （下次尺寸变化/主 FBO 重建会留下悬空附件）；摘掉后立即重挂自己的 depth texture。
            if (depthBorrowed) {
                depthBorrowed = false;
                int bound = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                if (framebuffer >= 0) {
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                    GL30.glFramebufferRenderbuffer(
                        GL30.GL_FRAMEBUFFER,
                        GL30.GL_DEPTH_ATTACHMENT,
                        GL30.GL_RENDERBUFFER,
                        0);
                    GL30.glFramebufferTexture2D(
                        GL30.GL_FRAMEBUFFER,
                        GL30.GL_DEPTH_ATTACHMENT,
                        GL11.GL_TEXTURE_2D,
                        depthTexture,
                        0);
                }
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bound);
            }
            for (BufferBuilder builder : ACTIVE.values()) {
                BufferBuilder.RenderedBuffer buffer = builder.end();
                if (buffer != null) {
                    buffer.release();
                }
            }
            ACTIVE.clear();
        }
    }

    /**
     * r6-2（方案 a）：把主 framebuffer 的场景深度 blit 进本类自己的 depth texture。
     *
     * <p>
     * <b>为什么必须这样</b>：1.7.10 的主 framebuffer 深度是 <b>renderbuffer</b>
     * （{@code build/rfg/minecraft-src/java/net/minecraft/client/shader/Framebuffer.java:108/117-124}：
     * {@code OpenGlHelper.func_153185_f()} + {@code GL_DEPTH_COMPONENT24}），renderbuffer 不能直接采样，
     * 也不适合长期挂到第二个 FBO 上（旧做法要查询附件类型再挂，实机日志出现
     * {@code thermal mask DISABLED: cannot resolve the main framebuffer's depth attachment}）。
     * {@code glBlitFramebuffer} 的深度拷贝（GL3.0 核心）允许 renderbuffer → texture，且不需要查询附件类型。
     *
     * <p>
     * 失败时只 WARN 一次并继续（遮罩盒子不做遮挡、可接受的降级），不再让整条热成像 pass 静默失效。
     *
     * @return 是否成功拷到场景深度
     */
    private static boolean blitSceneDepth(Framebuffer main, int width, int height) {
        if (depthTexture < 0) {
            return false;
        }
        int mainFramebuffer = main.framebufferObject;
        if (mainFramebuffer < 0) {
            // 未启用 FBO 时主目标就是 0 号（窗口默认 framebuffer）。GL30 的 blit 在默认 framebuffer
            // 上只保证 color；深度 blit 可能无效，这里按失败降级处理。
            mainFramebuffer = 0;
        }
        IntBuffer previousRead = BufferUtils.createIntBuffer(16);
        IntBuffer previousDraw = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING, previousRead);
        GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING, previousDraw);
        int readBinding = previousRead.get(0);
        int drawBinding = previousDraw.get(0);
        // 确保自己的 depth texture 挂在 DRAW FBO 上（上一帧若借用过主深度，finally 已重挂；这里再兜一次）。
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        // 先排空此前遗留的 GL 错误，保证下面读到的错误确实来自这次 blit（别的渲染路径可能留下旧错误）。
        for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            // drain
        }
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        int error = GL11.glGetError();
        // 成对恢复读/写绑定（调用方随后仍以为自己的 FBO 是当前 GL_FRAMEBUFFER）。
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readBinding);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawBinding);
        if (error != GL11.GL_NO_ERROR) {
            if (!depthBlitWarned) {
                depthBlitWarned = true;
                GunMod.LOGGER.warn(
                    "TACZ: thermal mask scene-depth blit FAILED (GL error 0x{}); the heat boxes will not be "
                        + "occluded by world geometry (thermal palette itself still works). "
                        + "mainFramebuffer={}, main.depthBuffer={}, ours={}x{}",
                    Integer.toHexString(error),
                    mainFramebuffer,
                    main.depthBuffer,
                    width,
                    height);
            }
            return false;
        }
        return true;
    }

    /**
     * 探测<b>主 framebuffer</b>的深度附件，返回可直接交给 {@link #attachSceneDepth(int, int, int)} 的三元组。
     *
     * <h2>为什么不再直接读 {@code Framebuffer#depthBuffer}</h2>
     * 1.7.10 原版的主 framebuffer 深度是 renderbuffer（
     * {@code build/rfg/minecraft-src/java/net/minecraft/client/shader/Framebuffer.java:108}
     * 用 {@code OpenGlHelper#func_153185_f()} 生成），所以早期实现直接
     * {@code if (main.depthBuffer <= 0) return;} + 恒走 {@code glFramebufferRenderbuffer}。
     *
     * <p>
     * 但 <b>Angelica</b>（1.7.10 的 Iris 移植）开 shader pack 时会把主 framebuffer 的深度附件
     * 从 renderbuffer 换成 <b>depth texture</b>（依据
     * {@code A/.../shaders/MixinFramebuffer.java:63-91}）。此时：
     * <ul>
     * <li>{@code main.depthBuffer} 可能为 0 或指向已废弃的 renderbuffer → 旧硬闸会让
     * <b>整条热成像 pass 静默失效</b>；</li>
     * <li>{@code GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE} 变成 {@code GL_TEXTURE}，此时必须用
     * {@code glFramebufferTexture2D} 挂，用 {@code glFramebufferRenderbuffer} 挂会得到
     * {@code GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT}。</li>
     * </ul>
     *
     * <p>
     * <b>必须在绑定本类 FBO 之前调用</b>：{@code glGetFramebufferAttachmentParameteri} 查询的是当前绑定的
     * framebuffer；本方法内部会临时绑定 {@code main.framebufferObject} 查询后恢复，因此也兼容 Angelica
     * 把主 framebuffer 绑在非 0 id 上的情况。
     *
     * @return {@link DepthAttachment}；无法探测（非 GL3.0 / 主目标不是 FBO / 无深度附件）返回 {@code null}
     */
    private static DepthAttachment resolveSceneDepth(Framebuffer main) {
        ContextCapabilities capabilities = GLContext.getCapabilities();
        if (!capabilities.OpenGL30 || main == null || main.framebufferObject < 0) {
            // 主目标是窗口默认 framebuffer（0 号）时没有可借用的深度附件对象。
            return null;
        }
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.framebufferObject);
        try {
            int type = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL30.GL_RENDERBUFFER && type != GL11.GL_TEXTURE) {
                // GL_NONE 等：没有深度附件。
                return null;
            }
            int object = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (object == 0) {
                return null;
            }
            int level = 0;
            if (type == GL11.GL_TEXTURE) {
                level = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            }
            return new DepthAttachment(type, object, level);
        } catch (RuntimeException unsupported) {
            // 某些驱动/上下文不实现该查询：按「探测失败」处理，回退现有行为。
            return null;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        }
    }

    /** 场景深度附件的探测结果（renderbuffer 或 depth texture）。 */
    private record DepthAttachment(int type, int object, int level) {}

    /**
     * 把场景深度附件挂到本类自己的 FBO 上（对应上游同名方法）。
     *
     * <p>
     * <b>两个分支都必须保留</b>：1.7.10 原版主 framebuffer 的深度是 renderbuffer
     * （走 {@code glFramebufferRenderbuffer}），而 <b>Angelica</b>（1.7.10 的 Iris）开 shader pack
     * 后会把它换成 <b>depth texture</b>（走 {@code glFramebufferTexture2D}，依据
     * {@code A/.../shaders/MixinFramebuffer.java:63-91}）。附件类型由
     * {@link #resolveSceneDepth(Framebuffer)} 探测后传入。
     */
    public static void attachSceneDepth(int type, int object, int level) {
        int bound = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int fbo = bound != 0 ? bound : framebuffer;
        if (fbo < 0) {
            return;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        if (type == GL11.GL_TEXTURE) {
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D,
                object,
                level);
        } else {
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, object);
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bound);
    }

    public static boolean isReady(int width, int height) {
        return ready && colorTexture >= 0 && targetWidth == width && targetHeight == height;
    }

    public static int textureOr(int fallback, int width, int height) {
        return isReady(width, height) ? colorTexture : fallback;
    }

    /**
     * 请求释放热源掩膜管线的每世界/每尺寸 GL 资源。<b>可从任意线程调用</b>（断线事件在 Netty IO 线程上触发）。
     *
     * <p>
     * 只写 volatile 标记，<b>不碰 GL、也不改渲染线程正在遍历的集合</b>：真正的 {@code beginFrame()} +
     * {@code glDeleteFramebuffers/glDeleteTextures} 由渲染线程上的 {@link #drainDeletes()} 执行 ——
     * GL 调用落在没有当前上下文的线程上时 LWJGL 会 <b>abort 整个 JVM</b>（不是抛异常），必须严格避免。
     *
     * <p>
     * <b>不销毁着色器程序</b>：程序是注册一次的静态资源（上游 {@code RegisterShadersEvent} 同语义），
     * 且 {@link #init()} 只在客户端初始化时调用一次 —— 若在这里销毁，一次断线后热成像就永久失效。
     */
    public static void close() {
        closeRequested = true;
        deletePending = true;
    }

    /**
     * 渲染线程每帧调用：执行 {@link #close()} 攒下的清理与 GL 删除。幂等、一次 {@code volatile} 读即可早退。
     */
    public static void drainDeletes() {
        if (!deletePending) {
            return;
        }
        deletePending = false;
        if (closeRequested) {
            closeRequested = false;
            beginFrame();
            if (framebuffer >= 0) {
                pendingFramebuffer = framebuffer;
                framebuffer = -1;
            }
            if (colorTexture >= 0) {
                pendingColorTexture = colorTexture;
                colorTexture = -1;
            }
            if (depthTexture >= 0) {
                pendingDepthTexture = depthTexture;
                depthTexture = -1;
            }
            targetWidth = -1;
            targetHeight = -1;
            ready = false;
        }
        int staleFramebuffer = pendingFramebuffer;
        if (staleFramebuffer >= 0) {
            pendingFramebuffer = -1;
            GL30.glDeleteFramebuffers(staleFramebuffer);
        }
        int staleTexture = pendingColorTexture;
        if (staleTexture >= 0) {
            pendingColorTexture = -1;
            GL11.glDeleteTextures(staleTexture);
        }
        int staleDepthTexture = pendingDepthTexture;
        if (staleDepthTexture >= 0) {
            pendingDepthTexture = -1;
            GL11.glDeleteTextures(staleDepthTexture);
        }
    }

    // ================================================================================================
    // 内部
    // ================================================================================================

    private static void ensureTarget(int width, int height) {
        if (framebuffer >= 0 && targetWidth == width && targetHeight == height) {
            return;
        }
        destroyTarget();
        colorTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
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
        framebuffer = GL30.glGenFramebuffers();
        int previousBinding = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            colorTexture,
            0);
        // r6-2：自己的深度纹理（GL3.0 的 glBlitFramebuffer 把主 FBO 的 scene depth 拷进来）。
        // 内部格式要与主 FBO 的深度格式匹配，否则深度 blit 可能被驱动拒绝：
        // · 默认（无 stencil）：主深度是 GL_DEPTH_COMPONENT24 renderbuffer（Framebuffer.java:120-124）
        // · Forge stencilBits>0 或 MainFramebufferStencil 运行期补挂后：主深度是 GL_DEPTH24_STENCIL8
        // （Framebuffer.java:125-130 / MainFramebufferStencil）
        depthTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        if (ScopeLensRenderer.isStencilAvailable()) {
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT,
                width,
                height,
                0,
                EXTPackedDepthStencil.GL_DEPTH_STENCIL_EXT,
                EXTPackedDepthStencil.GL_UNSIGNED_INT_24_8_EXT,
                (ByteBuffer) null);
        } else {
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL14.GL_DEPTH_COMPONENT24,
                width,
                height,
                0,
                GL11.GL_DEPTH_COMPONENT,
                GL11.GL_UNSIGNED_INT,
                (ByteBuffer) null);
        }
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
        // 恢复调用前的绑定，而不是硬编码 0 —— Angelica（1.7.10 的 Iris）可能把主 framebuffer
        // 绑在一个非 0 的 FBO 上，这里绑 0 会把它的目标冲掉。
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousBinding);
        targetWidth = width;
        targetHeight = height;
    }

    private static void destroyTarget() {
        if (framebuffer >= 0) {
            GL30.glDeleteFramebuffers(framebuffer);
        }
        if (colorTexture >= 0) {
            GL11.glDeleteTextures(colorTexture);
        }
        if (depthTexture >= 0) {
            GL11.glDeleteTextures(depthTexture);
        }
        framebuffer = -1;
        colorTexture = -1;
        depthTexture = -1;
        targetWidth = -1;
        targetHeight = -1;
    }

    private static FloatBuffer floatsToBuffer(float[] values) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(values);
        buffer.flip();
        return buffer;
    }

    /** 把列主序 4×4 矩阵写进指定 program 的 uniform（等价 {@code ShaderInstance#safeGetUniform#set}）。 */
    private static void setMatrix4(int programId, String name, float[] columnMajor) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location < 0) {
            return;
        }
        GL20.glUniformMatrix4(location, false, floatsToBuffer(columnMajor));
    }

    /** 上游 {@code record ThermalBuffers}：把实体绘制同时写进原 source 与热掩膜缓冲。 */
    public record ThermalBuffers(MultiBufferSource source) implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            VertexConsumer original = source.getBuffer(type);
            if (!collecting || type == null) {
                return original;
            }
            if (projection == null) {
                // 与上游一致：等到第一条遮罩顶点时才抓当前 GL 矩阵。
                float[] p = new float[16];
                float[] m = new float[16];
                FloatBuffer pb = BufferUtils.createFloatBuffer(16);
                FloatBuffer mb = BufferUtils.createFloatBuffer(16);
                GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, pb);
                GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mb);
                pb.get(p);
                mb.get(m);
                projection = p;
                modelView = m;
            }
            // 保留材质状态：盔甲的视角偏移必须与场景深度一致，所以遮罩沿用同一个 RenderType。
            BufferBuilder builder = ACTIVE.computeIfAbsent(
                DefaultVertexFormat.POSITION_COLOR_TEX,
                key -> BUILDERS.computeIfAbsent(key, unused -> {
                    BufferBuilder buffer = new BufferBuilder(4096);
                    buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
                    return buffer;
                }));
            if (!builder.building()) {
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            }
            return new TeeVertexConsumer(original, new MaskVertices(builder));
        }
    }

    /** mcshim 没有 {@code VertexMultiConsumer}：把一个顶点同时写进两个 consumer。 */
    private record TeeVertexConsumer(VertexConsumer first, VertexConsumer second) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            first.vertex(x, y, z);
            second.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            first.color(red, green, blue, alpha);
            second.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer color(float red, float green, float blue, float alpha) {
            first.color(red, green, blue, alpha);
            second.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            first.uv(u, v);
            second.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            first.overlayCoords(u, v);
            second.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            first.uv2(u, v);
            second.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            first.normal(x, y, z);
            second.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            first.endVertex();
            second.endVertex();
        }
    }

    /** 等价于原版轮廓生成器：保留贴图 cutout，忽略光照与受伤着色。 */
    public static final class MaskVertices implements VertexConsumer {

        private final VertexConsumer output;
        private double x;
        private double y;
        private double z;
        private float u;
        private float v;

        MaskVertices(VertexConsumer output) {
            this.output = output;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
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
            this.u = u;
            this.v = v;
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
        public void endVertex() {
            output.vertex(x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .endVertex();
        }
    }
}
