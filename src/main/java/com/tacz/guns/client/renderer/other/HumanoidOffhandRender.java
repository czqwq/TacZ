package com.tacz.guns.client.renderer.other;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.pojo.display.gun.LayerGunShow;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.util.math.MathUtil;

/**
 * 第三人称下把「副手 + 快捷栏里没拿在手上的枪」画在玩家身上（上游 1.20.1 的挂枪显示）。
 *
 * <h2>1.7.10 平台替换</h2>
 * <ul>
 * <li>上游 {@code LivingEntity} → {@link EntityLivingBase}；{@code player.getInventory().selected}
 * → {@link InventoryPlayer#currentItem}；{@code getItem(i)} → {@link InventoryPlayer#getStackInSlot(int)}。</li>
 * <li>上游 {@code Minecraft#getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay,
 * poseStack, bufferSource, level, entityId)} → 直接调同一 dest
 * {@link AnimateGeoItemRenderer#renderByItem(ItemStack, com.tacz.guns.mcshim.ItemDisplayContext,
 * PoseStack, MultiBufferSource, int, int)} 并传 {@code ItemDisplayContext.FIXED}
 * （= {@code GunItemRendererWrapper#renderByItem}，内部做 {@code GunItemTransforms.apply(FIXED, …)}）。
 * {@code renderStatic} 在 BEWLR 之前的 {@code T(-0.5,-0.5,-0.5)}（1.20 {@code ItemRenderer#render:129}）
 * 要自己补，见 {@code renderGunItem}。
 * <b>不要</b>退回 {@link ItemRenderer#renderItem(EntityLivingBase, ItemStack, int, IItemRenderer.ItemRenderType)}
 * 的 {@code EQUIPPED} 入口：那会在 `hotbar_show` pose 之外再叠一层 1.7.10 的 2D 纸片物品帧
 * （`RenderPlayer:319` + `:380-384` + `ForgeHooksClient:219`），实机表现为
 * 「挂枪竖直立在身体旁边、位置对不上」（原始现象见 {@code docs/review/r4/01-first-person.md}）。</li>
 * <li>{@link MultiBufferSource} 参数就是 `renderByItem` 的批次出口（1.7.10 侧由 `mcshim` 的
 * `BufferSource` 承担），不再走 `pushToGL()/popFromGL()` —— `renderByItem` 全程用 CPU 侧
 * {@link PoseStack} 把顶点烘好，不读 GL 矩阵。</li>
 * </ul>
 */
public class HumanoidOffhandRender {

    public static void renderGun(EntityLivingBase entity, PoseStack matrixStack, MultiBufferSource buffer,
        int packedLight) {
        if (RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.get()) {
            return;
        }
        renderOffhandGun(entity, matrixStack, buffer, packedLight);
        renderHotbarGun(entity, matrixStack, buffer, packedLight);
    }

    /**
     * 上游 {@code renderOffhandGun}：渲染副手上的枪。
     *
     * <p>
     * // TODO(L2): 上游 GunItemRendererWrapper/Inventory 用 1.20 的副手槽
     * // （{@code Entity#getOffhandItem()} / {@code Player#getInventory()}）。1.7.10 <b>没有副手槽</b>
     * // （双持是 1.9 引入的），因此此处没有可渲染的副手物品，方法体保持空转但保留方法名与签名，
     * // 由 {@code mixin/client/ItemInHandLayerMixin}（client-core 侧）继续按原结构调用。
     * // 视觉差异：1.7.10 下不会出现「副手挂枪」。
     */
    private static void renderOffhandGun(EntityLivingBase entity, PoseStack matrixStack, MultiBufferSource buffer,
        int packedLight) {
        // 1.7.10 无双持槽位，无副手物品可渲染（见方法 javadoc 的 TODO(L2)）。
    }

    private static void renderHotbarGun(EntityLivingBase entity, PoseStack matrixStack, MultiBufferSource buffer,
        int packedLight) {
        if (!(entity instanceof EntityPlayer player)) {
            return;
        }
        InventoryPlayer inventory = player.inventory;
        if (inventory == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (i == inventory.currentItem) {
                continue;
            }
            ItemStack stack = inventory.getStackInSlot(i);
            renderHotbarGun(entity, matrixStack, buffer, packedLight, stack, i);
        }
    }

    private static void renderHotbarGun(EntityLivingBase entity, PoseStack matrixStack, MultiBufferSource buffer,
        int packedLight, ItemStack itemStack, int inventoryIndex) {
        if (itemStack == null) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(itemStack);
        if (iGun == null) {
            return;
        }
        TimelessAPI.getGunDisplay(itemStack)
            .ifPresent(display -> {
                var hotbarShow = display.getHotbarShow();
                if (hotbarShow == null || hotbarShow.isEmpty()) {
                    return;
                }
                if (!hotbarShow.containsKey(inventoryIndex)) {
                    return;
                }
                LayerGunShow gunShow = hotbarShow.get(inventoryIndex);
                renderGunItem(entity, matrixStack, buffer, packedLight, itemStack, gunShow);
            });
    }

    private static void renderGunItem(EntityLivingBase entity, PoseStack matrixStack, MultiBufferSource buffer,
        int packedLight, ItemStack itemStack, LayerGunShow offhandShow) {
        Vector3f pos = offhandShow.getPos();
        Vector3f rotate = offhandShow.getRotate();
        Vector3f scale = offhandShow.getScale();
        matrixStack.pushPose();
        // 坐标系 = 枪包 display 的 `hotbar_show` pose；等价上游
        // `Minecraft#getItemRenderer().renderStatic(itemStack, FIXED, …, matrixStack, buffer, level, entityId)`
        // （`tmp/TACZ-1.20.1/.../HumanoidOffhandRender.java:91`）：对 `builtin/entity` 物品落到
        // `GunItemRendererWrapper#renderByItem(FIXED, …)`，内部做 `GunItemTransforms.apply(FIXED, …)`。
        // 不要退回 `ItemRenderer#renderItem(..., EQUIPPED)`：那会多叠一层 1.7.10 的 2D 纸片物品帧，
        // 且取的是 `equipped` 而不是 `fixed` 变换 ⇒ 挂枪竖直立在身体旁边、位置也不对。
        matrixStack.translate(-pos.x() / 16f, 1.5 - pos.y() / 16f, pos.z() / 16f);
        matrixStack.scale(-scale.x(), -scale.y(), scale.z());
        Quaternionf rotation = new Quaternionf();
        MathUtil.toQuaternion(
            (float) Math.toRadians(rotate.x),
            (float) Math.toRadians(rotate.y),
            (float) Math.toRadians(rotate.z),
            rotation);
        matrixStack.mulPose(rotation);
        // 上游 renderStatic(FIXED) 在 BEWLR 之前还有一次无条件的 T(-0.5,-0.5,-0.5)
        // （1.20 ItemRenderer#render:129），用于把 1×1×1 物品模型从角点居中到原点。
        // 这里直接调 renderByItem 绕过了 ItemRenderer，必须自己补，否则挂枪整体偏 (0.5,0.5,0.5) 格。
        matrixStack.translate(-0.5f, -0.5f, -0.5f);
        if (MinecraftForgeClient.getItemRenderer(
            itemStack,
            IItemRenderer.ItemRenderType.EQUIPPED) instanceof AnimateGeoItemRenderer<?, ?>renderer) {
            renderer.renderByItem(
                itemStack,
                ItemDisplayContext.FIXED,
                matrixStack,
                buffer,
                packedLight,
                OverlayTexture.NO_OVERLAY);
        }
        matrixStack.popPose();
    }
}
