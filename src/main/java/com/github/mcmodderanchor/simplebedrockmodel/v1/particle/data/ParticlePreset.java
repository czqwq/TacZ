// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/ParticlePreset.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IEmitterComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IEmitterComponentDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IParticleComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IParticleComponentDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceLighting;

/**
 * 粒子组件预设。从 {@link ParticleEffectDefinition} 构建，不可变。
 * <p>
 * 存储 {@link IParticleComponentDefinition}，按 {@code order()} 排序。
 */
public final class ParticlePreset {

    private final List<IParticleComponentDefinition> components;
    private final List<IParticleComponentDefinition> updateComponents;
    private final Map<Class<? extends IComponent>, IComponent> componentMap;

    private final ParticleAppearanceBillboard.FaceCameraMode faceCameraMode;
    private final boolean environmentLighting;

    public ParticlePreset(List<IParticleComponentDefinition> components) {
        List<IParticleComponentDefinition> sorted = new ArrayList<>(components);
        sorted.sort(Comparator.comparingInt(IParticleComponentDefinition::order));
        this.components = Collections.unmodifiableList(sorted);

        this.updateComponents = this.components.stream()
            .filter(IParticleComponentDefinition::requireUpdate)
            .toList();

        Map<Class<? extends IComponent>, IComponent> map = new HashMap<>();
        for (IParticleComponentDefinition c : this.components) {
            map.put(c.getClass(), c);
            for (Class<?> iface : c.getClass()
                .getInterfaces()) {
                if (IComponent.class.isAssignableFrom(iface) && iface != IComponent.class
                    && iface != IParticleComponent.class
                    && iface != IEmitterComponent.class
                    && iface != IParticleComponentDefinition.class
                    && iface != IEmitterComponentDefinition.class) {
                    @SuppressWarnings("unchecked")
                    Class<? extends IComponent> parentType = (Class<? extends IComponent>) iface;
                    map.putIfAbsent(parentType, c);
                }
            }
        }
        this.componentMap = Collections.unmodifiableMap(map);

        ParticleAppearanceBillboard billboard = find(ParticleAppearanceBillboard.class);
        this.faceCameraMode = billboard != null ? billboard.faceCameraMode()
            : ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ;

        this.environmentLighting = find(ParticleAppearanceLighting.class) != null;
    }

    public List<IParticleComponentDefinition> components() {
        return components;
    }

    public List<IParticleComponentDefinition> updateComponents() {
        return updateComponents;
    }

    @SuppressWarnings("unchecked")
    public <T extends IComponent> T find(Class<T> type) {
        return (T) componentMap.get(type);
    }

    public ParticleAppearanceBillboard.FaceCameraMode faceCameraMode() {
        return faceCameraMode;
    }

    public boolean environmentLighting() {
        return environmentLighting;
    }
}
