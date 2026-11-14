package com.family.studyboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int BG = 0xFF0B1A2B;
    private static final int CARD = 0xFF12263D;
    private static final int ACTIVE = 0xFF1B3350;
    private static final int TXT = 0xFFFFFFFF;
    private static final int SUB = 0xFF6B8BAE;
    private static final int BLUE = 0xFF4A90D9;
    private static final int GREEN = 0xFF4CD08A;
    private static final int AMBER = 0xFFFFB443;

    private TextView tvDate, tvClock, tvCurrent, tvNote, tvProgress;
    private CountdownRing ring;
    private LinearLayout listBox;
    private ScrollView scroll;

    private List<PlanStore.Item> items = new ArrayList<PlanStore.Item>();
    private final Handler handler = new Handler();
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String lastSpokenKey = "";
    private int curIndex = -1;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
        tts = new TextToSpeech(this, this);

        // 开机/启动时静默检查更新（内部有 6 小时间隔限制，不会每次都跑）
        Updater.autoCheck(this, new Updater.Callback() {
            @Override
            public void onResult(String status, String detail) {
                if ("updated".equals(status)) {
                    Toast.makeText(MainActivity.this, detail, Toast.LENGTH_LONG).show();
                } else if ("error".equals(status)) {
                    android.util.Log.w("StudyUpdater", "自动更新失败: " + detail);
                }
            }
        });
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new FrameLayout.LayoutParams(-1, -1));

        // ---------- 左侧：时钟 + 倒计时 ----------
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        left.setPadding((int) dp(18), (int) dp(16), (int) dp(8), (int) dp(16));
        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, -1, 0.40f);
        row.addView(left, lpLeft);

        tvDate = new TextView(this);
        tvDate.setTextColor(SUB);
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        left.addView(tvDate);

        tvClock = new TextView(this);
        tvClock.setTextColor(TXT);
        tvClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 78);
        tvClock.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        tvClock.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lpClock = new LinearLayout.LayoutParams(-2, -2);
        lpClock.topMargin = (int) dp(2);
        left.addView(tvClock, lpClock);

        ring = new CountdownRing(this);
        LinearLayout.LayoutParams lpRing = new LinearLayout.LayoutParams((int) dp(168), (int) dp(168));
        lpRing.topMargin = (int) dp(10);
        left.addView(ring, lpRing);

        tvCurrent = new TextView(this);
        tvCurrent.setTextColor(TXT);
        tvCurrent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvCurrent.setGravity(Gravity.CENTER);
        tvCurrent.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpCur = new LinearLayout.LayoutParams(-1, -2);
        lpCur.topMargin = (int) dp(10);
        left.addView(tvCurrent, lpCur);

        tvNote = new TextView(this);
        tvNote.setTextColor(SUB);
        tvNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvNote.setGravity(Gravity.CENTER);
        left.addView(tvNote);

        // ---------- 右侧：计划列表 ----------
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding((int) dp(8), (int) dp(16), (int) dp(18), (int) dp(12));
        LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(0, -1, 0.60f);
        row.addView(right, lpRight);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, (int) dp(8));

        TextView title = new TextView(this);
        title.setText("今日计划");
        title.setTextColor(TXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        tvProgress = new TextView(this);
        tvProgress.setTextColor(SUB);
        tvProgress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        head.addView(tvProgress);
        right.addView(head);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox, new FrameLayout.LayoutParams(-1, -2));
        right.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---------- 左下角隐藏热区：长按 2.5 秒进设置 ----------
        View hot = new View(this);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams((int) dp(120), (int) dp(120),
                Gravity.LEFT | Gravity.BOTTOM);
        root.addView(hot, hp);
        hot.setOnTouchListener(new View.OnTouchListener() {
            private Runnable fire;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    fire = new Runnable() {
                        @Override
                        public void run() {
                            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        }
                    };
                    handler.postDelayed(fire, 2500);
                } else if (e.getAction() == MotionEvent.ACTION_UP
                        || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (fire != null) handler.removeCallbacks(fire);
                }
                return true;
            }
        });

        // ---------- 右下角小齿轮：点一下进家长设置（有密码保护） ----------
        android.widget.ImageView gear = new android.widget.ImageView(this);
        gear.setImageResource(android.R.drawable.ic_menu_manage);
        gear.setAlpha(0.38f);
        gear.setPadding((int) dp(8), (int) dp(8), (int) dp(8), (int) dp(8));
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams((int) dp(46), (int) dp(46),
                Gravity.RIGHT | Gravity.BOTTOM);
        gp.rightMargin = (int) dp(4);
        gp.bottomMargin = (int) dp(4);
        root.addView(gear, gp);
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                } catch (Throwable t) {
                    android.widget.Toast.makeText(MainActivity.this,
                            "打开设置失败: " + t, android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });

        // ---------- 右下角极小版本号：方便确认自动更新是否生效 ----------
        TextView tvVer = new TextView(this);
        tvVer.setText("v" + Updater.currentName(this) + " (" + Updater.currentCode(this) + ")");
        tvVer.setTextSize(9f);
        tvVer.setTextColor(0xFF8A93A0);
        tvVer.setAlpha(0.55f);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.RIGHT | Gravity.BOTTOM);
        vp.rightMargin = (int) dp(56);
        vp.bottomMargin = (int) dp(18);
        root.addView(tvVer, vp);

        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        goFullscreen();
        loadPlan();
        renderList();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goFullscreen();
    }

    private void goFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onBackPressed() {
        // 屏蔽返回键
    }

    private void loadPlan() {
        items = PlanStore.parse(PlanStore.getPlanText(this));
        for (PlanStore.Item it : items) {
            it.done = PlanStore.isDone(this, it);
        }
    }

    private void renderList() {
        listBox.removeAllViews();
        int done = 0;
        for (int i = 0; i < items.size(); i++) {
            final PlanStore.Item it = items.get(i);
            if (it.done) done++;

            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding((int) dp(12), (int) dp(7), (int) dp(12), (int) dp(7));
            r.setBackground(round(i == curIndex ? ACTIVE : CARD, 8));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = (int) dp(5);
            r.setLayoutParams(rlp);

            TextView t = new TextView(this);
            t.setText(it.range());
            t.setTextColor(i == curIndex ? BLUE : SUB);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            r.addView(t, new LinearLayout.LayoutParams((int) dp(104), -2));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView ti = new TextView(this);
            ti.setText(it.title);
            ti.setTextColor(it.done ? SUB : TXT);
            ti.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            mid.addView(ti);
            if (it.note.length() > 0) {
                TextView nt = new TextView(this);
                nt.setText(it.note);
                nt.setTextColor(SUB);
                nt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                mid.addView(nt);
            }
            r.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView mark = new TextView(this);
            mark.setText(it.done ? "✓" : "");
            mark.setTextColor(GREEN);
            mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            mark.setGravity(Gravity.CENTER);
            r.addView(mark, new LinearLayout.LayoutParams((int) dp(34), -2));

            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    it.done = !it.done;
                    PlanStore.setDone(MainActivity.this, it, it.done);
                    renderList();
                }
            });

            listBox.addView(r);
        }
        if (items.size() == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有计划，长按左下角进入设置");
            empty.setTextColor(SUB);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setPadding((int) dp(12), (int) dp(20), 0, 0);
            listBox.addView(empty);
        }
        tvProgress.setText("完成 " + done + " / " + items.size());
    }

    private void tick() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);
        int s = c.get(Calendar.SECOND);
        int nowMin = h * 60 + m;

        tvClock.setText(String.format(Locale.US, "%02d:%02d", h, m));
        String[] wd = {"日", "一", "二", "三", "四", "五", "六"};
        tvDate.setText((c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日  星期"
                + wd[c.get(Calendar.DAY_OF_WEEK) - 1]);

        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            PlanStore.Item it = items.get(i);
            if (nowMin >= it.start && nowMin < it.end) {
                idx = i;
                break;
            }
        }

        if (idx != curIndex) {
            curIndex = idx;
            renderList();
        }

        if (idx >= 0) {
            PlanStore.Item it = items.get(idx);
            int leftSec = (it.end - nowMin) * 60 - s;
            int totalSec = Math.max(1, (it.end - it.start) * 60);
            float p = Math.max(0f, Math.min(1f, leftSec / (float) totalSec));
            ring.setData(p, String.format(Locale.US, "%d:%02d", Math.max(0, leftSec) / 60,
                    Math.max(0, leftSec) % 60), "剩余", leftSec <= 300);
            tvCurrent.setText(it.title);
            tvNote.setText(it.note);
        } else {
            ring.setData(0f, "--:--", "空闲", false);
            String next = "";
            for (PlanStore.Item it : items) {
                if (it.start > nowMin) {
                    next = "下一项 " + PlanStore.fmt(it.start) + " " + it.title;
                    break;
                }
            }
            tvCurrent.setText(next.length() > 0 ? next : "今天的计划都完成了");
            tvNote.setText("");
        }

        speakCheck(nowMin, s);
    }

    private void speakCheck(int nowMin, int sec) {
        if (!ttsReady || !PlanStore.ttsOn(this)) return;
        int ahead = PlanStore.getAhead(this);
        String today = PlanStore.today();

        for (PlanStore.Item it : items) {
            // 开始提醒（进入任务后 90 秒内）
            if (nowMin >= it.start && nowMin * 60 + sec < it.start * 60 + 90) {
                String k = today + "_" + it.key() + "_start";
                if (!k.equals(lastSpokenKey)) {
                    lastSpokenKey = k;
                    speak(it.speech.length() > 0 ? it.speech : ("该" + it.title + "了"));
                    return;
                }
            }
            // 提前提醒
            if (ahead > 0 && nowMin >= it.start - ahead && nowMin < it.start
                    && sec < 60 && (it.start - ahead) * 60 + 0 <= nowMin * 60 + sec) {
                String k = today + "_" + it.key() + "_ahead";
                if (!k.equals(lastSpokenKey)) {
                    lastSpokenKey = k;
                    speak(ahead + "分钟后要" + it.title + "啦，准备一下");
                    return;
                }
            }
        }
    }

    private void speak(String text) {
        if (tts == null) return;
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sb");
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.CHINA);
            ttsReady = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
            if (ttsReady) {
                tts.setSpeechRate(0.95f);
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
