package com.tacz.guns.client.resource.manager;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.gltf.AnimationStructure;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.gltf.RawAnimationStructure;
import com.tacz.guns.resource.manager.LazyJsonDataManager;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.PackDirectory;

public class GltfManager extends LazyJsonDataManager<AnimationStructure> {

    private static final PackDirectory FILE_TO_ID_CONVERTER = PackDirectory.of("animations", ".gltf");

    public GltfManager() {
        super(
            AnimationStructure.class,
            ClientAssetsManager.GSON,
            FILE_TO_ID_CONVERTER,
            "GltfAnimationLoader",
            // 上游 GltfManager.java:19 在客户端注册（1.20 的 FileToIdConverter 只能看到 assets/），
            // 因此扫描根必须是 assets 而不是 LazyJsonDataManager 默认的 data；
            // 与 bedrockModel / bedrockAnimation 两个客户端管理器保持一致。
            GunPackResources.ASSETS_ROOT,
            id -> GunMod.MOD_ID.equals(id.getResourceDomain()));
    }

    @Override
    protected @Nullable AnimationStructure parseJson(JsonElement element) throws JsonParseException {
        RawAnimationStructure rawStructure = getGson().fromJson(element, RawAnimationStructure.class);
        return rawStructure == null ? null : new AnimationStructure(rawStructure);
    }

    public AnimationStructure getGltfAnimation(ResourceLocation id) {
        return getData(id);
    }
}
