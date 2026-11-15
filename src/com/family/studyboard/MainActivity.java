package com.family.studyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 学习看板主界面。
 *
 * 布局（1024×600 横屏）：
 *   左栏：待机=大时钟 + 日期 + 问候；有任务=倒计时环 + 任务名 + 操作按钮
 *   右栏：两列 —— 「按时间」（接送/定时/日程）+ 「自己点开始」（随时任务）
 *
 * 语音：到点提醒、任务开始/暂停/完成、最后 1 分钟、接送提前 10 分钟都会播报，
 *       播报前先放合成提示音（见 Sfx）。
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int BG = PlanStore.COLOR_BG;
    private static final int CARD = PlanStore.COLOR_CARD;
    private static final int TXT = PlanStore.COLOR_TEXT;
    private static final int SUB = PlanStore.COLOR_SUB;
    private static final int FAINT = PlanStore.COLOR_FAINT;
    private static final int BLUE = PlanStore.COLOR_BLUE;
    private static final int GREEN = PlanStore.COLOR_GREEN;
    private static final int AMBER = PlanStore.COLOR_AMBER;
    private static final int RED = PlanStore.COLOR_RED;
    private static final int SOFT = PlanStore.COLOR_SOFT;
    private static final int LINE = PlanStore.COLOR_LINE;
    private static final int ALERT_BG = 0xFFFEE2E2;
    private static final int ALERT_FG = 0xFFB91C1C;

    private TextView tvDate, tvClock, tvGreet, tvAlert, tvCurrent, tvNote;
    private TextView btnPause, btnDone, btnReset;
    private TextView tvStar, tvStat, tvVer;
    private TextView cntFixed, cntFlex;
    private CountdownRing ring;
    private LinearLayout idleBox, runBox, colFixed, colFlex;

    private List<PlanStore.Item> items = new ArrayList<PlanStore.Item>();
    private PlanStore.Day day = new PlanStore.Day();
    private final Map<String, TextView> timeRefs = new HashMap<String, TextView>();

    private final Handler handler = new Handler();
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private int saveCounter = 0;
    private boolean showingRun = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            try {
                tick();
            } catch (Throwable t) {
                android.util.Log.w("StudyBoard", "tick error", t);
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
        tts = new TextToSpeech(this, this);

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

    // ---------------- 工具 ----------------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private GradientDrawable stroke(int fill, int line, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), line);
        return g;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        return t;
    }

    private TextView pill(String s, int bg, int fg, float size) {
        TextView t = text(s, size, fg);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(bg, 12));
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // ---------------- 界面 ----------------

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new FrameLayout.LayoutParams(-1, -1));

        buildLeft(row);
        buildRight(row);
        buildHotspots(root);
        return root;
    }

    private void buildLeft(LinearLayout row) {
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(18), dp(12), dp(8), dp(12));
        row.addView(left, new LinearLayout.LayoutParams(0, -1, 0.37f));

        // 接送提醒条（默认隐藏）
        tvAlert = text("", 13, ALERT_FG);
        tvAlert.setTypeface(Typeface.DEFAULT_BOLD);
        tvAlert.setGravity(Gravity.CENTER);
        tvAlert.setBackground(round(ALERT_BG, 10));
        tvAlert.setPadding(dp(8), dp(7), dp(8), dp(7));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.bottomMargin = dp(6);
        tvAlert.setVisibility(View.GONE);
        left.addView(tvAlert, alp);

        FrameLayout host = new FrameLayout(this);
        left.addView(host, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ===== 待机：大时钟 =====
        idleBox = new LinearLayout(this);
        idleBox.setOrientation(LinearLayout.VERTICAL);
        idleBox.setGravity(Gravity.CENTER);
        host.addView(idleBox, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        tvDate = text("", 13, SUB);
        tvDate.setGravity(Gravity.CENTER);
        idleBox.addView(tvDate);

        tvClock = text("--:--", 96, TXT);
        tvClock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tvClock.setIncludeFontPadding(false);
        tvClock.setGravity(Gravity.CENTER);
        tvClock.setMaxLines(1);
        try {
            tvClock.setAutoSizeTextTypeUniformWithConfiguration(48, 132, 4,
                    TypedValue.COMPLEX_UNIT_SP);
        } catch (Throwable ignored) {
        }
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(150));
        idleBox.addView(tvClock, clp);

        tvGreet = text("", 15, SUB);
        tvGreet.setGravity(Gravity.CENTER);
        idleBox.addView(tvGreet);

        // ===== 运行：倒计时 =====
        runBox = new LinearLayout(this);
        runBox.setOrientation(LinearLayout.VERTICAL);
        runBox.setGravity(Gravity.CENTER);
        runBox.setVisibility(View.GONE);
        host.addView(runBox, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        ring = new CountdownRing(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(150), dp(150));
        runBox.addView(ring, rlp);

        tvCurrent = text("", 19, TXT);
        tvCurrent.setTypeface(Typeface.DEFAULT_BOLD);
        tvCurrent.setGravity(Gravity.CENTER);
        tvCurrent.setMaxLines(1);
        LinearLayout.LayoutParams tcl = new LinearLayout.LayoutParams(-1, -2);
        tcl.topMargin = dp(8);
        runBox.addView(tvCurrent, tcl);

        tvNote = text("", 11, SUB);
        tvNote.setGravity(Gravity.CENTER);
        tvNote.setMaxLines(1);
        runBox.addView(tvNote);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(40));
        blp.topMargin = dp(10);
        runBox.addView(btns, blp);

        btnPause = text("暂停", 14, TXT);
        btnPause.setGravity(Gravity.CENTER);
        btnPause.setTypeface(Typeface.DEFAULT_BOLD);
        btnPause.setBackground(stroke(CARD, LINE, 12));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -1, 1f);
        p1.rightMargin = dp(8);
        btns.addView(btnPause, p1);

        btnDone = text("我完成了", 14, 0xFFFFFFFF);
        btnDone.setGravity(Gravity.CENTER);
        btnDone.setTypeface(Typeface.DEFAULT_BOLD);
        btnDone.setBackground(round(BLUE, 12));
        btns.addView(btnDone, new LinearLayout.LayoutParams(0, -1, 1f));

        btnReset = text("重置计时", 11, SUB);
        btnReset.setGravity(Gravity.CENTER);
        btnReset.setPadding(0, dp(6), 0, 0);
        runBox.addView(btnReset);

        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlanStore.Item act = activeItem();
                if (act == null) return;
                PlanStore.St st = PlanStore.st(day, act);
                if (st.st == PlanStore.S_RUN) pause(act, st);
                else start(act, st, false);
            }
        });
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlanStore.Item act = activeItem();
                if (act == null) return;
                finishTask(act, PlanStore.st(day, act), false);
            }
        });
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlanStore.Item act = activeItem();
                if (act == null) {
                    Toast.makeText(MainActivity.this, "当前没有在计时的任务", Toast.LENGTH_SHORT).show();
                    return;
                }
                PlanStore.St st = PlanStore.st(day, act);
                st.remain = Math.max(0, act.dur) * 60;
                st.endAt = 0;
                st.st = PlanStore.S_IDLE;
                st.warned = 0;
                PlanStore.saveDay(MainActivity.this, day);
                start(act, st, true);
                Toast.makeText(MainActivity.this, "已重置，重新计时", Toast.LENGTH_SHORT).show();
                say("已重置，重新计时", Sfx.TIP);
            }
        });
    }

    private void buildRight(LinearLayout row) {
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(8), dp(12), dp(12), dp(10));
        row.addView(right, new LinearLayout.LayoutParams(0, -1, 0.63f));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("大泽的今天", 18, TXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        tvStar = pill("★ 0", 0xFFFEF3C7, 0xFFB45309, 15);
        tvStar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askRedeem();
            }
        });
        head.addView(tvStar);
        right.addView(head);

        tvStat = text("", 11, SUB);
        tvStat.setPadding(0, dp(3), 0, dp(6));
        right.addView(tvStat);

        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.HORIZONTAL);
        right.addView(board, new LinearLayout.LayoutParams(-1, 0, 1f));

        colFixed = buildColumn(board, "按时间", true);
        colFlex = buildColumn(board, "自己点开始", false);
    }

    private LinearLayout buildColumn(LinearLayout board, String name, boolean first) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
        if (!first) cl.leftMargin = dp(8);
        board.addView(col, cl);

        LinearLayout ch = new LinearLayout(this);
        ch.setOrientation(LinearLayout.HORIZONTAL);
        ch.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, -2);
        chp.bottomMargin = dp(5);
        col.addView(ch, chp);

        TextView label = text(name, 11, 0xFF7A756E);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        ch.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView cnt = text("", 10, FAINT);
        cnt.setTypeface(Typeface.DEFAULT_BOLD);
        ch.addView(cnt);
        if (first) cntFixed = cnt;
        else cntFlex = cnt;

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sv.addView(box, new LinearLayout.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return box;
    }

    private void buildHotspots(FrameLayout root) {
        // 隐藏入口：左下角长按 2.5 秒
        View hot = new View(this);
        root.addView(hot, new FrameLayout.LayoutParams(dp(110), dp(110),
                Gravity.LEFT | Gravity.BOTTOM));
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

        ImageView gear = new ImageView(this);
        gear.setImageResource(android.R.drawable.ic_menu_manage);
        gear.setAlpha(0.28f);
        gear.setPadding(dp(8), dp(8), dp(8), dp(8));
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(38), dp(38),
                Gravity.RIGHT | Gravity.BOTTOM);
        gp.rightMargin = dp(4);
        gp.bottomMargin = dp(4);
        root.addView(gear, gp);
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "打开设置失败: " + t, Toast.LENGTH_LONG).show();
                }
            }
        });

        tvVer = text("v" + Updater.currentName(this) + " (" + Updater.currentCode(this) + ")",
                9, FAINT);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.RIGHT | Gravity.BOTTOM);
        vp.rightMargin = dp(46);
        vp.bottomMargin = dp(13);
        root.addView(tvVer, vp);
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onResume() {
        super.onResume();
        goFullscreen();
        items = PlanStore.parse(PlanStore.getPlanText(this));
        day = PlanStore.loadDay(this);
        repairRunning();
        renderList();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        PlanStore.saveDay(this, day);
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

    /** 重启后修正运行态：无结束时间的一律转暂停 */
    private void repairRunning() {
        boolean changed = false;
        for (PlanStore.Item it : items) {
            PlanStore.St st = PlanStore.st(day, it);
            if (st.st == PlanStore.S_RUN && st.endAt <= 0) {
                st.st = PlanStore.S_PAUSE;
                changed = true;
            }
            if (st.st == PlanStore.S_IDLE && st.remain <= 0 && it.dur > 0) {
                st.remain = it.dur * 60;
            }
        }
        if (changed) PlanStore.saveDay(this, day);
    }

    private PlanStore.Item activeItem() {
        for (PlanStore.Item it : items) {
            PlanStore.St st = PlanStore.st(day, it);
            if (st.st == PlanStore.S_RUN || st.st == PlanStore.S_PAUSE) return it;
        }
        return null;
    }

    // ---------------- 列表渲染 ----------------

    private void renderList() {
        colFixed.removeAllViews();
        colFlex.removeAllViews();
        timeRefs.clear();

        List<PlanStore.Item> timed = new ArrayList<PlanStore.Item>();
        List<PlanStore.Item> flex = new ArrayList<PlanStore.Item>();
        for (PlanStore.Item it : items) {
            if (it.type == PlanStore.T_FLEX) flex.add(it);
            else timed.add(it);
        }
        for (int i = 0; i < timed.size(); i++) {
            for (int j = i + 1; j < timed.size(); j++) {
                if (timed.get(j).start < timed.get(i).start) {
                    PlanStore.Item t = timed.get(i);
                    timed.set(i, timed.get(j));
                    timed.set(j, t);
                }
            }
        }

        for (PlanStore.Item it : timed) addCard(it, colFixed);
        for (PlanStore.Item it : flex) addCard(it, colFlex);

        if (items.size() == 0) {
            TextView empty = text("还没有计划，长按左下角进设置", 12, SUB);
            empty.setPadding(dp(4), dp(16), 0, 0);
            colFixed.addView(empty);
        }
        updateStats();
    }

    private void addCard(final PlanStore.Item it, LinearLayout parent) {
        final PlanStore.St st = PlanStore.st(day, it);
        final int color = it.color();

        boolean alerting = false;
        String alertText = null;
        if (it.type == PlanStore.T_GO) {
            int left = it.start - nowMin();
            if (left > 0 && left <= 10) {
                alerting = true;
                alertText = left + " 分后";
            } else if (left <= 0 && left > -20) {
                alerting = true;
                alertText = "该出门了";
            } else if (left <= -20) {
                alertText = "已出发";
            }
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(alerting ? stroke(0xFFFFF5F5, 0xFFFCA5A5, 13) : round(CARD, 13));
        card.setPadding(0, dp(5), dp(8), dp(5));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(5);
        card.setLayoutParams(clp);

        View bar = new View(this);
        bar.setBackground(round(color, 2));
        card.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(30)));

        TextView tTime = text(it.type == PlanStore.T_FLEX ? "随时" : PlanStore.fmt(it.start),
                12, color);
        tTime.setTypeface(Typeface.DEFAULT_BOLD);
        tTime.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(44), -2);
        tlp.leftMargin = dp(5);
        card.addView(tTime, tlp);
        timeRefs.put(it.key(), tTime);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
        mlp.leftMargin = dp(3);
        card.addView(mid, mlp);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tTitle = text(it.title, 14, TXT);
        tTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tTitle.setSingleLine(true);
        if (st.st == PlanStore.S_DONE) {
            tTitle.setPaintFlags(tTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tTitle.setTextColor(SUB);
        }
        titleRow.addView(tTitle);

        if (it.stars > 0) {
            TextView tStar = text("★" + it.stars, 10, AMBER);
            tStar.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.leftMargin = dp(4);
            titleRow.addView(tStar, slp);
        }
        mid.addView(titleRow);

        String sub = it.note;
        if (it.dur > 0) sub = (sub.length() > 0 ? sub + " · " : "") + it.dur + " 分钟";
        if (it.type == PlanStore.T_GO && sub.length() == 0) sub = "接送提醒";
        if (it.title.contains("游戏") && day.gameBonus > 0) {
            sub = sub + " · 已兑换 +" + day.gameBonus + " 分";
        }
        TextView tNote = text(sub, 10, SUB);
        tNote.setSingleLine(true);
        mid.addView(tNote);

        // 右侧：状态 / 按钮
        LinearLayout act = new LinearLayout(this);
        act.setOrientation(LinearLayout.HORIZONTAL);
        act.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(act);

        if (it.type == PlanStore.T_GO) {
            if (alertText != null) {
                TextView t = text(alertText, 11, alerting ? RED : FAINT);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                act.addView(t);
            }
        } else if (it.type == PlanStore.T_EVENT) {
            TextView t = text(it.dur > 0 ? it.dur + " 分" : "", 10, FAINT);
            act.addView(t);
        } else if (st.st == PlanStore.S_DONE) {
            TextView t = text(st.got > 0 ? "+" + st.got + "★" : "完成", 11, GREEN);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            act.addView(t);
            TextView again = miniBtn("再来", SOFT, SUB);
            LinearLayout.LayoutParams alp2 = new LinearLayout.LayoutParams(-2, -2);
            alp2.leftMargin = dp(4);
            again.setLayoutParams(alp2);
            again.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PlanStore.St s2 = PlanStore.st(day, it);
                    s2.remain = Math.max(0, it.dur) * 60;
                    s2.st = PlanStore.S_IDLE;
                    s2.endAt = 0;
                    start(it, s2, false);
                }
            });
            act.addView(again);
        } else if (st.st == PlanStore.S_MISS) {
            TextView again = miniBtn("补做", SOFT, TXT);
            again.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PlanStore.St s2 = PlanStore.st(day, it);
                    s2.remain = Math.max(0, it.dur) * 60;
                    s2.st = PlanStore.S_IDLE;
                    start(it, s2, false);
                }
            });
            act.addView(again);
        } else if (st.st == PlanStore.S_RUN || st.st == PlanStore.S_PAUSE) {
            TextView done = miniBtn("完成", BLUE, 0xFFFFFFFF);
            done.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finishTask(it, PlanStore.st(day, it), false);
                }
            });
            act.addView(done);
        } else if (it.type == PlanStore.T_FIXED) {
            int left = it.start - nowMin();
            TextView t = text(left > 0 ? left + " 分后" : "未开始", 10, FAINT);
            act.addView(t);
        } else {
            TextView go = miniBtn("开始", BLUE, 0xFFFFFFFF);
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    start(it, PlanStore.st(day, it), false);
                }
            });
            act.addView(go);
        }

        parent.addView(card);
    }

    private TextView miniBtn(String s, int bg, int fg) {
        TextView t = text(s, 11, fg);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(round(bg, 9));
        t.setPadding(dp(9), dp(4), dp(9), dp(4));
        t.setClickable(true);
        return t;
    }

    private void updateStats() {
        int[] s = PlanStore.summary(items, day);
        tvStar.setText("★ " + day.balance);
        String txt = "已完成 " + s[2] + "/" + s[3] + " 项 · 今日 +" + day.earned + "★";
        if (day.gameBonus > 0) txt = txt + " · 游戏 +" + day.gameBonus + " 分";
        tvStat.setText(txt);

        List<PlanStore.Item> timed = new ArrayList<PlanStore.Item>();
        List<PlanStore.Item> flex = new ArrayList<PlanStore.Item>();
        for (PlanStore.Item it : items) {
            if (it.type == PlanStore.T_FLEX) flex.add(it);
            else timed.add(it);
        }
        int[] a = PlanStore.columnStat(timed, day);
        int[] b = PlanStore.columnStat(flex, day);
        cntFixed.setText(a[0] + "/" + a[1]);
        cntFlex.setText(b[0] + "/" + b[1]);
    }

    // ---------------- 计时动作 ----------------

    private void start(PlanStore.Item it, PlanStore.St st, boolean silent) {
        int total = Math.max(0, it.dur) * 60;
        if (st.remain <= 0 || st.st == PlanStore.S_DONE) st.remain = total;
        st.st = PlanStore.S_RUN;
        st.endAt = System.currentTimeMillis() + st.remain * 1000L;
        st.warned = 0;
        PlanStore.saveDay(this, day);
        renderList();
        if (!silent) {
            say("开始" + it.title + "，" + Math.max(1, Math.round(st.remain / 60f)) + "分钟",
                    Sfx.START);
        }
    }

    private void pause(PlanStore.Item it, PlanStore.St st) {
        long now = System.currentTimeMillis();
        st.remain = (int) Math.max(0, (st.endAt - now) / 1000);
        st.st = PlanStore.S_PAUSE;
        st.endAt = 0;
        st.paused++;
        PlanStore.saveDay(this, day);
        renderList();
        say("已暂停", -1);
    }

    private void finishTask(PlanStore.Item it, PlanStore.St st, boolean auto) {
        int total = Math.max(60, it.dur * 60);
        int remain = Math.max(0, st.remain);
        int used = total - remain;
        int stars = it.stars;
        if (!auto && used * 2 < total) stars = (int) Math.ceil(it.stars / 2.0);
        if (st.got > 0) stars = 0;

        st.st = PlanStore.S_DONE;
        st.remain = 0;
        st.endAt = 0;
        if (stars > 0) {
            st.got += stars;
            day.balance += stars;
            day.earned += stars;
        }
        PlanStore.saveDay(this, day);
        renderList();

        String head = auto ? "时间到，" : "";
        if (stars > 0) {
            say(head + it.title + "完成，获得 " + stars + " 颗星", Sfx.DONE);
        } else {
            say(head + it.title + "完成", Sfx.DONE);
        }
    }

    // ---------------- 每秒刷新 ----------------

    private void tick() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);
        int nowMin = h * 60 + m;
        long now = System.currentTimeMillis();

        tvClock.setText(String.format(Locale.US, "%02d:%02d", h, m));
        String[] wd = {"日", "一", "二", "三", "四", "五", "六"};
        tvDate.setText((c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日 星期"
                + wd[c.get(Calendar.DAY_OF_WEEK) - 1]);
        tvGreet.setText(greet(h));

        if (!PlanStore.today().equals(day.date)) {
            day = PlanStore.loadDay(this);
            items = PlanStore.parse(PlanStore.getPlanText(this));
            renderList();
        }

        boolean dirty = false;

        for (PlanStore.Item it : items) {
            if (it.type == PlanStore.T_GO) {
                int left = it.start - nowMin;
                if (left > 0 && left <= 10 && !day.goAlerted.containsKey(it.key())) {
                    day.goAlerted.put(it.key(), 1);
                    dirty = true;
                    String body = it.speech.length() > 0 ? it.speech : ("要" + it.title);
                    say("还有 " + left + " 分钟，" + body, Sfx.GO);
                    toast("接送提醒 · " + left + " 分钟后" + it.title);
                } else if (left <= 0 && left > -20 && !day.goNow.containsKey(it.key())) {
                    day.goNow.put(it.key(), 1);
                    dirty = true;
                    say("现在该" + it.title + "了", Sfx.GO);
                }
                continue;
            }
            if (it.type == PlanStore.T_EVENT) {
                if (nowMin >= it.start && nowMin < it.start + 3
                        && !day.evAlerted.containsKey(it.key())) {
                    day.evAlerted.put(it.key(), 1);
                    dirty = true;
                    String body = it.speech.length() > 0 ? it.speech : ("该" + it.title + "了");
                    say(body, Sfx.TIP);
                }
                continue;
            }
            if (it.type != PlanStore.T_FIXED || it.dur <= 0) continue;
            PlanStore.St st = PlanStore.st(day, it);
            if (st.st == PlanStore.S_IDLE) {
                if (nowMin >= it.start && nowMin < it.end()) {
                    start(it, st, false);
                    dirty = true;
                } else if (nowMin >= it.end()) {
                    st.st = PlanStore.S_MISS;
                    dirty = true;
                }
            }
        }

        // 接送提醒条
        PlanStore.Item goNow = goAlert();
        if (goNow != null) {
            int left = goNow.start - nowMin;
            tvAlert.setVisibility(View.VISIBLE);
            tvAlert.setText(left > 0
                    ? "接送提醒 · 还有 " + left + " 分钟要" + goNow.title
                    : "接送提醒 · 现在该" + goNow.title + "了");
        } else {
            tvAlert.setVisibility(View.GONE);
        }

        PlanStore.Item act = null;
        PlanStore.St actSt = null;
        for (PlanStore.Item it : items) {
            PlanStore.St st = PlanStore.st(day, it);
            if (st.st == PlanStore.S_RUN) {
                act = it;
                actSt = st;
                break;
            }
        }
        if (act == null) {
            for (PlanStore.Item it : items) {
                PlanStore.St st = PlanStore.st(day, it);
                if (st.st == PlanStore.S_PAUSE) {
                    act = it;
                    actSt = st;
                    break;
                }
            }
        }

        if (act != null) {
            showMode(true);
            int total = Math.max(1, Math.max(act.dur, 1) * 60);
            int remain;
            if (actSt.st == PlanStore.S_RUN) {
                remain = (int) Math.max(0, (actSt.endAt - now) / 1000);
                actSt.remain = remain;
                if (remain > 0 && remain <= 60 && total >= 120 && actSt.warned == 0) {
                    actSt.warned = 1;
                    dirty = true;
                    say("还有最后一分钟", Sfx.TIP);
                }
                if (remain <= 0) {
                    finishTask(act, actSt, true);
                    if (dirty) PlanStore.saveDay(this, day);
                    return;
                }
            } else {
                remain = Math.max(0, actSt.remain);
            }
            String mmss = String.format(Locale.US, "%d:%02d", remain / 60, remain % 60);
            ring.setData(remain / (float) total, mmss,
                    actSt.st == PlanStore.S_RUN ? "剩余" : "已暂停", remain <= 60);
            tvCurrent.setText(act.title);
            tvNote.setText(actSt.st == PlanStore.S_RUN
                    ? (actSt.paused > 0 ? act.note + "　（暂停 " + actSt.paused + " 次）" : act.note)
                    : "已暂停，点「继续」接着计时");
            btnPause.setText(actSt.st == PlanStore.S_RUN ? "暂停" : "继续");

            TextView tv = timeRefs.get(act.key());
            if (tv != null) tv.setText(mmss);

            saveCounter++;
            if (saveCounter >= 10) {
                saveCounter = 0;
                PlanStore.saveDay(this, day);
            }
        } else {
            showMode(false);
            boolean dirtyCard = false;
            for (PlanStore.Item it : items) {
                if (it.type == PlanStore.T_GO) continue;
                PlanStore.St st = PlanStore.st(day, it);
                if (st.st == PlanStore.S_IDLE && it.type == PlanStore.T_FLEX) continue;
                if (st.st == PlanStore.S_RUN || st.st == PlanStore.S_PAUSE) dirtyCard = true;
            }
            if (dirtyCard) renderList();
        }

        if (dirty) {
            PlanStore.saveDay(this, day);
            renderList();
        }
    }

    private void showMode(boolean running) {
        if (showingRun == running) return;
        showingRun = running;
        idleBox.setVisibility(running ? View.GONE : View.VISIBLE);
        runBox.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    /** 当前处于「提前10分钟 / 已到点」窗口的接送项 */
    private PlanStore.Item goAlert() {
        int nowMin = nowMin();
        for (PlanStore.Item it : items) {
            if (it.type != PlanStore.T_GO) continue;
            int left = it.start - nowMin;
            if (left <= 10 && left > -20) return it;
        }
        return null;
    }

    private int nowMin() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private String greet(int h) {
        if (h < 5) return "大泽，该睡觉啦";
        if (h < 9) return "大泽，早上好";
        if (h < 12) return "大泽，上午好";
        if (h < 14) return "大泽，中午好";
        if (h < 18) return "大泽，下午好";
        if (h < 21) return "大泽，晚上好";
        return "大泽，该准备睡觉了";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------- 语音 ----------------

    /**
     * 先说一句话。sfx >= 0 时先放提示音，等音效播完再让 TTS 开口，避免叠在一起。
     */
    private void say(final String text, int sfx) {
        if (text == null || text.length() == 0) return;
        if (!PlanStore.ttsOn(this)) return;
        int delay = 0;
        if (sfx >= 0 && PlanStore.sfxOn(this)) {
            Sfx.play(this, sfx);
            delay = Sfx.durationMs(sfx);
        }
        if (!ttsReady) return;
        final String t = text;
        if (delay <= 0) {
            speak(t);
        } else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    speak(t);
                }
            }, delay);
        }
    }

    private void speak(String text) {
        if (tts == null || !ttsReady) return;
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sb");
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.CHINA);
            ttsReady = (r != TextToSpeech.LANG_MISSING_DATA
                    && r != TextToSpeech.LANG_NOT_SUPPORTED);
            if (ttsReady) {
                tts.setSpeechRate(0.98f);
                tts.setPitch(1.02f);
            }
        }
    }

    // ---------------- 星星兑换 ----------------

    private void askRedeem() {
        if (day.balance <= 0) {
            toast("还没有星星，先完成任务吧");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("兑换游戏时间")
                .setMessage("当前 " + day.balance + " ★　今日已兑换 +" + day.gameBonus + " 分钟\n\n用 1 ★ 换 2 分钟游戏时间？")
                .setPositiveButton("兑换 1 ★", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (day.balance <= 0) return;
                        day.balance -= 1;
                        day.gameBonus += 2;
                        PlanStore.saveDay(MainActivity.this, day);
                        renderList();
                        toast("游戏时间 +2 分钟");
                        say("好，游戏时间加两分钟", Sfx.TIP);
                    }
                })
                .setNegativeButton("算了", null)
                .show();
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
