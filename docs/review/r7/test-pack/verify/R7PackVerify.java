import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pack.DirectoryGunPackSource;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.IGunPackSource;
import com.tacz.guns.resource.pack.ZipGunPackSource;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import com.tacz.guns.resource.pojo.data.gun.GunData;

/**
 * 用真实运行期 classpath 验证 docs/review/r7/test-pack 的目录版枪包：走真实的 GunPackResources 扫描 +
 * CommonAssetsManager.GSON 反序列化 + CommonGunIndex 校验路径。 不经过 FML 启动（GunPackLoader 依赖 FMLCommonHandler，
 * 单测里不可用），但从枪包目录开始的扫描/解析/校验链路全部是生产代码。
 */
public final class R7PackVerify {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        File packDir = new File("tacz/tacz_review_pack");
        check("枪包目录存在", packDir.isDirectory(), packDir.getAbsolutePath());

        IGunPackSource source = new DirectoryGunPackSource(packDir.toPath());
        System.out.println("[scan] source = " + source.getName());
        List<IGunPackSource> sources = Collections.singletonList(source);

        Map<ResourceLocation, JsonElement> gunDataJson = GunPackResources.scanDirectory(
            sources,
            GunPackResources.DATA_ROOT,
            "data/guns",
            ".json",
            CommonAssetsManager.GSON);
        System.out.println("[scan] gun data ids = " + gunDataJson.keySet());
        check(
            "data/guns 下扫到 review_pistol_data",
            gunDataJson.keySet()
                .contains(new ResourceLocation("tacz_review_pack", "review_pistol_data")),
            gunDataJson.keySet()
                .toString());

        ExposedDataManager<GunData> gunDataManager = new ExposedDataManager<>(
            GunData.class,
            CommonAssetsManager.GSON,
            "data/guns",
            "GunDataLoader");
        gunDataManager.applyPublic(gunDataJson);

        Map<ResourceLocation, JsonElement> ammoJson = GunPackResources.scanDirectory(
            sources,
            GunPackResources.DATA_ROOT,
            "index/ammo",
            ".json",
            CommonAssetsManager.GSON);
        ExposedDataManager<CommonAmmoIndex> ammoManager = new ExposedDataManager<>(
            CommonAmmoIndex.class,
            CommonAssetsManager.GSON,
            "index/ammo",
            "AmmoIndexLoader");
        ammoManager.applyPublic(ammoJson);

        CommonAssetsManager manager = new CommonAssetsManager();
        setStatic(CommonAssetsManager.class, "INSTANCE", manager);
        setField(CommonAssetsManager.class, manager, "gunData", gunDataManager);
        setField(CommonAssetsManager.class, manager, "ammoIndex", ammoManager);
        setField(CommonAssetsManager.class, manager, "attachmentData", emptyManager(DataType.ATTACHMENT_DATA));
        setField(CommonAssetsManager.class, manager, "attachmentVariantData", emptyManager(DataType.ATTACHMENT_VARIANT_DATA));
        setField(CommonAssetsManager.class, manager, "slotAdapterData", emptyManager(DataType.SLOT_ADAPTER_DATA));
        setField(CommonAssetsManager.class, manager, "blockData", emptyManager(DataType.BLOCK_DATA));

        GunData data = manager.getGunData(new ResourceLocation("tacz_review_pack", "review_pistol_data"));
        check("gun data 已装载", data != null, "null");
        if (data != null) {
            check(
                "ammo id = " + data.getAmmoId(),
                new ResourceLocation("tacz_review_pack", "review_9mm").equals(data.getAmmoId()),
                String.valueOf(data.getAmmoId()));
            check("rpm = " + data.getRoundsPerMinute(), data.getRoundsPerMinute() == 320, "");
            check("bulletData 非 null", data.getBulletData() != null, "null");
            check("fireModeSet = " + data.getFireModeSet(), data.getFireModeSet().size() == 1, "");
            check("reload.type 非 null", data.getReloadData() != null && data.getReloadData().getType() != null, "");
        }

        Map<ResourceLocation, JsonElement> gunIndexJson = GunPackResources.scanDirectory(
            sources,
            GunPackResources.DATA_ROOT,
            "index/guns",
            ".json",
            CommonAssetsManager.GSON);
        System.out.println("[scan] gun index ids = " + gunIndexJson.keySet());
        JsonElement indexElement = gunIndexJson.get(new ResourceLocation("tacz_review_pack", "review_pistol"));
        GunIndexPOJO pojo = CommonAssetsManager.GSON.fromJson(indexElement, GunIndexPOJO.class);
        check("gun index pojo 已解析", pojo != null, "null");
        CommonGunIndex index = null;
        try {
            index = CommonAssetsManager.GSON.fromJson(indexElement, CommonGunIndex.class);
        } catch (RuntimeException exception) {
            check("gun index 校验通过（checkIndex/checkData）", false, exception.toString());
        }
        check("gun index 已装载", index != null, "null");
        if (index != null) {
            check("type = " + index.getType(), "pistol".equals(index.getType()), String.valueOf(index.getType()));
            check("gunData 已挂到 index", index.getGunData() != null, "null");
        }

        GunDisplay gunDisplay = read(
            ClientAssetsManager.GSON,
            "tacz/tacz_review_pack/assets/tacz_review_pack/display/guns/review_pistol_display.json",
            GunDisplay.class);
        check("gun display 已解析", gunDisplay != null, "null");
        Map<ResourceLocation, JsonElement> models = GunPackResources.scanDirectory(
            sources,
            GunPackResources.ASSETS_ROOT,
            "geo_models",
            ".json",
            CommonAssetsManager.GSON);
        List<String> texturePaths = source.listFiles("assets/tacz_review_pack/textures");
        System.out.println("[scan] geo ids = " + models.keySet());
        System.out.println("[scan] texture paths = " + texturePaths);
        if (gunDisplay != null) {
            check(
                "display.model 指向的 geo 已索引 = " + gunDisplay.getModelLocation(),
                models.containsKey(gunDisplay.getModelLocation()),
                models.keySet()
                    .toString());
            check(
                "display.texture 指向的 png 存在 = " + gunDisplay.getModelTexture(),
                texturePaths.contains(
                    "assets/tacz_review_pack/textures/" + gunDisplay.getModelTexture()
                        .getResourcePath() + ".png"),
                texturePaths.toString());
            check(
                "display.slot 指向的 png 存在 = " + gunDisplay.getSlotTextureLocation(),
                texturePaths.contains(
                    "assets/tacz_review_pack/textures/" + gunDisplay.getSlotTextureLocation()
                        .getResourcePath() + ".png"),
                texturePaths.toString());
        }

        AmmoDisplay ammoDisplay = read(
            ClientAssetsManager.GSON,
            "tacz/tacz_review_pack/assets/tacz_review_pack/display/ammo/review_9mm_display.json",
            AmmoDisplay.class);
        check("ammo display 已解析", ammoDisplay != null, "null");

        BedrockModelPOJO model = read(
            ClientAssetsManager.GSON,
            "tacz/tacz_review_pack/assets/tacz_review_pack/geo_models/gun/review_pistol_geo.json",
            BedrockModelPOJO.class);
        check("geo model 已解析", model != null, "null");
        if (model != null) {
            check("format_version = " + model.getFormatVersion(), "1.12.0".equals(model.getFormatVersion()), "");
            check("新版 geometry 已解析", model.getGeometryModelNew() != null, "null");
            if (model.getGeometryModelNew() != null) {
                int boneCount = model.getGeometryModelNew()
                    .getBones()
                    .size();
                check("bones 数量 = " + boneCount, boneCount == 3, String.valueOf(boneCount));
                int width = model.getGeometryModelNew()
                    .getDescription()
                    .getTextureWidth();
                int height = model.getGeometryModelNew()
                    .getDescription()
                    .getTextureHeight();
                check("texture 尺寸 = " + width + "x" + height, width == 16 && height == 16, "");
                check(
                    "root 骨骼存在",
                    model.getGeometryModelNew()
                        .getBones()
                        .stream()
                        .anyMatch(bone -> "root".equals(bone.getName())),
                    "");
            }
        }

        // 7) zip 版枪包：同一份内容压成 zip 后必须扫出同样的 id
        File zipFile = new File("tacz_review_pack.zip");
        if (zipFile.isFile()) {
            IGunPackSource zipSource = new ZipGunPackSource(zipFile.toPath());
            List<IGunPackSource> zipSources = Collections.singletonList(zipSource);
            Map<ResourceLocation, JsonElement> zipGunData = GunPackResources.scanDirectory(
                zipSources,
                GunPackResources.DATA_ROOT,
                "data/guns",
                ".json",
                CommonAssetsManager.GSON);
            Map<ResourceLocation, JsonElement> zipGunIndex = GunPackResources.scanDirectory(
                zipSources,
                GunPackResources.DATA_ROOT,
                "index/guns",
                ".json",
                CommonAssetsManager.GSON);
            check("zip 版 data/guns 与目录版一致", zipGunData.keySet()
                .equals(gunDataJson.keySet()), zipGunData.keySet()
                    .toString());
            check("zip 版 index/guns 与目录版一致", zipGunIndex.keySet()
                .equals(gunIndexJson.keySet()), zipGunIndex.keySet()
                    .toString());
            ((ZipGunPackSource) zipSource).close();
        } else {
            System.out.println("[skip] 未找到 tacz_review_pack.zip，跳过 zip 版校验");
        }

        // 8) lenient 解析：枪包 json 写 // 注释必须能读（默认枪包的数据文件大量带注释）
        File commentDir = new File("comment_check/data/comment_check/data/guns");
        commentDir.mkdirs();
        try (FileWriter writer = new java.io.FileWriter(
            new File(commentDir, "commented_data.json"),
            StandardCharsets.UTF_8)) {
            writer.write("{\n  // 这是注释\n  \"rpm\": 123\n}\n");
        }
        IGunPackSource commentSource = new DirectoryGunPackSource(new File("comment_check").toPath());
        Map<ResourceLocation, JsonElement> commented = GunPackResources.scanDirectory(
            Collections.singletonList(commentSource),
            GunPackResources.DATA_ROOT,
            "data/guns",
            ".json",
            CommonAssetsManager.GSON);
        check(
            "带 // 注释的 json 能被 lenient 解析",
            commented.containsKey(new ResourceLocation("comment_check", "commented_data")),
            commented.keySet()
                .toString());

        System.out.println(failures == 0 ? "RESULT: ALL PASS" : "RESULT: " + failures + " FAILURE(S)");
        if (failures != 0) {
            System.exit(1);
        }
    }

    /** 暴露 protected 的 apply(Map)，让验证脚本能直接喂扫描结果。 */
    static final class ExposedDataManager<T> extends CommonDataManager<T> {

        ExposedDataManager(Class<T> dataClass, Gson gson, String directory, String marker) {
            super(DataType.GUN_DATA, dataClass, gson, directory, marker);
        }

        void applyPublic(Map<ResourceLocation, JsonElement> map) {
            apply(map);
        }
    }

    private static CommonDataManager<Object> emptyManager(DataType type) {
        ExposedDataManager<Object> manager = new ExposedDataManager<>(
            Object.class,
            CommonAssetsManager.GSON,
            "noop",
            "noop");
        manager.applyPublic(Collections.emptyMap());
        return manager;
    }

    private static <T> T read(Gson gson, String path, Class<T> type) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, type);
        }
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            System.out.println("[PASS] " + what);
        } else {
            failures++;
            System.out.println("[FAIL] " + what + "  (actual: " + detail + ")");
        }
    }
}
