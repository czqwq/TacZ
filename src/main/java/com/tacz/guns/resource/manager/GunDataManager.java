package com.tacz.guns.resource.manager;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.gun.GunData;

/**
 * 枪械数据（{@code data/<ns>/data/guns}）管理器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/GunDataManager.java}。
 */
public class GunDataManager extends CommonDataManager<GunData> {

    private static final String EXCLUSIVE_ATTACHMENTS = "exclusive_attachments";

    public GunDataManager() {
        super(DataType.GUN_DATA, GunData.class, CommonAssetsManager.GSON, "data/guns", "GunDataLoader");
    }

    @Override
    protected GunData parseJson(JsonElement element) {
        GunData data = getGson().fromJson(element, getDataClass());
        initExclusiveAttachmentModifiers(data, element);
        return data;
    }

    public static void initExclusiveAttachmentModifiers(GunData data, JsonElement element) {
        if (data == null || !element.isJsonObject()) {
            return;
        }
        JsonElement exclusiveElement = element.getAsJsonObject()
            .get(EXCLUSIVE_ATTACHMENTS);
        if (exclusiveElement == null || !exclusiveElement.isJsonObject()) {
            return;
        }
        JsonObject exclusiveAttachments = exclusiveElement.getAsJsonObject();
        exclusiveAttachments.entrySet()
            .forEach(entry -> {
                ResourceLocation attachmentId = parseResourceLocation(entry.getKey());
                if (attachmentId == null) {
                    return;
                }
                var attachmentData = data.getExclusiveAttachments()
                    .get(attachmentId);
                if (attachmentData != null) {
                    AttachmentDataManager.initModifiers(attachmentData, entry.getValue(), CommonAssetsManager.GSON);
                }
            });
    }

    /**
     * 1.7.10 差异：没有 {@code ResourceLocation.tryParse(String)}。
     * 1.7.10 的 {@code new ResourceLocation(String)} 几乎不校验（见
     * {@code net.minecraft.util.ResourceLocation#ResourceLocation(String)}），这里补上「非法输入返回 null」的语义。
     */
    @Nullable
    private static ResourceLocation parseResourceLocation(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
