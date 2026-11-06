package com.tacz.guns.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.util.MovingObjectPosition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.GunInputHandler;
import com.tacz.guns.client.input.InteractKey;

/**
 * 持枪时拦住「原版左键 = 攻击/挖掘」与「原版右键实体」的客户端入口
 * （= 上游 {@code ClientPreventGunClick} 的 1.7.10 等价物）。
 *
 * <p>
 * 左键注入同时修掉「持枪左键点方块 → 客户端幽灵方块」。
 *
 * <p>
 * <b>上游依据</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/ClientPreventGunClick.java:21-41}
 * 监听 {@code InputEvent.InteractionKeyMappingTriggered}（攻击/使用键被映射时触发），持枪时
 * {@code event.setSwingHand(false)} + {@code event.setCanceled(true)}。
 * 取消的是<b>输入映射</b>，所以上游客户端<b>根本不会</b>进入
 * {@code Minecraft#startAttack} / {@code MultiPlayerGameMode#continueDestroyBlock}。
 *
 * <p>
 * <b>1.7.10 为什么必须用 Mixin（不能照搬 PlayerInteractEvent 接线）</b>：
 * <ul>
 * <li>右键（使用）的<b>方块/空气</b>分支已有等价拦截：{@code Minecraft#func_147121_ag()} 在调
 * {@code onPlayerRightClick} / {@code sendUseItem} 之前会 post {@code RIGHT_CLICK_BLOCK}
 * / {@code RIGHT_CLICK_AIR} 的 {@code PlayerInteractEvent}（{@code Minecraft.java:1528}、{@code :1556}），
 * 已被 {@code client/event/ClientPreventGunClick} 取消。但右键的<b>实体</b>分支
 * （{@code func_147121_ag} 的 case ENTITY，{@code Minecraft.java:1512-1517}）直接调
 * {@code PlayerControllerMP#interactWithEntitySendPacket}，<b>不投递 PlayerInteractEvent</b> —— 上游
 * {@code ClientPreventGunClick} 取消的是整条「使用」输入映射（含实体），因此这里补一个
 * {@code func_147121_ag} HEAD 注入，<b>只在 ENTITY 命中且未按交互键、目标不是展示框时</b>取消
 * （方块/空气分支继续走事件层拦截，不改变现有语义）；</li>
 * <li>左键侧<b>客户端根本不 post 事件</b>：{@code PlayerInteractEvent.Action.LEFT_CLICK_BLOCK}
 * 在全仓唯一的派发点是服务端的
 * {@code build/rfg/minecraft-src/java/net/minecraft/server/management/ItemInWorldManager.java:157}
 * （{@code onBlockClicked}），而它被 {@code event/PreventGunClick} 取消后只会回一个
 * {@code S23PacketBlockChange}（{@code :160}）「事后纠错」。而客户端在此之前已经完成了本地预测：
 * {@code Minecraft#func_147116_af}（{@code :1455-1496}）→ {@code PlayerControllerMP#clickBlock}
 * （{@code :186-229}）→ 创造模式走 {@code clickBlockCreative}（{@code :73-79}）→
 * {@code onPlayerDestroyBlock} → <b>本地 {@code setBlockToAir}</b>；
 * 持续挖掘走 {@code func_147115_a}（{@code :1422-1453}）→ {@code onPlayerDamageBlock}
 * （{@code :249-301}）本地累计 {@code curBlockDamageMP}（{@code :275}）到 1.0 自行挖掘
 * （{@code :284-292}）。服务端不认，于是<b>方块在客户端消失/进度条走完、服务端还在</b>
 * —— 这就是幽灵方块。结论：1.7.10 只能在客户端源码级注入拦截。</li>
 * </ul>
 *
 * <p>
 * <b>为什么注入 {@link Minecraft} 的这两个私有方法，而不是 {@code PlayerControllerMP}</b>：
 * <ul>
 * <li>只拦 {@code PlayerControllerMP#clickBlock}/{@code #onPlayerDamageBlock} 也能止住 desync，
 * 但 {@code func_147115_a} 在调用 {@code onPlayerDamageBlock} <b>之后</b>还会无条件执行
 * {@code effectRenderer.addBlockHitEffects(...)} + {@code thePlayer.swingItem()}
 * （{@code Minecraft.java:1441-1445}）→ 持枪挖方块会一直冒方块碎屑粒子并挥手，
 * 而这两个副作用正是上游 {@code setSwingHand(false)} 要抑制的东西。
 * 打在 {@code Minecraft} 层的 HEAD 才能连「挥手 + 碎屑」一起挡掉。</li>
 * <li>{@code func_147116_af} 的 {@code thePlayer.swingItem()} 在方法第 3 行（{@code :1459}），
 * 也在 HEAD 之后。</li>
 * </ul>
 *
 * <p>
 * <b>方法名与 {@code remap} 的取舍（任务要求写清）</b>：
 * {@code func_147116_af}/{@code func_147115_a}/{@code func_147121_ag} <b>不是</b> MCP 友好名，
 * 它们<b>就是 SRG 名本身</b>
 * （MCP stable 12 没有给这几个私有方法起名字，`build/rfg/minecraft-src` 里也只能看到 `func_` 形式）。
 * 1.7.10 的 Forge 运行期是 SRG 反混淆状态（崩溃栈里都是 `func_` 名），所以：
 * <ul>
 * <li>用 <b>SRG 名原文 + {@code remap = false}</b>。{@code remap = false} 让 Mixin 注解处理器
 * 完全跳过这个选择器的混淆表查找 —— 既不会报
 * {@code Unable to locate method mapping}（该报错形式的实证见
 * {@code mixin/client/GameRendererMixin.java:309-322} 对 {@code GL11} 的说明），
 * 也保证运行期拿到的就是同一个名字。</li>
 * <li>反过来若用 {@code remap = true}（默认），MCP→SRG 表里查不到这两个名字，
 * 结果虽然同样是「原样使用」，但会多一条 AP 警告，且语义靠「查不到」这个副作用成立，
 * 不如显式 {@code false} 清楚。全仓已有的同类先例是
 * {@code mixin/client/EntityRenderDispatcherMixin}（直接写 SRG 名 {@code func_147939_a}）。</li>
 * </ul>
 *
 * <p>
 * <b>{@code require = 0} + {@code expect = 1} 的取舍</b>：{@code Minecraft#func_147115_a} /
 * {@code func_147116_af}/{@code func_147121_ag} 是「OptiFine、各类挖掘/连点/交互优化 mod 会改写的目标」之一，
 * 方法名/签名被换掉就 0 命中。沿用 json 的 {@code injectors.defaultRequire = 1} 会让这种情况
 * 直接抛 {@code InjectionError} → <b>启动崩</b>。宁可退回「有幽灵方块」也不能起不来，
 * 因此 {@code require = 0}。
 *
 * <p>
 * {@code expect = 1} 的真实语义（已核 UniMixins 0.3.1 {@code InjectionInfo.java:524-538}）：
 * 它<b>不会</b>在普通运行下报警，只有开启 {@code -Dmixin.debug.injectors=true} 时才会把
 * 「0 命中」变成硬失败（{@code ... expected 1 invocation(s) but 0 succeeded}）；
 * 默认运行下 0 命中是<b>完全静默</b>的。所以本 Mixin 必须做实机验证，
 * 见 {@code docs/review/r2/08-mixin-fix.md} §三。
 *
 * <p>
 * <b>与上游的有意差异（均已登记）</b>：
 * <ol>
 * <li><b>左键不加 {@code InteractKey} 放行分支</b>。上游 {@code ClientPreventGunClick.java:27-29}
 * 在交互键按下时 {@code return}（不取消攻击）；但 1.7.10 的服务端
 * {@code event/PreventGunClick.java:30-40} <b>没有</b>交互键放行分支，仍然会取消
 * {@code LEFT_CLICK_BLOCK}。若客户端照抄放行，就会重新造出本次要修的幽灵方块。
 * 客户端必须与服务端一致，故这里不放行。</li>
 * <li><b>左键不特判展示框（{@code EntityItemFrame}）</b>。上游放行展示框是为了让「攻击映射」能
 * 触发点击展示框；而 1.7.10 的展示框交互走 <b>右键</b>，左键对展示框没有任何合法语义，无需放行。</li>
 * <li><b>附带的行为变化</b>：{@code func_147116_af} 同时负责「左键攻击实体」，因此持枪时
 * 左键<b>不再</b>对生物造成近战伤害 —— 这与上游一致（上游取消攻击映射后，持枪左键只开火，
 * 近战走 {@code MeleeKey}，见 {@code client/input/GunInputHandler.java:92}）。</li>
 * <li><b>右键按上游语义保留两个放行分支</b>：交互键（{@code InteractKey}，上游
 * {@code ClientPreventGunClick.java:27-29}）按下时放行、目标是展示框时放行
 * （上游 {@code :34-37}）。服务端对「使用」没有任何无条件取消，因此这里放行不会产生不同步。</li>
 * </ol>
 *
 * <p>
 * <b>生效状态</b>：✅ 已接线（左键两处 + 右键实体分支一处）。注入点命中与否无法静态证明，
 * 实机验证见 {@code docs/review/r7/input.md}。
 */
@Mixin(Minecraft.class)
public class MinecraftAttackMixin {

    /**
     * 「开始挖掘 / 攻击」入口：{@code Minecraft#func_147116_af()}
     * （{@code Minecraft.java:1455-1496}，调用点 {@code :2037-2040} 的
     * {@code while (keyBindAttack.isPressed())}）。
     *
     * <p>
     * 目标方法无返回值、无形参 → 回调只声明 {@code CallbackInfo}
     * （{@code @Inject} 必须带全部目标形参 + 回调信息参数，本方法没有形参）。
     */
    @Inject(method = "func_147116_af", at = @At("HEAD"), cancellable = true, require = 0, expect = 1, remap = false)
    private void tacz$preventGunLeftClick(CallbackInfo ci) {
        if (tacz$mainHandHoldGun()) {
            ci.cancel();
        }
    }

    /**
     * 「持续挖掘」入口：{@code Minecraft#func_147115_a(boolean leftClick)}
     * （{@code Minecraft.java:1422-1453}，调用点 {@code :2058}）。
     *
     * <p>
     * {@code leftClick == false} 时必须放行：那条分支是原版「没在挖」的收尾
     * （{@code leftClickCounter = 0} + {@code playerController.resetBlockRemoving()}，
     * {@code :1424-1427}、{@code :1448-1451}），拦掉会让「松手后清除挖掘进度」也失效。
     */
    @Inject(method = "func_147115_a", at = @At("HEAD"), cancellable = true, require = 0, expect = 1, remap = false)
    private void tacz$preventGunBlockDamage(boolean leftClick, CallbackInfo ci) {
        if (!leftClick || !tacz$mainHandHoldGun()) {
            return;
        }
        // 等价上游「客户端根本不会开始挖掘」：若玩家在挖到一半时把工具切成枪（1.7.10 换物品不会自动
        // 中止挖掘，见 PlayerControllerMP#sameToolAndBlock/:329-340 只在下次调用时才比对物品），
        // 这里补一次 resetBlockRemoving()：它会给服务端补发 ABORT_DESTROY_BLOCK(1)
        // 并清掉本地破坏进度（PlayerControllerMP.java:234-244），避免服务端 ItemInWorldManager
        // 继续自行 updateBlockRemoving 把方块挖掉（那会变成「服务端没了、客户端还在」的反向不同步）。
        Minecraft minecraft = (Minecraft) (Object) this;
        PlayerControllerMP controller = minecraft.playerController;
        if (controller != null) {
            controller.resetBlockRemoving();
        }
        ci.cancel();
    }

    /**
     * 右键的 <b>实体</b>分支：{@code Minecraft#func_147121_ag()} 的 case ENTITY
     * （{@code Minecraft.java:1512-1517}）直接调 {@code PlayerControllerMP#interactWithEntitySendPacket}，
     * 不投递 {@code PlayerInteractEvent}，因此 {@code ClientPreventGunClick} 拦不到。
     *
     * <p>
     * 只在该分支取消（方块/空气分支已由 {@code ClientPreventGunClick} 的事件拦截处理，保持原样），
     * 语义对齐上游：持枪右键实体不交互，但保留上游两个放行分支（交互键按下 / 目标是展示框）。
     */
    @Inject(method = "func_147121_ag", at = @At("HEAD"), cancellable = true, require = 0, expect = 1, remap = false)
    private void tacz$preventGunRightClickEntity(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        EntityClientPlayerMP player = minecraft.thePlayer;
        if (player == null || !IGun.mainHandHoldGun(player)) {
            return;
        }
        MovingObjectPosition hitResult = minecraft.objectMouseOver;
        if (hitResult == null || hitResult.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return;
        }
        // 上游 ClientPreventGunClick.java:27-29：交互键按下时允许交互
        if (GunInputHandler.isKeyDown(InteractKey.INTERACT_KEY)) {
            return;
        }
        // 上游 ClientPreventGunClick.java:34-37：展示框可以交互
        if (hitResult.entityHit instanceof EntityItemFrame) {
            return;
        }
        ci.cancel();
    }

    /** 主手是否持枪（对应上游的 {@code itemInHand.getItem() instanceof IGun}）。 */
    @Unique
    private boolean tacz$mainHandHoldGun() {
        EntityClientPlayerMP player = ((Minecraft) (Object) this).thePlayer;
        return player != null && IGun.mainHandHoldGun(player);
    }
}
