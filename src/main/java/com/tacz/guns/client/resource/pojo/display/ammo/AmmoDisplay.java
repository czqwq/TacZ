package com.tacz.guns.client.resource.pojo.display.ammo;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.resource.pojo.display.IDisplay;

public class AmmoDisplay implements IDisplay {

    @SerializedName("model")
    private ResourceLocation modelLocation;

    @SerializedName("texture")
    private ResourceLocation modelTexture;

    @Nullable
    @SerializedName("slot")
    private ResourceLocation slotTextureLocation;

    @Nullable
    @SerializedName("entity")
    private AmmoEntityDisplay ammoEntity;

    @Nullable
    @SerializedName("shell")
    private ShellDisplay shellDisplay;

    @Nullable
    @SerializedName("particle")
    private AmmoParticle particle;

    @Nullable
    @SerializedName("hit_surface_particles")
    private List<HitSurfaceParticle> hitSurfaceParticles;

    @Nullable
    @SerializedName("hit_block_particle_count")
    private Integer hitBlockParticleCount;

    @SerializedName("enable_bullet_hole")
    private boolean bulletHoleEnabled = true;

    @SerializedName("tracer_color")
    private String tracerColor = "0xFFFFFF";

    @Nullable
    @SerializedName("transform")
    private AmmoTransform transform;

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    @Nullable
    public ResourceLocation getSlotTextureLocation() {
        return slotTextureLocation;
    }

    @Nullable
    public AmmoEntityDisplay getAmmoEntity() {
        return ammoEntity;
    }

    @Nullable
    public ShellDisplay getShellDisplay() {
        return shellDisplay;
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

    public String getTracerColor() {
        return tracerColor;
    }

    @Nullable
    public AmmoTransform getTransform() {
        return transform;
    }

    @Override
    public void init() {
        if (modelTexture != null) {
            modelTexture = IDisplay.idToFile(modelTexture);
        }
        if (slotTextureLocation != null) {
            slotTextureLocation = IDisplay.idToFile(slotTextureLocation);
        }
        if (ammoEntity != null && ammoEntity.modelTexture != null) {
            ammoEntity.modelTexture = IDisplay.idToFile(ammoEntity.modelTexture);
        }
        if (shellDisplay != null && shellDisplay.modelTexture != null) {
            shellDisplay.modelTexture = IDisplay.idToFile(shellDisplay.modelTexture);
        }
    }
}
