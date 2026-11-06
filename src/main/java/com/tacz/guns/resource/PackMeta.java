package com.tacz.guns.resource;

import java.util.HashMap;

import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;

public class PackMeta {

    @SerializedName("namespace")
    private String name;

    @SerializedName("dependencies")
    private HashMap<String, String> dependencies = Maps.newHashMap();

    public PackMeta(String name, HashMap<String, String> dependencies) {
        this.name = name;
        this.dependencies = dependencies;
    }

    public HashMap<String, String> getDependencies() {
        return dependencies;
    }

    public String getName() {
        return name;
    }
}
