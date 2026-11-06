// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/resource/RawResourceLoader.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.resource;

import java.io.InputStream;

public interface RawResourceLoader {

    <T> T load(InputStream inputStream, Class<T> clazz);
}
