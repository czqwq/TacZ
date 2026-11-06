package com.tacz.guns.command.sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.PlayerSelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 移植说明（移植特有类，上游没有对应文件）：{@code EntityArgument.entities()} 的 1.7.10 替代品。
 *
 * <p>
 * <b>上游形态</b>：{@code commands.tacz} 的多个子命令（{@code attachment_lock} / {@code dummy} /
 * {@code hide_tooltip_part}）都声明了 {@code Commands.argument("target", EntityArgument.entities())}，
 * 执行时用 {@code EntityArgument.getEntities(context, "target")} 拿到 {@code Collection<? extends Entity>}。
 * brigadier 的 {@code EntitySelector} 在 1.20.1 支持 {@code @e}/{@code @a}/{@code @p}/{@code @r}/玩家名，
 * 以及 {@code [type=...]} 这类过滤器。
 *
 * <p>
 * <b>1.7.10 形态</b>：原版只有 {@link PlayerSelector}，它<b>只能选玩家</b>
 * （{@code @a}/{@code @p}/{@code @r}/玩家名，带 {@code [r=]/[x=]/[m=]...} 过滤），没有 {@code @e}，
 * 也没有 {@code type=} 过滤。因此本类做如下映射：
 * <ul>
 * <li>{@code @a}/{@code @p}/{@code @r}/玩家名 → {@link PlayerSelector#matchPlayers}；单个玩家名走
 * {@link CommandBase#getPlayer}（与指令内 {@code getPlayer(sender, args[i])} 的惯例一致）。</li>
 * <li>{@code @e} → 1.7.10 没有实体选择器，退化为"当前维度全部已加载的 {@link EntityLivingBase}"，
 * 即 {@code sender.getEntityWorld().loadedEntityList} 过滤活体。这是本移植引入的语义，上游没有。</li>
 * </ul>
 *
 * <p>
 * <b>丢掉的语义</b>：{@code type=}/{@code nbt=}/{@code scores=}/{@code predicate=} 等 1.20.1 选择器参数无法表达；
 * 选择器的语法错误不再被 brigadier 在解析期拦下，而是在执行期由 {@link PlayerSelector} 返回 null/空数组，
 * 本类统一抛 {@link PlayerNotFoundException}（沿用原版 {@code commands.generic.player.notFound} 语言键，
 * 不新增语言 key）。
 */
public final class CommandTargets {

    /** 1.20.1 {@code EntitySelector} 的"所有实体"标记；1.7.10 无原生对应，见类注释 */
    public static final String ALL_ENTITIES = "@e";

    private CommandTargets() {}

    /**
     * 等价上游 {@code EntityArgument.getEntities(context, "target")}。
     *
     * @param sender 命令发送者（{@link PlayerSelector} 需要它计算相对坐标与权限）
     * @param token  {@code args[i]}，即上游的 target 参数
     * @return 命中的活体实体列表，永不为 null
     * @throws PlayerNotFoundException 选择器没有命中任何玩家时（与原版指令行为一致）
     */
    public static List<EntityLivingBase> getLivingEntities(ICommandSender sender, String token) {
        if (token == null || token.isEmpty()) {
            throw new PlayerNotFoundException();
        }
        if (ALL_ENTITIES.equals(token)) {
            List<EntityLivingBase> all = new ArrayList<>();
            for (Entity entity : sender.getEntityWorld().loadedEntityList) {
                if (entity instanceof EntityLivingBase) {
                    all.add((EntityLivingBase) entity);
                }
            }
            if (all.isEmpty()) {
                throw new PlayerNotFoundException();
            }
            return all;
        }
        if (token.charAt(0) == '@') {
            EntityPlayerMP[] players = PlayerSelector.matchPlayers(sender, token);
            if (players == null || players.length == 0) {
                throw new PlayerNotFoundException();
            }
            List<EntityLivingBase> result = new ArrayList<>(players.length);
            Collections.addAll(result, players);
            return result;
        }
        return Collections.<EntityLivingBase>singletonList(CommandBase.getPlayer(sender, token));
    }
}
