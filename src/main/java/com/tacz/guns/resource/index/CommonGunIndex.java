package com.tacz.guns.resource.index;

import java.util.Arrays;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import com.google.common.base.Preconditions;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import com.tacz.guns.resource.pojo.data.gun.*;

public class CommonGunIndex {

    private static final Marker MARKER = MarkerManager.getMarker("CommonGunIndex");
    private GunData gunData;
    private String type;
    private GunIndexPOJO pojo;
    private int sort;
    private LuaTable script;
    private LuaTable scriptParam;
    private boolean hasUnloadAmmoScript;

    private CommonGunIndex() {}

    public static CommonGunIndex getInstance(GunIndexPOJO gunIndexPOJO) throws IllegalArgumentException {
        CommonGunIndex index = new CommonGunIndex();
        index.pojo = gunIndexPOJO;
        checkIndex(gunIndexPOJO, index);
        checkData(gunIndexPOJO, index);
        return index;
    }

    private static void checkIndex(GunIndexPOJO gunIndexPOJO, CommonGunIndex index) {
        Preconditions.checkArgument(gunIndexPOJO != null, "index object file is empty");
        Preconditions.checkArgument(StringUtils.isNoneBlank(gunIndexPOJO.getType()), "index object missing type field");
        index.type = gunIndexPOJO.getType();
        index.sort = Mth.clamp(gunIndexPOJO.getSort(), 0, 65536);
    }

    private static void checkData(GunIndexPOJO gunIndexPOJO, CommonGunIndex index) {
        ResourceLocation pojoData = gunIndexPOJO.getData();
        Preconditions.checkArgument(pojoData != null, "index object missing pojoData field");
        GunData data = CommonAssetsManager.get()
            .getGunData(pojoData);
        Preconditions.checkArgument(data != null, "there is no corresponding data file");
        Preconditions.checkArgument(data.getAmmoId() != null, "ammo id is empty");
        Preconditions.checkArgument(data.getAmmoAmount() >= 1, "ammo count must >= 1");
        int[] extendedMagAmmoAmount = data.getExtendedMagAmmoAmount();
        Preconditions.checkArgument(
            extendedMagAmmoAmount == null || extendedMagAmmoAmount.length >= 3,
            "extended_mag_ammo_amount size must is 3");
        Preconditions.checkArgument(data.getRoundsPerMinute() >= 1, "rpm count must >= 1");
        Preconditions.checkArgument(data.getBolt() != null, "bolt type is error");
        // 1.7.10 差异（崩溃防护）：上游 POJO 的 reload/recoil/fire_mode 都有非 null 默认值，只有 json 显式写
        // "reload": null / "recoil": null / "fire_mode": null 才会把字段覆盖成 null；上游在同样输入下也是 NPE。
        // 但本工程的装载契约是「单个文件失败只打日志、不中断整批」（JsonDataManager#apply 只 catch
        // IllegalArgumentException/JsonParseException），NPE 会穿透该契约一路抛到 CommonAssetsManager#reload
        // 并重抛 → 服务端起不来。因此这里把「字段被显式置空」转成 IllegalArgumentException，走既有的单文件跳过路径。
        Preconditions.checkArgument(data.getReloadData() != null, "reload data is empty");
        Preconditions.checkArgument(
            data.getReloadData()
                .getType() != null,
            "reload type is error");
        Preconditions.checkArgument(data.getFireModeSet() != null, "fire mode is empty");
        Preconditions.checkArgument(
            !data.getFireModeSet()
                .isEmpty(),
            "fire mode is empty");
        Preconditions.checkArgument(
            !data.getFireModeSet()
                .contains(null)
                && !data.getFireModeSet()
                    .contains(FireMode.UNKNOWN),
            "fire mode is error");
        checkInaccuracy(data);
        checkRecoil(data);
        checkScript(data, index);
        index.gunData = data;
    }

    private static void checkInaccuracy(GunData data) {
        Map<InaccuracyType, Float> defaultInaccuracy = InaccuracyType.getDefaultInaccuracy();
        Map<InaccuracyType, Float> readInaccuracy = data.getInaccuracy();
        if (readInaccuracy == null || readInaccuracy.isEmpty()) {
            data.setInaccuracy(defaultInaccuracy);
        } else {
            defaultInaccuracy.forEach(readInaccuracy::putIfAbsent);
        }
    }

    private static void checkRecoil(GunData data) {
        GunRecoil recoil = data.getRecoil();
        // 同上：json 里 "recoil": null 会让字段为 null（上游同源 NPE），这里转成可被单文件跳过路径捕获的异常。
        Preconditions.checkArgument(recoil != null, "recoil data is empty");
        GunRecoilKeyFrame[] pitch = recoil.getPitch();
        GunRecoilKeyFrame[] yaw = recoil.getYaw();
        // 1.7.10 差异（崩溃防护，同上面的 reload/fire_mode）：`{"value": null}` 或 `[null]` 会让
        // `keyFrame.getValue()` 为 null / `keyFrame` 本身为 null，`value.length` 直接 NPE。
        // NPE 不在 JsonDataManager#apply 的 catch 范围内 → 会冒到 CommonAssetsManager#reload 并重抛，
        // 一个坏配件/枪包 json 就能让服务端起不来。这里同样转成 IllegalArgumentException。
        if (pitch != null) {
            for (GunRecoilKeyFrame keyFrame : pitch) {
                Preconditions.checkArgument(keyFrame != null, "Recoil key frame must not be null");
                float[] value = keyFrame.getValue();
                Preconditions.checkArgument(value != null, "Recoil value is empty");
                Preconditions.checkArgument(value.length == 2, "Recoil value's length must be 2");
                Preconditions.checkArgument(value[0] <= value[1], "Recoil value's left must be less than right");
                Preconditions.checkArgument(keyFrame.getTime() >= 0, "Recoil time must be more than 0");
            }
            Arrays.sort(pitch);
        }

        if (yaw != null) {
            for (GunRecoilKeyFrame keyFrame : yaw) {
                Preconditions.checkArgument(keyFrame != null, "Recoil key frame must not be null");
                float[] value = keyFrame.getValue();
                Preconditions.checkArgument(value != null, "Recoil value is empty");
                Preconditions.checkArgument(value.length == 2, "Recoil value's length must be 2");
                Preconditions.checkArgument(value[0] <= value[1], "Recoil value's left must be less than right");
                Preconditions.checkArgument(keyFrame.getTime() >= 0, "Recoil time must be more than 0");
            }
            Arrays.sort(yaw);
        }
    }

    private static void checkScript(GunData data, CommonGunIndex index) {
        // 加载脚本
        ResourceLocation scriptId = data.getScript();
        CommonAssetsManager commonAssetsManager = CommonAssetsManager.getInstance();
        if (scriptId != null && commonAssetsManager != null) {
            index.script = commonAssetsManager.getScript(scriptId);
            if (index.script == null) {
                GunMod.LOGGER.warn(MARKER, "script '{}' not found", scriptId);
            }
        }
        // 多人客户端没有逻辑脚本，使用服务端同步的回调能力。
        index.hasUnloadAmmoScript = commonAssetsManager == null ? index.pojo.hasUnloadAmmoScript()
            : index.script != null && index.script.get("unload_ammo")
                .isfunction();
        // 加载脚本参数
        Map<String, Object> params = data.getScriptParam();
        if (params != null) {
            index.scriptParam = new LuaTable();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                index.scriptParam.set(entry.getKey(), CoerceJavaToLua.coerce(entry.getValue()));
            }
        }
    }

    public GunData getGunData() {
        return gunData;
    }

    public BulletData getBulletData() {
        return gunData.getBulletData();
    }

    public String getType() {
        return type;
    }

    public GunIndexPOJO getPojo() {
        return pojo;
    }

    public LuaTable getScript() {
        return script;
    }

    public boolean hasUnloadAmmoScript() {
        return hasUnloadAmmoScript;
    }

    public boolean canUnloadAmmo() {
        GunReloadData reload = gunData.getReloadData();
        return !reload.isInfinite() && reload.getType() != FeedType.FUEL
            && reload.getType() != FeedType.INVENTORY
            && (gunData.getScript() == null || gunData.isAllowAmmoUnload() || hasUnloadAmmoScript);
    }

    public LuaTable getScriptParam() {
        return scriptParam;
    }

    public int getSort() {
        return sort;
    }
}
