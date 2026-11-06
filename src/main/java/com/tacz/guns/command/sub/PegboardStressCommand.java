package com.tacz.guns.command.sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.resource.index.CommonGunIndex;

/**
 * 移植说明：{@code /tacz debug pegboard <count> <same|different> [pos]}（调试压力测试命令）。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/.../command/sub/PegboardStressCommand.java}）：
 * 
 * <pre>
 * Commands.literal("pegboard")
 *     .then(Commands.argument("count", IntegerArgumentType.integer(1, 16384))
 *         .then(Commands.literal("same").executes(...).then(Commands.argument("pos", BlockPosArgument.blockPos())...))
 *         .then(Commands.literal("different").executes(...).then(Commands.argument("pos", ...)...)))
 * </pre>
 * 
 * 按枪械索引的 {@code sort} 顺序刷枪，再把它们塞进一排新放的枪械挂板（{@code ModBlocks.GUN_PEGBOARD} +
 * {@code GunPegboardBlockEntity#setItems}），用来压测挂板渲染/同步。
 *
 * <p>
 * <b>1.7.10 形态 / 映射</b>：
 * <ul>
 * <li>字面量与参数路径 → {@code args[0]=count}、{@code args[1]=same|different}、{@code args[2..4]=x y z}
 * （1.7.10 没有语法树，参数按位置解析；上游 {@code pos} 是 {@code BlockPosArgument} 的 3 个 token）。</li>
 * <li>{@code IntegerArgumentType.integer(1, MAX_GUNS)}
 * → {@code CommandBase.parseIntBounded(sender, arg, 1, MAX_GUNS)}。</li>
 * <li>{@code BlockPosArgument.getBlockPos(...)} → {@link #parseBlockPos}（支持 {@code ~} 相对坐标，
 * 语义对齐 1.20 的 {@code BlockPosArgument}：解析 double 后向下取整）。</li>
 * <li>{@code source.getLevel()}/{@code ServerLevel} → {@code sender.getEntityWorld()}（{@link World}）。</li>
 * <li>{@code Direction.fromYRot(source.getRotation().y)} → {@link #yawToDirection}（1.7.10 用
 * {@link ForgeDirection}；yaw 取自 {@link EntityPlayerMP#rotationYaw}，命令方块等非玩家发送者视为 0）。</li>
 * <li>{@code forward.getClockWise()} → {@code yaw + 90°} 方向的 {@link ForgeDirection}（"玩家右手边"）。</li>
 * <li>{@code BlockPos} → {@link ChunkCoordinates}（1.7.10 的整数坐标三元组）；
 * {@code BlockPos#toShortString()} → {@link #toShortString}（{@code "x, y, z"}）。</li>
 * <li>{@code level.getMinBuildHeight()/getMaxBuildHeight()} → 1.7.10 固定 0..255；
 * {@code level.isLoaded(pos)} → {@code world.blockExists(x, y, z)}；
 * {@code getBlockState(pos).isAir()} → {@code world.isAirBlock(x, y, z)}。</li>
 * <li>{@code Component.translatable(...)} → {@link ChatComponentTranslation}（语言 key 未改动）；
 * {@code sendFailure/sendSuccess(..., false)} → {@link ICommandSender#addChatMessage}。</li>
 * <li>{@code stack.isEmpty()} → {@code stack == null}（1.7.10 用 null 表示空栈）；
 * {@code getFireModeSet().get(0)} 增加空表保护（数据包可能没有开火模式）。</li>
 * <li>{@code stream().sorted(...).map(...).toList()} → 等价 Java 8 循环 + {@link ArrayList}。</li>
 * </ul>
 *
 * <p>
 * <b>未完成（跨包依赖，见 {@link #placeBoards}）</b>：真正的"放方块 + 往挂板实体塞物品"这一段依赖
 * {@code init/ModBlocks#GUN_PEGBOARD}、{@code block/GunPegboardBlock}、{@code block/entity/GunPegboardBlockEntity}，
 * 这三个类仍是 1.20.1 形态（{@code RegistryObject}/{@code BlockState}/{@code BlockEntity}），命令类无法编译期引用。
 * 上游那一段逻辑以注释形式完整保留在 {@link #placeBoards} 的 javadoc 里，等它们移植为
 * {@code ModBlocks.GUN_PEGBOARD}（{@code Block}）+ {@code world.setBlock}/{@code getTileEntity} 后启用。
 * 因此本命令目前的行为是：<b>位置校验通过后报告放置失败</b>（复用上游
 * {@code commands.tacz.debug.pegboard.failed} 语言 key，不新增 key），不会假装成功。
 */
public final class PegboardStressCommand {

    public static final String PEGBOARD_NAME = "pegboard";
    private static final int MAX_GUNS = 16384;
    private static final int BOARDS_PER_ROW = 32;
    private static final int[] DISPLAY_SLOTS = { 1, 3, 9, 11 };
    /** 上游参数名，仅用于用法串，字符串不可改 */
    private static final String COUNT = "count";
    private static final String POS = "pos";
    private static final String MODE_SAME = "same";
    private static final String MODE_DIFFERENT = "different";

    /** 1.7.10 世界高度固定 0..255（上游 {@code level.getMinBuildHeight()/getMaxBuildHeight()}） */
    private static final int MIN_BUILD_HEIGHT = 0;
    private static final int MAX_BUILD_HEIGHT = 256;

    private static final String USAGE = "/tacz debug " + PEGBOARD_NAME
        + " <"
        + COUNT
        + ":1.."
        + MAX_GUNS
        + ">"
        + " <"
        + MODE_SAME
        + "|"
        + MODE_DIFFERENT
        + "> [<"
        + POS
        + ">]";

    /** 上游 {@code Direction.fromYRot} 的 1.7.10 等价物：yaw 0 = south，每 90° 一档 */
    private static final ForgeDirection[] YAW_DIRECTIONS = { ForgeDirection.SOUTH, ForgeDirection.WEST,
        ForgeDirection.NORTH, ForgeDirection.EAST };

    private PegboardStressCommand() {}

    public static int processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(USAGE);
        }
        int requested = CommandBase.parseIntBounded(sender, args[0], 1, MAX_GUNS);
        boolean same;
        if (MODE_SAME.equals(args[1])) {
            same = true;
        } else if (MODE_DIFFERENT.equals(args[1])) {
            same = false;
        } else {
            throw new WrongUsageException(USAGE);
        }
        boolean hasPos = args.length > 2;
        if (hasPos && args.length != 5) {
            throw new WrongUsageException(USAGE);
        }
        return generate(sender, requested, same, hasPos, args);
    }

    public static String getUsage() {
        return USAGE;
    }

    private static int generate(ICommandSender sender, int requested, boolean same, boolean hasPos, String[] args) {
        World world = sender.getEntityWorld();

        // The index sort is the order used by the creative gun list; the id makes ties deterministic.
        List<ItemStack> guns = collectGuns();
        if (guns.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("commands.tacz.debug.pegboard.no_guns"));
            return 0;
        }

        int boardCount = (requested + DISPLAY_SLOTS.length - 1) / DISPLAY_SLOTS.length;
        int columns = Math.min(BOARDS_PER_ROW, boardCount);
        float yaw = sender instanceof EntityPlayerMP ? ((EntityPlayerMP) sender).rotationYaw : 0.0F;
        ForgeDirection forward = yawToDirection(yaw);
        ForgeDirection right = yawToDirection(yaw + 90.0F);
        ChunkCoordinates origin = hasPos ? parseBlockPos(sender, args) : defaultOrigin(sender, forward, right, columns);

        List<ChunkCoordinates> positions = new ArrayList<>(boardCount);
        for (int i = 0; i < boardCount; i++) {
            ChunkCoordinates pos = relative(origin, right, i % columns, i / columns);
            if (pos.posY < MIN_BUILD_HEIGHT || pos.posY >= MAX_BUILD_HEIGHT
                || !world.blockExists(pos.posX, pos.posY, pos.posZ)
                || !world.isAirBlock(pos.posX, pos.posY, pos.posZ)) {
                sender.addChatMessage(
                    new ChatComponentTranslation("commands.tacz.debug.pegboard.blocked", toShortString(pos)));
                return 0;
            }
            positions.add(pos);
        }

        return placeBoards(sender, positions, requested, same, boardCount, origin, forward, guns);
    }

    /**
     * 上游「放置挂板并填物品」的那一段。
     *
     * <p>
     * <b>r2 修复</b>：原实现恒 {@code return 0}，TODO 理由是「依赖 {@code ModBlocks} /
     * {@code GunPegboardBlock} / {@code GunPegboardBlockEntity} 的 1.7.10 移植」——该理由<b>已过期</b>，
     * 这三个类都已交付（{@code GunPegboardBlock} 含染色/连接/掉落，{@code GunPegboardBlockEntity}
     * 含 {@code setItems(Map&lt;Integer, ItemStack&gt;)}）。现按下面这段上游逻辑实装。
     *
     * <pre>
     * // 上游（1.20.1）：
     * BlockState state = ModBlocks.GUN_PEGBOARD.get()
     *     .defaultBlockState()
     *     .setValue(GunPegboardBlock.FACING, forward.getOpposite());
     * int placed = 0;
     * for (int board = 0; board &lt; boardCount; board++) {
     *     BlockPos pos = positions.get(board);
     *     if (!level.setBlock(pos, state, Block.UPDATE_CLIENTS)
     *         || !(level.getBlockEntity(pos) instanceof GunPegboardBlockEntity pegboard)) {
     *         source
     *             .sendFailure(Component.translatable("commands.tacz.debug.pegboard.failed", placed, pos.toShortString()));
     *         return placed;
     *     }
     *     int slots = Math.min(DISPLAY_SLOTS.length, requested - placed);
     *     Map&lt;Integer, ItemStack&gt; contents = new LinkedHashMap&lt;&gt;(slots);
     *     for (int slot = 0; slot &lt; slots; slot++) {
     *         contents.put(DISPLAY_SLOTS[slot], guns.get(same ? 0 : (placed + slot) % guns.size()));
     *     }
     *     if (!pegboard.setItems(contents)) {
     *         source
     *             .sendFailure(Component.translatable("commands.tacz.debug.pegboard.failed", placed, pos.toShortString()));
     *         return placed;
     *     }
     *     placed += slots;
     * }
     * int distinct = same ? 1 : Math.min(requested, guns.size());
     * source.sendSuccess(
     *     () -&gt; Component
     *         .translatable("commands.tacz.debug.pegboard.success", placed, boardCount, distinct, origin.toShortString()),
     *     false);
     * return placed;
     * </pre>
     *
     * <p>
     * <b>1.7.10 差异</b>：{@code BlockState.setValue(FACING, ...)} → metadata 低 2 bit
     * （{@link GunPegboardBlock#withFacing(int, ForgeDirection)}，编码与上游
     * {@code Direction#get2DDataValue} 一致）；{@code Block.UPDATE_CLIENTS} → flag {@code 3}
     * （{@code BLOCK_UPDATE | SEND_TO_CLIENTS}）；{@code level.getBlockEntity} → {@link World#getTileEntity}
     * （1.7.10 的 TileEntity 在 {@code setBlock} 之后由 Chunk 惰性创建，顺序不能反）；
     * {@code sendSuccess/sendFailure} → {@link ICommandSender#addChatMessage}。
     *
     * @return 已放置的枪械数量（上游返回值语义）
     */
    private static int placeBoards(ICommandSender sender, List<ChunkCoordinates> positions, int requested, boolean same,
        int boardCount, ChunkCoordinates origin, ForgeDirection forward, List<ItemStack> guns) {
        World world = sender.getEntityWorld();
        int placed = 0;
        for (int board = 0; board < boardCount; board++) {
            ChunkCoordinates pos = positions.get(board);
            // 上游：state = GUN_PEGBOARD.defaultBlockState().setValue(FACING, forward.getOpposite())
            // 1.7.10：朝向压进 metadata 低 2 bit（GunPegboardBlock#withFacing，编码与上游
            // Direction#get2DDataValue 一致）。flag=2 对应上游 Block.UPDATE_CLIENTS（只下发给客户端，
            // 不触发邻块更新）；见 build/rfg/minecraft-src/java/net/minecraft/world/World.java 的 setBlock flags。
            int metadata = GunPegboardBlock.withFacing(0, forward.getOpposite());
            if (!world.setBlock(pos.posX, pos.posY, pos.posZ, ModBlocks.GUN_PEGBOARD, metadata, 2)) {
                sender.addChatMessage(
                    new ChatComponentTranslation("commands.tacz.debug.pegboard.failed", placed, toShortString(pos)));
                return placed;
            }
            // 1.7.10 的 TileEntity 是 setBlock 之后由 Chunk 惰性创建的，因此必须紧接 setBlock 再取。
            TileEntity tile = world.getTileEntity(pos.posX, pos.posY, pos.posZ);
            if (!(tile instanceof GunPegboardBlockEntity pegboard)) {
                sender.addChatMessage(
                    new ChatComponentTranslation("commands.tacz.debug.pegboard.failed", placed, toShortString(pos)));
                return placed;
            }
            int slots = Math.min(DISPLAY_SLOTS.length, requested - placed);
            Map<Integer, ItemStack> contents = new LinkedHashMap<>(slots);
            for (int slot = 0; slot < slots; slot++) {
                contents.put(DISPLAY_SLOTS[slot], guns.get(same ? 0 : (placed + slot) % guns.size()));
            }
            if (!pegboard.setItems(contents)) {
                sender.addChatMessage(
                    new ChatComponentTranslation("commands.tacz.debug.pegboard.failed", placed, toShortString(pos)));
                return placed;
            }
            placed += slots;
        }
        int distinct = same ? 1 : Math.min(requested, guns.size());
        // 上游 source.sendSuccess(supplier, false) → 1.7.10 直接 addChatMessage
        sender.addChatMessage(
            new ChatComponentTranslation(
                "commands.tacz.debug.pegboard.success",
                placed,
                boardCount,
                distinct,
                toShortString(origin)));
        return placed;
    }

    /**
     * 上游 {@code TimelessAPI.getAllCommonGunIndex().stream().sorted(...).map(...).toList()} 的 Java 8 循环版。
     */
    private static List<ItemStack> collectGuns() {
        List<Map.Entry<ResourceLocation, CommonGunIndex>> entries = new ArrayList<>(TimelessAPI.getAllCommonGunIndex());
        Collections.sort(
            entries,
            Comparator.comparingInt(
                (Map.Entry<ResourceLocation, CommonGunIndex> entry) -> entry.getValue()
                    .getSort())
                .thenComparing(
                    entry -> entry.getKey()
                        .toString()));

        List<ItemStack> guns = new ArrayList<>(entries.size());
        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : entries) {
            CommonGunIndex index = entry.getValue();
            List<FireMode> fireModes = index.getGunData() == null ? null
                : index.getGunData()
                    .getFireModeSet();
            FireMode fireMode = fireModes == null || fireModes.isEmpty() ? FireMode.UNKNOWN : fireModes.get(0);
            ItemStack stack = GunItemBuilder.create()
                .setId(entry.getKey())
                .setFireMode(fireMode)
                .build();
            // 上游 filter(stack -> !stack.isEmpty())；1.7.10 的空栈是 null
            if (stack != null) {
                guns.add(stack);
            }
        }
        return guns;
    }

    /**
     * 上游
     * {@code BlockPos.containing(source.getPosition()).relative(forward, 4).above().relative(right, -(columns - 1) / 2)}。
     */
    private static ChunkCoordinates defaultOrigin(ICommandSender sender, ForgeDirection forward, ForgeDirection right,
        int columns) {
        ChunkCoordinates base = sender.getPlayerCoordinates();
        int shift = -(columns - 1) / 2;
        return new ChunkCoordinates(
            base.posX + forward.offsetX * 4 + right.offsetX * shift,
            base.posY + 1,
            base.posZ + forward.offsetZ * 4 + right.offsetZ * shift);
    }

    /** {@code origin.relative(right, i % columns).above(i / columns)} */
    private static ChunkCoordinates relative(ChunkCoordinates origin, ForgeDirection direction, int horizontal,
        int up) {
        return new ChunkCoordinates(
            origin.posX + direction.offsetX * horizontal,
            origin.posY + up,
            origin.posZ + direction.offsetZ * horizontal);
    }

    /** 上游 {@code BlockPosArgument.getBlockPos(context, "pos")}：{@code args[2..4]} 为 x/y/z，支持 {@code ~} */
    private static ChunkCoordinates parseBlockPos(ICommandSender sender, String[] args) {
        ChunkCoordinates base = sender.getPlayerCoordinates();
        return new ChunkCoordinates(
            parseCoordinate(base.posX, args[2]),
            parseCoordinate(base.posY, args[3]),
            parseCoordinate(base.posZ, args[4]));
    }

    private static int parseCoordinate(int current, String arg) {
        boolean relative = arg.startsWith("~");
        try {
            double offset = relative ? (arg.length() > 1 ? Double.parseDouble(arg.substring(1)) : 0.0D)
                : Double.parseDouble(arg);
            return MathHelper.floor_double(relative ? current + offset : offset);
        } catch (NumberFormatException e) {
            throw new NumberInvalidException("commands.generic.num.invalid", arg);
        }
    }

    /** 上游 {@code Direction.fromYRot(yaw)}；返回水平朝向（1.7.10 用 {@link ForgeDirection}） */
    private static ForgeDirection yawToDirection(float yaw) {
        int index = MathHelper.floor_double(yaw * 4.0D / 360.0D + 0.5D) & 3;
        return YAW_DIRECTIONS[index];
    }

    /** 上游 {@code BlockPos#toShortString()}：{@code "x, y, z"} */
    private static String toShortString(ChunkCoordinates pos) {
        return pos.posX + ", " + pos.posY + ", " + pos.posZ;
    }
}
