package com.tacz.guns.client.renderer.item;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.resource.pojo.display.block.BlockItemTransforms;
import com.tacz.guns.client.resource.serialize.Vector3fSerializer;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 方块物品的 GUI 3D 图标：把上游 1.20 的 **block model JSON**（{@code assets/tacz/models/block/*.json}）
 * 直接烘成四边形，在 {@code Inventory} 槽位里按 1.20 的 GUI 帧绘制。
 *
 * <p>
 * 为什么需要：这些物品（{@code ammo_storage*} / {@code gun_pegboard} / {@code glow_gun_pegboard} /
 * {@code gun_display_case}）上游的物品模型是 {@code parent: tacz:block/xxx}（3D 方块图标），
 * 1.7.10 没有 JSON 方块模型体系，{@link net.minecraft.item.ItemBlock} 退回 block atlas 贴图 →
 * 图标是 64×64 UV 图集的碎块。
 *
 * <p>
 * 帧与 1.20 对齐：{@code GuiGraphics#renderItem}（{@code translate(x+8,y+8,150)}+
 * {@code scaling(1,-1,1)}+{@code scale(16,16,16)}，见 1.20.1 {@code GuiGraphics.java:478-494}）
 * + {@code ItemRenderer#render} 的 {@code display.gui} 变换 + {@code translate(-0.5,-0.5,-0.5)}
 * （{@code ItemRenderer.java:128-129}）。1.7.10 侧用 {@link RenderHelperCompat#applyGuiSlotScale}
 * 等价替换外框（推导见该类注释），并在 display 变换前后各补一次 {@code translate(±0.5)}：
 * 1.20 外框以槽位中心 {@code (x+8,y+8)} 为原点、显示变换以方块中心为原点，1.7.10 的 Forge 入口
 * 只平移到槽位左上角 {@code (x,y)}，两者相差的 {@code +8} 像素由这层 {@code +0.5 方块单位 (=8 像素)}
 * 提供。顶点在 CPU 侧变换后交给 {@link Tessellator}（GL 当前矩阵已由
 * {@code ForgeHooksClient#renderInventoryItem} 平移到图标左上角）。
 *
 * <p>
 * 只接管 {@code INVENTORY}：手持/掉落/展示框继续走原版方块渲染（1.7.10 {@code RenderItem} 的 3D 方块分支，
 * 外观 = 用方块图集贴图的等轴测方块；1.20 的手持/地面块模型渲染需要手臂骨骼帧与 display 变换，
 * 属另一处平台降级，不在本类范围）。
 */
public class BlockModelGuiItemRenderer implements IItemRenderer {

    private static final BlockModelGuiItemRenderer INSTANCE = new BlockModelGuiItemRenderer();

    /** 物品 → 方块模型（{@code tacz:block/...}）。由 {@code ClientSetupEvent} 注册。 */
    private static final Map<Item, ResourceLocation> ITEM_MODELS = new IdentityHashMap<>();

    /** 模型缓存：JSON 是内置资源、运行期不变；纹理图标每次渲染时按名字重新取（资源重载后 Sprite 会换实例）。 */
    private static final Map<ResourceLocation, Model> MODEL_CACHE = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Vector3f.class, new Vector3fSerializer())
        .create();

    /**
     * 父模型 {@code minecraft:block/block} 的 GUI 变换。1.7.10 没有 1.20 的 models 资源，
     * 这里按 1.20.1 {@code assets/minecraft/models/block/block.json} 的 {@code display.gui} 常量抄写
     * （rotation [30,225,0] / scale 0.625）。
     */
    private static final BlockItemTransforms.ItemTransform BLOCK_BLOCK_GUI = new BlockItemTransforms.ItemTransform(
        new Vector3f(30.0F, 225.0F, 0.0F),
        new Vector3f(0.0F, 0.0F, 0.0F),
        new Vector3f(0.625F, 0.625F, 0.625F));

    /** 六个方向的面顶点（顺序取自 1.20.1 {@code FaceInfo}，UV 顺序同 {@code BlockFaceUV}）。 */
    private static final String[] FACE_NAMES = { "down", "up", "north", "south", "west", "east" };

    private static final int[][] FACE_VERTEX_CODES = {
        // 0=minX 1=maxX 2=minY 3=maxY 4=minZ 5=maxZ
        { 0, 2, 5, 0, 2, 4, 1, 2, 4, 1, 2, 5 }, // down
        { 0, 3, 4, 0, 3, 5, 1, 3, 5, 1, 3, 4 }, // up
        { 1, 3, 4, 1, 2, 4, 0, 2, 4, 0, 3, 4 }, // north
        { 0, 3, 5, 0, 2, 5, 1, 2, 5, 1, 3, 5 }, // south
        { 0, 3, 4, 0, 2, 4, 0, 2, 5, 0, 3, 5 }, // west
        { 1, 3, 5, 1, 2, 5, 1, 2, 4, 1, 3, 4 } }; // east

    private static final float[][] FACE_NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    /** 单个四边形：4 个顶点位置（块空间 0..1）、4 组 UV（0..16，与 1.20 {@code BlockFaceUV} 同单位）、贴图名。 */
    private record Quad(float[] positions, float[] uvs, String texture, float normalX, float normalY, float normalZ) {}

    private record Model(List<Quad> quads, BlockItemTransforms.ItemTransform gui) {}

    private BlockModelGuiItemRenderer() {}

    /** 客户端初始化时登记：某方块物品用哪个 block model 当 GUI 图标。 */
    public static void register(Item item, ResourceLocation blockModel) {
        if (item == null) {
            return;
        }
        ITEM_MODELS.put(item, blockModel);
        MinecraftForgeClient.registerItemRenderer(item, INSTANCE);
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY && item != null && ITEM_MODELS.containsKey(item.getItem());
    }

    /** 返回 {@code false}：走 {@code ForgeHooksClient#renderInventoryItem} 的 else 分支（只平移，不套方块 helper 帧）。 */
    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (type != ItemRenderType.INVENTORY || item == null) {
            return;
        }
        Model model = modelFor(item.getItem());
        if (model == null || model.quads()
            .isEmpty()) {
            return;
        }
        PoseStack pose = new PoseStack();
        RenderHelperCompat.applyGuiSlotScale(pose);
        // 1.20 的槽位帧是 T(x+8,y+8,150)·S(16,-16,16)，显示变换以「方块中心」为原点（前面还有
        // ItemRenderer 的 translate(-0.5,-0.5,-0.5)，ItemRenderer.java:128-129），故这里围绕 0.5 包一层，
        // 使 0..1 的方块模型经过 display.gui 后仍落在槽位内（推导见本类类注释）。
        pose.translate(0.5F, 0.5F, 0.5F);
        model.gui()
            .apply(false, pose);
        pose.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f matrix = new Matrix4f(
            pose.last()
                .pose());
        Matrix3f normalMatrix = new Matrix3f(
            pose.last()
                .normal());

        boolean prevAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LIGHTING);
        try {
            TextureMap textureMap = Minecraft.getMinecraft()
                .getTextureMapBlocks();
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            Vector4f vertex = new Vector4f();
            Vector3f normal = new Vector3f();
            for (Quad quad : model.quads()) {
                IIcon icon = textureMap.getAtlasSprite(quad.texture());
                normal.set(quad.normalX(), quad.normalY(), quad.normalZ())
                    .mul(normalMatrix)
                    .normalize();
                float[] positions = quad.positions();
                float[] uvs = quad.uvs();
                for (int i = 0; i < 4; i++) {
                    vertex.set(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2], 1.0F)
                        .mul(matrix);
                    tessellator.setNormal(normal.x(), normal.y(), normal.z());
                    tessellator.addVertexWithUV(
                        vertex.x,
                        vertex.y,
                        vertex.z,
                        icon.getInterpolatedU(uvs[i * 2]),
                        icon.getInterpolatedV(uvs[i * 2 + 1]));
                }
            }
            tessellator.draw();
        } finally {
            toggle(GL11.GL_ALPHA_TEST, prevAlphaTest);
            toggle(GL11.GL_BLEND, prevBlend);
            toggle(GL11.GL_CULL_FACE, prevCull);
            toggle(GL11.GL_LIGHTING, prevLighting);
        }
    }

    private static void toggle(int cap, boolean enabled) {
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    @Nullable
    private static Model modelFor(Item item) {
        ResourceLocation location = ITEM_MODELS.get(item);
        if (location == null) {
            return null;
        }
        Model cached = MODEL_CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        Model loaded = load(location);
        if (loaded == null) {
            GunMod.LOGGER.warn("[TACZ] block model icon missing: {}", location);
            loaded = new Model(List.of(), BLOCK_BLOCK_GUI);
        }
        MODEL_CACHE.put(location, loaded);
        return loaded;
    }

    @Nullable
    private static Model load(ResourceLocation blockModelLocation) {
        try {
            RawModel raw = collect(blockModelLocation, 0);
            if (raw == null) {
                return null;
            }
            List<Quad> quads = new ArrayList<>();
            for (JsonObject element : raw.elements) {
                bakeElement(element, raw.textures, quads);
            }
            return new Model(quads, raw.gui == null ? BLOCK_BLOCK_GUI : raw.gui);
        } catch (IOException | RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ] failed to load block model {}", blockModelLocation, e);
            return null;
        }
    }

    /** 递归合并父模型：textures 子覆盖父；elements 取子（若为空）→ 父；gui 取链上第一个。 */
    @Nullable
    private static RawModel collect(ResourceLocation location, int depth) throws IOException {
        if (depth > 8) {
            return null;
        }
        JsonObject json = readJson(location);
        if (json == null) {
            return null;
        }
        RawModel result = new RawModel();
        JsonObject textures = getObject(json, "textures");
        if (textures != null) {
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                result.textures.put(
                    entry.getKey(),
                    entry.getValue()
                        .getAsString());
            }
        }
        JsonArray elements = getArray(json, "elements");
        if (elements != null) {
            for (JsonElement element : elements) {
                if (element.isJsonObject()) {
                    result.elements.add(element.getAsJsonObject());
                }
            }
        }
        JsonObject display = getObject(json, "display");
        if (display != null) {
            JsonObject gui = getObject(display, "gui");
            if (gui != null) {
                result.gui = GSON.fromJson(gui, BlockItemTransforms.ItemTransform.class);
            }
        }
        String parentName = getString(json, "parent");
        if (parentName == null) {
            return result;
        }
        if ("block/block".equals(parentName) || "minecraft:block/block".equals(parentName)) {
            // 1.7.10 没有 1.20 的内置模型资源，用常量代替（见 BLOCK_BLOCK_GUI）
            return result;
        }
        RawModel parent = collect(new ResourceLocation(parentName), depth + 1);
        if (parent == null) {
            return result;
        }
        parent.textures.putAll(result.textures);
        result.textures = parent.textures;
        if (result.elements.isEmpty()) {
            result.elements = parent.elements;
        }
        if (result.gui == null) {
            result.gui = parent.gui;
        }
        return result;
    }

    @Nullable
    private static JsonObject readJson(ResourceLocation location) throws IOException {
        ResourceLocation file = new ResourceLocation(
            location.getResourceDomain(),
            "models/" + location.getResourcePath() + ".json");
        IResource resource = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(file);
        if (resource == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement json = new JsonParser().parse(reader);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        }
    }

    private static void bakeElement(JsonObject element, Map<String, String> textures, List<Quad> quads) {
        float[] from = getFloat3(element, "from", 0.0F);
        float[] to = getFloat3(element, "to", 16.0F);
        float[] bounds = { from[0] / 16.0F, to[0] / 16.0F, from[1] / 16.0F, to[1] / 16.0F, from[2] / 16.0F,
            to[2] / 16.0F };
        JsonObject rotation = getObject(element, "rotation");
        if (rotation != null && rotation.has("angle")
            && rotation.get("angle")
                .getAsFloat() != 0.0F) {
            // 现有 TACZ 方块模型元素旋转全为 0；非 0 时跳过该元素而不是画错（宁缺勿错）
            return;
        }
        JsonObject faces = getObject(element, "faces");
        if (faces == null) {
            return;
        }
        for (int face = 0; face < FACE_NAMES.length; face++) {
            JsonObject faceJson = getObject(faces, FACE_NAMES[face]);
            if (faceJson == null) {
                continue;
            }
            JsonArray uvArray = getArray(faceJson, "uv");
            if (uvArray == null || uvArray.size() != 4) {
                continue;
            }
            float[] uv = new float[8];
            float u0 = uvArray.get(0)
                .getAsFloat();
            float v0 = uvArray.get(1)
                .getAsFloat();
            float u1 = uvArray.get(2)
                .getAsFloat();
            float v1 = uvArray.get(3)
                .getAsFloat();
            // 顶点顺序 0..3 → (u0,v0) (u0,v1) (u1,v1) (u1,v0)（BlockFaceUV rotation=0 的取值规则）
            uv[0] = u0;
            uv[1] = v0;
            uv[2] = u0;
            uv[3] = v1;
            uv[4] = u1;
            uv[5] = v1;
            uv[6] = u1;
            uv[7] = v0;
            String texture = getString(faceJson, "texture");
            String iconName = texture == null ? "missingno" : resolveTextureName(texture, textures);
            if (iconName == null) {
                continue;
            }
            int[] codes = FACE_VERTEX_CODES[face];
            float[] positions = new float[12];
            for (int i = 0; i < 4; i++) {
                positions[i * 3] = bounds[codes[i * 3]];
                positions[i * 3 + 1] = bounds[codes[i * 3 + 1]];
                positions[i * 3 + 2] = bounds[codes[i * 3 + 2]];
            }
            float[] normal = FACE_NORMALS[face];
            quads.add(new Quad(positions, uv, iconName, normal[0], normal[1], normal[2]));
        }
    }

    /**
     * 1.20 贴图引用（{@code #0} → {@code tacz:block/pegboard}）→ 1.7.10 方块图集 icon 名
     * （{@code tacz:pegboard}：资源路径去掉 1.20 才有的 {@code block/} 层）。
     */
    @Nullable
    private static String resolveTextureName(String reference, Map<String, String> textures) {
        String value = reference.startsWith("#") ? textures.get(reference.substring(1)) : reference;
        if (value == null) {
            return null;
        }
        ResourceLocation location = new ResourceLocation(value);
        String path = location.getResourcePath();
        if (path.startsWith("block/")) {
            path = path.substring("block/".length());
        }
        return location.getResourceDomain() + ":" + path;
    }

    @Nullable
    private static JsonObject getObject(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonArray getArray(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    @Nullable
    private static String getString(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static float[] getFloat3(JsonObject parent, String key, float defaultValue) {
        float[] result = { defaultValue, defaultValue, defaultValue };
        JsonArray array = getArray(parent, key);
        if (array != null && array.size() == 3) {
            for (int i = 0; i < 3; i++) {
                result[i] = array.get(i)
                    .getAsFloat();
            }
        }
        return result;
    }

    private static final class RawModel {

        private Map<String, String> textures = new HashMap<>();
        private List<JsonObject> elements = new ArrayList<>();
        @Nullable
        private BlockItemTransforms.ItemTransform gui;
    }
}
