package com.family.studyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    private static final String SELF = "com.family.studyboard/.MainActivity";

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (a == null) return;
        if (!(a.equals(Intent.ACTION_BOOT_COMPLETED)
                || a.equals("android.intent.action.QUICKBOOT_POWERON")
                || a.equals(Intent.ACTION_LOCKED_BOOT_COMPLETED))) {
            return;
        }

        // 1) 常规方式拉起
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Exception ignored) {
        }

        // 2) root 兜底：开机后在被抢的窗口期反复拉回前台
        //    当贝/小度桌面通常会在 BOOT_COMPLETED 后几秒到几十秒内自启抢焦点
        execSu("sleep 5; am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        execSu("sleep 10; am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        execSu("sleep 18; am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        execSu("sleep 30; am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        execSu("sleep 45; am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        execSu("sleep 65; am start -a android.intent.action.MAIN -c android.intent.category.HOME");
    }

    private void execSu(String cmd) {
        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        } catch (Exception ignored) {
        }
    }
}
