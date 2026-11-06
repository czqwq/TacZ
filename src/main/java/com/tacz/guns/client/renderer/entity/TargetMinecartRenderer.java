package com.tacz.guns.client.renderer.entity;

import java.util.Map;
import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderMinecart;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.util.ResourceLocation;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.entity.TargetMinecart;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 靶车（{@code TargetMinecart}）渲染器：基岩版模型 + 玩家头颅。
 *
 * <h2>1.7.10 平台替换</h2>
 * <ul>
 * <li>上游 {@code extends MinecartRenderer<TargetMinecart>} + {@code EntityRendererProvider.Context}
 * → 1.7.10 {@link RenderMinecart}（无参构造，{@code shadowRadius} → {@code shadowSize}）。</li>
 * <li>上游 {@code renderMinecartContents(minecart, partialTicks, BlockState, poseStack, buffer, packedLight)}
 * → 1.7.10 {@link RenderMinecart#func_147910_a(EntityMinecart, float, net.minecraft.block.Block, int)}
 * （MCP stable 名，语义就是「渲染矿车里的装载物」；1.7.10 无 {@code BlockState}，用 {@code Block}+metadata）。
 * 本类保留上游方法名 {@link #renderMinecartContents} 作为核心实现，由 {@code func_147910_a} 转发。</li>
 * <li>上游 {@code Minecraft#getSkinManager().getInsecureSkinInformation(profile)} /
 * {@code registerTexture(tex, Type.SKIN)} → 1.7.10 {@code Minecraft#func_152342_ad()}（{@code SkinManager}）
 * 的 {@code func_152788_a(GameProfile)} / {@code func_152792_a(MinecraftProfileTexture, Type)}。</li>
 * <li>上游 {@code TargetMinecart#getDefaultDisplayBlockState()}（决定是否进装载物渲染）→ 1.7.10
 * {@link TargetMinecart#func_145817_o()}；本版该方块的 renderType 还必须是 != -1，见该方法注释。</li>
 * <li>上游 {@code DefaultPlayerSkin.getDefaultSkin(uuid)}：1.7.10 没有「按 uuid 分默认皮肤」的概念
 * （默认只有 Steve），降级为 {@link SkinManager#field_152793_a}（{@code textures/entity/steve.png}）。
 * // TODO(L2): 上游 TargetMinecartRenderer.java:76 用 DefaultPlayerSkin 按 UUID 选 Alex/Steve；
 * // 1.7.10 无 DefaultPlayerSkin，此处降级为固定 Steve 皮肤。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class TargetMinecartRenderer extends RenderMinecart {

    private static final String HEAD_NAME = "head";
    private static final String HEAD_2_NAME = "head2";

    public TargetMinecartRenderer() {
        super();
        this.shadowSize = 0.25F;
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MINECART_MODEL_LOCATION);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return InternalAssetLoader.ENTITY_EMPTY_TEXTURE;
    }

    /**
     * 1.7.10 的「矿车装载物」渲染入口，转发到上游同名方法 {@link #renderMinecartContents}。
     *
     * <p>
     * <b>本钩子有前置条件</b>：{@code RenderMinecart#doRender:103} 只在
     * {@code func_145820_n().getRenderType() != -1} 时调用它。{@code func_145820_n()} 在未设自定义装载方块时
     * 返回 {@code EntityMinecart#func_145817_o()}（默认 {@code Blocks.air}，renderType=-1），
     * 所以 {@link TargetMinecart} 必须覆写它给出一个 renderType!=-1 的方块（见
     * {@link TargetMinecart#func_145817_o()}），否则本方法永不执行 —— 表现就是「只有阴影没模型」。
     * 上游 1.20 由 {@code TargetMinecart#getDefaultDisplayBlockState()} 承担同一职责。
     */
    @Override
    protected void func_147910_a(EntityMinecart minecart, float partialTicks, Block block, int metadata) {
        if (!(minecart instanceof TargetMinecart targetMinecart)) {
            return;
        }
        PoseStack stack = new PoseStack();
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate();
        try {
            renderMinecartContents(
                targetMinecart,
                partialTicks,
                block,
                metadata,
                stack,
                buffer,
                minecart.getBrightnessForRender(partialTicks));
        } finally {
            buffer.endBatch();
        }
    }

    /**
     * 上游 {@code renderMinecartContents(TargetMinecart, float, BlockState, PoseStack, MultiBufferSource, int)}。
     *
     * <p>
     * 1.7.10 无 {@code BlockState}，参数改为 {@code Block} + metadata（本实现不使用这两个参数，
     * 与上游一致：上游也没用它们，只是被 {@code MinecartRenderer} 的模板方法签名要求）。
     */
    public void renderMinecartContents(TargetMinecart targetMinecart, float partialTicks, Block block, int metadata,
        PoseStack stack, MultiBufferSource buffer, int packedLight) {
        Optional<BedrockModel> modelOpt = getModel();
        if (!modelOpt.isPresent()) {
            return;
        }
        BedrockModel model = modelOpt.get();
        BedrockPart headModel = model.getNode(HEAD_NAME);
        BedrockPart head2Model = model.getNode(HEAD_2_NAME);
        if (headModel == null || head2Model == null) {
            return;
        }
        headModel.visible = false;
        head2Model.visible = false;

        stack.pushPose();
        // 坐标换算（1.20 帧 → 1.7.10 帧）。上游那 4 行是在 1.20 的装载物帧里写的：
        // pre120 = T(0,0.375,0)·S(0.75)·T(-0.5,-0.125,0.5)·Ry(90) [MinecartRenderer.java:69,87-89]，偏移 k=6
        // 1.7.10 的装载物帧只有 pre107 = S(0.75)·T(0,6/16,0) [RenderMinecart.java:105-109]。
        // 右乘 pre107 的逆即得 1.7.10 帧下的等价内层：T(-0.5,0,0.5)·Ry(90)·T(0.5,1.875,0.5)·S(1.5)·Rz(180)·Ry(-90)
        // （因 S⁻¹·T(0,0.375,0)·S = T(0,0.5,0)，与 T(0,-6/16,0) 相消）
        // 平移项相消 + 均匀缩放与旋转可交换 + Ry(90)·Rz(180)·Ry(-90) = Rx(180) ⇒ T(0,1.875,0)·S(1.5)·Rx(180)。
        stack.translate(0, 1.875, 0);
        stack.scale(1.5f, 1.5f, 1.5f);
        stack.mulPose(Axis.XP.rotationDegrees(180));
        RenderType renderType = RenderType.entityTranslucent(InternalAssetLoader.TARGET_MINECART_TEXTURE_LOCATION);
        model.render(stack, ItemDisplayContext.NONE, renderType, packedLight, OverlayTexture.NO_OVERLAY);
        if (targetMinecart.getGameProfile() != null) {
            stack.translate(0, 1, -4.5 / 16d);
            GameProfile gameProfile = targetMinecart.getGameProfile();
            ResourceLocation skin = resolveSkin(gameProfile);
            headModel.visible = true;
            RenderType skullRenderType = RenderType.entityTranslucentCull(skin);
            headModel.render(
                stack,
                ItemDisplayContext.NONE,
                buffer.getBuffer(skullRenderType),
                packedLight,
                OverlayTexture.NO_OVERLAY);

            head2Model.visible = true;
            stack.translate(0, 0, 0.01);
            head2Model.render(
                stack,
                ItemDisplayContext.NONE,
                buffer.getBuffer(skullRenderType),
                packedLight,
                OverlayTexture.NO_OVERLAY);
        }
        stack.popPose();
    }

    /**
     * 取玩家皮肤：先查本地皮肤缓存（未签名信息），没有再退回默认 Steve。
     *
     * <p>
     * 对应上游 {@code Minecraft#getSkinManager().getInsecureSkinInformation(profile)} +
     * {@code registerTexture(...)} + {@code DefaultPlayerSkin.getDefaultSkin(uuid)}。
     */
    private static ResourceLocation resolveSkin(GameProfile gameProfile) {
        SkinManager skinManager = Minecraft.getMinecraft()
            .func_152342_ad();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = skinManager.func_152788_a(gameProfile);
        if (map != null && map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            return skinManager
                .func_152792_a(map.get(MinecraftProfileTexture.Type.SKIN), MinecraftProfileTexture.Type.SKIN);
        }
        return SkinManager.field_152793_a;
    }
}
