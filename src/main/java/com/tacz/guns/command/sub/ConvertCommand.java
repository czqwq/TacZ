package com.tacz.guns.command.sub;

import java.util.function.Consumer;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

import com.tacz.guns.GunMod;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * 移植说明：{@code /tacz convert}。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/ConvertCommand.java}）：
 * {@code Commands.literal("convert").executes(ConvertCommand::convert)}，无参数；
 * 内部 {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PackConvertor.convert(context.getSource()))}
 * —— 只在客户端把 {@code config/tacz/custom/*.zip} 的旧版枪包转换成新版目录，服务端不做任何事。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>{@code Commands.literal("convert")} → {@link #CONVERT_NAME}（由 {@link com.tacz.guns.command.RootCommand}
 * 分发），{@code executes(...)} → {@link #processCommand}。</li>
 * <li>{@code Dist#CLIENT} 判断 → {@code FMLCommonHandler.instance().getSide().isClient()}。</li>
 * <li>上游的转换实现 {@code com.tacz.guns.resource.PackConvertor#convert(CommandSourceStack)}
 * 目前仍是 1.20.1 签名（参数是 {@code CommandSourceStack}），且它引用了
 * {@code com.tacz.guns.client.resource.pojo.PackInfo}。命令类禁止 import client 包，也拿不到
 * {@code CommandSourceStack}，所以这里改成<b>回调注册</b>：
 * 由客户端初始化时调用
 * {@code ConvertCommand.setConverter(PackConvertor::convert)}（待 {@code PackConvertor} 移植为
 * {@code convert(ICommandSender)} 之后接线）。</li>
 * <li>{@code Command.SINGLE_SUCCESS} → {@link #SINGLE_SUCCESS}（值 1）。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：{@code DistExecutor} 的编译期分端检查（1.7.10 用 {@code @SideOnly} + 代理代替）；
 * 在转换器尚未接线时命令静默成功（只打一条 warn 日志），不冒充"已完成转换"。
 */
public class ConvertCommand {

    public static final String CONVERT_NAME = "convert";
    /** 上游 {@code com.mojang.brigadier.Command.SINGLE_SUCCESS} */
    private static final int SINGLE_SUCCESS = 1;

    private static final String USAGE = "/tacz " + CONVERT_NAME;

    /**
     * 客户端枪包转换入口，签名与 {@code PackConvertor#convert} 移植后应保持一致
     * （参数为命令发送者，用于把转换进度直接发到聊天栏）。
     */
    private static Consumer<ICommandSender> converter;

    /**
     * 注册客户端转换实现。
     *
     * <p>
     * 由 {@code client/ClientProxy#init} 注入
     * （{@code ConvertCommand.setConverter(PackConvertor::convert)}）。
     *
     * <p>
     * 1.7.10 用「命令侧留回调位 + 客户端入口注入」替代上游的
     * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PackConvertor.convert(source))}，
     * 使 {@code command/**} 不必 import client 包。
     *
     * <p>
     * 原 TODO(port)「PackConvertor 仍是 1.20.1 签名」已于 r2 删除：{@code resource/PackConvertor.java:62}
     * 早已是 {@code convert(ICommandSender)}，缺的只是注入这一行。
     */
    public static void setConverter(Consumer<ICommandSender> clientConverter) {
        converter = clientConverter;
    }

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length != 0) {
            throw new WrongUsageException(USAGE);
        }
        // 上游只在客户端执行；专用服务端上该命令什么也不做（DistExecutor.unsafeRunWhenOn 不触发）
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            Consumer<ICommandSender> current = converter;
            if (current != null) {
                current.accept(sender);
            } else {
                GunMod.LOGGER.warn("[TACZ] /tacz convert: 客户端枪包转换器未注入（应由 ClientProxy#init 调用 setConverter），跳过转换。");
            }
        }
        return SINGLE_SUCCESS;
    }

    public static String getUsage() {
        return USAGE;
    }
}
