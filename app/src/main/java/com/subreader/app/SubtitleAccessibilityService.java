package com.subreader.app;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class SubtitleAccessibilityService extends AccessibilityService implements TextToSpeech.OnInitListener {

    // Không xóa seenLines bao giờ (trừ khi user bấm clear).
    // Tránh BUG 1: câu cũ bị xóa khỏi history rồi vào zone lại → đọc lại.
    // Dùng LinkedHashSet không giới hạn — bộ nhớ không đáng kể (vài KB cho cả buổi xem phim).
    private static final int CATCH_UP_MAX_RATE = 4;

    private static SubtitleAccessibilityService instance;
    private AudioManager audioManager;
    private TextToSpeech tts;
    public static boolean isActive = false;
    public static float speechRate = 1.0f;
    public static float maxRate = 2.5f;
    public static float speechPitch = 1.0f;
    public static float speechVolume = 0.8f;
    public static boolean filterShort = true;
    public static int readDelay = 0;
    public static Voice selectedVoice = null;
    public static Locale selectedLocale = Locale.ENGLISH;
    public static String savedVoiceName = "";
    public static int zoneTop = 60;
    public static int zoneBottom = 100;
    public static int zoneLeft = 0;
    public static int zoneRight = 100;
    public static boolean useZone = false;

    // Tất cả dòng đã từng thêm vào queue — không bao giờ trim trong session.
    // FIX BUG 1: không xóa theo HISTORY_SIZE nữa.
    private final LinkedHashSet<String> seenLines = new LinkedHashSet<>();

    // Hàng đợi app-level
    private final List<String> readQueue = new ArrayList<>();

    // FIX BUG 3: dùng boolean riêng biệt, chỉ set trên main thread qua Handler
    // isSpeaking chỉ được đọc/ghi trên main thread → không có race condition
    private boolean isSpeaking = false;
    private boolean ttsReady = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Snapshot zone: hash của danh sách dòng hiện tại
    // FIX BUG 4: dùng hash thay vì join bằng \n để tránh collision khi text có \n
    private int lastZoneHash = 0;

    // Snapshot zone sau clearHistory để bỏ qua các dòng đang hiển thị lúc resume
    // FIX BUG 2: khi resume, bơm zone hiện tại vào seenLines trước để không đọc lại
    private boolean pendingZoneFlush = false;

    public static SubtitleAccessibilityService getInstance() { return instance; }

    @Override
    public void onServiceConnected() {
        instance = this;
        audioManager = (AudioManager) getSystemService("audio");
        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status != 0) return;
        tts.setAudioAttributes(
            new AudioAttributes.Builder().setUsage(4).setContentType(1).build()
        );
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            // Các callback này đến từ TTS thread — chỉ post lên main thread, không đụng state trực tiếp
            @Override public void onStart(String id) {
                handler.post(new Runnable() { @Override public void run() { isSpeaking = true; } });
            }
            @Override public void onDone(String id) {
                handler.post(new Runnable() { @Override public void run() { isSpeaking = false; drainQueue(); } });
            }
            @Override public void onError(String id) {
                handler.post(new Runnable() { @Override public void run() { isSpeaking = false; drainQueue(); } });
            }
        });
        applyTtsSettings();
        ttsReady = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isActive || !ttsReady) return;
        int type = event.getEventType();
        if (type != 2048 && type != 16 && type != 32) return;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        List<String> zoneLines = new ArrayList<>();

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null && !windows.isEmpty()) {
            for (AccessibilityWindowInfo win : windows) {
                AccessibilityNodeInfo root = win.getRoot();
                if (root != null) { collectSubtitleText(root, zoneLines, dm); root.recycle(); }
            }
        } else {
            AccessibilityNodeInfo root2 = getRootInActiveWindow();
            if (root2 != null) { collectSubtitleText(root2, zoneLines, dm); root2.recycle(); }
        }
        if (zoneLines.isEmpty()) return;

        // FIX BUG 4: hash dựa trên từng dòng riêng biệt, không join chuỗi
        int hash = zoneLines.hashCode();
        if (hash == lastZoneHash) return;
        lastZoneHash = hash;

        // FIX BUG 2: nếu vừa clearHistory/resume, đánh dấu tất cả dòng đang hiển thị
        // là "đã seen" mà không đọc — để không đọc lại sub cũ khi resume
        if (pendingZoneFlush) {
            pendingZoneFlush = false;
            for (String line : zoneLines) seenLines.add(line);
            return;
        }

        boolean added = false;
        for (String line : zoneLines) {
            if (filterShort && line.length() < 3) continue;
            if (!LangDetector.matches(line, selectedLocale)) continue;
            if (seenLines.contains(line)) continue;

            seenLines.add(line);
            readQueue.add(line);
            added = true;
        }

        if (added) drainQueue();
    }

    /**
     * Kéo câu đầu queue ra đọc — chỉ chạy trên main thread.
     * FIX BUG 3: isSpeaking chỉ được set trên main thread (qua handler.post),
     * drainQueue cũng chỉ chạy trên main thread → không có race condition.
     */
    private void drainQueue() {
        // Tất cả điều kiện check trên main thread — an toàn, không race
        if (isSpeaking || !ttsReady || !isActive || readQueue.isEmpty()) return;

        String next = readQueue.remove(0);
        int backlog = readQueue.size();

        // Tăng tốc theo backlog: +30% mỗi câu tồn đọng
        float boost = 1.0f + backlog * 0.3f;
        float rate  = Math.min(speechRate * boost, Math.min(maxRate, CATCH_UP_MAX_RATE));
        tts.setSpeechRate(rate);

        // isSpeaking = true NGAY TRƯỚC khi gọi tts.speak()
        // Không chờ onStart() callback (đến muộn hơn, từ thread khác)
        // → đảm bảo drainQueue() không chạy lại trước khi TTS thực sự bắt đầu
        isSpeaking = true;
        tts.speak(next, TextToSpeech.QUEUE_FLUSH, null, "sub_" + System.currentTimeMillis());

        final String display = next;
        if (MainActivity.staticLastSub != null) {
            handler.post(new Runnable() {
                @Override public void run() { MainActivity.staticLastSub.setText(display); }
            });
        }
    }

    private void collectSubtitleText(AccessibilityNodeInfo node, List<String> out, DisplayMetrics dm) {
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            String viewId = node.getViewIdResourceName();
            String cls = node.getClassName() != null ? node.getClassName().toString() : "";
            boolean match = false;
            if (viewId != null) {
                String id = viewId.toLowerCase(Locale.ROOT);
                if (id.contains("subtitle") || id.contains("caption") || id.contains("sub_text")
                        || id.contains("exo_sub") || id.contains("mpv_sub") || id.contains("vlc_sub")) {
                    match = true;
                }
            }
            if (!match && cls.contains("TextView")) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                int len = text.length();
                if (useZone) {
                    int topPx    = (dm.heightPixels * zoneTop)    / 100;
                    int bottomPx = (dm.heightPixels * zoneBottom) / 100;
                    int leftPx   = (dm.widthPixels  * zoneLeft)   / 100;
                    int rightPx  = (dm.widthPixels  * zoneRight)  / 100;
                    if (bounds.top >= topPx && bounds.bottom <= bottomPx
                            && bounds.left >= leftPx && bounds.right <= rightPx
                            && len >= 3 && len <= 300) {
                        match = true;
                    }
                } else if (bounds.top > dm.heightPixels * 0.55f && len >= 3 && len <= 300) {
                    match = true;
                }
            }
            if (match) {
                String t = text.toString().trim();
                if (!t.isEmpty() && !out.contains(t)) out.add(t);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectSubtitleText(child, out, dm);
            if (child != null) child.recycle();
        }
    }

    public void testSpeak(String text) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "test");
    }

    public static void clearHistory() {
        SubtitleAccessibilityService inst = getInstance();
        if (inst == null) return;
        inst.seenLines.clear();
        inst.readQueue.clear();
        inst.lastZoneHash = 0;
        // FIX BUG 2: đánh dấu cần flush zone hiện tại ở event tiếp theo
        inst.pendingZoneFlush = true;
        if (inst.ttsReady && inst.tts != null) {
            inst.tts.stop();
            inst.isSpeaking = false;
        }
    }

    public static void applyTtsSettings() {
        SubtitleAccessibilityService inst = getInstance();
        if (inst == null || !inst.ttsReady) return;
        if (selectedVoice != null) inst.tts.setVoice(selectedVoice);
        else if (selectedLocale != null) inst.tts.setLanguage(selectedLocale);
        inst.tts.setSpeechRate(speechRate);
        inst.tts.setPitch(speechPitch);
        AudioManager am = inst.audioManager;
        if (am != null) {
            int maxVol = am.getStreamMaxVolume(4);
            am.setStreamVolume(4, Math.round(maxVol * speechVolume), 1);
        }
    }

    @Override public void onInterrupt() { if (tts != null) tts.stop(); }

    @Override
    public void onDestroy() {
        instance = null;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
