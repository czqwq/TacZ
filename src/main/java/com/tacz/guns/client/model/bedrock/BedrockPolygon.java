package com.tacz.guns.client.model.bedrock;

import net.minecraftforge.common.util.ForgeDirection;

import org.joml.Vector3f;

public class BedrockPolygon {

    public final BedrockVertex[] vertices;
    public final Vector3f normal;

    /**
     * 移植说明：上游用 {@code net.minecraft.core.Direction}（1.20），1.7.10 无该类。
     * 这里改用 {@link ForgeDirection}：它是 1.7.10 的六面枚举，{@code DOWN/UP/NORTH/SOUTH/WEST/EAST}
     * 的命名与法线方向（{@code offsetX/Y/Z}）与 1.20 {@code Direction} 的 {@code step()} 完全一致，
     * 所以几何计算不需要任何调整。见 docs/PORT_PLAN.md §3.1 的 Direction 映射。
     */
    public BedrockPolygon(BedrockVertex[] vertices, float u1, float v1, float u2, float v2, float texWidth,
        float texHeight, boolean mirror, ForgeDirection direction) {
        this.vertices = vertices;
        float f = 0.0F / texWidth;
        float f1 = 0.0F / texHeight;
        vertices[0] = vertices[0].remap(u2 / texWidth - f, v1 / texHeight + f1);
        vertices[1] = vertices[1].remap(u1 / texWidth + f, v1 / texHeight + f1);
        vertices[2] = vertices[2].remap(u1 / texWidth + f, v2 / texHeight - f1);
        vertices[3] = vertices[3].remap(u2 / texWidth - f, v2 / texHeight - f1);
        if (mirror) {
            int i = vertices.length;
            for (int j = 0; j < i / 2; ++j) {
                BedrockVertex bedrockVertex = vertices[j];
                vertices[j] = vertices[i - 1 - j];
                vertices[i - 1 - j] = bedrockVertex;
            }
        }
        this.normal = new Vector3f(direction.offsetX, direction.offsetY, direction.offsetZ);
        if (mirror) {
            this.normal.mul(-1.0F, 1.0F, 1.0F);
        }
    }
}
