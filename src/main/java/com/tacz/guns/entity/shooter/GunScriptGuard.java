package com.tacz.guns.entity.shooter;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.luaj.vm2.LuaError;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;

/**
 * 兜底，防止脚本爆炸连服务端一起扬了。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/GunScriptGuard.java}。
 * 由 {@code LivingEntityMixin} 的 {@code tacz$runGunAction} 调用，方法签名必须与上游一致。
 *
 * <p>
 * 平台替换（相对上游）：
 * {@code LivingEntity → EntityLivingBase}、{@code ItemStack.EMPTY → null}、
 * {@code ItemStack#isEmpty() → stack == null || stack.getItem() == null}（1.7.10 无 EMPTY 单例）、
 * {@code Entity#getUUID() → Entity#getUniqueID()}、{@code getMainHandItem() → getHeldItem()}。
 */
public class GunScriptGuard {

    private GunScriptGuard() {}

    public static void run(ShooterDataHolder data, EntityLivingBase shooter, @Nullable ResourceLocation gunId,
        String operation, Runnable action) {
        run(data, shooter, gunId, operation, () -> {
            action.run();
            return null;
        }, null);
    }

    public static <T> T run(ShooterDataHolder data, EntityLivingBase shooter, @Nullable ResourceLocation gunId,
        String operation, Supplier<T> action, T fallback) {
        if (data.gunScriptFailed) {
            return fallback;
        }
        if (gunId == null || !gunId.equals(data.currentGunId)) {
            return fallback;
        }
        // 1.7.10：Supplier#get() 可能返回 null（空手）；统一用 getCurrentGunItem() 归一化，
        // 与上游「ItemStack.EMPTY 恒非 null、由 hasGunId 返回 false」等价。
        ItemStack current = data.getCurrentGunItem();
        if (!hasGunId(current, gunId) || !hasGunId(shooter.getHeldItem(), gunId)) {
            data.cancelGunScriptState();
            return fallback;
        }
        try {
            T result = action.get();
            return data.gunScriptFailed ? fallback : result;
        } catch (LuaError exception) {
            data.cancelGunScriptState();
            data.gunScriptFailed = true;
            GunMod.LOGGER.error(
                "Gun script failed for {} during {} (entity {}); paused until next draw",
                gunId,
                operation,
                shooter.getUniqueID(),
                exception);
            return fallback;
        }
    }

    private static boolean hasGunId(@Nullable ItemStack stack, ResourceLocation gunId) {
        // 1.7.10 没有 ItemStack.EMPTY，空栈即 null
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && gunId.equals(gun.getGunId(stack));
    }
}
