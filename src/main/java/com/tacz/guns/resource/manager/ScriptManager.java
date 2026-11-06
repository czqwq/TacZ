package com.tacz.guns.resource.manager;

import java.io.IOException;
import java.io.Reader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;
import org.luaj.vm2.lib.jse.JseStringLib;

import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.vmlib.LuaLibrary;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.IGunPackReloadListener;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.PackDirectory;

/**
 * 枪包逻辑脚本（{@code <root>/<namespace>/scripts/*.lua}）管理器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/ScriptManager.java}，
 * 是 {@code extends SimplePreparableReloadListener<List<Map.Entry<String, Supplier<LuaTable>>>>}：
 * {@code prepare} 里初始化 lua globals、把每个脚本注册成 {@code package.preload} 条目，
 * {@code apply} 里真正求值。
 *
 * <p>
 * 1.7.10 差异：两段式折叠为同步 {@link #reload()}；脚本的读取改为按需重开
 * {@link GunPackResources.PackedResource}（gson/luaj 侧语义不变）。
 * {@code FileToIdConverter} 换成 {@link PackDirectory}（{@code ("scripts", ".lua")}）。
 */
public class ScriptManager implements IGunPackReloadListener {

    private static final Marker MARKER = MarkerManager.getMarker("ScriptLoader");
    private Globals globals;
    private final Map<String, LuaTable> scriptMap = Maps.newHashMap();
    private final PackDirectory filetoidconverter;
    private final List<LuaLibrary> libraries;
    private final String root;

    public ScriptManager(PackDirectory converter, List<LuaLibrary> libraries) {
        this(converter, libraries, GunPackResources.DATA_ROOT);
    }

    /**
     * @param root {@link GunPackResources#DATA_ROOT}（服务端）或 {@link GunPackResources#ASSETS_ROOT}（客户端）
     */
    public ScriptManager(PackDirectory converter, List<LuaLibrary> libraries, String root) {
        this.filetoidconverter = converter;
        this.libraries = libraries;
        this.root = root;
    }

    @Override
    public void reload() {
        // 初始化 globals
        initGlobals();
        // 打包加载函数，设置 globals 的 preload
        List<Map.Entry<String, Supplier<LuaTable>>> output = new ArrayList<>();
        for (Map.Entry<ResourceLocation, GunPackResources.PackedResource> entry : GunPackResources
            .scanDirectoryResources(
                GunPackLoader.INSTANCE.getPackSources(),
                root,
                filetoidconverter.prefix(),
                filetoidconverter.extension())
            .entrySet()) {
            var wrappedEntry = wrapLoadingFunction(entry.getKey(), entry.getValue());
            output.add(wrappedEntry);
            globals.get("package")
                .get("preload")
                .set(wrappedEntry.getKey(), new LuaFunction() {

                    @Override
                    public LuaValue call(LuaValue modname, LuaValue env) {
                        return wrappedEntry.getValue()
                            .get();
                    }
                });
        }

        scriptMap.clear();
        output.forEach(
            entry -> scriptMap.put(
                entry.getKey(),
                entry.getValue()
                    .get()));
    }

    private Map.Entry<String, Supplier<LuaTable>> wrapLoadingFunction(ResourceLocation resourceLocation,
        GunPackResources.PackedResource resource) {
        String moduleName = getModuleName(resourceLocation);
        return new AbstractMap.SimpleEntry<>(moduleName, new Supplier<>() {

            private LuaTable loaded = null;

            @Override
            public LuaTable get() {
                if (loaded != null) {
                    return loaded;
                }
                try (Reader reader = GunPackResources.openReader(resource.source(), resource.path())) {
                    if (reader == null) {
                        GunMod.LOGGER.warn(MARKER, "Failed to read script file: {}", resourceLocation);
                        return null;
                    }
                    LuaValue chunk = globals.load(reader, moduleName);
                    loaded = chunk.call()
                        .checktable(1);
                    return loaded;
                } catch (IllegalArgumentException | IOException | LuaError exception) {
                    GunMod.LOGGER.warn(MARKER, "Failed to read script file: {}", resourceLocation);
                }
                return null;
            }
        });
    }

    private void initGlobals() {
        globals = secureStandardGlobals();
        // LuaJC.install(globals);
        if (libraries != null) {
            libraries.forEach(library -> library.install(globals));
        }
    }

    private String getModuleName(ResourceLocation resourceLocation) {
        // 1.7.10 差异：ResourceLocation 没有 getNamespace()/getPath()，对应物是
        // getResourceDomain()/getResourcePath()
        return resourceLocation.getResourceDomain() + "_" + resourceLocation.getResourcePath();
    }

    public @Nullable LuaTable getScript(ResourceLocation id) {
        return scriptMap.get(getModuleName(id));
    }

    private static Globals secureStandardGlobals() {
        Globals globals = new Globals();
        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new JseStringLib());
        // No CoroutineLib
        globals.load(new JseMathLib());
        // No JseIoLib
        // No JseOsLib
        // No LuajavaLib
        LoadState.install(globals);
        LuaC.install(globals);
        return globals;
    }
}
