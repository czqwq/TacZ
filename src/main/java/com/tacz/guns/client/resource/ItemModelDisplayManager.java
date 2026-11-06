package com.tacz.guns.client.resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.joml.Matrix4f;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.renderer.item.DisplayItemRenderContext;
import com.tacz.guns.client.resource.pojo.display.block.BlockItemTransforms;
import com.tacz.guns.client.resource.pojo.model.ItemModelDisplay;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 1.20 物品模型 {@code display} 段（{@code assets/tacz/models/item/*.json}）在 1.7.10 的读取与消费端。
 *
 * <p>
 * 上游由原版 {@code ItemRenderer#render} 施加：{@code getTransform(ctx).apply(leftHand, poseStack)} 之后
 * <b>无条件</b> {@code translate(-0.5,-0.5,-0.5)}（tmp/minecraft-src-1.20.1/.../entity/ItemRenderer.java:128-129）。
 * 1.7.10 的 {@code ForgeHooksClient} 各分支帧与 1.20 不同（INVENTORY 没有 GUI 缩放、EQUIPPED/第一人称叠着
 * 「2D 纸片物品」帧、ENTITY 多一层 {@code S(0.5)}），因此 {@link #applyDisplayTransform} 逐分支先抵消 1.7.10
 * 自带帧再套 1.20 帧，即实现 {@code F_1.7.10 · A == F_1.20}（逐分支推导见各私有方法注释；`F` 均不含手臂骨骼帧，
 * 该帧两侧同源、在矩阵左端相消）。
 */
public final class ItemModelDisplayManager {

    /** 物品模型位置 → display 数据；{@code models/item/*.json} 可被资源包覆盖，故按 ResourceLocation 缓存。 */
    private static final Map<ResourceLocation, ItemModelDisplay> CACHE = new HashMap<>();

    private ItemModelDisplayManager() {}

    /** 读取指定物品模型（如 {@code tacz:item/ammo}）的 {@code display} 段；读不到时返回 {@link ItemModelDisplay#NO_DISPLAY}。 */
    public static ItemModelDisplay get(ResourceLocation itemModel) {
        ItemModelDisplay cached = CACHE.get(itemModel);
        if (cached != null) {
            return cached;
        }
        ItemModelDisplay loaded = load(itemModel);
        if (loaded == null) {
            // 读失败（资源管理器未就绪/IO 异常）：不缓存，下一帧重试
            return ItemModelDisplay.NO_DISPLAY;
        }
        CACHE.put(itemModel, loaded);
        return loaded;
    }

    /** 资源重载时清缓存（由 {@code InternalAssetLoader#onResourceReload} 调用）。 */
    public static void onResourceReload() {
        CACHE.clear();
    }

    private static ItemModelDisplay load(ResourceLocation itemModel) {
        // 1.7.10 的 IResourceManager 只认完整资源路径：models/item/xxx.json 会被解析到 assets/<domain>/ 下
        ResourceLocation file = new ResourceLocation(
            itemModel.getResourceDomain(),
            "models/" + itemModel.getResourcePath() + ".json");
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            // 资源管理器尚未就绪：不缓存，等下一帧重试
            return null;
        }
        try {
            IResource resource = minecraft.getResourceManager()
                .getResource(file);
            if (resource == null) {
                return ItemModelDisplay.NO_DISPLAY;
            }
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonElement json = new JsonParser().parse(reader);
                if (!json.isJsonObject()) {
                    return ItemModelDisplay.NO_DISPLAY;
                }
                JsonElement display = json.getAsJsonObject()
                    .get("display");
                if (display == null || !display.isJsonObject()) {
                    return ItemModelDisplay.NO_DISPLAY;
                }
                ItemModelDisplay parsed = ClientAssetsManager.GSON.fromJson(display, ItemModelDisplay.class);
                return parsed == null ? ItemModelDisplay.NO_DISPLAY : parsed.resolveLeftHandFallback();
            }
        } catch (IOException | RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ] failed to load item model display: {}", file, e);
            return null;
        }
    }

    /**
     * 1.7.10 {@code ItemRenderType} → 1.20 {@code ItemDisplayContext}。
     *
     * <p>
     * 在 {@link RenderHelperCompat#displayContextOf} 的映射上补两处 1.7.10 的「固定姿态」入口：
     * 展示框（{@code RenderItem.renderInFrame == true}，RenderItemFrame.java:248-250）与
     * 展示柜/挂板/雕像（{@link DisplayItemRenderContext#isActive()}，上游都调 {@code renderStatic(FIXED)}）
     * 都从 {@code ENTITY} 入口进来，1.20 对应 {@code ItemDisplayContext.FIXED}，不能当 {@code GROUND}。
     */
    public static ItemDisplayContext displayContextOf(IItemRenderer.ItemRenderType type) {
        if (type == IItemRenderer.ItemRenderType.ENTITY
            && (RenderItem.renderInFrame || DisplayItemRenderContext.isActive())) {
            return ItemDisplayContext.FIXED;
        }
        return RenderHelperCompat.displayContextOf(type, false);
    }

    /**
     * {@code renderStatic} 路径的 packedLight 覆盖值（ThreadLocal）。
     *
     * <p>
     * 1.7.10 {@code IItemRenderer#renderItem} 没有 1.20 BEWLR 的 {@code packedLight} 参数；展示柜/挂板/雕像经
     * {@code StaticItemRenderHelper#renderStatic}（上游 {@code ItemRenderer#renderStatic} 的替身）调用自定义渲染器，
     * 因此由该 helper 在调用前后 {@link #pushPackedLight}/{@link #popPackedLight} 把亮度传进来。ThreadLocal +
     * 保存旧值 ⇒ 渲染线程隔离、嵌套调用（例如展示柜里再走一次 renderStatic）也不会串值。
     */
    private static final ThreadLocal<Integer> CURRENT_PACKED_LIGHT = new ThreadLocal<>();

    /** 设置当前 static 路径的 packedLight，返回旧值供 {@link #popPackedLight} 恢复（null = 之前未设置）。 */
    public static Integer pushPackedLight(int packedLight) {
        Integer previous = CURRENT_PACKED_LIGHT.get();
        CURRENT_PACKED_LIGHT.set(packedLight);
        return previous;
    }

    /** 与 {@link #pushPackedLight} 成对，恢复/清除覆盖值。 */
    public static void popPackedLight(Integer previous) {
        if (previous == null) {
            CURRENT_PACKED_LIGHT.remove();
        } else {
            CURRENT_PACKED_LIGHT.set(previous);
        }
    }

    /**
     * 1.7.10 {@code IItemRenderer} 没有 1.20 BEWLR 的 {@code packedLight} 参数，这里补出传给模型的亮度。
     *
     * <p>
     * {@code EQUIPPED}/{@code EQUIPPED_FIRST_PERSON}/{@code ENTITY} 走 {@link RenderHelperCompat#packedLightFor}
     * （实体亮度）；展示柜/挂板/雕像由 {@link #pushPackedLight} 的 ThreadLocal 覆盖（见其注释）。
     */
    public static int packedLightFor(IItemRenderer.ItemRenderType type, Object[] data) {
        Integer override = CURRENT_PACKED_LIGHT.get();
        if (override != null) {
            return override;
        }
        return RenderHelperCompat.packedLightFor(type, data);
    }

    /**
     * 把 display 变换（含 1.20 {@code ItemRenderer#render:129} 的无条件 -0.5）按渲染分支折算进 PoseStack。
     *
     * <p>
     * 必须在 {@code renderByItem} 之前、且与 {@link #displayContextOf} 同一渲染上下文调用。
     * 1.7.10 只有主手/右手路径（无双持槽），故 display 的左手镜像参数恒为 {@code false}。
     */
    public static void applyDisplayTransform(IItemRenderer.ItemRenderType type, ItemModelDisplay display,
        PoseStack poseStack) {
        if (type == null) {
            return;
        }
        switch (type) {
            case INVENTORY -> applyGuiDisplay(display, poseStack);
            case EQUIPPED -> applyEquippedDisplay(display, poseStack);
            case EQUIPPED_FIRST_PERSON -> applyFirstPersonDisplay(display, poseStack);
            case ENTITY -> {
                if (RenderItem.renderInFrame) {
                    // 展示框：F17 = frame基座·S(0.5)（ForgeHooksClient:147），F20 = frame基座·S(0.5)·display·T(-0.5)
                    applyFixedDisplay(display, poseStack, 1.0F);
                } else if (DisplayItemRenderContext.isActive()) {
                    // 展示柜/挂板/雕像：StaticItemRenderHelper 走 ENTITY 并先 S(0.5)（它把上游 renderStatic(FIXED) 退化成
                    // ENTITY），故补偿为 inv(S0.5)·display.fixed·T(-0.5)
                    applyFixedDisplay(display, poseStack, 2.0F);
                } else {
                    applyGroundDisplay(display, poseStack);
                }
            }
            default -> {}
        }
    }

    /**
     * GUI（INVENTORY）：{@code applyGuiSlotScale · T(0.5) · display.gui · T(-0.5)}。
     *
     * <p>
     * 1.7.10 的 ForgeHooksClient#renderInventoryItem:187-205 只把 GL 平移到图标左上角、没有 1.20 的
     * {@code GuiGraphics#renderItem:481-494} 槽位缩放；{@link RenderHelperCompat#applyGuiSlotScale} 已把两者
     * 折算成等价帧（其中已含 1.20 的 {@code T(-0.5)}），故 display 只需再夹一层 ±0.5。z 方向的 GUI 深度
     * 约定不同（1.7.10 用 {@code -3+zLevel}、1.20 用 {@code 150}），不影响屏幕位置。
     */
    private static void applyGuiDisplay(ItemModelDisplay display, PoseStack poseStack) {
        RenderHelperCompat.applyGuiSlotScale(poseStack);
        poseStack.translate(0.5F, 0.5F, 0.5F);
        display.getTransform(ItemDisplayContext.GUI)
            .apply(false, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    /**
     * 第三人称手持（EQUIPPED）：{@code EQUIPPED_COMPENSATION · display.thirdperson_righthand · T(-0.5)}。
     *
     * <p>
     * 目标帧 {@code F20_pre = Rx(-90)·Ry(180)·T(+1/16,0.125,-0.625)}（1.20 ItemInHandLayer#renderArmWithItem:55-59
     * 右手）；1.7.10 侧的自带帧见 {@link #EQUIPPED_COMPENSATION} 的注释。
     */
    private static void applyEquippedDisplay(ItemModelDisplay display, PoseStack poseStack) {
        poseStack.mulPoseMatrix(EQUIPPED_COMPENSATION);
        display.getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
            .apply(false, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    /**
     * 第一人称手持（EQUIPPED_FIRST_PERSON）：{@code T(0.5)·S(2.5)·Ry(-45)·display.firstperson_righthand·T(-0.5)}。
     *
     * <p>
     * {@code F17 = T(0.56,-0.52,-0.72)·Ry(45)·S(0.4)·T(-0.5)}（1.7.10 ItemRenderer#renderItemInFirstPerson:434-444
     * 的 2D 纸片帧 + ForgeHooksClient:216-221 的 EQUIPPED_BLOCK 分支）；{@code F20 = T(0.56,-0.52,-0.72)·display·T(-0.5)}
     * （1.20 ItemInHandRenderer#applyItemArmTransform:318-321，待机时攻击变换为单位矩阵）。两侧外层的视角延滞
     * 旋转相同 ⇒ {@code A = inv(F17)·F20} 只剩本式。
     */
    private static void applyFirstPersonDisplay(ItemModelDisplay display, PoseStack poseStack) {
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(2.5F, 2.5F, 2.5F);
        poseStack.mulPose(Axis.YN.rotationDegrees(45.0F));
        display.getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
            .apply(false, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    /**
     * 掉落物（ENTITY→GROUND）：{@code T(0, 0.5*ground.scale.y, 0)·S(2)·display.ground·T(-0.5)}。
     *
     * <p>
     * {@code F17 = T(0,bob,0)·Ry(spin)·S(0.5)}（RenderItem#doRender:95 + ForgeHooksClient#renderEntityItem:92-99
     * 的 ENTITY_ROTATION/ENTITY_BOBBING helper + :147 的 S(0.5)）；{@code F20 = T(0, bob+0.25*ground.scale.y, 0)
     * ·Ry(spin)·display·T(-0.5)}（1.20 ItemEntityRenderer#render:63-67 + ItemRenderer.java:128-129，0.25 系数来自
     * :64-65）。bob/spin 在 {@code inv(F17)·F20} 中相消。注释中 gy = {@code ground.scale.y}。
     */
    private static void applyGroundDisplay(ItemModelDisplay display, PoseStack poseStack) {
        BlockItemTransforms.ItemTransform ground = display.getTransform(ItemDisplayContext.GROUND);
        poseStack.translate(
            0.0F,
            0.5F * ground.getScale()
                .y(),
            0.0F);
        poseStack.scale(2.0F, 2.0F, 2.0F);
        ground.apply(false, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    /**
     * 固定姿态（FIXED）：{@code S(补偿倍率)·display.fixed·T(-0.5)}。
     *
     * <p>
     * 展示框：1.7.10 的 {@code S(0.5)}（ForgeHooksClient:147）与 1.20 的 {@code S(0.5)}（ItemFrameRenderer:109）
     * 相同、相消，补偿倍率 = 1；1.20 的 frame 自身摆位（{@code T(0,0,0.4375)}/旋转）与 1.7.10 RenderItemFrame
     * 的摆位属平台差异，不在本类补偿。
     * 展示柜/挂板/雕像：{@code StaticItemRenderHelper} 把 1.20 {@code renderStatic(FIXED)} 退化成 ENTITY 并先施加
     * {@code S(0.5)}，而 1.20 的 renderStatic 路径没有这个缩放 ⇒ 补偿倍率 = 2。
     */
    private static void applyFixedDisplay(ItemModelDisplay display, PoseStack poseStack, float compensationScale) {
        poseStack.scale(compensationScale, compensationScale, compensationScale);
        display.getTransform(ItemDisplayContext.FIXED)
            .apply(false, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    /**
     * 第三人称 EQUIPPED 的帧补偿 {@code inv(F_paper17)·F20_pre}（display 变换在其后再叠）。
     *
     * <p>
     * F_paper17 = {@code T(-1/16,7/16,1/16)}（RenderPlayer.java:318-319）
     * · {@code T(0.25,0.1875,-0.1875)·S(0.375)·Rz(60)·Rx(-90)·Rz(20)}（RenderPlayer.java:377-385 的 2D 纸片分支；
     * 本渲染器的 {@code BLOCK_3D} helper 保持 false，与枪械一致）
     * · {@code T(-0.5,-0.5,-0.5)}（ForgeHooksClient.java:216-221 的 EQUIPPED_BLOCK 分支）。
     * F20_pre 见 {@link #applyEquippedDisplay}。
     */
    private static final Matrix4f EQUIPPED_COMPENSATION = buildEquippedCompensation();

    private static Matrix4f buildEquippedCompensation() {
        Matrix4f frame = new Matrix4f();
        // RenderPlayer.java:319
        frame.translate(-1.0F / 16.0F, 7.0F / 16.0F, 1.0F / 16.0F);
        // RenderPlayer.java:380-384（2D 纸片分支）
        frame.translate(0.25F, 0.1875F, -0.1875F);
        frame.scale(0.375F, 0.375F, 0.375F);
        frame.rotateZ((float) Math.toRadians(60.0F));
        frame.rotateX((float) Math.toRadians(-90.0F));
        frame.rotateZ((float) Math.toRadians(20.0F));
        // ForgeHooksClient.java:219（EQUIPPED_BLOCK 分支）
        frame.translate(-0.5F, -0.5F, -0.5F);
        // 1.20 ItemInHandLayer#renderArmWithItem:56-59（右手）
        Matrix4f target = new Matrix4f();
        target.rotateX((float) Math.toRadians(-90.0F));
        target.rotateY((float) Math.toRadians(180.0F));
        target.translate(1.0F / 16.0F, 0.125F, -0.625F);
        return frame.invert()
            .mul(target);
    }
}
