package com.tacz.guns.config;

import java.io.File;

import com.tacz.guns.config.sync.SyncConfig;

/**
 * 服务端（SERVER）配置，对应上游 1.20.1
 * {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/ServerConfig.java}（{@code ModConfig.Type.SERVER}）。
 *
 * <p>
 * 上游注释提到的「Forge 配置文件加载时间窗口问题」在 1.7.10 表现为：可用 {@link #isLoaded()} 判断配置是否已读。
 *
 * <p>
 * 1.7.10 差异（重要）：上游 {@code ModConfig.Type.SERVER} 由 Forge 在玩家登录时自动把服务端配置同步给客户端，
 * 因此 client/** 里的代码可以直接 {@code SyncConfig.XXX.get()}。1.7.10 没有这个机制，改为
 * {@code network/message/ServerMessageSyncConfig} 在玩家登录时手动下发，客户端收到后覆盖本地内存中的值
 * （见 {@link SyncConfig#applyFromServer}）。语义差异：客户端本地文件里的值会被服务端值覆盖，
 * 且配置文件本身不会被改写（与上游 Forge 一致）。
 */
public class ServerConfig {

    public static final String FILE_NAME = "tacz-server.cfg";

    public static TaczConfigSpec SERVER_CONFIG_SPEC;

    private ServerConfig() {}

    public static TaczConfigSpec init(File configFile) {
        TaczConfigSpec spec = new TaczConfigSpec(configFile);
        ConfigBuilder builder = new ConfigBuilder(spec);
        SyncConfig.init(builder);
        SERVER_CONFIG_SPEC = builder.build();
        return SERVER_CONFIG_SPEC;
    }

    /**
     * 因为配置文件的加载时间窗口问题，上游会缓存一份「是否已加载」的检查位。1.7.10 用同一语义：
     * {@link TaczConfigSpec#isLoaded()}。
     */
    public static boolean isLoaded() {
        return SERVER_CONFIG_SPEC != null && SERVER_CONFIG_SPEC.isLoaded();
    }
}
