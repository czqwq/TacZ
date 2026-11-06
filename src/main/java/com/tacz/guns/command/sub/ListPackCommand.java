package com.tacz.guns.command.sub;

/**
 * 移植说明：{@code ListPackCommand}（上游即为空壳）。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/ListPackCommand.java}）：整个文件只有一个空的
 * {@code public class ListPackCommand {}}，既没有 {@code get()} 也没有 brigadier 节点，并且<b>没有</b>被
 * {@code RootCommand#register} 挂进命令树 —— 是上游遗留的占位类（原本要做的 {@code /tacz list} 没实现）。
 *
 * <p>
 * <b>1.7.10 形态</b>：保持同样的空壳与类名，避免上游后续补齐时出现类名漂移。
 * 这里只补 javadoc 说明，不新增任何命令：命令名与语言 key 集合必须与上游完全一致。
 */
public class ListPackCommand {
}
