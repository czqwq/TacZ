package com.tacz.guns.command.sub;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;

import com.tacz.guns.resource.CommonAssetsManager;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * 移植说明：{@code /tacz reload}。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/ReloadCommand.java}）：
 * {@code Commands.literal("reload").executes(ReloadCommand::reloadAllPack)}，无参数；
 * 内部 {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ReloadCommand::reloadClient)} +
 * {@code DistExecutor.safeRunWhenOn(Dist.DEDICATED_SERVER, () -> CommonAssetsManager::reloadAllPack)}，
 * 计时用 {@code org.apache.commons.lang3.time.StopWatch}，结果用
 * {@code Component.translatable("commands.tacz.reload.success", time)} 广播给发送者。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>{@code Commands.literal("reload")} → {@link #RELOAD_NAME} + {@code args[0].equals("reload")}（由
 * {@link com.tacz.guns.command.RootCommand} 分发），{@code executes(...)} → {@link #processCommand}。</li>
 * <li>{@code DistExecutor#unsafeRunWhenOn(Dist.CLIENT, ...)} → {@code FMLCommonHandler.instance().getSide().isClient()}
 * 判断 + 客户端回调（{@link #setClientReloader}）。<b>不</b>在这里 import {@code com.tacz.guns.client.*}：
 * 命令类会被专用服务端加载，直接引用客户端类会在服务端触发 {@code NoClassDefFoundError}。
 * 客户端资源管理器（{@code ClientAssetsManager::reloadAllPack}）由 {@code ClientProxy} 在 init 阶段注册进来。</li>
 * <li>{@code DistExecutor#safeRunWhenOn(Dist.DEDICATED_SERVER, ...)} → {@code !side.isClient()} 时调
 * {@link CommonAssetsManager#reloadAllPack()}。</li>
 * <li>{@code StopWatch} → {@link System#nanoTime()}（1.7.10 的 commons-lang3 版本较老，避免 {@code createStarted()}）；
 * 计时语义相同（毫秒，double）。</li>
 * <li>{@code Component.translatable(key, args)} → {@link ChatComponentTranslation}（{@code %s}/{@code %.3f} 由
 * 原版 {@code String.format} 填充，语言文件里的 key 与占位符不变）；
 * {@code sendSystemMessage(...)} → {@link ICommandSender#addChatMessage}。</li>
 * <li>{@code Command.SINGLE_SUCCESS} → 常量 {@link #SINGLE_SUCCESS}（值同为 1）。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：{@code DistExecutor} 的"编译期安全分端"（编译期校验客户端 lambda 不泄漏到服务端）
 * 在 1.7.10 只能靠 {@code @SideOnly} + 代理类手工保证；本类用回调注册法绕开，语义等价。
 */
public class ReloadCommand {

    public static final String RELOAD_NAME = "reload";
    /** 上游 {@code com.mojang.brigadier.Command.SINGLE_SUCCESS} */
    public static final int SINGLE_SUCCESS = 1;

    private static final String USAGE = "/tacz reload";

    /** 由 {@code ClientProxy} 注册的客户端重载入口，专用服务端永远为 null */
    private static Runnable clientReloader;

    /**
     * 注册客户端资源重载动作。调用方（{@code ClientProxy}）：
     * {@code ReloadCommand.setClientReloader(ClientAssetsManager::reloadAllPack)}。
     *
     * <p>
     * 这是上游 {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ReloadCommand::reloadClient)}
     * 在 1.7.10 的替身：命令类不能直接链接客户端类。
     */
    public static void setClientReloader(Runnable reloader) {
        clientReloader = reloader;
    }

    /** 等价上游 {@code ReloadCommand#reloadClient}，改为调用注册进来的回调 */
    public static void reloadClient() {
        if (clientReloader != null) {
            clientReloader.run();
        }
    }

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length != 0) {
            throw new WrongUsageException(USAGE);
        }
        long start = System.nanoTime();
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            reloadClient();
        } else {
            CommonAssetsManager.reloadAllPack();
        }
        double time = (System.nanoTime() - start) / 1000.0D / 1000.0D;
        sender.addChatMessage(new ChatComponentTranslation("commands.tacz.reload.success", time));
        return SINGLE_SUCCESS;
    }
}
