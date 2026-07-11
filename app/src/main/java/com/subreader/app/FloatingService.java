package com.subreader.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.Nullable;

public class FloatingService extends Service {

    public static boolean showSubOverlay = true;
    public static int     overlayAlpha  = 80;
    public static int     overlayColor  = 0xFF1DB954;

    private WindowManager wm;
    private DisplayMetrics dm;

    // Nút nổi
    private View floatView;
    private WindowManager.LayoutParams floatParams;

    // Overlay chế độ bình thường (không touch)
    private View zoneFrameView;

    // Overlay chế độ picker (toàn màn hình, bắt touch)
    private ZonePickerView zonePickerView;
    private boolean zonePickerActive = false;

    private static FloatingService instance;
    public static FloatingService getInstance() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        dm = getResources().getDisplayMetrics();
        createFloatingButton();
        if (showSubOverlay) showZoneFrame();
    }

    // ── Nút nổi ──────────────────────────────────────────────────────────
    private void createFloatingButton() {
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);
        floatParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        floatParams.gravity = Gravity.BOTTOM | Gravity.END;
        floatParams.x = 24; floatParams.y = 120;
        wm.addView(floatView, floatParams);

        TextView tvStatus = floatView.findViewById(R.id.tvFloatStatus);
        android.widget.ImageButton btnMain = floatView.findViewById(R.id.btnFloat);
        android.widget.ImageButton btnZone = floatView.findViewById(R.id.btnZone);

        btnMain.setOnTouchListener(new MoveTouchListener(floatView, floatParams, wm, () -> {
            SubtitleAccessibilityService.isActive = !SubtitleAccessibilityService.isActive;
            tvStatus.setText(SubtitleAccessibilityService.isActive ? "🔊" : "⏸");
            if (SubtitleAccessibilityService.isActive) SubtitleAccessibilityService.clearHistory();
        }));

        btnZone.setOnClickListener(v -> {
            if (zonePickerActive) exitZonePicker();
            else enterZonePicker();
        });

        tvStatus.setText(SubtitleAccessibilityService.isActive ? "🔊" : "⏸");
    }

    // ── Khung vùng đọc bình thường (không touch được) ────────────────────
    public void showZoneFrame() {
        removeZoneFrame();
        if (!showSubOverlay) return;

        zoneFrameView = new View(this) {
            final Paint paint = new Paint();
            @Override protected void onDraw(Canvas c) {
                int t = dm.heightPixels * SubtitleAccessibilityService.zoneTop    / 100;
                int b = dm.heightPixels * SubtitleAccessibilityService.zoneBottom / 100;
                int l = dm.widthPixels  * SubtitleAccessibilityService.zoneLeft   / 100;
                int r = dm.widthPixels  * SubtitleAccessibilityService.zoneRight  / 100;
                // Nền mờ
                paint.setColor(Color.argb(overlayAlpha,
                        Color.red(overlayColor), Color.green(overlayColor), Color.blue(overlayColor)));
                paint.setStyle(Paint.Style.FILL);
                c.drawRect(l, t, r, b, paint);
                // Viền
                paint.setColor(overlayColor);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                c.drawRect(l, t, r, b, paint);
            }
        };
        zoneFrameView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        wm.addView(zoneFrameView, p);
    }

    public void updateOverlayAlpha() {
        if (zoneFrameView != null) zoneFrameView.invalidate();
    }

    public void removeZoneFramePublic() { removeZoneFrame(); }

    private void removeZoneFrame() {
        if (zoneFrameView != null) {
            try { wm.removeView(zoneFrameView); } catch (Exception ignored) {}
            zoneFrameView = null;
        }
    }

    // ── Zone Picker — toàn màn hình, kéo 4 cạnh ──────────────────────────
    public void enterZonePicker() {
        zonePickerActive = true;
        SubtitleAccessibilityService.useZone = true;
        removeZoneFrame();
        removeZonePicker();

        zonePickerView = new ZonePickerView(this, dm, () -> exitZonePicker());

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.x = 0;
        p.y = 0;
        wm.addView(zonePickerView, p);
    }

    public void exitZonePicker() {
        SubtitleAccessibilityService.useZone = true; // tự bật sau khi chọn vùng
        zonePickerActive = false;
        removeZonePicker();
        Prefs.save(this);
        if (showSubOverlay) showZoneFrame();
    }

    private void removeZonePicker() {
        if (zonePickerView != null) {
            try { wm.removeView(zonePickerView); } catch (Exception ignored) {}
            zonePickerView = null;
        }
    }

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        instance = null;
        try { if (floatView     != null) wm.removeView(floatView);     } catch (Exception ignored) {}
        try { if (zoneFrameView != null) wm.removeView(zoneFrameView); } catch (Exception ignored) {}
        try { if (zonePickerView!= null) wm.removeView(zonePickerView);} catch (Exception ignored) {}
        super.onDestroy();
    }

    // ── MoveTouchListener cho nút nổi ─────────────────────────────────────
    static class MoveTouchListener implements View.OnTouchListener {
        private final View v;
        private final WindowManager.LayoutParams p;
        private final WindowManager wm;
        private final Runnable onClick;
        private int initX, initY, rawX, rawY;
        private boolean moved;

        MoveTouchListener(View v, WindowManager.LayoutParams p, WindowManager wm, Runnable onClick) {
            this.v = v; this.p = p; this.wm = wm; this.onClick = onClick;
        }

        public boolean onTouch(View view, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX = p.x; initY = p.y;
                    rawX = (int) e.getRawX(); rawY = (int) e.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) e.getRawX() - rawX;
                    int dy = (int) e.getRawY() - rawY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        p.x = initX - dx; p.y = initY - dy;
                        wm.updateViewLayout(v, p);
                        moved = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) onClick.run();
                    return true;
            }
            return false;
        }
    }
}
