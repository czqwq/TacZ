package com.tacz.guns.client.init;

import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.renderer.block.GunDisplayCaseRenderer;
import com.tacz.guns.client.renderer.block.GunPegboardRenderer;
import com.tacz.guns.client.renderer.block.GunSmithTableRenderer;
import com.tacz.guns.client.renderer.block.StatueRenderer;
import com.tacz.guns.client.renderer.block.TargetRenderer;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.client.renderer.entity.TargetMinecartRenderer;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.TargetMinecart;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 实体渲染器 / 方块实体（TileEntity）特殊渲染器注册。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/init/ModEntitiesRender.java}，
 * 通过 {@code @Mod.EventBusSubscriber(bus = MOD)} 订阅
 * {@code EntityRenderersEvent.RegisterRenderers}，用 {@code EntityRenderers.register(EntityType, supplier)} /
 * {@code BlockEntityRenderers.register(BlockEntityType, supplier)} 注册。
 *
 * <p>
 * <b>1.7.10 映射</b>：
 * <ul>
 * <li>{@code EntityRenderers.register(EntityType, supplier)} →
 * {@link RenderingRegistry#registerEntityRenderingHandler(Class, net.minecraft.client.renderer.entity.Render)}
 * （1.7.10 没有 {@code EntityType} 注册表对象，直接按 <b>实体 Class</b> 注册）；</li>
 * <li>{@code BlockEntityRenderers.register(BlockEntityType, supplier)} →
 * {@link ClientRegistry#bindTileEntitySpecialRenderer(Class, net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer)}
 * （1.7.10 的 TESR 是按 <b>TileEntity Class</b> 绑定，且必须传实例而非供应商）。</li>
 * </ul>
 *
 * <p>
 * 与上游一致：这是客户端初始化期的一次性注册，由 {@link ClientSetupEvent#onClientSetup()} 调用。
 */
@SideOnly(Side.CLIENT)
public final class ModEntitiesRender {

    private ModEntitiesRender() {}

    /** 对应上游 {@code EntityRenderersEvent.RegisterRenderers}。 */
    public static void register() {
        // 实体渲染器（上游 EntityRenderers.register）
        RenderingRegistry.registerEntityRenderingHandler(EntityKineticBullet.class, new EntityBulletRenderer());
        RenderingRegistry.registerEntityRenderingHandler(TargetMinecart.class, new TargetMinecartRenderer());

        // 方块实体特殊渲染器（上游 BlockEntityRenderers.register）
        ClientRegistry.bindTileEntitySpecialRenderer(GunSmithTableBlockEntity.class, new GunSmithTableRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TargetBlockEntity.class, new TargetRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(StatueBlockEntity.class, new StatueRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(GunPegboardBlockEntity.class, new GunPegboardRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(GunDisplayCaseBlockEntity.class, new GunDisplayCaseRenderer());
    }
}
