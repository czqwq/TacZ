package com.tacz.guns.client.renderer.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.joml.Matrix4f;

import com.gtnewhorizon.gtnhlib.client.model.BakeData;
import com.gtnewhorizon.gtnhlib.client.model.BakedModelQuadContext;
import com.gtnewhorizon.gtnhlib.client.model.ModelISBRH;
import com.gtnewhorizon.gtnhlib.client.model.baked.BakedModel;
import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;
import com.gtnewhorizon.gtnhlib.client.model.loading.ResourceLoc;
import com.gtnewhorizon.gtnhlib.client.model.unbaked.JSONModel;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.resource.ItemModelDisplayManager;
import com.tacz.guns.client.resource.pojo.model.ItemModelDisplay;
import com.tacz.guns.item.AmmoBoxItem;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

/**
 * 弹药盒（{@code AmmoBoxItem}）的 3D 物品渲染器。
 *
 * <h2>上游依据</h2>
 * 1.20 的弹药盒是<b>纯数据驱动</b>的物品模型：
 * <ul>
 * <li>{@code assets/tacz/models/item/ammo_box.json} 只有 {@code overrides}，按物品属性
 * {@code tacz:ammo_statue}（0..8，值由 {@code AmmoBoxItem#getStatue} 算出）选择 9 个模型之一；</li>
 * <li>9 个模型在 {@code assets/tacz/models/item/ammo_box/*.json}，是普通 JSON 元素模型
 * （{@code elements} + {@code faces} + {@code tintindex}，参考 1.20 {@code ItemModel} 格式）。</li>
 * </ul>
 * 1.7.10 没有 JSON 物品模型系统，移植早期只给了 {@code registerIcons("tacz:ammo_box")} 的平面图标
 * （那是模型贴图集，不是图标），实机上表现为所有弹药盒图标错乱。
 *
 * <h2>1.7.10 实现</h2>
 * 运行环境常驻的 GTNHLib 已内置 1.20 JSON 模型解析/烘焙（{@code ModelRegistry#getJSONModel} →
 * {@code JSONModel#bake}，同样是 1.20 模型语义），并且会自动把 {@code models/**} 引用的贴图注册进方块图集
 * （{@code registerModid} 的扫描，见 {@code ClientProxy#preInit}）。因此这里只做「桥」：
 * <ol>
 * <li>按 {@code AmmoBoxItem#getStatue} 选出上表中的模型，用 GTNHLib 烘焙成四边形（按模型路径缓存，资源重载时清空）；</li>
 * <li>用 {@link ItemModelDisplayManager#applyDisplayTransform} 把 1.20 物品显示帧折算到 1.7.10 的
 * GUI/手持/掉落物帧（9 个模型都没有 {@code display} 段 ⇒ {@link ItemModelDisplay#NO_DISPLAY}，
 * 只剩各上下文的基础帧）；</li>
 * <li>用 {@code entityCutoutNoCull(方块图集)} 一次性画完所有面（图集里已有这些贴图），
 * 颜色 = 物品染色（{@code AmmoBoxItem#getColor}，对应 1.20 物品 tint）× 逐面方向光
 * （{@code ModelISBRH#diffuseLight}，等价 1.20 物品模型的 3D GUI 光照）。</li>
 * </ol>
 *
 * <p>
 * 该渲染器的 {shouldUseRenderHelper} 与 {@link GunSmithTableItemRenderer} 保持一致（EQUIPPED 走
 * {@code EQUIPPED_BLOCK}、ENTITY 申请自转/浮沉），否则 1.7.10 会再叠一层 2D 纸片帧。
 */
public class AmmoBoxItemRenderer implements IItemRenderer {

    /**
     * {@code tacz:ammo_statue} 属性值（{@link AmmoBoxItem#getStatue}）→ 模型路径，顺序与上游
     * {@code models/item/ammo_box.json} 的 {@code overrides} 一一对应。
     */
    private static final String[] MODEL_PATHS = {
        // 0/1 铁质
        "item/ammo_box/iron_ammo_box_open", "item/ammo_box/iron_ammo_box_close",
        // 2/3 金质
        "item/ammo_box/gold_ammo_box_open", "item/ammo_box/gold_ammo_box_close",
        // 4/5 钻石
        "item/ammo_box/diamond_ammo_box_open", "item/ammo_box/diamond_ammo_box_close",
        // 6/7 创造
        "item/ammo_box/creative_ammo_box_open", "item/ammo_box/creative_ammo_box_close",
        // 8 创造（全类型）
        "item/ammo_box/all_type_creative_ammo_box" };

    /** 模型贴图都注册在方块图集里（GTNHLib 自动贴图注册的落地位置），因此一次绑定即可画完。 */
    private static final ResourceLocation ATLAS_TEXTURE = TextureMap.locationBlocksTexture;

    /** 模型路径 → 烘焙结果。只在客户端渲染线程读写；资源重载时清空（贴图/模型可能被资源包覆盖）。 */
    private static final Map<String, BakedModel> MODEL_CACHE = new HashMap<>();

    /** 资源重载时清空缓存（由 {@code InternalAssetLoader#onResourceReload} 调用，与其它客户端模型缓存同一时机）。 */
    public static void onResourceReload() {
        MODEL_CACHE.clear();
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return item != null;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return switch (type) {
            case EQUIPPED, EQUIPPED_FIRST_PERSON -> helper == ItemRendererHelper.EQUIPPED_BLOCK;
            case ENTITY -> !RenderItem.renderInFrame
                && (helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING);
            default -> false;
        };
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        BakedModel model = modelFor(stack);
        if (model == null) {
            return;
        }
        PoseStack poseStack = new PoseStack();
        ItemModelDisplayManager.applyDisplayTransform(type, ItemModelDisplay.NO_DISPLAY, poseStack);
        int packedLight = ItemModelDisplayManager.packedLightFor(type, data);
        Matrix4f pose = poseStack.last()
            .pose();

        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate();
        try {
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(ATLAS_TEXTURE));
            QuadContext context = new QuadContext(stack);
            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                context.quadFacing = facing;
                for (ModelQuadView quad : model.getQuads(context)) {
                    emitQuad(consumer, pose, quad, stack, packedLight);
                }
            }
        } finally {
            bufferSource.endBatch();
        }
    }

    /** 取（并按需烘焙）{@code ammo_statue} 对应的模型；失败时返回 {@code null} 让调用方画原版图标。 */
    private static BakedModel modelFor(ItemStack stack) {
        int statue = (int) AmmoBoxItem.getStatue(stack);
        if (statue < 0 || statue >= MODEL_PATHS.length) {
            statue = 0;
        }
        String path = MODEL_PATHS[statue];
        BakedModel cached = MODEL_CACHE.get(path);
        if (cached != null) {
            return cached;
        }
        try {
            JSONModel jsonModel = ModelRegistry.getJSONModel(new ResourceLoc.ModelLoc(GunMod.MOD_ID, path));
            BakedModel baked = jsonModel.bake(BakeData.IDENTITY);
            MODEL_CACHE.put(path, baked);
            return baked;
        } catch (RuntimeException e) {
            GunMod.LOGGER.error("[TACZ] failed to bake ammo box item model {}", path, e);
            return null;
        }
    }

    /**
     * 写一个四边形：颜色 = 物品 tint（{@code tintindex} 面才有）× 方向光；UV 已经由烘焙阶段
     * （{@code JSONModel#bake} → {@code bakeSprite}）换算到图集坐标。
     */
    private static void emitQuad(VertexConsumer consumer, Matrix4f pose, ModelQuadView quad, ItemStack stack,
        int packedLight) {
        int tint = quad.getColorIndex() < 0 ? -1 : AmmoBoxItem.getColor(stack, quad.getColorIndex());
        float tintR = tint == -1 ? 1.0F : ((tint >> 16) & 0xFF) / 255.0F;
        float tintG = tint == -1 ? 1.0F : ((tint >> 8) & 0xFF) / 255.0F;
        float tintB = tint == -1 ? 1.0F : (tint & 0xFF) / 255.0F;

        // 方向光：等价 1.20 物品模型的 3D GUI 光照（模型没有 gui_light 段 ⇒ 默认 3D 光照）；
        // emissiveness 对应 1.20 元素/面自发光，取两者的最大值。
        float shade = quad.hasDirectionalShading() ? ModelISBRH.diffuseLight(quad.getComputedFaceNormal()) : 1.0F;
        shade = Math.max(shade, quad.getEmissiveness() / 15.0F);

        for (int i = 0; i < 4; ++i) {
            consumer.vertex(pose, quad.getX(i), quad.getY(i), quad.getZ(i));
            consumer.color(tintR * shade, tintG * shade, tintB * shade, 1.0F);
            consumer.uv(quad.getTexU(i), quad.getTexV(i));
            consumer.uv2(packedLight & 0xFFFF, (packedLight >> 16) & 0xFFFF);
            consumer.endVertex();
        }
    }

    /**
     * {@link BakedModelQuadContext} 的极简实现：{@code getQuads} 只用 {@link #quadFacing}，
     * 物品模型没有方块状态（{@code getBlockState} 返回 {@code null}，本渲染器不会走
     * {@code ModelRegistry#getBakedModel(BakedModelQuadContext)} 那条需要方块状态的路径）。
     */
    private static final class QuadContext implements BakedModelQuadContext.Item {

        private final ItemStack stack;
        private ModelQuadFacing quadFacing = ModelQuadFacing.UNASSIGNED;

        private QuadContext(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemStack getItemStack() {
            return stack;
        }

        @Override
        public com.gtnewhorizon.gtnhlib.blockstate.core.BlockState getBlockState() {
            return null;
        }

        @Override
        public ModelQuadFacing getQuadFacing() {
            return quadFacing;
        }

        @Override
        public Random getRandom() {
            return null;
        }

        @Override
        public java.util.function.Supplier<com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable> getQuadPool() {
            return null;
        }
    }
}
