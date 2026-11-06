package com.tacz.guns.init;

import com.tacz.guns.command.RootCommand;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/**
 * 移植说明：{@code /tacz} 命令的注册入口。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/CommandRegistry.java}）：
 * 
 * <pre>
 * &#64;Mod.EventBusSubscriber // 监听 Forge 事件总线（非 MOD 总线）
 * public final class CommandRegistry {
 * 
 *     &#64;SubscribeEvent
 *     public static void onServerStaring(RegisterCommandsEvent event) {
 *         RootCommand.register(event.getDispatcher()); // 把 brigadier 命令树交给 dispatcher
 *     }
 * }
 * </pre>
 * 
 * 上游靠 {@code RegisterCommandsEvent}（服务器启动时、{@code CommandDispatcher} 建好之后）把命令挂进 dispatcher。
 *
 * <p>
 * <b>1.7.10 形态</b>：没有 {@code RegisterCommandsEvent}，也没有 {@code CommandDispatcher}；
 * 对应物是 {@link FMLServerStartingEvent}（FML 在服务器启动时触发），注册方法是
 * {@link FMLServerStartingEvent#registerServerCommand(net.minecraft.command.ICommand)}，
 * 它最终调用 1.7.10 的 {@code net.minecraft.command.CommandHandler#registerCommand}。
 * 因此本类提供一个静态方法供 {@code CommonProxy#serverStarting} 调用：
 * 
 * <pre>
 * 
 * // CommonProxy
 * &#64;Override
 * public void serverStarting(FMLServerStartingEvent event) {
 *     CommandRegistry.registerCommands(event);
 * }
 * </pre>
 *
 * <p>
 * <b>丢掉的语义</b>：
 * <ul>
 * <li>上游支持 {@code RegisterCommandsEvent#getCommandSelection()}/{@code getBuildContext()}（区分
 * {@code DEDICATED}/{@code INTEGRATED}、做重载），1.7.10 无此概念，命令对所有 {@code ICommandSender} 一视同仁。</li>
 * <li>上游 {@code @Mod.EventBusSubscriber} 自动注册；1.7.10 必须由代理显式调用（见上）。</li>
 * <li>集成服务器每次开新世界都会重新触发 {@link FMLServerStartingEvent}，所以这里每次 new 一个
 * {@link RootCommand} 交给 {@code CommandHandler}（{@code CommandHandler} 每个世界重建，不会重复注册）。</li>
 * </ul>
 */
public final class CommandRegistry {

    private CommandRegistry() {}

    /**
     * 等价上游 {@code CommandRegistry#onServerStaring(RegisterCommandsEvent)}。
     *
     * @param event FML 服务器启动事件，由 {@code CommonProxy#serverStarting} 传入
     */
    public static void registerCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new RootCommand());
    }
}
