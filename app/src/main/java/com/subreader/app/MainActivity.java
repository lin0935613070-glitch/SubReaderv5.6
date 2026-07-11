package com.subreader.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private Button   btnToggle, btnTest, btnFloat, btnSetZone, btnClearHistory;
    private TextView tvStatus, tvLastSub;
    private SeekBar  sbSpeed, sbPitch, sbVolume, sbAlpha, sbMaxRate;
    private TextView tvSpeed, tvPitch, tvVolume, tvAlpha, tvMaxRate;
    private Switch   swFilter, swUseZone, swShowOverlay;
    private EditText etDelay;
    private Spinner  spLanguage, spVoice;

    public static TextView staticLastSub;

    private TextToSpeech scanTts;
    private final List<Locale> availableLocales = new ArrayList<>();
    private final List<Voice>  availableVoices  = new ArrayList<>();
    private final List<String> localeLabels     = new ArrayList<>();
    private final List<String> voiceLabels      = new ArrayList<>();

    // Cờ ngăn listener kích hoạt khi đang set giá trị vào UI
    private boolean uiReady = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // B1: Load cài đặt đã lưu vào static fields TRƯỚC KHI làm gì khác
        Prefs.load(this);

        setContentView(R.layout.activity_main);

        // B2: Bind views
        btnToggle       = findViewById(R.id.btnToggle);
        btnTest         = findViewById(R.id.btnTest);
        btnFloat        = findViewById(R.id.btnFloat);
        btnSetZone      = findViewById(R.id.btnSetZone);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        tvStatus        = findViewById(R.id.tvStatus);
        tvLastSub       = findViewById(R.id.tvLastSub);
        sbSpeed         = findViewById(R.id.sbSpeed);
        sbPitch         = findViewById(R.id.sbPitch);
        sbVolume        = findViewById(R.id.sbVolume);
        sbAlpha         = findViewById(R.id.sbAlpha);
        sbMaxRate       = findViewById(R.id.sbMaxRate);
        tvMaxRate       = findViewById(R.id.tvMaxRate);
        tvSpeed         = findViewById(R.id.tvSpeed);
        tvPitch         = findViewById(R.id.tvPitch);
        tvVolume        = findViewById(R.id.tvVolume);
        tvAlpha         = findViewById(R.id.tvAlpha);
        swFilter        = findViewById(R.id.swFilter);
        swUseZone       = findViewById(R.id.swUseZone);
        swShowOverlay   = findViewById(R.id.swShowOverlay);
        etDelay         = findViewById(R.id.etDelay);
        spLanguage      = findViewById(R.id.spLanguage);
        spVoice         = findViewById(R.id.spVoice);
        staticLastSub   = tvLastSub;

        // B3: Đổ giá trị đã load vào UI — uiReady=false nên listener KHÔNG lưu gì cả
        uiReady = false;
        sbSpeed.setMax(20);
        sbSpeed.setProgress(Math.round(SubtitleAccessibilityService.speechRate * 10));
        sbPitch.setMax(20);
        sbPitch.setProgress(Math.round(SubtitleAccessibilityService.speechPitch * 10));
        sbVolume.setMax(100);
        sbVolume.setProgress(Math.round(SubtitleAccessibilityService.speechVolume * 100));
        sbAlpha.setMax(255);
        sbAlpha.setProgress(FloatingService.overlayAlpha);
        swFilter.setChecked(SubtitleAccessibilityService.filterShort);
        swUseZone.setChecked(SubtitleAccessibilityService.useZone);
        swShowOverlay.setChecked(FloatingService.showSubOverlay);
        etDelay.setText(SubtitleAccessibilityService.readDelay > 0
                ? String.valueOf(SubtitleAccessibilityService.readDelay) : "");
        updateLabels();
        findViewById(R.id.layoutZone).setVisibility(
                SubtitleAccessibilityService.useZone ? View.VISIBLE : View.GONE);

        // B4: Gắn listeners SAU KHI đã set giá trị
        uiReady = true;

        sbSpeed.setOnSeekBarChangeListener(seekListener);
        sbPitch.setOnSeekBarChangeListener(seekListener);
        sbVolume.setOnSeekBarChangeListener(seekListener);
        sbAlpha.setOnSeekBarChangeListener(seekListener);
        sbMaxRate.setOnSeekBarChangeListener(seekListener);

        swFilter.setOnCheckedChangeListener((b, v) -> {
            if (!uiReady) return;
            SubtitleAccessibilityService.filterShort = v;
            Prefs.save(this);
        });

        swUseZone.setOnCheckedChangeListener((b, checked) -> {
            if (!uiReady) return;
            SubtitleAccessibilityService.useZone = checked;
            findViewById(R.id.layoutZone).setVisibility(checked ? View.VISIBLE : View.GONE);
            Prefs.save(this);
        });

        swShowOverlay.setOnCheckedChangeListener((b, checked) -> {
            if (!uiReady) return;
            FloatingService.showSubOverlay = checked;
            FloatingService fi = FloatingService.getInstance();
            if (fi != null) {
                if (checked) fi.showZoneFrame();
                else         fi.removeZoneFramePublic();
            }
            Prefs.save(this);
        });

        // B5: Buttons
        btnToggle.setOnClickListener(v -> {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "Vui lòng bật Accessibility Service", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } else {
                SubtitleAccessibilityService.isActive = !SubtitleAccessibilityService.isActive;
                if (SubtitleAccessibilityService.isActive) SubtitleAccessibilityService.clearHistory();
                Prefs.save(this);
                updateStatus();
            }
        });

        btnTest.setOnClickListener(v -> {
            SubtitleAccessibilityService inst = SubtitleAccessibilityService.getInstance();
            if (inst != null) inst.testSpeak("This is a subtitle test. Thử nghiệm đọc phụ đề.");
            else Toast.makeText(this, "Hãy bật Accessibility Service trước", Toast.LENGTH_SHORT).show();
        });

        btnFloat.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                Toast.makeText(this, "Cấp quyền 'Hiển thị trên ứng dụng khác' rồi quay lại", Toast.LENGTH_LONG).show();
            } else {
                startService(new Intent(this, FloatingService.class));
                moveTaskToBack(true);
            }
        });

        btnSetZone.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                Toast.makeText(this, "Cấp quyền rồi quay lại", Toast.LENGTH_LONG).show();
                return;
            }
            startService(new Intent(this, FloatingService.class));
            handler.postDelayed(() -> {
                FloatingService fi = FloatingService.getInstance();
                if (fi != null) fi.enterZonePicker();
            }, 400);
            moveTaskToBack(true);
            Toast.makeText(this, "1 ngón kéo • 2 ngón phóng to/thu nhỏ • Xong để lưu", Toast.LENGTH_LONG).show();
        });

        btnClearHistory.setOnClickListener(v -> {
            SubtitleAccessibilityService.clearHistory();
            Toast.makeText(this, "Đã xóa lịch sử — sẽ đọc lại từ đầu", Toast.LENGTH_SHORT).show();
        });

        // B6: Scan giọng TTS — chạy async, khi xong tự điền spinner và khôi phục
        spLanguage.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Đang tải..."}));
        spVoice.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Đang tải..."}));

        scanTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) runOnUiThread(this::loadAndRestoreVoice);
        });

        // Khi đổi ngôn ngữ thủ công
        spLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!uiReady || pos >= availableLocales.size()) return;
                Locale loc = availableLocales.get(pos);
                SubtitleAccessibilityService.selectedLocale = loc;
                fillVoiceSpinner(loc, ""); // reset về giọng đầu tiên
                Prefs.save(MainActivity.this);
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });

        // Khi đổi giọng thủ công
        spVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!uiReady || pos >= availableVoices.size()) return;
                Voice picked = availableVoices.get(pos);
                SubtitleAccessibilityService.selectedVoice   = picked;
                SubtitleAccessibilityService.savedVoiceName  = picked.getName();
                SubtitleAccessibilityService.selectedLocale  = picked.getLocale();
                SubtitleAccessibilityService.applyTtsSettings();
                Prefs.save(MainActivity.this);
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });

        updateStatus();
    }

    // ── Scan TTS rồi khôi phục đúng ngôn ngữ + giọng đã lưu ─────────────
    private void loadAndRestoreVoice() {
        try {
            Set<Voice> voices = scanTts.getVoices();
            if (voices == null) return;

            // Thu thập locale
            List<Locale> locales = new ArrayList<>();
            for (Voice vv : voices)
                if (!locales.contains(vv.getLocale())) locales.add(vv.getLocale());
            locales.sort((a, b) -> {
                int pa = langPriority(a), pb = langPriority(b);
                return pa != pb ? pa - pb : a.getDisplayName().compareTo(b.getDisplayName());
            });
            availableLocales.clear(); localeLabels.clear();
            for (Locale loc : locales) {
                availableLocales.add(loc);
                localeLabels.add(loc.getDisplayName(Locale.getDefault()));
            }

            // Tìm ngôn ngữ đã lưu
            Locale savedLocale = SubtitleAccessibilityService.selectedLocale;
            int selLang = 0;
            for (int i = 0; i < availableLocales.size(); i++) {
                if (savedLocale != null
                        && availableLocales.get(i).getLanguage().equals(savedLocale.getLanguage())) {
                    selLang = i;
                    break;
                }
            }

            // Set spinner ngôn ngữ — tắt uiReady
            uiReady = false;
            spLanguage.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, localeLabels));
            spLanguage.setSelection(selLang);
            uiReady = true;

            // Điền giọng và chọn lại giọng đã lưu
            fillVoiceSpinner(availableLocales.get(selLang),
                    SubtitleAccessibilityService.savedVoiceName);

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi tải giọng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ── Điền spinner giọng, khôi phục savedVoiceName nếu có ──────────────
    private void fillVoiceSpinner(Locale locale, String savedVoiceName) {
        try {
            Set<Voice> voices = scanTts.getVoices();
            if (voices == null) return;

            availableVoices.clear();
            voiceLabels.clear();

            for (Voice vv : voices) {
                if (!vv.getLocale().getLanguage().equals(locale.getLanguage())) continue;
                availableVoices.add(vv);
                String n = vv.getName().toLowerCase(Locale.ROOT);
                String gender = n.contains("female") ? "👩 Nữ"
                              : n.contains("male")   ? "👨 Nam" : "🔈";
                String q = vv.getQuality() >= Voice.QUALITY_VERY_HIGH ? " ★★★"
                         : vv.getQuality() >= Voice.QUALITY_HIGH      ? " ★★"  : " ★";
                voiceLabels.add(gender + " " + vv.getName().replaceAll(".*#", "").replace("-", " ") + q);
            }

            if (availableVoices.isEmpty()) return;

            // Tìm giọng đã lưu — so sánh tên chính xác
            int selVoice = 0;
            for (int i = 0; i < availableVoices.size(); i++) {
                if (availableVoices.get(i).getName().equals(savedVoiceName)) {
                    selVoice = i;
                    break;
                }
            }

            // Set spinner — tắt uiReady
            uiReady = false;
            spVoice.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, voiceLabels));
            spVoice.setSelection(selVoice);
            uiReady = true;

            // Áp giọng vào TTS ngay lập tức
            Voice toApply = availableVoices.get(selVoice);
            SubtitleAccessibilityService.selectedVoice  = toApply;
            SubtitleAccessibilityService.savedVoiceName = toApply.getName();
            SubtitleAccessibilityService.selectedLocale = toApply.getLocale();
            SubtitleAccessibilityService.applyTtsSettings();

        } catch (Exception ignored) {}
    }

    private int langPriority(Locale l) {
        if (l.getLanguage().equals("en")) return 0;
        if (l.getLanguage().equals("vi")) return 1;
        return 2;
    }

    // ── SeekBar listener — lưu ngay khi kéo ──────────────────────────────
    private final SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
            updateLabels();
            if (!uiReady || !fromUser) return; // fromUser=false khi set bằng code → bỏ qua
            SubtitleAccessibilityService.speechRate   = sbSpeed.getProgress()  / 10f;
            SubtitleAccessibilityService.speechPitch  = sbPitch.getProgress()  / 10f;
            SubtitleAccessibilityService.speechVolume = sbVolume.getProgress() / 100f;
            FloatingService.overlayAlpha              = sbAlpha.getProgress();
        SubtitleAccessibilityService.maxRate      = sbMaxRate.getProgress() / 10f;
            SubtitleAccessibilityService.applyTtsSettings();
            FloatingService fi = FloatingService.getInstance();
            if (fi != null) fi.updateOverlayAlpha();
            Prefs.save(MainActivity.this);
        }
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) {}
    };

    private void updateLabels() {
        tvSpeed.setText(String.format("Tốc độ: %.1fx",   sbSpeed.getProgress()  / 10f));
        tvPitch.setText(String.format("Cao độ: %.1f",    sbPitch.getProgress()  / 10f));
        tvVolume.setText(String.format("Âm lượng: %d%%", sbVolume.getProgress()));
        tvAlpha.setText(String.format("Độ mờ vùng đọc: %d%%", sbAlpha.getProgress() * 100 / 255));
        tvMaxRate.setText(String.format("Tốc độ tối đa auto: %.1fx", sbMaxRate.getProgress() / 10f));
    }

    private void updateStatus() {
        if (!isAccessibilityEnabled()) {
            tvStatus.setText("⚠️ Accessibility chưa bật");
            tvStatus.setTextColor(0xFFFF6B35);
            btnToggle.setText("Mở cài đặt Accessibility");
        } else if (SubtitleAccessibilityService.isActive) {
            tvStatus.setText("🔊 Đang đọc sub tự động...");
            tvStatus.setTextColor(0xFF4CAF50);
            btnToggle.setText("Tắt đọc sub");
        } else {
            tvStatus.setText("⏸ Đã tắt");
            tvStatus.setTextColor(0xFF888888);
            btnToggle.setText("Bật đọc sub");
        }
    }

    private boolean isAccessibilityEnabled() {
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return !TextUtils.isEmpty(s) && s.contains(getPackageName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        // Sync lại UI từ static fields (FloatingService có thể đã thay đổi khi app ở nền)
        if (uiReady) {
            uiReady = false;
            swUseZone.setChecked(SubtitleAccessibilityService.useZone);
            swShowOverlay.setChecked(FloatingService.showSubOverlay);
            sbAlpha.setProgress(FloatingService.overlayAlpha);
            sbMaxRate.setProgress(Math.round(SubtitleAccessibilityService.maxRate * 10));
            uiReady = true;
        }
    }

    @Override
    protected void onDestroy() {
        if (scanTts != null) { scanTts.stop(); scanTts.shutdown(); }
        super.onDestroy();
    }
}
