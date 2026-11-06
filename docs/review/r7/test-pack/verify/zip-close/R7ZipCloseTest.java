import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.tacz.guns.resource.pack.ZipGunPackSource;

/** 验证 ZipGunPackSource 的「延迟关闭」：跨 close() 的在读流不能被打断，最后一个读者离开后句柄必须真的释放。 */
public final class R7ZipCloseTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        File zip = new File("close_test.zip");
        if (zip.exists()) {
            Files.delete(zip.toPath());
        }
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("data/x/data/guns/a.json"));
            out.write("{\"k\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("data/x/data/guns/b.json"));
            out.write("{}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        ZipGunPackSource source = new ZipGunPackSource(zip.toPath());
        List<String> files = source.listFiles("data/");
        check("listFiles 命中 2 个条目", files.size() == 2, String.valueOf(files));

        InputStream stream = source.open("data/x/data/guns/a.json");
        check("open 成功", stream != null, "null");
        byte[] buffer = new byte[8];
        int first = stream.read(buffer, 0, 8);
        check("读到开头 8 字节", first == 8, String.valueOf(first));

        // 模拟并发：另一端重扫枪包，请求关闭旧 source（旧实现会立刻关掉 ZipFile）
        source.close();
        check("close() 后不再借出新流", source.open("data/x/data/guns/b.json") == null, "非 null");

        try {
            int total = first;
            int read;
            while ((read = stream.read(buffer, 0, buffer.length)) != -1) {
                total += read;
            }
            check("跨 close() 的在读流仍能读到 EOF（总字节 " + total + "）", total > 8, String.valueOf(total));
        } catch (Exception exception) {
            check("跨 close() 的在读流仍能读到 EOF", false, exception.toString());
        } finally {
            stream.close();
        }

        // 最后一个读者关闭后句柄必须真的释放：Windows 上还能删掉文件即证明
        boolean deleted;
        try {
            deleted = Files.deleteIfExists(zip.toPath());
        } catch (Exception exception) {
            deleted = false;
        }
        check("最后一个读者离开后 zip 句柄已释放（文件可删除）", deleted, "删除失败 = 句柄未释放");

        System.out.println(failures == 0 ? "RESULT: ALL PASS" : "RESULT: " + failures + " FAILURE(S)");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            System.out.println("[PASS] " + what);
        } else {
            failures++;
            System.out.println("[FAIL] " + what + "  (actual: " + detail + ")");
        }
    }
}
