package com.tacz.guns.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 摘要工具（枪包导出指纹、备份去重都用它）。
 *
 * <p>
 * <b>移植说明</b>：
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/util/Md5Utils.java} 只用
 * {@link MessageDigest} 等 JDK 类型，1.7.10 侧逐行一致，<b>无语义差异</b>。
 * 注意上游把 {@link MessageDigest} 作为静态单例共享（非线程安全），此处保持上游行为不做修正，
 * 以免改变已导出的指纹。
 */
public final class Md5Utils {

    private static final int STREAM_BUFFER_LENGTH = 1024;
    private static final MessageDigest DIGEST;

    static {
        try {
            DIGEST = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String md5Hex(InputStream inputStream) throws IOException {
        return toHexString(md5(inputStream));
    }

    public static String md5Hex(byte[] data) {
        return toHexString(DIGEST.digest(data));
    }

    public static byte[] md5(InputStream inputStream) throws IOException {
        final byte[] buffer = new byte[STREAM_BUFFER_LENGTH];
        int read = inputStream.read(buffer, 0, STREAM_BUFFER_LENGTH);
        while (read > -1) {
            DIGEST.update(buffer, 0, read);
            read = inputStream.read(buffer, 0, STREAM_BUFFER_LENGTH);
        }
        return DIGEST.digest();
    }

    public static byte[] md5(byte[] data) {
        return DIGEST.digest(data);
    }

    public static String toHexString(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
