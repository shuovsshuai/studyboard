package com.family.studyboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private static final int BG = 0xFF0B1A2B;
    private static final int TXT = 0xFFFFFFFF;
    private static final int SUB = 0xFF6B8BAE;

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private String runRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            long deadline = System.currentTimeMillis() + 15000;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
                if (System.currentTimeMillis() > deadline) break;
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "失败: " + e.getMessage();
        }
    }

    /** 启动指定包的桌面界面 */
    private boolean launchPkg(String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            try {
                Intent i2 = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i2 != null) {
                    i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i2);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** 找出指定包里声明了 HOME 的组件名 */
    private String findHomeComponent(String pkg) {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        java.util.List<android.content.pm.ResolveInfo> list =
                getPackageManager().queryIntentActivities(home, 0);
        for (android.content.pm.ResolveInfo ri : list) {
            if (ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName)) {
                return pkg + "/" + ri.activityInfo.name;
            }
        }
        return null;
    }

    /** 退出到指定桌面：优先用 root 显式启动，保证一定切过去 */
    private void exitToLauncher(String... pkgs) {
        for (String pkg : pkgs) {
            String comp = findHomeComponent(pkg);
            if (comp == null) continue;
            // 1) root 显式 am start
            String r = runRoot("am start -a android.intent.action.MAIN -c android.intent.category.HOME -n " + comp);
            if (r != null && !r.contains("Error") && !r.contains("Exception")
                    && !r.contains("失败") && !r.contains("Permission")) {
                Toast.makeText(this, "已切到桌面，点「学习看板」图标可回来", Toast.LENGTH_LONG).show();
                return;
            }
            // 2) 普通显式启动
            try {
                String[] parts = comp.split("/");
                String cls = parts[1].startsWith(".") ? parts[0] + parts[1] : parts[1];
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                i.setClassName(parts[0], cls);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                Toast.makeText(this, "已切到桌面，点「学习看板」图标可回来", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, "没找到可用的桌面程序", Toast.LENGTH_LONG).show();
    }

    /** 回到当前系统桌面；若自己已被设为默认，则退回当贝/小度 */
    private void goSystemHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = getPackageManager()
                    .resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            String cur = ri == null ? "" : ri.activityInfo.packageName;
            if (cur.equals(getPackageName())) {
                if (launchPkg("com.dangbei.tvlauncher")) return;
                if (launchPkg("com.baidu.launcher")) return;
                Toast.makeText(this, "没找到其他桌面", Toast.LENGTH_LONG).show();
                return;
            }
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Exception e) {
            if (launchPkg("com.dangbei.tvlauncher")) return;
            if (launchPkg("com.baidu.launcher")) return;
            Toast.makeText(this, "退出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildPin());
    }

    private TextView label(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        return t;
    }

    private EditText input(String hint, int lines, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(SUB);
        e.setTextColor(TXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setInputType(type);
        e.setBackgroundColor(0xFF12263D);
        e.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
        if (lines > 1) {
            e.setMinLines(lines);
            e.setGravity(Gravity.TOP | Gravity.LEFT);
            e.setSingleLine(false);
        }
        return e;
    }

    private View buildPin() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setGravity(Gravity.CENTER);
        root.setPadding((int) dp(40), (int) dp(20), (int) dp(40), (int) dp(20));

        root.addView(label("家长设置", 22, TXT));
        TextView tip = label("输入密码后进入", 13, SUB);
        tip.setPadding(0, (int) dp(6), 0, (int) dp(14));
        root.addView(tip);

        final EditText pin = input("4位密码", 1, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        root.addView(pin, new LinearLayout.LayoutParams((int) dp(240), -2));

        Button ok = new Button(this);
        ok.setText("确定");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams((int) dp(160), (int) dp(48));
        blp.topMargin = (int) dp(16);
        ok.setLayoutParams(blp);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pin.getText().toString().equals(PlanStore.getPin(SettingsActivity.this))) {
                    setContentView(buildSettings());
                } else {
                    Toast.makeText(SettingsActivity.this, "密码不对", Toast.LENGTH_SHORT).show();
                    pin.setText("");
                }
            }
        });
        root.addView(ok);

        Button cancel = new Button(this);
        cancel.setText("返回看板");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams((int) dp(160), (int) dp(44));
        clp.topMargin = (int) dp(6);
        cancel.setLayoutParams(clp);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(cancel);

        return root;
    }

    private View buildSettings() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) dp(20), (int) dp(14), (int) dp(20), (int) dp(20));

        root.addView(label("计划表", 18, TXT));
        TextView help = label("每行一项，格式：开始-结束 名称|备注|提醒语\n例如：\n07:30-08:00 早读|大声读课文|该早读啦，把书翻到昨天那一页\n"
                + "备注和提醒语可以留空，但竖线要保留", 12, SUB);
        help.setPadding(0, (int) dp(6), 0, (int) dp(10));
        root.addView(help);

        final EditText plan = input("", 12, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        plan.setText(PlanStore.getPlanText(this));
        root.addView(plan, new LinearLayout.LayoutParams(-1, (int) dp(210)));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(0, (int) dp(12), 0, 0);

        row2.addView(label("提前提醒(分钟)", 13, TXT));
        final EditText ahead = input("5", 1, InputType.TYPE_CLASS_NUMBER);
        ahead.setText(String.valueOf(PlanStore.getAhead(this)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams((int) dp(70), -2);
        alp.leftMargin = (int) dp(10);
        row2.addView(ahead, alp);

        row2.addView(label("   新密码", 13, TXT));
        final EditText np = input("不改则留空", 1, InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams((int) dp(110), -2);
        nlp.leftMargin = (int) dp(10);
        row2.addView(np, nlp);

        root.addView(row2);

        Button save = new Button(this);
        save.setText("保存并回看板");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int) dp(200), (int) dp(50));
        slp.topMargin = (int) dp(16);
        save.setLayoutParams(slp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = plan.getText().toString().trim();
                if (PlanStore.parse(text).size() == 0) {
                    Toast.makeText(SettingsActivity.this, "格式不对，一行都没解析出来", Toast.LENGTH_LONG).show();
                    return;
                }
                PlanStore.setPlanText(SettingsActivity.this, text);
                int a = 5;
                try {
                    a = Integer.parseInt(ahead.getText().toString().trim());
                } catch (Exception ignored) {
                }
                getSharedPreferences(PlanStore.PREF, MODE_PRIVATE).edit()
                        .putInt(PlanStore.KEY_AHEAD, Math.max(0, Math.min(60, a))).apply();
                String p = np.getText().toString().trim();
                if (p.length() >= 4) {
                    getSharedPreferences(PlanStore.PREF, MODE_PRIVATE).edit()
                            .putString(PlanStore.KEY_PIN, p).apply();
                }
                Toast.makeText(SettingsActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        root.addView(save);

        // ---------- 出口与桌面接管 ----------
        final TextView log = label("", 11, SUB);
        log.setPadding(0, (int) dp(6), 0, (int) dp(6));

        LinearLayout exitRow = new LinearLayout(this);
        exitRow.setOrientation(LinearLayout.HORIZONTAL);
        exitRow.setPadding(0, (int) dp(14), 0, 0);

        Button exit = new Button(this);
        exit.setText("退出到当贝桌面");
        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitToLauncher("com.dangbei.tvlauncher", "com.baidu.launcher");
            }
        });
        exitRow.addView(exit, new LinearLayout.LayoutParams((int) dp(180), (int) dp(48)));

        Button takeover = new Button(this);
        takeover.setText("接管桌面(root)");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams((int) dp(170), (int) dp(48));
        tlp.leftMargin = (int) dp(10);
        takeover.setLayoutParams(tlp);
        takeover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String comp = findHomeComponent(getPackageName());
                if (comp == null) comp = getPackageName() + "/.MainActivity";
                StringBuilder sb = new StringBuilder();
                sb.append("目标组件: ").append(comp).append("\n");
                sb.append("① su 测试:\n").append(runRoot("id")).append("\n");
                sb.append("② pm set-home-activity:\n").append(runRoot("pm set-home-activity --user 0 " + comp)).append("\n");
                sb.append("③ cmd package set-home-activity:\n").append(runRoot("cmd package set-home-activity " + comp)).append("\n");
                String cur = runRoot("cmd package resolve-activity -c android.intent.category.HOME");
                if (cur.length() > 240) cur = cur.substring(0, 240) + "...";
                sb.append("④ 当前默认桌面:\n").append(cur).append("\n");
                log.setText(sb.toString());
                log.setTextColor(0xFFFFD786);
            }
        });
        exitRow.addView(takeover);

        Button restore = new Button(this);
        restore.setText("恢复小度桌面(root)");
        LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams((int) dp(180), (int) dp(48));
        rlp2.leftMargin = (int) dp(10);
        restore.setLayoutParams(rlp2);
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String comp = findHomeComponent("com.baidu.launcher");
                if (comp == null) comp = findHomeComponent("com.dangbei.tvlauncher");
                if (comp == null) {
                    Toast.makeText(SettingsActivity.this, "没找到小度/当贝桌面组件", Toast.LENGTH_LONG).show();
                    return;
                }
                String r = runRoot("cmd package set-home-activity " + comp);
                Toast.makeText(SettingsActivity.this, r.length() == 0 ? "已恢复：" + comp : r, Toast.LENGTH_LONG).show();
            }
        });
        exitRow.addView(restore);

        root.addView(exitRow);

        TextView tip = label("提示：退出到当贝桌面后，在桌面图标里点「学习看板」即可回来；若桌面无图标，重启会自动进看板。", 12, SUB);
        tip.setPadding(0, (int) dp(10), 0, 0);
        root.addView(tip);

        // ---------- 小度桌面开关 ----------
        LinearLayout bdRow = new LinearLayout(this);
        bdRow.setOrientation(LinearLayout.HORIZONTAL);
        bdRow.setPadding(0, (int) dp(10), 0, 0);

        Button hideBd = new Button(this);
        hideBd.setText("禁用小度桌面(root)");
        hideBd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String r = runRoot("pm disable-user --user 0 com.baidu.launcher");
                log.setText("禁用 com.baidu.launcher:\n" + (r.length() == 0 ? "(无输出=成功)" : r)
                        + "\n\n注意：小度语音唤醒后可能无法显示界面，可随时点右侧按钮恢复。");
                log.setTextColor(0xFFFF9E9E);
            }
        });
        bdRow.addView(hideBd, new LinearLayout.LayoutParams((int) dp(190), (int) dp(48)));

        Button showBd = new Button(this);
        showBd.setText("启用小度桌面(root)");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams((int) dp(190), (int) dp(48));
        blp.leftMargin = (int) dp(10);
        showBd.setLayoutParams(blp);
        showBd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String r = runRoot("pm enable com.baidu.launcher");
                log.setText("启用 com.baidu.launcher:\n" + (r.length() == 0 ? "(无输出=成功)" : r));
                log.setTextColor(0xFF9EE8A0);
            }
        });
        bdRow.addView(showBd);

        root.addView(bdRow);

        LinearLayout dgRow = new LinearLayout(this);
        dgRow.setOrientation(LinearLayout.HORIZONTAL);
        dgRow.setPadding(0, (int) dp(10), 0, 0);
        Button diag = new Button(this);
        diag.setText("抓现场日志(root)");
        diag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                String top = runRoot("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -3");
                sb.append("【当前前台】\n").append(top.length() == 0 ? "(未取到)" : top).append("\n\n");
                String home = runRoot("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
                sb.append("【系统默认桌面】\n").append(home.length() == 0 ? "(空)" : home).append("\n\n");
                String crash = runRoot("logcat -d -b crash -t 80 | grep -iE 'studyboard|FATAL EXCEPTION' | tail -6");
                sb.append("【崩溃记录】\n").append(crash.length() == 0 ? "(无)" : crash);
                String out = sb.toString();
                if (out.length() > 1100) out = out.substring(out.length() - 1100);
                log.setText(out);
                log.setTextColor(0xFF9FE0FF);
            }
        });
        dgRow.addView(diag, new LinearLayout.LayoutParams((int) dp(190), (int) dp(48)));
        root.addView(dgRow);

        // ---------- 开机守护脚本（Magisk service.d，独立于 App 进程） ----------
        LinearLayout bgRow = new LinearLayout(this);
        bgRow.setOrientation(LinearLayout.HORIZONTAL);
        bgRow.setPadding(0, (int) dp(10), 0, 0);

        Button bootGuard = new Button(this);
        bootGuard.setText("写入开机守护脚本(root)");
        bootGuard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] lines = {
                        "#!/system/bin/sh",
                        "sleep 12",
                        "am start -n com.family.studyboard/.MainActivity",
                        "sleep 8",
                        "am start -n com.family.studyboard/.MainActivity",
                        "sleep 12",
                        "am start -n com.family.studyboard/.MainActivity",
                        "sleep 18",
                        "am start -n com.family.studyboard/.MainActivity",
                        "sleep 30",
                        "am start -n com.family.studyboard/.MainActivity",
                        "sleep 45",
                        "am start -n com.family.studyboard/.MainActivity"
                };
                StringBuilder c = new StringBuilder("mkdir -p /data/adb/service.d;");
                for (int i = 0; i < lines.length; i++) {
                    c.append("echo '").append(lines[i]).append("' ")
                            .append(i == 0 ? ">" : ">>")
                            .append(" /data/adb/service.d/studyboard_boot.sh;");
                }
                c.append("chmod 755 /data/adb/service.d/studyboard_boot.sh;");
                c.append("ls -l /data/adb/service.d/studyboard_boot.sh");
                String r = runRoot(c.toString());
                log.setText("写入结果:\n" + (r.length() == 0 ? "(无输出，可能成功)" : r)
                        + "\n\n下次开机会由 Magisk 以 root 执行此脚本，在 12~125 秒间逐次把看板拉回前台。");
                log.setTextColor(0xFF9FE0FF);
            }
        });
        bgRow.addView(bootGuard, new LinearLayout.LayoutParams((int) dp(210), (int) dp(48)));

        Button pullNow = new Button(this);
        pullNow.setText("立即拉回看板(root)");
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams((int) dp(190), (int) dp(48));
        plp.leftMargin = (int) dp(10);
        pullNow.setLayoutParams(plp);
        pullNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String r = runRoot("am start -n com.family.studyboard/.MainActivity");
                log.setText("拉回结果:\n" + (r.length() == 0 ? "(无输出)" : r));
                log.setTextColor(0xFF9FE0FF);
            }
        });
        bgRow.addView(pullNow);

        root.addView(bgRow);

        Button appMgr = new Button(this);
        appMgr.setText("应用管理(root)：停用/启用应用");
        LinearLayout.LayoutParams amp = new LinearLayout.LayoutParams((int) dp(300), (int) dp(48));
        amp.topMargin = (int) dp(10);
        appMgr.setLayoutParams(amp);
        appMgr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(SettingsActivity.this, AppListActivity.class));
                } catch (Throwable t) {
                    Toast.makeText(SettingsActivity.this, "打开失败: " + t, Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(appMgr);

        // ---------- 自动更新 ----------
        TextView upTitle = label("自动更新（从 GitHub Releases 拉取）", 16, TXT);
        upTitle.setPadding(0, (int) dp(16), 0, (int) dp(4));
        root.addView(upTitle);

        final TextView upInfo = label(
                "当前 v" + Updater.currentName(this) + "（" + Updater.currentCode(this) + "）　上次检查："
                        + Updater.lastCheckText(this), 12, SUB);
        root.addView(upInfo);

        String curBase = Updater.base(this);
        final EditText upUrl = input("https://github.com/用户名/仓库/releases/latest/download/", 1,
                InputType.TYPE_TEXT_VARIATION_URI);
        upUrl.setText(curBase.contains("CHANGE_ME") ? "" : curBase);
        upUrl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        upUrl.setSingleLine(true);
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(-1, -2);
        upLp.topMargin = (int) dp(6);
        upUrl.setLayoutParams(upLp);
        root.addView(upUrl);

        final android.widget.CheckBox upAuto = new android.widget.CheckBox(this);
        upAuto.setText("开机自动检查，有新版本直接静默安装");
        upAuto.setTextColor(SUB);
        upAuto.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        upAuto.setChecked(Updater.autoEnabled(this));
        upAuto.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                Updater.setAuto(SettingsActivity.this, checked);
            }
        });
        root.addView(upAuto);

        Button upCheck = new Button(this);
        upCheck.setText("保存地址并立即检查");
        LinearLayout.LayoutParams ucp = new LinearLayout.LayoutParams((int) dp(230), (int) dp(48));
        ucp.topMargin = (int) dp(6);
        upCheck.setLayoutParams(ucp);
        upCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Updater.setBase(SettingsActivity.this, upUrl.getText().toString());
                upInfo.setText("正在检查…（大约 10 秒）");
                Updater.check(SettingsActivity.this, new Updater.Callback() {
                    @Override
                    public void onResult(String status, String detail) {
                        upInfo.setText("当前 v" + Updater.currentName(SettingsActivity.this)
                                + "　上次检查：" + Updater.lastCheckText(SettingsActivity.this)
                                + "\n结果：" + detail);
                        Toast.makeText(SettingsActivity.this, detail, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        root.addView(upCheck);

        root.addView(log);

        Button back2 = new Button(this);
        back2.setText("返回看板");
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams((int) dp(170), (int) dp(46));
        b2.topMargin = (int) dp(8);
        back2.setLayoutParams(b2);
        back2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(back2);

        sc.addView(root, new ViewGroup.LayoutParams(-1, -2));
        return sc;
    }
}
