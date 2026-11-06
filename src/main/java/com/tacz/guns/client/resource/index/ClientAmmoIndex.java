package com.tacz.guns.client.resource.index;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.base.Preconditions;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.resource.ClientAssetLoadDispatcher;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.ammo.*;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.config.client.ResourceConfig;
import com.tacz.guns.resource.pojo.AmmoIndexPOJO;
import com.tacz.guns.util.ColorHex;

public class ClientAmmoIndex {

    /**
     * TODO(port): 上游 ClientAmmoIndex.java:189 用 {@code MissingTextureAtlasSprite.getLocation()}（1.20）
     * 取「缺失材质」的 atlas 资源位置。1.7.10 没有该常量；绑定一个不存在的贴图路径时
     * {@code TextureManager} 会自动渲染原版 missingno 棋盘格，故用固定占位路径。
     */
    private static final ResourceLocation MISSING_TEXTURE = new ResourceLocation("minecraft", "missingno");
    private final Object modelLoadLock = new Object();
    private String name;
    private AmmoDisplay display;
    private @Nullable BedrockAmmoModel ammoModel;
    private @Nullable ResourceLocation modelTextureLocation;
    private ResourceLocation slotTextureLocation;
    private @Nullable BedrockAmmoModel ammoEntityModel;
    private @Nullable ResourceLocation ammoEntityTextureLocation;
    private @Nullable BedrockAmmoModel shellModel;
    private @Nullable ResourceLocation shellTextureLocation;
    private int stackSize;
    private @Nullable AmmoParticle particle;
    private @Nullable List<HitSurfaceParticle> hitSurfaceParticles;
    private @Nullable Integer hitBlockParticleCount;
    private boolean bulletHoleEnabled = true;
    private float[] tracerColor = new float[] { 1f, 1f, 1f };
    private AmmoTransform transform;
    private @Nullable String tooltipKey;
    private volatile boolean modelsLoaded = false;
    private volatile boolean modelsLoadFailed = false;
    private volatile CompletableFuture<Void> warmUpTask = null;

    private ClientAmmoIndex() {}

    public static ClientAmmoIndex getInstance(AmmoIndexPOJO clientPojo) throws IllegalArgumentException {
        ClientAmmoIndex index = new ClientAmmoIndex();
        checkIndex(clientPojo, index);
        AmmoDisplay display = checkDisplay(clientPojo, index);
        checkName(clientPojo, index);
        checkSlotTexture(display, index);
        checkStackSize(clientPojo, index);
        checkParticle(display, index);
        checkHitSurfaceParticles(display, index);
        checkHitBlockParticleCount(display, index);
        checkBulletHoleEnabled(display, index);
        checkTracerColor(display, index);
        checkTransform(display, index);
        if (!ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD.get()) {
            index.ensureModelsLoaded();
        }
        return index;
    }

    private static void checkIndex(AmmoIndexPOJO ammoIndexPOJO, ClientAmmoIndex index) {
        Preconditions.checkArgument(ammoIndexPOJO != null, "index object file is empty");
        index.tooltipKey = ammoIndexPOJO.getTooltip();
    }

    private static void checkName(AmmoIndexPOJO ammoIndexPOJO, ClientAmmoIndex index) {
        index.name = ammoIndexPOJO.getName();
        if (StringUtils.isBlank(index.name)) {
            index.name = "custom.tacz.error.no_name";
        }
    }

    @NotNull
    private static AmmoDisplay checkDisplay(AmmoIndexPOJO ammoIndexPOJO, ClientAmmoIndex index) {
        ResourceLocation pojoDisplay = ammoIndexPOJO.getDisplay();
        Preconditions.checkArgument(pojoDisplay != null, "index object missing display field");

        AmmoDisplay display = ClientAssetsManager.INSTANCE.getAmmoDisplay(pojoDisplay);
        Preconditions.checkArgument(display != null, "there is no corresponding display file");
        index.display = display;
        return display;
    }

    public void warmUp() {
        if (!ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD.get()) {
            ensureModelsLoaded();
            return;
        }
        if (modelsLoaded || modelsLoadFailed || warmUpTask != null) {
            return;
        }
        synchronized (modelLoadLock) {
            if (modelsLoaded || modelsLoadFailed || warmUpTask != null) {
                return;
            }
            warmUpTask = CompletableFuture.runAsync(this::loadModelsIfNecessary, ClientAssetLoadDispatcher.executor());
            warmUpTask.whenComplete((unused, throwable) -> {
                if (throwable != null) {
                    handleModelsLoadFailure(throwable);
                }
            });
        }
    }

    private void ensureModelsLoaded() {
        if (modelsLoaded || modelsLoadFailed) {
            return;
        }
        CompletableFuture<Void> task = warmUpTask;
        if (task == null) {
            try {
                loadModelsIfNecessary();
            } catch (Throwable throwable) {
                handleModelsLoadFailure(throwable);
            }
            return;
        }
        try {
            task.join();
        } catch (CompletionException exception) {
            handleModelsLoadFailure(exception.getCause() == null ? exception : exception.getCause());
        }
    }

    private void loadModelsIfNecessary() {
        if (modelsLoaded) {
            return;
        }
        synchronized (modelLoadLock) {
            if (modelsLoaded) {
                return;
            }
            checkTextureAndModel(display, this);
            checkAmmoEntity(display, this);
            checkShell(display, this);
            modelsLoaded = true;
        }
    }

    private void handleModelsLoadFailure(Throwable throwable) {
        boolean shouldLog = false;
        synchronized (modelLoadLock) {
            if (!modelsLoaded) {
                shouldLog = !modelsLoadFailed;
                warmUpTask = null;
                modelsLoadFailed = true;
            }
        }
        if (shouldLog) {
            GunMod.LOGGER.warn("Failed to load ammo models {}", display.getModelLocation(), throwable);
        }
    }

    private static void checkTextureAndModel(AmmoDisplay display, ClientAmmoIndex index) {
        // 检查模型
        ResourceLocation modelLocation = display.getModelLocation();
        if (modelLocation == null) {
            return;
        }
        BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(modelLocation);
        Preconditions.checkArgument(modelPOJO != null, "there is no corresponding model file");
        // 检查材质
        index.modelTextureLocation = display.getModelTexture();
        // 先判断是不是 1.10.0 版本基岩版模型文件
        if (BedrockVersion.isLegacyVersion(modelPOJO) && modelPOJO.getGeometryModelLegacy() != null) {
            index.ammoModel = new BedrockAmmoModel(modelPOJO, BedrockVersion.LEGACY);
        }
        // 判定是不是 1.12.0 版本基岩版模型文件
        if (BedrockVersion.isNewVersion(modelPOJO) && modelPOJO.getGeometryModelNew() != null) {
            index.ammoModel = new BedrockAmmoModel(modelPOJO, BedrockVersion.NEW);
        }
    }

    private static void checkSlotTexture(AmmoDisplay display, ClientAmmoIndex index) {
        // 加载 GUI 内枪械图标
        ResourceLocation slotTexture = display.getSlotTextureLocation();
        index.slotTextureLocation = slotTexture == null ? MISSING_TEXTURE : slotTexture;
    }

    private static void checkAmmoEntity(AmmoDisplay display, ClientAmmoIndex index) {
        AmmoEntityDisplay ammoEntity = display.getAmmoEntity();
        if (ammoEntity != null && ammoEntity.getModelLocation() != null && ammoEntity.getModelTexture() != null) {
            index.ammoEntityTextureLocation = ammoEntity.getModelTexture();
            ResourceLocation modelLocation = ammoEntity.getModelLocation();
            BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(modelLocation);
            if (modelPOJO == null) {
                return;
            }
            // 先判断是不是 1.10.0 版本基岩版模型文件
            if (BedrockVersion.isLegacyVersion(modelPOJO) && modelPOJO.getGeometryModelLegacy() != null) {
                index.ammoEntityModel = new BedrockAmmoModel(modelPOJO, BedrockVersion.LEGACY);
            }
            // 判定是不是 1.12.0 版本基岩版模型文件
            if (BedrockVersion.isNewVersion(modelPOJO) && modelPOJO.getGeometryModelNew() != null) {
                index.ammoEntityModel = new BedrockAmmoModel(modelPOJO, BedrockVersion.NEW);
            }
        }
    }

    private static void checkShell(AmmoDisplay display, ClientAmmoIndex index) {
        ShellDisplay shellDisplay = display.getShellDisplay();
        if (shellDisplay != null && shellDisplay.getModelLocation() != null && shellDisplay.getModelTexture() != null) {
            index.shellTextureLocation = shellDisplay.getModelTexture();
            ResourceLocation modelLocation = shellDisplay.getModelLocation();
            BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(modelLocation);
            if (modelPOJO == null) {
                return;
            }
            // 先判断是不是 1.10.0 版本基岩版模型文件
            if (BedrockVersion.isLegacyVersion(modelPOJO) && modelPOJO.getGeometryModelLegacy() != null) {
                index.shellModel = new BedrockAmmoModel(modelPOJO, BedrockVersion.LEGACY);
            }
            // 判定是不是 1.12.0 版本基岩版模型文件
            if (BedrockVersion.isNewVersion(modelPOJO) && modelPOJO.getGeometryModelNew() != null) {
                index.shellModel = new BedrockAmmoModel(modelPOJO, BedrockVersion.NEW);
            }
        }
    }

    private static void checkParticle(AmmoDisplay display, ClientAmmoIndex index) {
        if (display.getParticle() != null) {
            AmmoParticle particle = display.getParticle();
            String name = particle.getName();
            // 上游 ClientAmmoIndex.java:238 这里写的是无参的 StringUtils.isNoneBlank()（恒为 true），
            // 显然是 StringUtils.isNoneBlank(name) 的笔误；1.20 侧名字非法时 readParticle 会抛异常、
            // 于是 index.particle 保持 null（生成端回退到弹药物理粒子）。1.7.10 没有可校验的粒子注册表，
            // 但「名字缺失」是能判定的：这里补上 name 参数，保持「名字缺失 → 不装配粒子 → 回退」的上游语义。
            if (StringUtils.isNoneBlank(name)) {
                // TODO(port): 上游 ClientAmmoIndex.java:238 用 ParticleArgument.readParticle(...) 把名字解析为
                // 1.20 的 ParticleOptions（粒子类型 + 参数），非法名字抛 CommandSyntaxException。
                // 1.7.10 粒子只按名字字符串生成（World#spawnParticle(String, ...)，只认 path 部分），
                // 因此与 GunDisplayInstance#parseParticleName 一致：去掉可选的 namespace 前缀后保存名字。
                String particleName = name.trim();
                int colon = particleName.indexOf(':');
                if (colon >= 0) {
                    particleName = particleName.substring(colon + 1);
                }
                if (particleName.isEmpty()) {
                    return;
                }
                particle.setParticleOptions(particleName);
                Preconditions.checkArgument(particle.getCount() > 0, "particle count must be greater than 0");
                Preconditions.checkArgument(particle.getLifeTime() > 0, "particle life time must be greater than 0");
                index.particle = particle;
            }
        }
    }

    private static void checkHitSurfaceParticles(AmmoDisplay display, ClientAmmoIndex index) {
        index.hitSurfaceParticles = display.getHitSurfaceParticles();
    }

    private static void checkHitBlockParticleCount(AmmoDisplay display, ClientAmmoIndex index) {
        Integer count = display.getHitBlockParticleCount();
        index.hitBlockParticleCount = count == null ? null : Math.max(count, 0);
    }

    private static void checkBulletHoleEnabled(AmmoDisplay display, ClientAmmoIndex index) {
        index.bulletHoleEnabled = display.isBulletHoleEnabled();
    }

    private static void checkTracerColor(AmmoDisplay display, ClientAmmoIndex index) {
        String tracerColorText = display.getTracerColor();
        if (StringUtils.isNoneBlank(tracerColorText)) {
            index.tracerColor = ColorHex.colorTextToRbgFloatArray(tracerColorText);
        }
    }

    private static void checkTransform(AmmoDisplay display, ClientAmmoIndex index) {
        AmmoTransform readTransform = display.getTransform();
        if (readTransform == null || readTransform.getScale() == null) {
            index.transform = AmmoTransform.getDefault();
        } else {
            index.transform = display.getTransform();
        }
    }

    private static void checkStackSize(AmmoIndexPOJO clientPojo, ClientAmmoIndex index) {
        index.stackSize = Math.max(clientPojo.getStackSize(), 1);
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getTooltipKey() {
        return tooltipKey;
    }

    @Nullable
    public BedrockAmmoModel getAmmoModel() {
        ensureModelsLoaded();
        return ammoModel;
    }

    @Nullable
    public ResourceLocation getModelTextureLocation() {
        ensureModelsLoaded();
        return modelTextureLocation;
    }

    public ResourceLocation getSlotTextureLocation() {
        return slotTextureLocation;
    }

    public int getStackSize() {
        return stackSize;
    }

    @Nullable
    public BedrockAmmoModel getAmmoEntityModel() {
        ensureModelsLoaded();
        return ammoEntityModel;
    }

    @Nullable
    public ResourceLocation getAmmoEntityTextureLocation() {
        ensureModelsLoaded();
        return ammoEntityTextureLocation;
    }

    @Nullable
    public BedrockAmmoModel getShellModel() {
        ensureModelsLoaded();
        return shellModel;
    }

    @Nullable
    public ResourceLocation getShellTextureLocation() {
        ensureModelsLoaded();
        return shellTextureLocation;
    }

    @Nullable
    public AmmoParticle getParticle() {
        return particle;
    }

    @Nullable
    public List<HitSurfaceParticle> getHitSurfaceParticles() {
        return hitSurfaceParticles;
    }

    @Nullable
    public Integer getHitBlockParticleCount() {
        return hitBlockParticleCount;
    }

    public boolean isBulletHoleEnabled() {
        return bulletHoleEnabled;
    }

    public float[] getTracerColor() {
        return tracerColor;
    }

    public AmmoTransform getTransform() {
        return transform;
    }
}
