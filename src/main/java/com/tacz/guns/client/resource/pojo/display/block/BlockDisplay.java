package com.tacz.guns.client.resource.pojo.display.block;

import net.minecraft.util.ResourceLocation;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.resource.pojo.display.IDisplay;

public class BlockDisplay implements IDisplay {

    @SerializedName("model")
    private ResourceLocation modelLocation;
    @SerializedName("texture")
    private ResourceLocation modelTexture;
    // TODO(port): 上游 BlockDisplay.java:14 是 1.20 的 ItemTransforms（net.minecraft.client.renderer.block.model），
    // 1.7.10 没有该包，改用 TACZ 自有的等价 POJO（见 BlockItemTransforms 的类注释）。
    @SerializedName("transforms")
    private BlockItemTransforms transforms;

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    public BlockItemTransforms getTransforms() {
        return transforms;
    }

    @Override
    public void init() {
        if (modelTexture != null) {
            modelTexture = IDisplay.idToFile(modelTexture);
        }
    }
}
