package com.tacz.guns.client.renderer.block;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.tacz.guns.client.resource.ItemModelDisplayManager;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 1.7.10 侧 {@code net.minecraft.client.renderer.entity.ItemRenderer#renderStatic} 的替身：
 * 在一段任意位姿下把物品画成一个「世界中的物件」。
 *
 * <h2>为什么需要这个类</h2>
 * 上游 {@code GunDisplayCaseRenderer} / {@code GunPegboardRenderer} / {@code StatueRenderer} 都调用
 * {@code Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
 * overlay, poseStack, buffer, level, 0)}。1.7.10 <b>没有</b> {@code ItemRenderer#renderStatic}、
 * 也没有 {@code ItemDisplayContext}，物品在世界中的渲染只有两条既有路径：
 * <ul>
 * <li>Forge 的 {@link IItemRenderer}（{@code MinecraftForgeClient.getItemRenderer}），TACZ 的枪械/配件就
 * 注册在这条路上（见 {@code LocalPlayerDraw} / {@code ItemClientHooks}）；</li>
 * <li>原版 {@link RenderItem} 的掉落物 / GUI 图标路径。</li>
 * </ul>
 * 因此本类按「先问 Forge 自定义渲染器、否则退回原版扁平图标」的顺序复刻
 * {@code ForgeHooksClient.renderEntityItem} + {@code RenderItem#renderItemIntoGUI} 的既有算法，
 * 不引入新机制。
 *
 * <p>
 * 上游没有对应文件，属移植新增的胶水（依据 {@code docs/PORT_PLAN.md} 3.1 节
 * 「{@code Minecraft#getItemRenderer()} → {@code getRenderItem()}」与 {@code ItemTransforms} 整包缺失的记录）。
 * 放在 {@code client/renderer/block} 是因为本包 3 个渲染器共用它；逻辑上更贴近
 * {@code client/renderer/item}，若日后该包统一提供同类入口，可把 3 个调用点改过去。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@code ItemDisplayContext} → {@link IItemRenderer.ItemRenderType}（{@link #toRenderType}）。
 * 1.7.10 只有 {@code ENTITY / EQUIPPED / EQUIPPED_FIRST_PERSON / INVENTORY / FIRST_PERSON_MAP}
 * 五种，没有 {@code FIXED} 与 {@code HEAD}，二者都退化为 {@code ENTITY}。<b>调用方必须包在
 * {@code DisplayItemRenderContext.render(...)} 里</b>：TACZ 自研渲染器（Ammo/Attachment/GunSmithTable）
 * 会据此把上下文还原成 1.20 的 {@code FIXED} 并套用物品模型 {@code display} 帧补偿
 * （见 {@link ItemModelDisplayManager}）；其它 mod 的 {@code IItemRenderer} 仍按 {@code ENTITY} 处理。</li>
 * <li>1.7.10 没有 1.20 的 {@code ItemRenderer#renderStatic}：TACZ 物品模型的 {@code display} 段由
 * {@link ItemModelDisplayManager} 在 {@code IItemRenderer} 侧读取并消费（本类只负责传上下文与亮度）。</li>
 * <li>{@code packedOverlay}（1.20 的受伤闪红）在 1.7.10 顶点格式里没有对应槽位，被忽略。</li>
 * </ul>
 */
public final class StaticItemRenderHelper {

    /** 与原版 {@code RenderItem} 内部那个私有的 {@code renderBlocksRi} 等价的一次性实例。 */
    private static final RenderBlocks RENDER_BLOCKS = new RenderBlocks();

    private StaticItemRenderHelper() {}

    /**
     * 对应上游 {@code ItemRenderer#renderStatic(ItemStack, ItemDisplayContext, int, int, PoseStack,
     * MultiBufferSource, Level, int)}。
     *
     * <p>
     * {@code bufferSource} 只用于语义对应（1.20 里物品顶点会进同一个 {@code BufferSource} 批次）；
     * 1.7.10 的 {@link IItemRenderer} / {@link RenderItem} 都是<b>立即</b>用固定管线画的，
     * 因此本方法直接施加 {@code poseStack} 到 {@code GL_MODELVIEW} 后立即绘制，不写 bufferSource。
     */
    public static void renderStatic(ItemStack stack, ItemDisplayContext context, int packedLight, int packedOverlay,
        PoseStack poseStack, MultiBufferSource bufferSource, World world, int seed) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        IItemRenderer.ItemRenderType type = toRenderType(context);
        IItemRenderer customRenderer = MinecraftForgeClient.getItemRenderer(stack, type);

        poseStack.pushToGL();
        try {
            // 1.7.10 的 lightmap 是「两段打包值」，直接喂 setLightmapTextureCoords（布局见 mcshim.LightTexture）；
            // 顶级渲染器（非自定义渲染器）走固定管线时的光照也依赖这里。
            OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                LightTexture.u(packedLight),
                LightTexture.v(packedLight));
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            // IItemRenderer 接口没有 packedLight 参数：经 ItemModelDisplayManager 的 ThreadLocal 传递，
            // 否则 Ammo/Attachment/GunSmithTable 等模型会退化成透传实体亮度/满亮度（非发光挂板夜里发亮）。
            Integer previousLight = ItemModelDisplayManager.pushPackedLight(packedLight);
            try {
                if (customRenderer != null && customRenderer.handleRenderType(stack, type)) {
                    renderWithCustomRenderer(stack, type, customRenderer, world);
                } else {
                    renderVanillaIcon(stack);
                }
            } finally {
                ItemModelDisplayManager.popPackedLight(previousLight);
            }
        } finally {
            poseStack.popFromGL();
        }
    }

    /**
     * 复刻 {@code ForgeHooksClient#renderEntityItem} 的缩放/贴图部分（去掉掉落物的旋转与上下浮动）。
     *
     * <p>
     * 原版把 {@code RenderBlocks} 与 {@code EntityItem} 作为 {@code data} 传给
     * {@link IItemRenderer#renderItem}（见 1.7.10 {@code ForgeHooksClient.java:136}），
     * 1.7.10 侧的自定义渲染器依赖这两个参数取图标/光照，因此这里构造一个零位姿的临时
     * {@link EntityItem} 与之对齐。
     *
     * <p>
     * 非 3D 分支的 {@code S(0.5)} 是 1.7.10 Forge 掉落物帧的既有约定，1.20 {@code renderStatic(FIXED)}
     * 没有这层缩放；TACZ 自研渲染器在 {@code DisplayItemRenderContext} 激活时会自行补 {@code S(2)}
     * 并换用 {@code display.fixed}（见 {@code ItemModelDisplayManager#applyDisplayTransform}）。
     */
    private static void renderWithCustomRenderer(ItemStack stack, IItemRenderer.ItemRenderType type,
        IItemRenderer customRenderer, World world) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityItem entityItem = new EntityItem(
            world == null ? minecraft.theWorld : world,
            0.0D,
            0.0D,
            0.0D,
            stack.copy());
        if (entityItem.getEntityItem() != null) {
            entityItem.getEntityItem().stackSize = 1;
        }
        entityItem.hoverStart = 0.0F;

        boolean block3d = customRenderer.shouldUseRenderHelper(type, stack, IItemRenderer.ItemRendererHelper.BLOCK_3D);
        Block block = stack.getItem() instanceof ItemBlock ? Block.getBlockFromItem(stack.getItem()) : null;
        minecraft.getTextureManager()
            .bindTexture(
                stack.getItemSpriteNumber() == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);

        if (block3d || block != null && RenderBlocks.renderItemIn3d(block.getRenderType())) {
            int renderType = block != null ? block.getRenderType() : 1;
            float scale = renderType == 1 || renderType == 19 || renderType == 12 || renderType == 2 ? 0.5F : 0.25F;
            boolean blend = block != null && block.getRenderBlockPass() > 0;
            if (blend) {
                GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            }
            GL11.glScalef(scale, scale, scale);
            customRenderer.renderItem(type, stack, RENDER_BLOCKS, entityItem);
            if (blend) {
                GL11.glDisable(GL11.GL_BLEND);
            }
        } else {
            GL11.glScalef(0.5F, 0.5F, 0.5F);
            customRenderer.renderItem(type, stack, RENDER_BLOCKS, entityItem);
        }
    }

    /**
     * 没有 Forge 自定义渲染器时的退路：画物品的扁平图标（原版 {@code RenderItem#renderItemIntoGUI}）。
     *
     * <p>
     * {@code renderItemIntoGUI} 工作在 GUI 像素坐标（16x16、+Y 向下），本方法用
     * {@code 1/16} 缩放 + Y 翻转把它翻译到「以 (0,0,0) 为中心、边长 1 的方块空间」。
     *
     * <p>
     * // TODO(L2): 上游 GunDisplayCaseRenderer.java:131-142 做的是
     * // {@code getItemRenderer().renderStatic(stack, FIXED, ..., context, buffer, level, 0)}，
     * // 由 1.20 的 ItemRenderer 施加物品模型 JSON 的 {@code FIXED} 变换并走 3D 图标渲染；
     * // 1.7.10 无 ItemTransforms、无 ItemRenderer#renderStatic，此处改用
     * // {@code RenderItem#renderItemIntoGUI} 在 1/16 缩放的翻转坐标系里近似（图标为薄片，非挤出模型）。
     */
    private static void renderVanillaIcon(ItemStack stack) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GL11.glTranslatef(-0.5F, 0.5F, 0.0F);
        GL11.glScalef(1.0F / 16.0F, -1.0F / 16.0F, 1.0F / 16.0F);
        RenderItem.getInstance()
            .renderItemIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, 0, 0);
    }

    /**
     * 对应 1.20 {@code ItemDisplayContext} → 1.7.10 {@code IItemRenderer.ItemRenderType} 的映射。
     * 1.7.10 没有 {@code FIXED} / {@code HEAD}，二者退化为 {@code ENTITY}。
     */
    public static IItemRenderer.ItemRenderType toRenderType(ItemDisplayContext context) {
        if (context == null) {
            return IItemRenderer.ItemRenderType.ENTITY;
        }
        switch (context) {
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                return IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON;
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                return IItemRenderer.ItemRenderType.EQUIPPED;
            case GUI:
                return IItemRenderer.ItemRenderType.INVENTORY;
            case NONE:
            case HEAD:
            case GROUND:
            case FIXED:
            default:
                return IItemRenderer.ItemRenderType.ENTITY;
        }
    }
}
