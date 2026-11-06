package com.tacz.guns.resource.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.GunMod;

/**
 * 目录形态枪包（{@code <gamedir>/tacz/<packName>/}）的 {@link IGunPackSource} 实现。
 *
 * <p>
 * 对应上游 1.20.1 中 {@code PathPackResources}（{@code SecureJar.from(gunPack.path)} 包装的目录）：
 * 参考 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/GunPackLoader.java:96-117}。
 * 1.7.10 没有 {@code SecureJar}，直接走 {@code java.nio.file}。
 */
public final class DirectoryGunPackSource implements IGunPackSource {

    private final Path root;
    private final String name;

    public DirectoryGunPackSource(Path root) {
        this(
            root,
            root.getFileName() == null ? root.toString()
                : root.getFileName()
                    .toString());
    }

    public DirectoryGunPackSource(Path root, String name) {
        this.root = root.toAbsolutePath()
            .normalize();
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> listFiles(String pathPrefix) {
        Path base = resolveInside(pathPrefix);
        if (base == null || !Files.isDirectory(base)) {
            return Collections.emptyList();
        }
        List<String> output = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(base)) {
            stream.filter(Files::isRegularFile)
                .forEach(path -> output.add(toRelative(path)));
        } catch (IOException exception) {
            GunMod.LOGGER.error("Failed to list gun pack files from {} in {}", pathPrefix, root, exception);
        }
        Collections.sort(output);
        return output;
    }

    @Override
    public @Nullable InputStream open(String path) {
        Path file = resolveInside(path);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException exception) {
            GunMod.LOGGER.error("Failed to open gun pack file {} in {}", path, root, exception);
            return null;
        }
    }

    /**
     * 解析包内相对路径，并守住目录穿越：解析结果必须仍在包根目录之内。
     * （枪包是用户可放置的第三方内容，直接 {@code root.resolve(path)} 可能被 {@code ../} 越狱。）
     */
    private @Nullable Path resolveInside(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        Path resolved = root.resolve(relativePath)
            .normalize();
        return resolved.startsWith(root) ? resolved : null;
    }

    private String toRelative(Path path) {
        return root.relativize(
            path.toAbsolutePath()
                .normalize())
            .toString()
            .replace('\\', '/');
    }
}
