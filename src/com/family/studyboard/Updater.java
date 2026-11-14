package com.family.studyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * App 自更新：从 GitHub Releases 的 latest 下载 update.json + StudyBoard.apk，
 * 用 root 静默安装。装完因为本 App 是默认桌面，系统会自动重新拉起新的版本。
 */
public final class Updater {

    /** 更新源根地址：末尾必须带 /。留空则用这个默认值。 */
    public static final String DEFAULT_BASE =
            "https://github.com/CHANGE_ME/studyboard/releases/latest/download/";

    private static final String TAG = "StudyUpdater";
    private static final String SP = "studyboard";
    private static final String KEY_BASE = "update_base";
    private static final String KEY_LAST = "update_last_check";
    private static final String KEY_AUTO = "update_auto";
    /** 自动检查间隔：6 小时 */
    private static final long INTERVAL_MS = 6L * 3600 * 1000;

    /** 回调在主线程。status: skip / uptodate / updated / error */
    public interface Callback {
        void onResult(String status, String detail);
    }

    private Updater() {
    }

    // ---------- 配置 ----------

    public static String base(Context c) {
        String v = sp(c).getString(KEY_BASE, "");
        if (v == null) v = "";
        v = v.trim();
        if (v.length() == 0) return DEFAULT_BASE;
        if (!v.endsWith("/")) v = v + "/";
        return v;
    }

    public static void setBase(Context c, String v) {
        sp(c).edit().putString(KEY_BASE, v == null ? "" : v.trim()).apply();
    }

    public static boolean autoEnabled(Context c) {
        return sp(c).getBoolean(KEY_AUTO, true);
    }

    public static void setAuto(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_AUTO, on).apply();
    }

    public static long lastCheck(Context c) {
        return sp(c).getLong(KEY_LAST, 0);
    }

    public static int currentCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static String lastCheckText(Context c) {
        long t = lastCheck(c);
        if (t <= 0) return "从未检查";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm");
        return f.format(new java.util.Date(t));
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    // ---------- 检查 ----------

    /** 到间隔才检查；开机时调一次即可 */
    public static void autoCheck(Context ctx, Callback cb) {
        Context c = ctx.getApplicationContext();
        if (!autoEnabled(c)) {
            if (cb != null) cb.onResult("skip", "自动更新已关闭");
            return;
        }
        if (System.currentTimeMillis() - lastCheck(c) < INTERVAL_MS) {
            if (cb != null) cb.onResult("skip", "距上次检查不到 6 小时");
            return;
        }
        check(ctx, cb);
    }

    /** 无视间隔，立刻检查 */
    public static void check(final Context ctx, final Callback cb) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String status;
                String detail;
                try {
                    String base = base(c);
                    String json = fetchText(base + "update.json");
                    JSONObject o = new JSONObject(json);
                    int remoteCode = o.optInt("versionCode", 0);
                    String remoteName = o.optString("versionName", "?");
                    String apkName = o.optString("apk", "StudyBoard.apk");
                    int cur = currentCode(c);
                    if (remoteCode <= cur) {
                        status = "uptodate";
                        detail = "已是最新 v" + cur + "，" + "服务器 v" + remoteName;
                    } else {
                        File apk = new File(c.getCacheDir(), "update.apk");
                        download(base + apkName, apk);
                        boolean ok = installDetached(c, apk);
                        status = ok ? "updated" : "error";
                        detail = ok
                                ? ("正在安装 v" + remoteName + " (" + remoteCode + ")，随后自动重启")
                                : "下载成功但 root 安装失败，请检查 Magisk 授权";
                    }
                } catch (Throwable t) {
                    status = "error";
                    detail = t.getClass().getSimpleName() + ": " + t.getMessage();
                    Log.w(TAG, "check failed", t);
                }
                sp(c).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
                final String s = status;
                final String d = detail;
                if (cb != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(s, d);
                        }
                    });
                }
            }
        }, "updater").start();
    }

    // ---------- 网络 ----------

    private static String fetchText(String url) throws Exception {
        HttpURLConnection conn = open(url);
        InputStream in = conn.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        conn.disconnect();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static void download(String url, File out) throws Exception {
        HttpURLConnection conn = open(url);
        int len = conn.getContentLength();
        InputStream in = conn.getInputStream();
        OutputStream os = new FileOutputStream(out);
        byte[] buf = new byte[16384];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0) {
            os.write(buf, 0, n);
            total += n;
        }
        os.flush();
        os.close();
        in.close();
        conn.disconnect();
        if (total < 4096) throw new Exception("下载的文件过小（" + total + " 字节），下载地址可能不对");
        if (len > 0 && total != len) throw new Exception("下载不完整：" + total + "/" + len);
        Log.i(TAG, "downloaded " + total + " bytes -> " + out.getAbsolutePath());
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "StudyBoard-Updater");
        conn.setRequestProperty("Cache-Control", "no-cache");
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new Exception("HTTP " + code + "：" + url);
        }
        return conn;
    }

    // ---------- 安装 ----------

    /**
     * 用 root 后台安装。放到子 shell 里执行并脱离当前进程组，
     * 因为 pm install 会杀掉本 App 进程，不脱离的话后续的 am start 会跟着一起死。
     */
    private static boolean installDetached(Context c, File apk) {
        try {
            String script = "( pm install -r -d '" + apk.getAbsolutePath() + "'"
                    + " > /data/local/tmp/sb_update.log 2>&1;"
                    + " echo EXIT=$? >> /data/local/tmp/sb_update.log;"
                    + " sleep 3;"
                    + " am start -n " + c.getPackageName() + "/.MainActivity"
                    + " >> /data/local/tmp/sb_update.log 2>&1 ) &";
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", script});
            int rc = p.waitFor();
            Log.i(TAG, "su launch rc=" + rc);
            return rc == 0;
        } catch (Exception e) {
            Log.w(TAG, "install failed", e);
            return false;
        }
    }
}
