plugins {
    id("com.github.ElytraServers.elytra-conventions") version "v1.1.1"
    id("com.gtnewhorizons.gtnhconvention")
}

// =====================================================================================================
// 移植进行中（1.20.1 -> 1.7.10）
//
// 上游 1.20.1 的源码已全量复制到 src/main/java/com/tacz/guns/** 与 src/main/resources/**。
// 尚未完成 1.7.10 适配的包暂时排除出编译，保证 `build` 在任何阶段都可用（IDEA 仍会分析这些文件）。
// 每完成一个包的适配，就从下面的列表里删掉对应的一行。进度清单见 docs/PORT_STATUS.md。
// =====================================================================================================
// 移植进行中：尚未完成 1.7.10 适配的包可以临时排除出编译，保证 `build` 在任何阶段都可用。
// 当前**未启用任何排除**（保留真实状态）；需要临时出一个可构建版本时，把未完成的包路径取消注释即可。
// 各包的实测编译错误数见 docs/PORT_STATUS.md。
val pendingPortExcludes: List<String> = listOf(
    // "com/tacz/guns/client/**",
    // "com/tacz/guns/compat/**",
    // "com/tacz/guns/mixin/**",
    // "com/tacz/guns/block/**",
    // "com/tacz/guns/inventory/**",
    // "com/tacz/guns/crafting/**",
    // "com/tacz/guns/entity/sync/**",
)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // 移植期间需要一次性看到全部错误（默认只报 100 个），便于按包定位剩余工作量
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "5000"))
    pendingPortExcludes.forEach { exclude(it) }
}
