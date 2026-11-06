package com.tacz.guns.client.renderer.scope;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.tacz.guns.GunMod;

/**
 * 1.7.10 侧的 GLSL 程序封装，用来替代 1.20 的 {@code ShaderInstance} + {@code RegisterShadersEvent}。
 *
 * <h2>为什么不用 1.7.10 的 {@code ShaderGroup}</h2>
 * 1.7.10 自带 {@code net.minecraft.client.shader.{ShaderGroup,Shader,ShaderManager}}，但它只服务
 * <b>JSON 后处理管线</b>（{@code assets/<ns>/shaders/post/<name>.json} 里声明的
 * {@code targets} / {@code passes} / 固定的 {@code Sampler}/{@code DiffuseSampler} 绑定），
 * 有几个硬限制让上游这份镜片着色器无法原样套进去：
 * <ul>
 * <li>uniform <b>不能按帧动态写</b>（没有 {@code safeGetUniform} 之类的公开入口把
 * {@code LensCenter/LensRadius/Distortion/...} 每帧刷进去；只能靠 {@code ShaderManager} 内部的
 * 静态 uniform 表）。上游每帧要写 15 个运行时 uniform。</li>
 * <li>一个 {@code ShaderGroup} 表达的是「对整块 target 做全屏 pass」，而上游要的是
 * 「<b>只在镜片几何（视空间圆）内</b>做 pass，且要读模板缓冲」，
 * {@code ShaderGroup} 没有自定义几何与模板测试的入口。</li>
 * </ul>
 * 因此这里直接用 {@code org.lwjgl.opengl.GL20}（LWJGL2 自带，1.7.10 的 compatibility profile 下
 * {@code glVertexPointer} 落在 generic attribute 0 上，所以 {@code Tessellator} 画的顶点能被
 * {@code attribute vec3 Position} 读到）。
 *
 * <h2>着色器源码从哪来</h2>
 * 上游 {@code assets/tacz/shaders/core/scope_lens.{vsh,fsh}} 是 <b>GLSL 150</b>
 * （{@code #version 150} + {@code in/out} + {@code texture()} + {@code texelFetch}），
 * 1.7.10 的 GL 语境最高稳定到 <b>GLSL 120</b>（{@code attribute/varying} + {@code texture2D()} +
 * 无 {@code texelFetch}）。因此本类内嵌一份 **GLSL 120 的等价源码**，
 * 与上游逐行对应关系写在 {@link #VERTEX_SOURCE} / {@link #FRAGMENT_SOURCE} 的注释里。
 */
final class ScopeLensProgram {

    // ================================================================================================
    // GLSL 120 源码：与上游 shaders/core/scope_lens.vsh 一一对应
    // 上游 本文件
    // #version 150 → #version 120
    // in vec3 Position → attribute vec3 Position
    // （fragment 输出改 gl_FragColor）
    // ================================================================================================
    static final String VERTEX_SOURCE = """
        #version 120

        attribute vec3 Position;

        uniform mat4 ModelViewMat;
        uniform mat4 ProjMat;

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
        }
        """;

    /**
     * 与上游 {@code shaders/core/scope_lens.fsh} 逐段对应，差异只有三处（都是为了降到 GLSL 120）：
     * <ol>
     * <li>{@code out vec4 fragColor} → {@code gl_FragColor}。</li>
     * <li>{@code texture(sampler, uv)} → {@code texture2D(sampler, uv)}。</li>
     * <li>{@code distanceToEdge} 里 {@code ContourReady != 0} 的分支（用 {@code texelFetch} 读 JFA 轮廓图）
     * <b>整体删除</b>，只保留上游同函数里的 <b>椭圆近似</b>分支 —— 这段分支本来就是上游为
     * 「shader/资源失败」准备的兜底（见上游 scope_lens.fsh:53-55 注释
     * "Shader/resource failures retain the previous elliptical approximation"）。
     * 引发它不可用的原因与后续接线见 {@link ScopeLensContour} 类注释。</li>
     * </ol>
     */
    static final String FRAGMENT_SOURCE = """
        #version 120

        uniform sampler2D Sampler0;
        uniform sampler2D Sampler1;
        uniform sampler2D Sampler2;
        uniform vec2 ScreenSize;
        uniform vec2 LensCenter;
        uniform vec2 LensRadius;
        uniform int ContourReady;
        uniform vec4 ContourRect;
        uniform vec2 ContourSize;
        uniform float Distortion;
        uniform float Aberration;
        uniform float EdgeWidth;
        uniform float VignetteStrength;
        uniform float VignetteWidth;
        uniform vec3 LensTint;
        uniform int VisionMode;
        uniform int ThermalReady;
        uniform float VisionGain;
        uniform float VisionNoise;
        uniform float SensorTime;

        vec2 clampUv(vec2 uv) {
            vec2 halfTexel = 0.5 / ScreenSize;
            return clamp(uv, halfTexel, 1.0 - halfTexel);
        }

        float luminance(vec3 color) {
            return dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        float grain() {
            vec2 pixel = floor(gl_FragCoord.xy);
            float frame = floor(SensorTime * 30.0);
            return fract(sin(dot(pixel, vec2(12.9898, 78.233)) + frame * 37.719) * 43758.5453) - 0.5;
        }

        vec3 ironbow(float heat) {
            vec3 cold = vec3(0.015, 0.01, 0.06);
            vec3 purple = vec3(0.30, 0.025, 0.48);
            vec3 red = vec3(0.90, 0.10, 0.035);
            vec3 yellow = vec3(1.0, 0.82, 0.12);
            if (heat < 0.35) return mix(cold, purple, heat / 0.35);
            if (heat < 0.65) return mix(purple, red, (heat - 0.35) / 0.30);
            if (heat < 0.85) return mix(red, yellow, (heat - 0.65) / 0.20);
            return mix(yellow, vec3(1.0), (heat - 0.85) / 0.15);
        }

        // GLSL 120: upstream's texelFetch/JFA contour branch is removed; only the elliptical
        // approximation is kept -- it is upstream's own fallback path (scope_lens.fsh:53-55).
        // IMPORTANT: this whole shader source must stay PURE ASCII. LWJGL2 hands GL the source as
        // bytes; a non-ASCII comment corrupts the token stream and the driver reports its error on
        // the FOLLOWING line (that is exactly how the Chinese comment here once broke line 51).
        // Chinese explanations belong in this class' Java javadoc, never inside the string.
        float distanceToEdge(vec2 uv) {
            return 1.0 - clamp(length((uv - LensCenter) / max(LensRadius, vec2(0.00001))), 0.0, 1.0);
        }

        void main() {
            vec2 uv = gl_FragCoord.xy / ScreenSize;
            vec2 relative = uv - LensCenter;

            float ramp = 0.0;
            float edge = 0.0;
            if (EdgeWidth > 0.0 || (VignetteStrength > 0.0 && VignetteWidth > 0.0)) {
                float distance = distanceToEdge(uv);
                if (EdgeWidth > 0.0) {
                    float width = 0.155 * EdgeWidth;
                    ramp = 1.0 - smoothstep(0.0, min(width * 3.0, 1.0), distance);
                }
                if (VignetteStrength > 0.0 && VignetteWidth > 0.0) {
                    float falloff = distance / (0.155 * VignetteWidth);
                    edge = exp(-falloff * falloff) * VignetteStrength;
                }
            }

            // Sample in screen space so hand FOV cannot magnify the scene a second time.
            vec2 sampleUv = uv - relative * Distortion * ramp;
            // Digital sensors are monochrome before their display palette is applied.
            bool digital = VisionMode == 1 || VisionMode == 2 || (VisionMode >= 3 && ThermalReady != 0);
            vec2 split = digital ? vec2(0.0) : relative * Aberration * ramp;
            vec3 scene = vec3(
                texture2D(Sampler0, clampUv(sampleUv - split)).r,
                texture2D(Sampler0, clampUv(sampleUv)).g,
                texture2D(Sampler0, clampUv(sampleUv + split)).b
            );
            vec3 color = scene * LensTint;
            if (VisionMode == 1 || VisionMode == 2) {
                float signal = 1.0 - exp(-pow(max(luminance(scene), 0.0), 0.65) * VisionGain);
                signal = clamp(signal + grain() * VisionNoise, 0.0, 1.0);
                vec3 phosphor = VisionMode == 1 ? vec3(0.18, 1.0, 0.28) : vec3(0.82, 0.94, 1.0);
                color = phosphor * signal;
            } else if (VisionMode >= 3 && ThermalReady != 0) {
                float detail = clamp(luminance(scene), 0.0, 1.0);
                float target = texture2D(Sampler1, clampUv(sampleUv)).r;
                float heat = mix(0.06 + detail * 0.24, 0.78 + detail * 0.20, target);
                heat = clamp(heat + grain() * VisionNoise, 0.0, 1.0);
                if (VisionMode == 3) color = vec3(heat);
                else if (VisionMode == 4) color = vec3(1.0 - heat);
                else color = ironbow(heat);
            }
            gl_FragColor = vec4(color * (1.0 - edge), 1.0);
        }
        """;

    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    private ScopeLensProgram(int programId) {
        this.programId = programId;
    }

    /** 编译并链接；失败返回 {@code null}（调用方回退成「不画镜片」）。 */
    static ScopeLensProgram compile() {
        // 先做开发期静态自检：把「非 ASCII 注释 / #version 不在首行 / 用了 GLSL130+ 特性」
        // 这类会表现为「驱动报错行号错位」的坑提前说清楚。
        validateSource("scope_lens.vertex", VERTEX_SOURCE);
        validateSource("scope_lens.fragment", FRAGMENT_SOURCE);
        int vertex = compileStage(GL20.GL_VERTEX_SHADER, VERTEX_SOURCE, "vertex");
        if (vertex == 0) {
            return null;
        }
        int fragment = compileStage(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE, "fragment");
        if (fragment == 0) {
            GL20.glDeleteShader(vertex);
            return null;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        // 顶点属性位置固定成 0：1.7.10 的 Tessellator 走 glVertexPointer，落在 generic attribute 0。
        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            GunMod.LOGGER.error("Scope lens shader link failed: {}", GL20.glGetProgramInfoLog(program, 4096));
            GL20.glDeleteProgram(program);
            return null;
        }
        return new ScopeLensProgram(program);
    }

    private static int compileStage(int type, String source, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            // 与上游的差异：1.20 的 ShaderInstance 失败时只报一句异常；这里连
            // 「完整 GL info log + 带行号的源码」一起打出来，否则像
            // 「0(51): syntax error, unexpected integer constant」这种偏移一行的报错根本没法定位
            // （真实原因往往在上一条注释里，见 FRAGMENT_SOURCE 的 ASCII 说明）。
            GunMod.LOGGER.error(
                "Scope lens {} shader compile failed:\n{}\n{}\n{}",
                label,
                GL20.glGetShaderInfoLog(shader, 8192),
                "--- source (line numbers are what the driver reports) ---",
                numberedSource(source));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    /**
     * 开发期自检：把 GLSL 源码按 GLSL 120 + LWJGL2 的已知坑静态过一遍。
     *
     * <p>
     * 检查 3 条（全部来自真实踩坑，不是防御性编程）：
     * <ol>
     * <li><b>整份源码必须纯 ASCII（0x00~0x7E）</b>：LWJGL2 把 {@code CharSequence} 交给
     * {@code glShaderSource} 时按字节传递，注释里出现 UTF-8 中文会打乱 token 流，
     * 驱动报错的行号会<b>偏到下一行</b>。实测日志
     * {@code 0(51) : error C0000: syntax error, unexpected integer constant} 的真实原因就是
     * 第 50 行的中文注释。</li>
     * <li>{@code #version} 必须是源码第一个 token（前面不能有注释/空行/缩进）。</li>
     * <li>GLSL 120 里不能出现 {@code #version 150} 才有的 {@code in}/{@code out} 存储类与
     * {@code texture(}/{@code texelFetch(} 函数。</li>
     * </ol>
     *
     * @return 全部通过返回 {@code true}；否则打印问题并返回 {@code false}
     */
    static boolean validateSource(String label, String source) {
        boolean ok = true;
        if (!source.startsWith("#version 120")) {
            GunMod.LOGGER
                .error("GLSL [{}]: '#version 120' must be the very first token, got: {}", label, firstLine(source));
            ok = false;
        }
        int line = 1;
        int column = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                column = 0;
                continue;
            }
            column++;
            if (c > 0x7E || (c < 0x20 && c != '\t')) {
                GunMod.LOGGER.error(
                    "GLSL [{}]: non-ASCII/control char U+{} at line {} column {} -- "
                        + "the driver will report its error on the FOLLOWING line, keep shader "
                        + "source pure ASCII and move non-English text into the Java javadoc",
                    label,
                    Integer.toHexString(c)
                        .toUpperCase(),
                    line,
                    column);
                ok = false;
            }
        }
        if (source.contains("#version 150") || source.contains("texelFetch(")) {
            GunMod.LOGGER.error("GLSL [{}]: uses GLSL 130+ only features (texelFetch / #version 150)", label);
            ok = false;
        }
        return ok;
    }

    /** 带行号的源码，行号与驱动报的 {@code 0(N)} 对齐。 */
    static String numberedSource(String source) {
        StringBuilder builder = new StringBuilder(source.length() + 256);
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            builder.append(String.format("%4d| %s%n", i + 1, lines[i]));
        }
        return builder.toString();
    }

    private static String firstLine(String source) {
        int newline = source.indexOf('\n');
        return newline < 0 ? source : source.substring(0, newline);
    }

    void use() {
        GL20.glUseProgram(programId);
    }

    /**
     * 通用编译入口（供 {@link ScopeThermalRenderer} 等其它 GLSL 程序复用）。
     *
     * <p>
     * 1.7.10 的 {@code Tessellator} 用固定管线顶点指针送数据，compatibility profile 下这些指针
     * 别名到固定的 generic attribute 槽位：{@code glVertexPointer}→0、
     * {@code glColorPointer}→3、{@code glMultiTexCoord0Pointer}→8、
     * {@code glNormalPointer}→2。因此属性位置必须显式绑定成这些值，着色器才读得到。
     *
     * @param attributeNames     着色器里的 attribute 名
     * @param attributeLocations 与之一一对应的 generic attribute 槽位
     * @return GL program id；失败返回 0
     */
    static int compileRaw(String vertexSource, String fragmentSource, String[] attributeNames,
        int[] attributeLocations) {
        // 同 compile()：先静态自检，避免「非 ASCII 注释导致驱动报错行号错位」再次发生。
        validateSource("raw-vertex", vertexSource);
        validateSource("raw-fragment", fragmentSource);
        int vertex = compileStage(GL20.GL_VERTEX_SHADER, vertexSource, "raw-vertex");
        if (vertex == 0) {
            return 0;
        }
        int fragment = compileStage(GL20.GL_FRAGMENT_SHADER, fragmentSource, "raw-fragment");
        if (fragment == 0) {
            GL20.glDeleteShader(vertex);
            return 0;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        for (int i = 0; i < attributeNames.length; i++) {
            GL20.glBindAttribLocation(program, attributeLocations[i], attributeNames[i]);
        }
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            GunMod.LOGGER.error("GLSL program link failed: {}", GL20.glGetProgramInfoLog(program, 4096));
            GL20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    void release() {
        GL20.glUseProgram(0);
    }

    /** 对应 1.20 {@code ShaderInstance#safeGetUniform(String)#set(...)} 的各个重载。 */
    void set1f(String name, float value) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform1f(location, value);
        }
    }

    void set1i(String name, int value) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform1i(location, value);
        }
    }

    void set2f(String name, float x, float y) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform2f(location, x, y);
        }
    }

    void set3f(String name, float x, float y, float z) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform3f(location, x, y, z);
        }
    }

    void set4f(String name, float x, float y, float z, float w) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }

    void setMatrix4f(String name, float[] columnMajor) {
        int location = location(name);
        if (location >= 0) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            buffer.put(columnMajor);
            buffer.flip();
            GL20.glUniformMatrix4(location, false, buffer);
        }
    }

    private int location(String name) {
        Integer cached = uniformCache.get(name);
        if (cached != null) {
            return cached;
        }
        int location = GL20.glGetUniformLocation(programId, name);
        uniformCache.put(name, location);
        return location;
    }
}
