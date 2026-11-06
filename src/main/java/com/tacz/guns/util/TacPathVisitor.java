package com.tacz.guns.util;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiConsumer;

import net.minecraft.util.ResourceLocation;

/**
 * 遍历枪包目录，把「后缀匹配的文件」回调成 {@code (资源 id, 文件路径)}。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}（1.7.10 同名类）。</li>
 * <li>1.7.10 的 {@code new ResourceLocation(namespace, path)} 与 1.20 一致（自动 lowercase），
 * 路径分隔符已由 {@link PathHandler#getPath} 统一成 {@code '/'}，<b>无语义差异</b>。</li>
 * <li>这是 {@code api/**} 的编译期契约类，<b>公开类名、构造签名与 {@code visitFile} 保持不变</b>。</li>
 * </ul>
 */
public class TacPathVisitor extends SimpleFileVisitor<Path> {

    private final File root;
    private final String namespace;
    private final String suffix;
    private final BiConsumer<ResourceLocation, Path> consumer;

    public TacPathVisitor(File root, String namespace, String suffix, BiConsumer<ResourceLocation, Path> consumer) {
        this.root = root;
        this.namespace = namespace;
        this.suffix = suffix;
        this.consumer = consumer;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (file.toFile()
            .getName()
            .endsWith(suffix)) {
            String path = PathHandler.getPath(root.toPath(), file, suffix);
            ResourceLocation id = new ResourceLocation(namespace, path);
            consumer.accept(id, file);
        }
        return FileVisitResult.CONTINUE;
    }
}
