package com.tacz.guns.util;

import java.nio.file.Path;

/**
 * 计算「文件相对根目录的路径（去掉后缀）」。
 *
 * <p>
 * <b>移植说明</b>：
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/util/PathHandler.java} 只用
 * {@link Path}，没有 Minecraft 类型依赖。1.7.10 侧逐行保持一致，<b>无语义差异</b>。
 */
public final class PathHandler {

    public static String getPath(Path root, Path file, String suffix) {
        String relative = root.relativize(file)
            .toString();
        String relativeWithoutSuffix = relative.substring(0, relative.length() - suffix.length());
        return relativeWithoutSuffix.replace('\\', '/');
    }
}
