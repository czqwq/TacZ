package com.tacz.guns.resource.pack;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.GunMod;

/**
 * zip 形态枪包（{@code <gamedir>/tacz/<packName>.zip}）的 {@link IGunPackSource} 实现。
 *
 * <p>
 * 对应上游 1.20.1 中 {@code Pack.readMetaAndCreate} 把 zip 转成 {@code PathPackResources}（{@code SecureJar}）的路径：
 * 参考 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/GunPackLoader.java:96-117,120-133}。
 * 1.7.10 没有 {@code SecureJar}，直接走 {@code java.util.zip.ZipFile}。
 *
 * <p>
 * {@code ZipFile} 实例会被复用（枪包内文件数较多，逐个重开 zip 太慢），
 * 因此本类持有文件句柄：{@link #close()} 必须在枪包列表重建时调用，见
 * {@link com.tacz.guns.resource.GunPackLoader#reloadPackList()}。
 * 关闭是<b>延迟</b>的：已借出的输入流读完后才真正释放句柄（并发读窗口见 {@link #close()}）。
 */
public final class ZipGunPackSource implements IGunPackSource {

    private final Path path;
    private final String name;
    /** 懒打开的 zip 句柄；{@link #close()} 会置空，因此必须 volatile（见 {@link #zip()} 的注释）。 */
    private volatile ZipFile zipFile;
    /** 已借出、尚未关闭的输入流数量（见 {@link #close()}：有读者时延迟关闭）。 */
    private int activeReaders;
    /** 是否已经要求关闭（之后不再借出新流）。 */
    private boolean closeRequested;

    public ZipGunPackSource(Path path) {
        this(
            path,
            stripExtension(
                path.getFileName() == null ? path.toString()
                    : path.getFileName()
                        .toString()));
    }

    public ZipGunPackSource(Path path, String name) {
        this.path = path.toAbsolutePath()
            .normalize();
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public synchronized List<String> listFiles(String pathPrefix) {
        if (closeRequested) {
            // 已退役（见 open/close）：不再重开句柄，否则会留下无人关闭的文件句柄。
            return Collections.emptyList();
        }
        ZipFile zip = zip();
        if (zip == null) {
            return Collections.emptyList();
        }
        String prefix = pathPrefix == null || pathPrefix.isEmpty() ? "" : normalizePrefix(pathPrefix);
        List<String> output = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String entryName = entry.getName()
                .replace('\\', '/');
            if (entryName.startsWith(prefix)) {
                output.add(entryName);
            }
        }
        Collections.sort(output);
        return output;
    }

    @Override
    public synchronized @Nullable InputStream open(String path) {
        if (closeRequested) {
            // 已退役：重建枪包列表后旧 source 不再有新读者；仍在读的流由 activeReaders 保护到读完为止。
            return null;
        }
        ZipFile zip = zip();
        if (zip == null) {
            return null;
        }
        ZipEntry entry = zip.getEntry(path);
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        try {
            InputStream stream = zip.getInputStream(entry);
            activeReaders++;
            return new TrackedInputStream(stream);
        } catch (IOException exception) {
            GunMod.LOGGER.error("Failed to read zip gun pack entry {} in {}", path, this.path, exception);
            return null;
        }
    }

    /**
     * 请求释放 zip 句柄。重建枪包列表时必须调用。
     *
     * <p>
     * <b>1.7.10 差异（单人游戏并发）</b>：单人游戏里客户端与服务端共用 {@code GunPackLoader} 的同一份枪包列表，
     * 而 {@code /tacz reload} 会在服务端线程重扫（本方法被调用）时，客户端线程可能正在读同一 zip 里的贴图/模型。
     * 直接关句柄会让那次读取抛 {@code zip file closed}（表现为贴图丢失/索引缺项）。因此这里只置
     * {@link #closeRequested}，等已借出的流全部关闭（{@link #activeReaders} 归零）后才真正关闭句柄：
     * 正常路径下延迟只有一次读取的时长（毫秒级），不会长期占住文件。
     */
    public synchronized void close() {
        closeRequested = true;
        if (activeReaders == 0) {
            doClose();
        }
    }

    /** 借出的流关闭时回调；最后一个读者离开且已请求关闭才真正关句柄。 */
    private synchronized void releaseReader() {
        if (activeReaders > 0) {
            activeReaders--;
        }
        if (activeReaders == 0 && closeRequested) {
            doClose();
        }
    }

    private void doClose() {
        ZipFile zip = this.zipFile;
        this.zipFile = null;
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException exception) {
                GunMod.LOGGER.warn("Failed to close zip gun pack {}", this.path, exception);
            }
        }
    }

    /**
     * 懒打开并复用 zip 句柄。
     *
     * <p>
     * 1.7.10 差异（并发）：{@code ZipFile} 本身的读操作是线程安全的，但「首次打开」这段不是——
     * {@code LazyJsonDataManager#getData} 会在客户端渲染线程/服务端线程按需触发枪包内文件读取，
     * 两个线程同时看到 {@code null} 会各开一个 {@code ZipFile}，后写入的覆盖前一个，
     * 前一个句柄永远不关闭（Windows 上表现为删不掉枪包 zip）。因此这里加锁。
     */
    private synchronized @Nullable ZipFile zip() {
        if (zipFile != null) {
            return zipFile;
        }
        try {
            zipFile = new ZipFile(this.path.toFile());
            return zipFile;
        } catch (IOException exception) {
            GunMod.LOGGER.error("Failed to open zip gun pack {}", this.path, exception);
            return null;
        }
    }

    /** 借出的 zip 输入流：关闭时通知 source 记账（用于延迟关闭句柄）。 */
    private final class TrackedInputStream extends FilterInputStream {

        private boolean released;

        TrackedInputStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (!released) {
                    released = true;
                    releaseReader();
                }
            }
        }
    }

    private static String normalizePrefix(String pathPrefix) {
        return pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }
}
