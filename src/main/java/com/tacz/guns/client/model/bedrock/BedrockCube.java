package com.tacz.guns.client.model.bedrock;

import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.VertexConsumer;

public interface BedrockCube {

    void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float red, float green,
        float blue, float alpha);
}
