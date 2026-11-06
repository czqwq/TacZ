package com.tacz.guns.client.event;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.client.gui.overlay.DisplayCaseLockHintOverlay;
import com.tacz.guns.client.input.GunInputHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 空手对着枪械展示柜按「使用」键时，在 HUD 上提示「潜行 + 使用」的组合键。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/GunDisplayCaseClientInteractionEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT)}），两个处理器
 * {@code onUseKey(InputEvent.Key)} 与 {@code onUseMouse(InputEvent.MouseButton.Post)} 分别覆盖
 * 「使用键绑在键盘上」与「绑在鼠标上」两种情况。
 *
 * <p>
 * 平台替换 / 降级（相对上游）：
 * <ul>
 * <li><b>1.7.10 没有 {@code InputEvent.*}</b>。上游两个处理器都只在「使用键刚按下」时做一件事
 * （{@code showHintForTarget(minecraft, false)}），因此改由
 * {@link #onClientTick(TickEvent.ClientTickEvent)} 轮询 {@code GameSettings#isKeyDown} 做<b>按下沿</b>检测
 * （与 client/input 的 {@code GunInputHandler} 同一套做法）；按下沿之后仍由
 * {@link #onUseKey(Minecraft)} / {@link #onUseMouse(Minecraft)} 两个同名方法承担上游的守卫逻辑，
 * 按键绑在键盘还是鼠标由 {@code getKeyCode() >= 0} 区分（1.7.10 约定：负值 = 鼠标键）。</li>
 * <li>{@code player.isSpectator()} 删除：1.7.10 <b>没有旁观者模式</b>（1.8+ 才有）。</li>
 * <li>{@code minecraft.hitResult} → {@code objectMouseOver}（{@link MovingObjectPosition}）；
 * {@code BlockHitResult} → {@code typeOfHit == BLOCK} + {@code blockX/Y/Z}；
 * {@code level.getBlockState(pos).getBlock()/getValue(LOCKED)} →
 * {@code world.getBlock(x,y,z)} + {@code world.getBlockMetadata(x,y,z)} 与
 * {@link GunDisplayCaseBlock#isLocked(int)}（1.7.10 用 metadata 表达 LOCKED 属性）。</li>
 * <li>{@code options.keyShift/keyUse.getTranslatedKeyMessage().getString()} →
 * {@code StatCollector.translateToLocal(keyBinding.getKeyDescription())}（1.7.10 的等价键名文案）；
 * {@code Component.translatable} → {@link ChatComponentTranslation}。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（{@code cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115}）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在事件派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法(...)} 在这里改为 {@code public void 处理方法(...)}
 * （类名 / 方法名 / 参数 / 语义一律不变；非处理方法的静态工具方法与字段仍保持 {@code static}）。
 * 本类也因为「{@code EventBus#register(Class)} 在 1.7.10 无效」而提供 public 无参构造，
 * 由 {@code client/ClientProxy} 反射 {@code new} 后注册到正确的总线。
 */
public class GunDisplayCaseClientInteractionEvent {

    /** 上一 tick 的使用键状态，用于做按下沿检测（移植特有）。 */
    private static boolean lastUseKeyDown = false;

    /**
     * 移植特有：替代 1.7.10 不存在的 {@code InputEvent.Key} / {@code InputEvent.MouseButton.Post}。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        // 上游 InputEvent.* 只在没有 GUI 打开时派发（开容器/聊天时不会触发），1.7.10 的物理按键轮询
        // 没有这一层，必须自己挡掉（行为等价）
        if (minecraft.currentScreen != null) {
            lastUseKeyDown = false;
            return;
        }
        boolean useKeyDown = GunInputHandler.isKeyDown(minecraft.gameSettings.keyBindUseItem);
        boolean pressed = useKeyDown && !lastUseKeyDown;
        lastUseKeyDown = useKeyDown;
        if (!pressed) {
            return;
        }
        if (minecraft.gameSettings.keyBindUseItem.getKeyCode() >= 0) {
            onUseKey(minecraft);
        } else {
            onUseMouse(minecraft);
        }
    }

    /** 对应上游 {@code onUseKey(InputEvent.Key)} 的守卫部分。 */
    public static void onUseKey(Minecraft minecraft) {
        EntityPlayerSP player = minecraft.thePlayer;
        // 上游：player == null || player.isSpectator() → return；1.7.10 无旁观者模式
        if (player == null) {
            return;
        }
        if (player.getHeldItem() != null || player.isSneaking()) {
            return;
        }
        showHintForTarget(minecraft, false);
    }

    /** 对应上游 {@code onUseMouse(InputEvent.MouseButton.Post)} 的守卫部分。 */
    public static void onUseMouse(Minecraft minecraft) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) {
            return;
        }
        if (player.getHeldItem() != null || player.isSneaking()) {
            return;
        }
        showHintForTarget(minecraft, false);
    }

    public static void showHintForTarget(Minecraft minecraft, boolean lockedOnly) {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || minecraft.theWorld == null) {
            return;
        }
        int x = hit.blockX;
        int y = hit.blockY;
        int z = hit.blockZ;
        Block block = minecraft.theWorld.getBlock(x, y, z);
        if (!(block instanceof GunDisplayCaseBlock)) {
            return;
        }
        boolean locked = GunDisplayCaseBlock.isLocked(minecraft.theWorld.getBlockMetadata(x, y, z));
        if (lockedOnly && !locked) {
            return;
        }
        DisplayCaseLockHintOverlay.show(createHint(locked, minecraft));
    }

    private static IChatComponent createHint(boolean locked, Minecraft minecraft) {
        // 1.7.10 的等价「按键名称」：KeyBinding 的描述 key 走语言表
        String shiftKey = StatCollector.translateToLocal(minecraft.gameSettings.keyBindSneak.getKeyDescription());
        String useKey = StatCollector.translateToLocal(minecraft.gameSettings.keyBindUseItem.getKeyDescription());
        String chord = shiftKey + " + " + useKey;
        return new ChatComponentTranslation(
            locked ? "gui.tacz.display_case.locked_hint" : "gui.tacz.display_case.unlocked_hint",
            chord);
    }

    public static boolean holdingGun(EntityPlayerSP player) {
        ItemStack stack = player.getHeldItem();
        return stack != null && stack.getItem() instanceof IGun;
    }
}
