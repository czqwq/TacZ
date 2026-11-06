package com.tacz.guns.mcshim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

/**
 * 对应 1.20.1 的 {@code net.minecraft.server.packs.resources.Resource}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 有 {@code net.minecraft.client.resources.IResource}，但它是客户端专用的（{@code client} 包），
 * 而 TACZ 的枪包加载在<b>服务端</b>也要跑。因此本类不复用 {@code IResource}，
 * 而是包住一个 {@link PackSource} + 路径，按需打开流。
 *
 * <h2>与 1.20 {@code Resource} 的差异</h2>
 * <ul>
 * <li>1.20 的 {@code Resource} 是接口（{@code open()} 抛 {@code IOException}），还有
 * {@code sourcePackId()}、{@code isBuiltin()}。本类是<b>具体类</b>，方法不抛受检异常
 * （内部转 {@code RuntimeException}），以减少移植时的 try/catch 噪声。</li>
 * <li>{@code metadata()}（mcmeta）未移植。</li>
 * <li>{@link #id()} 是资源 id（带 namespace），<b>不是</b>文件路径；文件路径见 {@link #path()}。</li>
 * </ul>
 */
public class Resource {

    private final ResourceLocation id;
    private final PackSource source;
    private final String path;
    /** 已读进内存的字节（来自 {@link #fromBytes} 时非 null）。 */
    private final byte[] bytes;

    public Resource(ResourceLocation id, PackSource source, String path) {
        this.id = id;
        this.source = source;
        this.path = path;
        this.bytes = null;
    }

    private Resource(ResourceLocation id, String path, byte[] bytes) {
        this.id = id;
        this.source = null;
        this.path = path;
        this.bytes = bytes;
    }

    /** 用内存字节构造一个资源（用于单元测试或「内置资源」）。 */
    public static Resource fromBytes(ResourceLocation id, String path, byte[] bytes) {
        return new Resource(id, path, bytes);
    }

    /** 资源 id（带 namespace），对应 1.20 {@code Resource#id()}。 */
    public ResourceLocation id() {
        return id;
    }

    /** 相对文件路径（例如 {@code data/tacz/index/guns/ak47.json}）。 */
    public String path() {
        return path;
    }

    /** 来源名字，对应 1.20 {@code Resource#sourcePackId()}。 */
    public String sourcePackId() {
        return source == null ? "memory" : source.getName();
    }

    /** 来源（可能为 {@code null}，表示内存资源）。 */
    @Nullable
    public PackSource source() {
        return source;
    }

    /**
     * 打开资源流，对应 1.20 {@code Resource#open()}。
     *
     * <p>
     * 每次调用返回<b>新的</b>流；调用方负责关闭。
     *
     * @throws RuntimeException 底层打不开时
     */
    public InputStream open() {
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        InputStream stream = source == null ? null : source.open(path);
        if (stream == null) {
            throw new RuntimeException("Resource not readable: " + path + " (from " + sourcePackId() + ")");
        }
        return stream;
    }

    /** 把整个资源读进内存。 */
    public byte[] readAll() {
        if (bytes != null) {
            return bytes;
        }
        try (InputStream stream = open()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource " + path, e);
        }
    }

    @Override
    public String toString() {
        return id + " -> " + path + " (" + sourcePackId() + ")";
    }
}
