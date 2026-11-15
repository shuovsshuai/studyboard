package com.family.studyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * 计划与状态存储。
 *
 * 计划行格式（每行一项，空格分隔）：
 *   类型 时刻 时长 星星 分类 名称|备注|播报语
 *   - 类型：随时（自己点开始，右列） / 定时（到点自动计时，左列） / 日程（只提示，左列） / 接送（提前10分钟提醒）
 *   - 时刻：HH:MM；「随时」写 - 即可
 *   - 时长：分钟，日程/接送可写 0
 *   - 星星：0 表示不给星
 *   - 分类：接送 学习 运动 娱乐 游戏 生活 其他（决定卡片颜色，可省略）
 * 例：
 *   接送 07:50 0 0 接送 出门上学|送大泽去学校|该出门了，别忘带书包
 *   定时 16:00 60 1 学习 写作业|今天的作业|该写作业了
 *   随时 - 30 3 学习 课外阅读|自己挑一本书|该阅读啦，读半小时
 */
public class PlanStore {

    public static final String PREF = "studyboard";
    public static final String KEY_PLAN = "plan_text";
    public static final String KEY_PIN = "pin";
    public static final String KEY_AHEAD = "ahead_min";
    public static final String KEY_TTS_ON = "tts_on";
    public static final String KEY_SFX_ON = "sfx_on";
    public static final String KEY_DAY = "day_json";
    public static final String KEY_DEFAULT_PLAN_SEED = "plan_seed";

    /** 任务类型 */
    public static final int T_FLEX = 0;   // 随时
    public static final int T_FIXED = 1;  // 定时
    public static final int T_EVENT = 2;  // 日程
    public static final int T_GO = 3;     // 接送

    /** 单项状态 */
    public static final int S_IDLE = 0;
    public static final int S_RUN = 1;
    public static final int S_PAUSE = 2;
    public static final int S_DONE = 3;
    public static final int S_MISS = 4;

    /** 分类与配色（与原型一致） */
    public static final String[] CATS = {"接送", "学习", "运动", "娱乐", "游戏", "生活", "其他"};
    public static final int[] CAT_COLORS = {
            0xFFEF4444, 0xFF3B82F6, 0xFF10B981, 0xFF8B5CF6,
            0xFFF59E0B, 0xFF94A3B8, 0xFF64748B
    };

    public static final int COLOR_BLUE = 0xFF3B82F6;
    public static final int COLOR_GREEN = 0xFF10B981;
    public static final int COLOR_AMBER = 0xFFF59E0B;
    public static final int COLOR_RED = 0xFFEF4444;
    public static final int COLOR_TEXT = 0xFF1F2937;
    public static final int COLOR_SUB = 0xFF9CA3AF;
    public static final int COLOR_FAINT = 0xFFC4C9D2;
    public static final int COLOR_CARD = 0xFFFFFFFF;
    public static final int COLOR_BG = 0xFFFFF9F2;
    public static final int COLOR_SOFT = 0xFFF3F4F6;
    public static final int COLOR_LINE = 0xFFE5E7EB;

    /** 默认计划：按用户实际作息（v3 定稿） */
    public static final String DEFAULT_PLAN =
            "接送 07:50 0 0 接送 出门上学|送大泽去学校|该出门了，别忘带书包\n" +
            "接送 10:50 0 0 接送 出门接大泽|提前到校门口等|要去接大泽了\n" +
            "接送 13:45 0 0 接送 出门上学|下午送大泽|该出门了\n" +
            "接送 15:30 0 0 接送 出门接大泽|提前到校门口等|要去接大泽了\n" +
            "定时 16:00 60 1 学习 写作业|今天的作业|该写作业了\n" +
            "日程 17:00 60 0 生活 吃晚饭||吃晚饭啦，慢慢吃\n" +
            "定时 18:00 60 0 游戏 玩游戏|固定一小时，可用★加时|游戏时间到\n" +
            "定时 19:00 30 2 学习 英语|跟读和单词|该读英语了\n" +
            "日程 20:00 30 0 生活 洗漱睡觉||该洗漱了，准备睡觉\n" +
            "随时 - 30 3 学习 课外阅读|自己挑一本书|该阅读啦，读半小时\n" +
            "随时 - 20 1 学习 数学练习|口算或练习册一页|该做数学了\n" +
            "随时 - 10 1 学习 背古诗|背会一首得1★|来背一首古诗\n" +
            "随时 - 30 1 娱乐 看B站|半小时，到点自己关|可以看会儿B站\n" +
            "随时 - 25 2 运动 打篮球|投篮或运球练习|去打球吧\n" +
            "随时 - 10 2 运动 跳绳200个|分两组也行|跳绳两百个";

    // ---------------- 计划项 ----------------

    public static class Item {
        public int type;
        public int start;   // 分钟，FLEX 为 -1
        public int dur;     // 分钟
        public int stars;
        public String cat;
        public String title;
        public String note;
        public String speech;

        public Item(int type, int start, int dur, int stars, String cat,
                    String title, String note, String speech) {
            this.type = type;
            this.start = start;
            this.dur = dur;
            this.stars = stars;
            this.cat = cat == null ? "其他" : cat;
            this.title = title;
            this.note = note == null ? "" : note;
            this.speech = speech == null ? "" : speech;
        }

        public String key() {
            return type + "_" + start + "_" + title;
        }

        public boolean isFlex() {
            return type == T_FLEX;
        }

        public int end() {
            return start < 0 ? -1 : start + Math.max(0, dur);
        }

        public int color() {
            return catColor(cat);
        }

        public String range() {
            if (start < 0) return "随时";
            if (dur <= 0) return fmt(start);
            return fmt(start) + "-" + fmt(end());
        }
    }

    /** 单项当日状态 */
    public static class St {
        public int st = S_IDLE;
        public int remain;      // 剩余秒（暂停/空闲时有效）
        public long endAt;      // 运行中：结束时间戳
        public int paused;      // 暂停次数
        public int got;         // 今日已得星（>0 表示已拿过奖励）
        public int warned;      // 最后一分钟已提醒
    }

    /** 当日账本 */
    public static class Day {
        public String date = "";
        public int balance = 0;
        public int earned = 0;
        public int gameBonus = 0;
        public HashMap<String, St> map = new HashMap<String, St>();
        public HashMap<String, Integer> goAlerted = new HashMap<String, Integer>();
        public HashMap<String, Integer> goNow = new HashMap<String, Integer>();
        public HashMap<String, Integer> evAlerted = new HashMap<String, Integer>();
    }

    public static int catColor(String cat) {
        for (int i = 0; i < CATS.length; i++) {
            if (CATS[i].equals(cat)) return CAT_COLORS[i];
        }
        return CAT_COLORS[CAT_COLORS.length - 1];
    }

    public static boolean isCat(String s) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(s)) return true;
        return false;
    }

    // ---------------- 解析 ----------------

    public static String fmt(int min) {
        return String.format(Locale.US, "%02d:%02d", (min / 60) % 24, min % 60);
    }

    public static int[] parseTime(String s) {
        try {
            String[] p = s.trim().split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return new int[]{h * 60 + m};
        } catch (Exception e) {
            return null;
        }
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    public static List<Item> parse(String text) {
        List<Item> list = new ArrayList<Item>();
        if (text == null) return list;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            String[] tok = line.split("\\s+", 6);
            if (tok.length < 5) continue;

            int type;
            String t0 = tok[0];
            if (t0.startsWith("随时") || t0.equalsIgnoreCase("flex")) type = T_FLEX;
            else if (t0.startsWith("定时") || t0.equalsIgnoreCase("fix")) type = T_FIXED;
            else if (t0.startsWith("日程") || t0.equalsIgnoreCase("event")) type = T_EVENT;
            else if (t0.startsWith("接送") || t0.equalsIgnoreCase("go")) type = T_GO;
            else continue;

            int start = -1;
            if (!"-".equals(tok[1])) {
                int[] a = parseTime(tok[1]);
                if (a == null) continue;
                start = a[0];
            }
            if (type != T_FLEX && start < 0) continue;

            int dur = num(tok[2]);
            int stars = num(tok[3]);
            if (dur < 0) dur = 0;
            if (stars < 0) stars = 0;

            String cat = "其他";
            String rest;
            if (tok.length >= 6 && isCat(tok[4])) {
                cat = tok[4];
                rest = tok[5];
            } else {
                rest = tok[4];
                if (type == T_GO) cat = "接送";
                else if (type == T_FIXED || type == T_FLEX) cat = "学习";
                else cat = "生活";
            }

            String[] parts = rest.split("\\|");
            String title = parts[0].trim();
            if (title.length() == 0) continue;
            String note = parts.length > 1 ? parts[1].trim() : "";
            String speech = parts.length > 2 ? parts[2].trim() : "";
            list.add(new Item(type, start, dur, stars, cat, title, note, speech));
        }
        return list;
    }

    // ---------------- 配置 ----------------

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String getPlanText(Context c) {
        SharedPreferences p = sp(c);
        // 计划结构升级一次：用户没自定义过就换到新默认（带接送/分类）
        int seed = p.getInt(KEY_DEFAULT_PLAN_SEED, 0);
        if (seed < 3) {
            String cur = p.getString(KEY_PLAN, null);
            if (cur == null || cur.indexOf("接送 ") < 0) {
                p.edit().putString(KEY_PLAN, DEFAULT_PLAN).putInt(KEY_DEFAULT_PLAN_SEED, 3).apply();
            } else {
                p.edit().putInt(KEY_DEFAULT_PLAN_SEED, 3).apply();
            }
        }
        return p.getString(KEY_PLAN, DEFAULT_PLAN);
    }

    public static void setPlanText(Context c, String t) {
        sp(c).edit().putString(KEY_PLAN, t).putInt(KEY_DEFAULT_PLAN_SEED, 3).apply();
    }

    public static void resetPlan(Context c) {
        sp(c).edit().putString(KEY_PLAN, DEFAULT_PLAN).putInt(KEY_DEFAULT_PLAN_SEED, 3).apply();
    }

    public static String getPin(Context c) {
        return sp(c).getString(KEY_PIN, "1234");
    }

    public static int getAhead(Context c) {
        return sp(c).getInt(KEY_AHEAD, 10);
    }

    public static boolean ttsOn(Context c) {
        return sp(c).getBoolean(KEY_TTS_ON, true);
    }

    public static void setTtsOn(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_TTS_ON, on).apply();
    }

    public static boolean sfxOn(Context c) {
        return sp(c).getBoolean(KEY_SFX_ON, true);
    }

    public static void setSfxOn(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_SFX_ON, on).apply();
    }

    public static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().getTime());
    }

    // ---------------- 当日账本 ----------------

    public static Day loadDay(Context c) {
        Day d = new Day();
        String raw = sp(c).getString(KEY_DAY, "");
        if (raw != null && raw.length() > 0) {
            try {
                JSONObject o = new JSONObject(raw);
                d.date = o.optString("d", "");
                d.balance = o.optInt("b", 0);
                d.earned = o.optInt("e", 0);
                d.gameBonus = o.optInt("g", 0);
                JSONObject s = o.optJSONObject("s");
                if (s != null) {
                    java.util.Iterator<String> it = s.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject so = s.optJSONObject(k);
                        if (so == null) continue;
                        St st = new St();
                        st.st = so.optInt("st", S_IDLE);
                        st.remain = so.optInt("r", 0);
                        st.endAt = so.optLong("ea", 0);
                        st.paused = so.optInt("p", 0);
                        st.got = so.optInt("got", 0);
                        st.warned = so.optInt("w", 0);
                        d.map.put(k, st);
                    }
                }
                readFlags(o.optJSONObject("ga"), d.goAlerted);
                readFlags(o.optJSONObject("gn"), d.goNow);
                readFlags(o.optJSONObject("ev"), d.evAlerted);
            } catch (Exception ignored) {
            }
        }
        String today = today();
        if (!today.equals(d.date)) {
            d.date = today;
            d.earned = 0;
            d.gameBonus = 0;
            d.map.clear();
            d.goAlerted.clear();
            d.goNow.clear();
            d.evAlerted.clear();
        }
        return d;
    }

    private static void readFlags(JSONObject o, HashMap<String, Integer> to) {
        if (o == null) return;
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            to.put(k, 1);
        }
    }

    private static JSONObject writeFlags(HashMap<String, Integer> m) {
        JSONObject o = new JSONObject();
        try {
            for (String k : m.keySet()) o.put(k, 1);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static void saveDay(Context c, Day d) {
        try {
            JSONObject o = new JSONObject();
            o.put("d", d.date);
            o.put("b", d.balance);
            o.put("e", d.earned);
            o.put("g", d.gameBonus);
            JSONObject s = new JSONObject();
            for (String k : d.map.keySet()) {
                St st = d.map.get(k);
                JSONObject so = new JSONObject();
                so.put("st", st.st);
                so.put("r", st.remain);
                so.put("ea", st.endAt);
                so.put("p", st.paused);
                so.put("got", st.got);
                so.put("w", st.warned);
                s.put(k, so);
            }
            o.put("s", s);
            o.put("ga", writeFlags(d.goAlerted));
            o.put("gn", writeFlags(d.goNow));
            o.put("ev", writeFlags(d.evAlerted));
            sp(c).edit().putString(KEY_DAY, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static St st(Day d, Item it) {
        St st = d.map.get(it.key());
        if (st == null) {
            st = new St();
            st.remain = Math.max(0, it.dur) * 60;
            d.map.put(it.key(), st);
        }
        return st;
    }

    /** 今日应得 / 已得 / 已完成项数 / 可计星项数 */
    public static int[] summary(List<Item> items, Day d) {
        int total = 0, got = 0, done = 0, countable = 0;
        for (Item it : items) {
            if (it.type == T_EVENT || it.type == T_GO || it.stars <= 0) continue;
            countable++;
            total += it.stars;
            St s = d.map.get(it.key());
            if (s != null) {
                got += s.got;
                if (s.st == S_DONE) done++;
            }
        }
        return new int[]{total, got, done, countable};
    }

    /** 某列完成情况 */
    public static int[] columnStat(List<Item> list, Day d) {
        int done = 0, all = 0;
        for (Item it : list) {
            if (it.type == T_EVENT || it.type == T_GO) continue;
            all++;
            St s = d.map.get(it.key());
            if (s != null && s.st == S_DONE) done++;
        }
        return new int[]{done, all};
    }
}
