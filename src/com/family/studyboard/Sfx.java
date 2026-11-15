package com.family.studyboard;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * 提示音合成与播放。
 *
 * 不用音频资源文件，直接用正弦波合成短音效：
 *   TIP   日程/一般提醒   「叮—咚」下行两声
 *   GO    接送提醒        门铃（两声重复，更醒目）
 *   START 任务开始        上行三音
 *   DONE  任务完成        上行三音 + 长尾音
 *
 * 好处：APK 不增加资源、不受 aapt2 打包影响、可随时调音高。
 */
public class Sfx {

    public static final int TIP = 0;
    public static final int GO = 1;
    public static final int START = 2;
    public static final int DONE = 3;

    private static final int SR = 22050;

    private static final float C5 = 523.25f;
    private static final float E5 = 659.25f;
    private static final float G5 = 783.99f;
    private static final float C6 = 1046.50f;
    private static final float E6 = 1318.51f;
    private static final float G6 = 1568.00f;

    private static AudioTrack playing;

    /** 音效大致时长（毫秒），用于安排 TTS 之后开口 */
    public static int durationMs(int kind) {
        switch (kind) {
            case GO: return 900;
            case START: return 320;
            case DONE: return 420;
            case TIP:
            default: return 320;
        }
    }

    public static void play(final Context c, final int kind) {
        if (!PlanStore.sfxOn(c)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                playNow(kind);
            }
        }).start();
    }

    private static synchronized void playNow(int kind) {
        short[] pcm = build(kind);
        if (pcm == null || pcm.length == 0) return;
        AudioTrack at = null;
        try {
            stopLast();
            int bytes = pcm.length * 2;
            at = new AudioTrack(AudioManager.STREAM_MUSIC, SR,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bytes, AudioTrack.MODE_STATIC);
            at.write(pcm, 0, pcm.length);
            playing = at;
            at.play();
            Thread.sleep(pcm.length * 1000L / SR + 60);
            try {
                at.stop();
            } catch (Throwable ignored) {
            }
            if (playing == at) playing = null;
            at.release();
        } catch (Throwable t) {
            if (at != null) {
                try {
                    at.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void stopLast() {
        if (playing != null) {
            try {
                playing.stop();
                playing.release();
            } catch (Throwable ignored) {
            }
            playing = null;
        }
    }

    private static short[] build(int kind) {
        switch (kind) {
            case GO: {
                short[] a = concat(new float[][]{
                        {E6, 0.16f}, {C6, 0.22f}, {0f, 0.14f},
                        {E6, 0.16f}, {C6, 0.26f}
                }, new float[]{0.55f, 0.5f, 0f, 0.55f, 0.5f});
                return a;
            }
            case START:
                return concat(new float[][]{{C5, 0.08f}, {E5, 0.08f}, {G5, 0.16f}},
                        new float[]{0.5f, 0.5f, 0.55f});
            case DONE:
                return concat(new float[][]{{C6, 0.07f}, {E6, 0.07f}, {G6, 0.26f}},
                        new float[]{0.5f, 0.5f, 0.6f});
            case TIP:
            default:
                return concat(new float[][]{{C6, 0.10f}, {G5, 0.20f}},
                        new float[]{0.5f, 0.48f});
        }
    }

    /** 按「频率,时长」序列合成，间隔不加静音（0 频率即静音段） */
    private static short[] concat(float[][] notes, float[] vols) {
        int total = 0;
        int[] lens = new int[notes.length];
        for (int i = 0; i < notes.length; i++) {
            lens[i] = (int) (notes[i][1] * SR);
            total += lens[i];
        }
        short[] out = new short[total];
        int at = 0;
        for (int i = 0; i < notes.length; i++) {
            float f = notes[i][0];
            float v = vols[i];
            int n = lens[i];
            if (f > 0) {
                double w = 2 * Math.PI * f / SR;
                int attack = Math.max(1, SR / 100);   // 10ms 淡入，防爆音
                for (int k = 0; k < n; k++) {
                    double env = Math.exp(-2.6 * k / n);
                    if (k < attack) env *= (double) k / attack;
                    out[at + k] = (short) (Math.sin(w * k) * env * v * Short.MAX_VALUE);
                }
            }
            at += n;
        }
        return out;
    }
}
