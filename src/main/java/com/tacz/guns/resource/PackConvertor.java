package com.tacz.guns.resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.time.StopWatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.resource.pojo.PackInfo;

/**
 * 旧版枪包（{@code <ns>/pack.json} 布局）到新版（{@code assets|data/<ns>/…} + {@code gunpack.meta.json}）的转换器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/PackConvertor.java}。
 *
 * <p>
 * <b>1.7.10 差异</b>（均为 API 换名，转换语义完全不变）：
 * <ul>
 * <li>{@code CommandSourceStack#sendSystemMessage} → {@link ICommandSender#addChatMessage(IChatComponent)}；</li>
 * <li>{@code Component#translatable} → {@link ChatComponentTranslation}，{@code append} → {@code appendSibling}；</li>
 * <li>{@code FMLPaths.GAMEDIR.get().resolve("tacz")} → {@link GunPackLoader#getPackDirectory()}；</li>
 * <li>json 解析改用 gson 2.2.4 可用的 API（{@code fromJson} / {@code toJson}，无 {@code JsonParser.parseReader}）。</li>
 * </ul>
 */
public class PackConvertor {

    public static final Path FOLDER = Paths.get("config", GunMod.MOD_ID, "custom");
    public static final Pattern PACK_INFO_PATTERN = Pattern.compile("^(\\w+)/pack\\.json$");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static IChatComponent pre(IChatComponent component) {
        return new ChatComponentTranslation("message.tacz.pre").appendSibling(component);
    }

    private static void msg(ICommandSender source, IChatComponent component) {
        source.addChatMessage(pre(component));
    }

    public static void convert(ICommandSender source) {
        Path resourcePacksPath = GunPackLoader.getPackDirectory();
        File folder = resourcePacksPath.toFile();
        if (!folder.isDirectory()) {
            try {
                Files.createDirectories(folder.toPath());
            } catch (Exception e) {
                msg(source, new ChatComponentTranslation("message.tacz.converter.init_failed"));
                GunMod.LOGGER.error("Failed to init tacz directory...", e);
                return;
            }
        }

        File[] files = FOLDER.toFile()
            .listFiles();
        int cnt = 0;
        int skip = 0;
        int error = 0;
        if (files != null && files.length > 0) {
            StopWatch watch = new StopWatch();
            watch.start();
            {
                msg(source, new ChatComponentTranslation("message.tacz.converter.start"));
                GunMod.LOGGER.info("Start converting legacy packs...");
                for (File file : files) {
                    if (file.isFile() && file.getName()
                        .endsWith(".zip")) {
                        PackConvertor.LegacyPack pack = fromZipFile(file);
                        if (pack != null) {
                            msg(
                                source,
                                new ChatComponentTranslation("message.tacz.converter.pack.start", file.getName()));
                            GunMod.LOGGER.info("Attempt to converting legacy pack: {}", file.getName());
                            try {
                                pack.convert();
                            } catch (FileAlreadyExistsException e) {
                                msg(source, new ChatComponentTranslation("message.tacz.converter.pack.exist"));
                                GunMod.LOGGER.warn("Target file already exists: {}", file.getName());
                                skip++;
                                continue;
                            } catch (Exception e) {
                                msg(
                                    source,
                                    new ChatComponentTranslation("message.tacz.converter.pack.failed", file.getName()));
                                GunMod.LOGGER.error("Failed to convert legacy pack: {}", file.getName(), e);
                                error++;
                                continue;
                            }
                            cnt++;
                            msg(
                                source,
                                new ChatComponentTranslation("message.tacz.converter.pack.finish", file.getName()));
                            GunMod.LOGGER.info("Legacy pack converted: {}", file.getName());
                        }
                    } else {
                        msg(source, new ChatComponentTranslation("message.tacz.converter.pack.folder", file.getName()));
                        GunMod.LOGGER.warn("Skip folder: {}", file.getName());
                        skip++;
                    }
                }
            }
            watch.stop();
            double time = watch.getTime();
            msg(source, new ChatComponentTranslation("message.tacz.converter.finish", time, cnt, skip, error));
            GunMod.LOGGER.info(
                "Convert finished! Total time: {} ms. Success : {}. Skipped {}. Failed: {}. Restart the game to load new packs!",
                time,
                cnt,
                skip,
                error);
        }
    }

    public static PackConvertor.LegacyPack fromZipFile(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            var iteration = zipFile.entries();
            while (iteration.hasMoreElements()) {
                String path = iteration.nextElement()
                    .getName();
                Matcher matcher = PACK_INFO_PATTERN.matcher(path);

                if (matcher.find()) {
                    String namespace = matcher.group(1);
                    ZipEntry entry = zipFile.getEntry(path);
                    try (InputStream stream = zipFile.getInputStream(entry)) {
                        PackInfo info = GSON
                            .fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PackInfo.class);
                        if (info != null) {
                            return new PackConvertor.LegacyPack(file, namespace, info);
                        }
                    } catch (IOException | JsonParseException exception) {
                        GunMod.LOGGER.warn(exception.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public record LegacyPack(File file, String namespace, PackInfo info) {

        private static final Pattern DISPLAY_PATTERN = Pattern.compile("^(\\w+)/(\\w+)/display/([\\w/.]+)$");
        private static final Pattern INDEX_PATTERN = Pattern.compile("^(\\w+)/(\\w+)/index/([\\w/.]+)$");
        private static final Pattern DATA_PATTERN = Pattern.compile("^(\\w+)/(\\w+)/data/([\\w/.]+)$");
        private static final Pattern MODELS_PATTERN = Pattern.compile("^(\\w+)/models/([\\w/.]+)$");
        private static final Pattern LANG_PATTERN = Pattern.compile("^(\\w+)/lang/([\\w/.]+)$");
        private static final Pattern ANIMATION_PATTERN = Pattern.compile("^(\\w+)/animations/([\\w/.]+)$");
        private static final Pattern TEXTURE_PATTERN = Pattern.compile("^(\\w+)/textures/([\\w/.]+)$");
        private static final Pattern SOUND_PATTERN = Pattern.compile("^(\\w+)/sounds/([\\w/.]+)$");
        private static final Pattern PLAYER_ANIMATOR_PATTERN = Pattern.compile("^(\\w+)/player_animator/([\\w/.]+)$");
        private static final Pattern TAGS_PATTERN = Pattern.compile("^(\\w+)/tags/([\\w/.]+)$");
        private static final Pattern RECIPE_PATTERN = Pattern.compile("^(\\w+)/recipes/([\\w/.]+)$");
        private static final Pattern PACK_INFO = Pattern.compile("^(\\w+)/pack\\.json$");

        private boolean parsePackInfo(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = PACK_INFO.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String newPath = "assets/" + namespace + "/gunpack_info.json";
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseRecipe(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = RECIPE_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String path = matcher.group(2);
                String newPath = "data/" + namespace + "/recipes/" + path;

                try (InputStream stream = oldPack.getInputStream(entry)) {
                    JsonObject object = GSON
                        .fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                    if (object != null) {
                        object.addProperty("type", "tacz:gun_smith_table_crafting");
                        newZip.putNextEntry(new ZipEntry(newPath));
                        newZip.write(
                            GSON.toJson(object)
                                .getBytes());
                        newZip.closeEntry();
                    }
                } catch (JsonParseException e) {
                    GunMod.LOGGER.warn("Failed to parse recipe to new style: {}", entry.getName());
                }
                return true;
            }
            return false;
        }

        private boolean parseTags(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = TAGS_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String path = matcher.group(2);
                String newPath = "data/" + namespace + "/tacz_tags/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parsePlayerAnimator(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack)
            throws IOException {
            Matcher matcher = PLAYER_ANIMATOR_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String path = matcher.group(2);
                String newPath = "assets/" + namespace + "/player_animator/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseSound(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = SOUND_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String path = matcher.group(2);
                String newPath = "assets/" + namespace + "/tacz_sounds/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseTexture(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = TEXTURE_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String path = matcher.group(2);
                String newPath = "assets/" + namespace + "/textures/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseAnimation(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = ANIMATION_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String path = matcher.group(2);
                String newPath = "assets/" + namespace + "/animations/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseLang(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = LANG_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String path = matcher.group(2);
                String newPath = "assets/" + namespace + "/lang/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseModels(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = MODELS_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String path = matcher.group(2);
                String newPath = "assets/" + namespace + "/geo_models/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseDisplay(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = DISPLAY_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String type = matcher.group(2);
                String path = matcher.group(3);
                String newPath = "assets/" + namespace + "/display/" + type + "/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseIndex(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = INDEX_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String type = matcher.group(2);
                String path = matcher.group(3);
                String newPath = "data/" + namespace + "/index/" + type + "/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private boolean parseData(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack) throws IOException {
            Matcher matcher = DATA_PATTERN.matcher(entry.getName());
            if (matcher.find()) {
                String namespace = matcher.group(1);
                String type = matcher.group(2);
                String path = matcher.group(3);
                String newPath = "data/" + namespace + "/data/" + type + "/" + path;
                writeEntry(newZip, entry, oldPack, newPath);
                return true;
            }
            return false;
        }

        private void writeEntry(ZipOutputStream newZip, ZipEntry entry, ZipFile oldPack, String newPath)
            throws IOException {
            newZip.putNextEntry(new ZipEntry(newPath));

            try (InputStream is = oldPack.getInputStream(entry)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    newZip.write(buffer, 0, len);
                }
            }
            newZip.closeEntry();
        }

        private void addMeta(ZipOutputStream newZip) throws IOException {
            ZipEntry entry = new ZipEntry("gunpack.meta.json");

            newZip.putNextEntry(entry);
            PackMeta meta = new PackMeta(namespace, null);
            newZip.write(
                GSON.toJson(meta)
                    .getBytes());
            newZip.closeEntry();
        }

        public void convert() throws FileAlreadyExistsException {
            Path newPath = GunPackLoader.getPackDirectory();
            String newName = file.getName()
                .replace(".zip", "") + "_converted.zip";
            File newFile = new File(newPath.toFile(), newName);

            if (newFile.isFile() && newFile.exists()) {
                throw new FileAlreadyExistsException("File already exists: " + newFile.getName());
            }

            try (ZipFile oldPack = new ZipFile(file);
                FileOutputStream outputStream = new FileOutputStream(newFile);
                ZipOutputStream newZip = new ZipOutputStream(outputStream)) {

                addMeta(newZip);

                var iteration = oldPack.entries();
                while (iteration.hasMoreElements()) {
                    ZipEntry entry = iteration.nextElement();
                    if (parseDisplay(newZip, entry, oldPack)) continue;
                    if (parseIndex(newZip, entry, oldPack)) continue;
                    if (parseData(newZip, entry, oldPack)) continue;
                    if (parseModels(newZip, entry, oldPack)) continue;
                    if (parseLang(newZip, entry, oldPack)) continue;
                    if (parseAnimation(newZip, entry, oldPack)) continue;
                    if (parseTexture(newZip, entry, oldPack)) continue;
                    if (parseSound(newZip, entry, oldPack)) continue;
                    if (parsePlayerAnimator(newZip, entry, oldPack)) continue;
                    if (parseTags(newZip, entry, oldPack)) continue;
                    if (parseRecipe(newZip, entry, oldPack)) continue;
                    if (parsePackInfo(newZip, entry, oldPack)) continue;
                }
            } catch (IOException e) {
                GunMod.LOGGER.warn("Failed to convert pack: {}", file.getName());
            }
        }
    }
}
