package com.tacz.guns.mixin.client;

import net.minecraft.client.resources.Locale;

import org.spongepowered.asm.mixin.Mixin;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/LanguageMixin.java}（<b>上游注入就是注释状态，本类为可加载空 Mixin</b>）。
 *
 * <p>
 * <b>上游用途</b>：上游本来想 mixin {@code ClientLanguage#getOrDefault} / {@code ClientLanguage#has}，
 * 把枪包（GunPack）的语言表插进原版语言查询链，让枪包里的 {@code tacz.gun.*} / {@code tacz.ammo.*} 能显示。
 * <b>但上游这两个 {@code @Inject} 本来就是整段注释掉的</b>
 * （{@code LanguageMixin.java:9-32}），且它们引用的 {@code ClientAssetManager#getLanguages} 在
 * 上游 1.2.0 里已经不存在 —— 也就是说这个 Mixin 在上游是<b>死代码</b>。
 *
 * <p>
 * <b>1.7.10 等价改写</b>：
 * <ul>
 * <li>{@code net.minecraft.client.resources.language.ClientLanguage}（1.13+）在 1.7.10 对应
 * {@code net.minecraft.client.resources.Locale}（{@code Locale.java:106-126} 的
 * {@code loadLocaleData(InputStream)} 用真 UTF-8 读 lang 文件）与
 * {@code net.minecraft.client.resources.LanguageManager}。</li>
 * <li>因为上游本身就是死代码，本类<b>不恢复任何注入</b>：只把 {@code @Mixin} 目标换成 1.7.10 的
 * {@code Locale}，注解体保留并注释，保持「文件在、位置在、说明在」。</li>
 * </ul>
 *
 * <p>
 * <b>枪包语言在 1.7.10 的正确落点（已裁决，不走 Mixin）</b>：
 * 见 {@code docs/ASSET_PORT_NOTES.md} §5.1 与 {@code docs/PORT_STATUS.md} 的裁决记录 ——
 * 走 {@code cpw.mods.fml.common.registry.LanguageRegistry} 注入
 * （{@code addStringLocalization(Properties, String)} / {@code injectLanguage(String, HashMap)} /
 * {@code loadLocalization(URL, String, boolean)}），由 {@code common-resource} / {@code common-infra} 负责。
 * 因此本 Mixin <b>不需要</b>生效。
 *
 * <p>
 * <b>生效状态</b>：可安全加载的空 Mixin（无任何注入，不改变行为）。
 * 若 lead 希望 {@code mixins.tacz.json} 只列「真正起作用」的 Mixin，可排除本类。
 */
@Mixin(Locale.class)
public class LanguageMixin {
    // 上游（注释状态，原样保留）：
    //
    // @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
    // at = @At(value = "HEAD"), cancellable = true)
    // public void getCustomLanguage(String key, String defaultValue, CallbackInfoReturnable<String> call) {
    // String code = Minecraft.getInstance().getLanguageManager().getSelected();
    // Map<String, String> languages = ClientAssetManager.INSTANCE.getLanguages(code);
    // Map<String, String> alternative = ClientAssetManager.INSTANCE.getLanguages("en_us");
    // if (languages != null && languages.containsKey(key)) {
    // call.setReturnValue(languages.get(key));
    // } else if (alternative != null && alternative.containsKey(key)) {
    // call.setReturnValue(alternative.get(key));
    // }
    // }
    //
    // @Inject(method = "has(Ljava/lang/String;)Z", at = @At(value = "HEAD"), cancellable = true)
    // public void hasCustomLanguage(String key, CallbackInfoReturnable<Boolean> call) {
    // ... 同上 ...
    // }
    //
    // 1.7.10 对应位置（若将来真的要启用）：
    // Locale#translateKeyPrivate(String)（Locale.java:131，私有，做 key→value 查表）
    // Locale#formatMessage(String, Object[])（Locale.java:140，公开入口）
    // 由于语言注入已改走 LanguageRegistry，此处保持空实现。
}
