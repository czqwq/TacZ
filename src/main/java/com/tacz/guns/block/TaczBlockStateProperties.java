package com.tacz.guns.block;

import java.lang.reflect.Type;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockPropertyTrait;
import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.properties.BooleanBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.properties.DirectionBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry;
import com.tacz.guns.init.ModBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * TACZ 方块的 GTNHLib blockstate 属性注册（<b>客户端模型渲染专用</b>）。
 *
 * <h2>为什么需要它</h2>
 * GTNHLib 的 JSON 模型系统（{@code com.gtnewhorizon.gtnhlib.client.model}）会按
 * {@code assets/tacz/blockstates/<block>.json} 的 {@code variants}/{@code multipart} 选模型，
 * 但选择条件来自 {@code BlockState#toMap()}（属性名 → 属性值字符串）。1.7.10 方块只有 metadata，
 * 因此必须把「1.20.1 BlockState 属性」映射成 GTNHLib {@link BlockProperty} 注册到方块上，
 * 属性名与取值必须与 blockstate JSON 完全一致（{@code facing=north}、{@code locked=true} 等）。
 *
 * <p>
 * 上游 1.20.1 的 BlockState 由 {@code StateDefinition} 声明，本类是它在 1.7.10 的等价物；
 * metadata 编码见各 block 类注释（{@link BlockMetadata#facing2D(ForgeDirection)} 等）。
 *
 * <h2>注册内容</h2>
 * <ul>
 * <li>弹药库（铁/金/钻石）：{@code facing}；</li>
 * <li>枪架（普通/发光）：{@code facing}、{@code shape}、{@code dyed}（染色状态不在 metadata，
 * 走 {@link GunPegboardBlock#pegboardColor} 现算）；</li>
 * <li>武器盒：{@code facing}、{@code locked}、{@code left/right/up/down}（连接状态按邻居现算，
 * 见 {@link GunDisplayCaseBlock#isConnected}）。</li>
 * </ul>
 *
 * <p>
 * 物品形态的默认值 = 1.20 {@code defaultBlockState()}（facing=north、locked=true、
 * 未连接、直板、未染色），由 {@code registerBlockItemProperty(block, prop, defaultValue)} 的
 * 只读 item 属性提供。
 */
@SideOnly(Side.CLIENT)
public final class TaczBlockStateProperties {

    /** facing 占用 metadata 低 2 bit（与 1.20 {@code Direction#get2DDataValue()} 编码一致）。 */
    private static final int FACING_MASK = 0b11;

    /** 武器盒 LOCKED 位（与 {@link GunDisplayCaseBlock} 的 metadata 编码一致）。 */
    private static final int LOCKED_FLAG = 1 << 2;

    private TaczBlockStateProperties() {}

    /** 客户端初始化时调用（必须在第一次渲染方块之前）。 */
    public static void register() {
        BlockProperty<ForgeDirection> facing = DirectionBlockProperty
            .facing(FACING_MASK, BlockMetadata::facing2D, BlockMetadata::facing);

        register(ModBlocks.AMMO_STORAGE, facing, ForgeDirection.NORTH);
        register(ModBlocks.AMMO_STORAGE_GOLD, facing, ForgeDirection.NORTH);
        register(ModBlocks.AMMO_STORAGE_DIAMOND, facing, ForgeDirection.NORTH);

        BlockProperty<String> shape = pegboardShape();
        BooleanBlockProperty dyed = pegboardDyed();
        for (Block pegboard : new Block[] { ModBlocks.GUN_PEGBOARD, ModBlocks.GLOW_GUN_PEGBOARD }) {
            register(pegboard, facing, ForgeDirection.NORTH);
            register(pegboard, shape, PegboardShape.STRAIGHT.getSerializedName());
            register(pegboard, dyed, Boolean.FALSE);
        }

        BlockProperty<Boolean> locked = BooleanBlockProperty.flag("locked", LOCKED_FLAG);
        register(ModBlocks.GUN_DISPLAY_CASE, facing, ForgeDirection.NORTH);
        register(ModBlocks.GUN_DISPLAY_CASE, locked, Boolean.TRUE);
        for (ForgeDirection dir : new ForgeDirection[] { ForgeDirection.UP, ForgeDirection.DOWN }) {
            register(ModBlocks.GUN_DISPLAY_CASE, connectionProperty(dirName(dir), dir), Boolean.FALSE);
        }
        // left/right 是「相对朝向」的方向：left = facing 的逆时针、right = 顺时针（与上游同名属性一致）。
        register(ModBlocks.GUN_DISPLAY_CASE, relativeConnectionProperty("left", false), Boolean.FALSE);
        register(ModBlocks.GUN_DISPLAY_CASE, relativeConnectionProperty("right", true), Boolean.FALSE);
    }

    private static <T> void register(Block block, BlockProperty<T> property, T itemDefault) {
        if (block == null) {
            return;
        }
        // 3 参重载 = 方块属性用实际实现 + 物品形态只读属性恒为 defaultValue（= defaultBlockState 语义）
        BlockPropertyRegistry.registerBlockItemProperty(block, property, itemDefault);
    }

    /** 1.20 {@code Direction#getName()} 的小写方向名。 */
    private static String dirName(ForgeDirection direction) {
        return direction.name()
            .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * {@code shape} 属性（枪架）：
     * metadata bit 2-3 存 {@link PegboardShape#toMeta()}，属性值用 {@link PegboardShape#getSerializedName()}。
     */
    private static BlockProperty<String> pegboardShape() {
        return new MetaBlockProperty<String>() {

            @Override
            public String getName() {
                return "shape";
            }

            @Override
            public Type getType() {
                return String.class;
            }

            @Override
            public boolean hasTrait(BlockPropertyTrait trait) {
                return switch (trait) {
                    case OnlyNeedsMeta, SupportsWorld, SupportsStacks, WorldMutable, StackMutable -> true;
                    default -> false;
                };
            }

            @Override
            public String getValue(int meta) {
                return GunPegboardBlock.getShape(meta)
                    .getSerializedName();
            }

            @Override
            public int getMeta(String value, int existing) {
                for (PegboardShape shape : PegboardShape.values()) {
                    if (shape.getSerializedName()
                        .equals(value)) {
                        return GunPegboardBlock.withShape(existing, shape);
                    }
                }
                return existing;
            }

            @Override
            public String stringify(String value) {
                return value;
            }

            @Override
            public String parse(String text) {
                return text;
            }
        };
    }

    /** {@code dyed} 属性（枪架）：上游 DYED 不进 metadata，按 TE/世界状态现算。 */
    private static BooleanBlockProperty pegboardDyed() {
        return new BooleanBlockProperty() {

            @Override
            public String getName() {
                return "dyed";
            }

            @Override
            public boolean hasTrait(BlockPropertyTrait trait) {
                return switch (trait) {
                    case SupportsWorld, SupportsStacks -> true;
                    default -> false;
                };
            }

            @Override
            public Boolean getValue(IBlockAccess world, int x, int y, int z) {
                return GunPegboardBlock.pegboardColor(world, x, y, z) >= 0;
            }

            @Override
            public Boolean getValue(ItemStack stack) {
                return Boolean.FALSE;
            }
        };
    }

    /** 武器盒连接属性（up/down 的绝对方向）。 */
    private static BooleanBlockProperty connectionProperty(String name, ForgeDirection direction) {
        return new BooleanBlockProperty() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean hasTrait(BlockPropertyTrait trait) {
                return switch (trait) {
                    case SupportsWorld, SupportsStacks -> true;
                    default -> false;
                };
            }

            @Override
            public Boolean getValue(IBlockAccess world, int x, int y, int z) {
                return GunDisplayCaseBlock.isConnected(world, x, y, z, direction);
            }

            @Override
            public Boolean getValue(ItemStack stack) {
                return Boolean.FALSE;
            }
        };
    }

    /** 武器盒连接属性（left/right 相对朝向）；{@code clockwise=true} 表示 facing 的顺时针方向。 */
    private static BooleanBlockProperty relativeConnectionProperty(String name, boolean clockwise) {
        return new BooleanBlockProperty() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean hasTrait(BlockPropertyTrait trait) {
                return switch (trait) {
                    case SupportsWorld, SupportsStacks -> true;
                    default -> false;
                };
            }

            @Override
            public Boolean getValue(IBlockAccess world, int x, int y, int z) {
                ForgeDirection facing = BlockMetadata.facing(world.getBlockMetadata(x, y, z));
                ForgeDirection direction = clockwise ? BlockMetadata.clockwise(facing)
                    : BlockMetadata.counterClockwise(facing);
                return GunDisplayCaseBlock.isConnected(world, x, y, z, direction);
            }

            @Override
            public Boolean getValue(ItemStack stack) {
                return Boolean.FALSE;
            }
        };
    }
}
