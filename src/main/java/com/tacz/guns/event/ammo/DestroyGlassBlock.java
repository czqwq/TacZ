package com.tacz.guns.event.ammo;

import net.minecraft.block.Block;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.util.AmmoBlockConfigRead;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 子弹击碎符合配置的脆弱方块（默认玻璃类，{@code AmmoConfig.DESTROY_GLASS} + 硬度阈值 + 白/黑名单）。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/ammo/DestroyGlassBlock.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}；{@code @SubscribeEvent} 换包；</li>
 * <li>{@code Level} → {@link World}；{@code BlockState} → {@link Block} + {@code metadata}
 * （{@code AmmoHitBlockEvent#getState()} / {@code #getMetadata()}）；</li>
 * <li>{@code BlockPos} → {@code getHitResult().blockX/blockY/blockZ}；</li>
 * <li>{@code state.getDestroySpeed(level, pos)} → {@code Block#getBlockHardness(World, int, int, int)}；</li>
 * <li>{@code AmmoBlockConfigRead.canDestroy(state)} → {@code canDestroy(Block, int metadata)}；</li>
 * <li>{@code Level#destroyBlock(pos, false, owner)} → 1.7.10 等价两步：
 * {@code World#setBlockToAir(x,y,z)}（flags=3，不掉落）+ {@code World#playAuxSFX(2001, ...)}
 * （方块破坏特效），与 {@code destroyBlock(pos,false)} 的 1.7.10 内部实现一致。</li>
 * </ul>
 * <p>
 * TODO(port): 上游 DestroyGlassBlock.java:29 把 {@code ammo.getOwner()} 传给 {@code destroyBlock}，
 * 用于统计/成就归属；1.7.10 的 {@code setBlockToAir} 路径没有 owner 参数，玩家「破坏方块」统计不会增加。
 * 如需补齐，应显式调用 {@code EntityPlayer#addStat(StatList.mineBlockStatArray[...])}。
 */
public class DestroyGlassBlock {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new DestroyGlassBlock());
    }

    @SubscribeEvent
    public void onAmmoHitBlock(AmmoHitBlockEvent event) {
        if (!AmmoConfig.DESTROY_GLASS.get()) {
            return;
        }
        World world = event.getLevel();
        Block block = event.getState();
        MovingObjectPosition hitResult = event.getHitResult();
        if (block == null || hitResult == null) {
            return;
        }
        int x = hitResult.blockX;
        int y = hitResult.blockY;
        int z = hitResult.blockZ;
        int metadata = event.getMetadata();
        float hardness = block.getBlockHardness(world, x, y, z);
        if (hardness < 0 || hardness > AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.get()) {
            return;
        }
        if (AmmoBlockConfigRead.canDestroy(block, metadata)) {
            // 等价上游 destroyBlock(pos, false)：无掉落 + 破坏特效
            world.setBlockToAir(x, y, z);
            world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (metadata << 12));
        }
    }
}
