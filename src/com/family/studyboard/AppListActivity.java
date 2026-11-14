package com.family.studyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 应用管理：用 root 停用 / 启用任意应用 */
public class AppListActivity extends Activity {

    private static final int BG = 0xFF0B1A2B;
    private static final int CARD = 0xFF12263D;
    private static final int TXT = 0xFFFFFFFF;
    private static final int SUB = 0xFF6B8BAE;

    /** 绝对不能停的包（停了自己就没救） */
    private static final String[] PROTECT = {
            "com.family.studyboard",
            "com.estrongs.android.pop.pro",
            "com.estrongs.android.pop",
            "com.dangbei.tvlauncher",
            "com.topjohnwu.magisk",
            "android"
    };

    private LinearLayout listBox;
    private TextView stat;
    private int filter = 0; // 0=已启用 1=已停用 2=全部

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = tv("应用管理", 19, TXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        Button f0 = smallBtn("已启用");
        f0.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { filter = 0; refresh(); }
        });
        head.addView(f0);
        Button f1 = smallBtn("已停用");
        f1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { filter = 1; refresh(); }
        });
        head.addView(f1);
        Button f2 = smallBtn("全部");
        f2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { filter = 2; refresh(); }
        });
        head.addView(f2);
        Button back = smallBtn("返回");
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        head.addView(back);

        root.addView(head);

        stat = tv("", 12, SUB, false);
        stat.setPadding(0, dp(6), 0, dp(6));
        root.addView(stat);

        ScrollView sc = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        sc.addView(listBox, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        refresh();
    }

    private void refresh() {
        listBox.removeAllViews();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> all = pm.getInstalledApplications(0);
        List<ApplicationInfo> show = new ArrayList<ApplicationInfo>();
        int enabledCount = 0, disabledCount = 0;

        for (ApplicationInfo ai : all) {
            boolean en = isEnabled(ai.packageName);
            if (en) enabledCount++; else disabledCount++;
            if (filter == 0 && !en) continue;
            if (filter == 1 && en) continue;
            show.add(ai);
        }

        Collections.sort(show, new Comparator<ApplicationInfo>() {
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                String na = String.valueOf(a.loadLabel(getPackageManager()));
                String nb = String.valueOf(b.loadLabel(getPackageManager()));
                return na.compareToIgnoreCase(nb);
            }
        });

        stat.setText("已启用 " + enabledCount + " 个　已停用 " + disabledCount + " 个　共 " + all.size() + " 个");

        for (final ApplicationInfo ai : show) {
            final boolean en = isEnabled(ai.packageName);
            final String pkg = ai.packageName;
            boolean prot = protectedPkg(pkg);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundColor(CARD);
            row.setPadding(dp(12), dp(7), dp(12), dp(7));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = dp(4);
            row.setLayoutParams(rlp);

            String label = String.valueOf(ai.loadLabel(pm));
            TextView name = tv((en ? "" : "【已停用】") + label, 14, en ? TXT : 0xFFFF8A80, true);
            row.addView(name);
            TextView sub = tv(pkg, 11, SUB, false);
            row.addView(sub);
            if (prot) {
                TextView p = tv("受保护，不建议停用", 10, 0xFFFFD786, false);
                row.addView(p);
            }

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    confirmToggle(pkg, String.valueOf(ai.loadLabel(getPackageManager())), en);
                }
            });
            listBox.addView(row);
        }
    }

    private void confirmToggle(final String pkg, String label, final boolean currentlyEnabled) {
        String msg = currentlyEnabled
                ? "停用「" + label + "」？\n\n停用后应用不再运行、图标消失，随时可在此恢复。"
                : "重新启用「" + label + "」？";
        new AlertDialog.Builder(this)
                .setTitle(currentlyEnabled ? "停用应用" : "启用应用")
                .setMessage(msg + "\n\n包名：" + pkg)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String cmd = currentlyEnabled
                                ? "pm disable-user --user 0 " + pkg
                                : "pm enable " + pkg;
                        String r = runRoot(cmd);
                        String low = r == null ? "" : r.toLowerCase();
                        boolean ok = r != null && (r.length() == 0 || low.contains("new state")
                                || low.contains("success") || low.contains("disabled") || low.contains("enabled"));
                        Toast.makeText(AppListActivity.this,
                                (ok ? "已执行：" : "返回：") + (r.length() == 0 ? "(无输出)" : r),
                                Toast.LENGTH_LONG).show();
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean isEnabled(String pkg) {
        try {
            int s = getPackageManager().getApplicationEnabledSetting(pkg);
            return s != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    && s != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean protectedPkg(String pkg) {
        for (String p : PROTECT) if (p.equals(pkg)) return true;
        return false;
    }

    private String runRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            long deadline = System.currentTimeMillis() + 12000;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
                if (System.currentTimeMillis() > deadline) break;
            }
            BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = er.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "失败: " + e.getMessage();
        }
    }

    private TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button smallBtn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(36));
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
