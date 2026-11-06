// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/exclusion/NullAdapter.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class NullAdapter<T> extends TypeAdapter<T> {

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        out.nullValue(); // 序列化时直接输出 null
    }

    @Override
    public T read(JsonReader in) throws IOException {
        // 跳过整个 JSON 值，然后返回 null
        in.skipValue();
        return null;
    }
}
