package com.tacz.guns.block;

/**
 * 方块渲染 id 的持有者（<b>双端安全</b>：只放一个 int，不引用任何客户端类）。
 *
 * <p>
 * 上游 1.20.1 的方块渲染形状由 {@code Block#getRenderShape(BlockState)} 表达
 * （{@code RenderShape.MODEL} / {@code RenderShape.ENTITYBLOCK_ANIMATED} / {@code INVISIBLE}）；
 * 1.7.10 的等价物是 {@code Block#getRenderType()} 返回的整数 id：
 * <ul>
 * <li>{@code 0} = {@code RenderBlocks} 的原版立方体（单 {@code IIcon} 铺满每面）；</li>
 * <li>{@code -1} = 方块本体不渲染，交给 {@code TileEntitySpecialRenderer}；</li>
 * <li>其它值 = 由 {@code RenderingRegistry} 登记的 {@code ISimpleBlockRenderingHandler} 处理。</li>
 * </ul>
 *
 * <p>
 * {@code RenderShape.MODEL} 的三个方块（弹药库 / 枪架 / 展示柜）用第三类（GTNHLib JSON 模型系统），id 在客户端初始化时由
 * {@code client/init/ClientSetupEvent} 里写入（来源是 GTNHLib {@code ModelISBRH.JSON_ISBRH_ID}）。未安装时（专用服务器、
 * 或客户端初始化尚未执行）保持 {@link #VANILLA_CUBE}，此时退回移植原来的立方体渲染而不是让方块消失 ——
 * 这是有意的安全兜底。
 */
public final class BlockRenderIds {

    /** {@code RenderBlocks} 的原版立方体渲染 id。 */
    public static final int VANILLA_CUBE = 0;

    private static volatile int jsonModel = VANILLA_CUBE;

    private BlockRenderIds() {}

    /** 1.20.1 {@code RenderShape.MODEL} 的 1.7.10 渲染 id。 */
    public static int jsonModel() {
        return jsonModel;
    }

    /** 由客户端初始化（ClientSetupEvent → TaczBlockStateProperties 接线）写入。 */
    public static void setJsonModel(int renderId) {
        jsonModel = renderId;
    }
}
