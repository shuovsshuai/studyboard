package com.family.studyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PlanStore {

    public static final String PREF = "studyboard";
    public static final String KEY_PLAN = "plan_text";
    public static final String KEY_PIN = "pin";
    public static final String KEY_AHEAD = "ahead_min";
    public static final String KEY_TTS_ON = "tts_on";
    public static final String KEY_DONE_PREFIX = "done_";

    public static final String DEFAULT_PLAN =
            "07:00-07:30 起床洗漱||起床啦，洗漱一下\n" +
            "07:30-08:00 早读|大声读课文|该早读啦，把书翻到昨天那一页\n" +
            "08:00-08:30 早饭||吃早饭，慢慢吃\n" +
            "08:30-09:30 数学|口算一页|该做数学了，先做口算\n" +
            "09:45-10:30 语文|写字一课|语文时间到\n" +
            "10:45-11:30 英语|跟读十分钟|该读英语了\n" +
            "12:00-13:00 午饭||午饭时间\n" +
            "13:00-14:00 午休||躺一会儿，闭眼休息\n" +
            "14:30-15:30 作业|今天的作业|该写作业了\n" +
            "15:45-16:30 阅读|自己挑一本书|看书时间，二十分钟以上\n" +
            "17:00-18:00 户外活动||出去活动一下，别待在家里\n" +
            "19:00-19:40 练琴||练琴时间到\n" +
            "20:30-21:00 洗漱睡觉||该洗漱了，准备睡觉";

    public static class Item {
        public int start;
        public int end;
        public String title;
        public String note;
        public String speech;
        public boolean done;

        public Item(int start, int end, String title, String note, String speech) {
            this.start = start;
            this.end = end;
            this.title = title;
            this.note = note == null ? "" : note;
            this.speech = speech == null ? "" : speech;
        }

        public String range() {
            return fmt(start) + "-" + fmt(end);
        }

        public String key() {
            return start + "_" + title;
        }
    }

    public static String fmt(int min) {
        return String.format(Locale.US, "%02d:%02d", (min / 60) % 24, min % 60);
    }

    public static int[] parseTime(String s) {
        try {
            String[] p = s.trim().split(":");
            return new int[]{Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1])};
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Item> parse(String text) {
        List<Item> list = new ArrayList<Item>();
        if (text == null) return list;
        String[] lines = text.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            int sp = line.indexOf(' ');
            if (sp < 0) continue;
            String range = line.substring(0, sp).trim();
            String rest = line.substring(sp + 1).trim();
            String[] parts = rest.split("\\|");
            String title = parts.length > 0 ? parts[0].trim() : rest;
            String note = parts.length > 1 ? parts[1].trim() : "";
            String speech = parts.length > 2 ? parts[2].trim() : "";
            String[] se = range.split("-");
            if (se.length != 2) continue;
            int[] a = parseTime(se[0]);
            int[] b = parseTime(se[1]);
            if (a == null || b == null) continue;
            if (title.length() == 0) continue;
            list.add(new Item(a[0], b[0], title, note, speech));
        }
        return list;
    }

    public static String getPlanText(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getString(KEY_PLAN, DEFAULT_PLAN);
    }

    public static void setPlanText(Context c, String t) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_PLAN, t).apply();
    }

    public static String getPin(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_PIN, "1234");
    }

    public static int getAhead(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_AHEAD, 5);
    }

    public static boolean ttsOn(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_TTS_ON, true);
    }

    public static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().getTime());
    }

    public static boolean isDone(Context c, Item it) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_DONE_PREFIX + today() + "_" + it.key(), false);
    }

    public static void setDone(Context c, Item it, boolean v) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_DONE_PREFIX + today() + "_" + it.key(), v).apply();
    }
}
