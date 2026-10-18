import android.app.usage.StorageStats;
import android.app.usage.ExternalStorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/** Read-only storage audit run as the existing ADB shell identity; installs no app. */
public final class StorageReport {
    public static void main(String[] args) throws Exception {
        // Talk to the system service as shell directly; constructing ActivityThread here
        // unnecessarily loads a UI context on a TV with only 1 GiB of memory.
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class)
                .invoke(null, "storagestats");
        Class<?> api = Class.forName("android.app.usage.IStorageStatsManager");
        Object service = Class.forName("android.app.usage.IStorageStatsManager$Stub")
                .getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
        if (args.length > 0 && args[0].equals("--summary")) {
            JSONObject summary = new JSONObject();
            StorageStats all = (StorageStats) api.getMethod("queryStatsForUser", String.class, int.class, String.class)
                    .invoke(service, null, 0, "com.android.shell");
            summary.put("all_user_app_bytes", all.getAppBytes());
            summary.put("all_user_data_bytes_including_cache", all.getDataBytes());
            summary.put("all_user_cache_bytes", all.getCacheBytes());
            ExternalStorageStats external = (ExternalStorageStats) api
                    .getMethod("queryExternalStatsForUser", String.class, int.class, String.class)
                    .invoke(service, null, 0, "com.android.shell");
            summary.put("shared_total_bytes", external.getTotalBytes());
            summary.put("shared_app_bytes_already_in_user_data", external.getAppBytes());
            summary.put("shared_audio_bytes", external.getAudioBytes());
            summary.put("shared_video_bytes", external.getVideoBytes());
            summary.put("shared_image_bytes", external.getImageBytes());
            summary.put("shared_obb_bytes", external.getClass().getMethod("getObbBytes").invoke(external));
            android.os.StatFs fs = new android.os.StatFs("/data");
            summary.put("data_filesystem_total_bytes", fs.getTotalBytes());
            summary.put("data_filesystem_available_bytes", fs.getAvailableBytes());
            summary.put("data_filesystem_free_bytes_including_reserved", fs.getFreeBytes());
            summary.put("advertised_total_bytes", api.getMethod("getTotalBytes", String.class, String.class)
                    .invoke(service, null, "com.android.shell"));
            summary.put("captured_at_ms", System.currentTimeMillis());
            System.out.println(summary.toString());
            return;
        }
        Method query = api.getMethod("queryStatsForPackage", String.class, String.class, int.class, String.class);
        JSONArray packages = new JSONArray();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String name;
        while ((name = input.readLine()) != null) {
            if (name.isEmpty()) continue;
            JSONObject row = new JSONObject();
            row.put("package", name);
            try {
                StorageStats stats = (StorageStats) query.invoke(service, null, name, 0, "com.android.shell");
                row.put("app_bytes", stats.getAppBytes());
                row.put("data_bytes_including_cache", stats.getDataBytes());
                row.put("cache_bytes", stats.getCacheBytes());
            } catch (Exception failure) {
                row.put("error", failure.toString());
            }
            packages.put(row);
        }
        JSONObject result = new JSONObject();
        result.put("captured_at_ms", System.currentTimeMillis());
        result.put("source", "StorageStatsManager live queries as ADB shell");
        result.put("packages", packages);
        System.out.println(result.toString());
    }
}
