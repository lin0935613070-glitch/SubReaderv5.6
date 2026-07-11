package com.subreader.app;

import android.content.Context;
import android.graphics.*;
import android.util.DisplayMetrics;
import android.view.*;

public class ZonePickerView extends View {

    private final DisplayMetrics dm;
    private final Runnable onDone;

    private final Paint paintMask   = new Paint();
    private final Paint paintZone   = new Paint();
    private final Paint paintBorder = new Paint();
    private final Paint paintHandle = new Paint();
    private final Paint paintText   = new Paint();
    private final Paint paintBtn    = new Paint();

    private final float HP;

    private static final int NONE   = 0;
    private static final int TOP    = 1;
    private static final int BOTTOM = 2;
    private static final int LEFT   = 3;
    private static final int RIGHT  = 4;
    private static final int MOVE   = 5;
    private static final int PINCH  = 6;

    private int   mode = NONE;
    private float lastX, lastY;
    private float accumDx, accumDy;

    // Pinch
    private float initDist;
    private int   initTop, initBottom, initLeft, initRight;
    private float initCx, initCy;

    // Nút Xong — góc trên phải cố định
    // Tọa độ tính theo pixel view (getX/getY), không phải Raw
    private float btnL, btnT, btnR, btnB;
    private static final float BTN_W = 190f;
    private static final float BTN_H = 76f;

    public ZonePickerView(Context ctx, DisplayMetrics dm, Runnable onDone) {
        super(ctx);
        this.dm     = dm;
        this.onDone = onDone;
        HP = 56 * dm.density;

        // Vị trí nút Xong: góc trên phải, cách mép 20dp
        float mr = 20 * dm.density;
        float mt = 72 * dm.density; // tránh status bar
        btnR = dm.widthPixels  - mr;
        btnL = btnR - BTN_W;
        btnT = mt;
        btnB = btnT + BTN_H;

        paintMask.setColor(Color.argb(140, 0, 0, 0));
        paintMask.setStyle(Paint.Style.FILL);

        paintZone.setColor(Color.argb(40, 29, 185, 84));
        paintZone.setStyle(Paint.Style.FILL);

        paintBorder.setColor(0xFF1DB954);
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(5f);
        paintBorder.setAntiAlias(true);

        paintHandle.setColor(Color.argb(220, 29, 185, 84));
        paintHandle.setStyle(Paint.Style.FILL);

        paintText.setColor(Color.WHITE);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setAntiAlias(true);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);

        paintBtn.setStyle(Paint.Style.FILL);
        paintBtn.setAntiAlias(true);
    }

    private RectF zone() {
        float l = dm.widthPixels  * SubtitleAccessibilityService.zoneLeft   / 100f;
        float r = dm.widthPixels  * SubtitleAccessibilityService.zoneRight  / 100f;
        float t = dm.heightPixels * SubtitleAccessibilityService.zoneTop    / 100f;
        float b = dm.heightPixels * SubtitleAccessibilityService.zoneBottom / 100f;
        return new RectF(l, t, r, b);
    }

    @Override
    protected void onDraw(Canvas c) {
        RectF z = zone();
        float W = dm.widthPixels, H = dm.heightPixels;

        // Mặt nạ tối
        c.drawRect(0,       0,        W,      z.top,    paintMask);
        c.drawRect(0,       z.bottom, W,      H,        paintMask);
        c.drawRect(0,       z.top,    z.left, z.bottom, paintMask);
        c.drawRect(z.right, z.top,    W,      z.bottom, paintMask);

        // Nền xanh trong vùng
        c.drawRect(z, paintZone);

        // 4 handle cạnh
        c.drawRect(z.left,       z.top,        z.right,     z.top    + HP, paintHandle);
        c.drawRect(z.left,       z.bottom - HP, z.right,    z.bottom,      paintHandle);
        c.drawRect(z.left,       z.top + HP,   z.left + HP, z.bottom - HP, paintHandle);
        c.drawRect(z.right - HP, z.top + HP,   z.right,     z.bottom - HP, paintHandle);

        // Viền
        c.drawRect(z, paintBorder);

        // Nhãn cạnh
        paintText.setTextSize(26f);
        c.drawText("▲", z.centerX(), z.top + HP * 0.7f, paintText);
        c.drawText("▼", z.centerX(), z.bottom - HP * 0.1f, paintText);

        // Gợi ý giữa khung
        paintText.setTextSize(17f);
        paintText.setColor(Color.argb(190, 255, 255, 255));
        c.drawText("👆👆 2 ngón: phóng to/thu nhỏ", z.centerX(), z.centerY() - 12f, paintText);
        c.drawText("☝ 1 ngón giữa: di chuyển",       z.centerX(), z.centerY() + 16f, paintText);
        paintText.setColor(Color.WHITE);

        // % vùng — góc trên trái
        paintBtn.setColor(Color.argb(180, 0, 0, 0));
        float infoR = btnL - 12;
        float infoL = 12;
        c.drawRoundRect(infoL, btnT, infoR, btnB, 12, 12, paintBtn);
        paintText.setTextSize(16f);
        paintText.setColor(0xFF1DB954);
        paintText.setTextAlign(Paint.Align.CENTER);
        c.drawText(String.format("↑%d%% ↓%d%% ←%d%% →%d%%",
                SubtitleAccessibilityService.zoneTop,
                SubtitleAccessibilityService.zoneBottom,
                SubtitleAccessibilityService.zoneLeft,
                SubtitleAccessibilityService.zoneRight),
                (infoL + infoR) / 2f, (btnT + btnB) / 2f + 6f, paintText);
        paintText.setColor(Color.WHITE);

        // ── Nút Xong — góc trên PHẢI, nền xanh đậm nổi bật ──────────────
        paintBtn.setColor(0xFF1DB954);
        c.drawRoundRect(btnL, btnT, btnR, btnB, 18, 18, paintBtn);
        // Viền trắng để nổi bật hơn
        paintBorder.setColor(Color.WHITE);
        paintBorder.setStrokeWidth(2f);
        c.drawRoundRect(btnL, btnT, btnR, btnB, 18, 18, paintBorder);
        paintBorder.setColor(0xFF1DB954);
        paintBorder.setStrokeWidth(5f);

        paintText.setTextSize(30f);
        paintText.setColor(Color.WHITE);
        c.drawText("✅  Xong", (btnL + btnR) / 2f, (btnT + btnB) / 2f + 11f, paintText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Dùng getX/getY (tọa độ trong view) — nhất quán với tọa độ vẽ Canvas
        float x = e.getX();
        float y = e.getY();
        int   action = e.getActionMasked();
        int   pc     = e.getPointerCount();

        // ── 2 ngón PINCH ─────────────────────────────────────────────────
        if (pc == 2) {
            float x0 = e.getX(0), y0 = e.getY(0);
            float x1 = e.getX(1), y1 = e.getY(1);
            float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;
            float dist = (float) Math.hypot(x1 - x0, y1 - y0);

            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    mode       = PINCH;
                    initDist   = Math.max(dist, 10f);
                    initTop    = SubtitleAccessibilityService.zoneTop;
                    initBottom = SubtitleAccessibilityService.zoneBottom;
                    initLeft   = SubtitleAccessibilityService.zoneLeft;
                    initRight  = SubtitleAccessibilityService.zoneRight;
                    initCx = cx; initCy = cy;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (mode != PINCH) return true;
                    float scale  = dist / initDist;
                    float cxPct  = (initLeft  + initRight)  / 2f;
                    float cyPct  = (initTop   + initBottom) / 2f;
                    float halfW  = (initRight  - initLeft)  / 2f;
                    float halfH  = (initBottom - initTop)   / 2f;
                    float mdx    = (cx - initCx) * 100f / dm.widthPixels;
                    float mdy    = (cy - initCy) * 100f / dm.heightPixels;

                    int nL = clamp((int)(cxPct - halfW * scale + mdx), 0, 94);
                    int nR = clamp((int)(cxPct + halfW * scale + mdx), 6, 100);
                    int nT = clamp((int)(cyPct - halfH * scale + mdy), 0, 94);
                    int nB = clamp((int)(cyPct + halfH * scale + mdy), 6, 100);
                    if (nR - nL >= 5) {
                        SubtitleAccessibilityService.zoneLeft  = nL;
                        SubtitleAccessibilityService.zoneRight = nR;
                    }
                    if (nB - nT >= 5) {
                        SubtitleAccessibilityService.zoneTop    = nT;
                        SubtitleAccessibilityService.zoneBottom = nB;
                    }
                    invalidate();
                    return true;

                case MotionEvent.ACTION_POINTER_UP:
                    mode = NONE;
                    return true;
            }
            return true;
        }

        // ── 1 ngón ───────────────────────────────────────────────────────
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                // Kiểm tra nút Xong TRƯỚC TIÊN
                if (x >= btnL && x <= btnR && y >= btnT && y <= btnB) {
                    mode = -1; // mode đặc biệt: đang giữ nút Xong
                    return true;
                }
                RectF z = zone();
                mode   = hitTest(x, y, z);
                lastX  = x; lastY = y;
                accumDx = 0; accumDy = 0;
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mode == -1 || mode == NONE || mode == PINCH) return true;
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x; lastY = y;

                switch (mode) {
                    case TOP:
                        SubtitleAccessibilityService.zoneTop = clamp(
                                (int)(y * 100 / dm.heightPixels),
                                0, SubtitleAccessibilityService.zoneBottom - 5);
                        break;
                    case BOTTOM:
                        SubtitleAccessibilityService.zoneBottom = clamp(
                                (int)(y * 100 / dm.heightPixels),
                                SubtitleAccessibilityService.zoneTop + 5, 100);
                        break;
                    case LEFT:
                        SubtitleAccessibilityService.zoneLeft = clamp(
                                (int)(x * 100 / dm.widthPixels),
                                0, SubtitleAccessibilityService.zoneRight - 5);
                        break;
                    case RIGHT:
                        SubtitleAccessibilityService.zoneRight = clamp(
                                (int)(x * 100 / dm.widthPixels),
                                SubtitleAccessibilityService.zoneLeft + 5, 100);
                        break;
                    case MOVE: {
                        accumDx += dx * 100f / dm.widthPixels;
                        accumDy += dy * 100f / dm.heightPixels;
                        int stepX = (int) accumDx;
                        int stepY = (int) accumDy;
                        if (stepX != 0 || stepY != 0) {
                            int w  = SubtitleAccessibilityService.zoneRight  - SubtitleAccessibilityService.zoneLeft;
                            int h  = SubtitleAccessibilityService.zoneBottom - SubtitleAccessibilityService.zoneTop;
                            int nl = clamp(SubtitleAccessibilityService.zoneLeft + stepX, 0, 100 - w);
                            int nt = clamp(SubtitleAccessibilityService.zoneTop  + stepY, 0, 100 - h);
                            SubtitleAccessibilityService.zoneLeft   = nl;
                            SubtitleAccessibilityService.zoneRight  = nl + w;
                            SubtitleAccessibilityService.zoneTop    = nt;
                            SubtitleAccessibilityService.zoneBottom = nt + h;
                            accumDx -= stepX;
                            accumDy -= stepY;
                        }
                        break;
                    }
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (mode == -1) {
                    // Xác nhận tap nút Xong khi nhả tay
                    if (x >= btnL && x <= btnR && y >= btnT && y <= btnB) {
                        onDone.run();
                    }
                }
                mode = NONE;
                return true;
            }
        }
        return false;
    }

    private int hitTest(float x, float y, RectF z) {
        boolean inX = x >= z.left && x <= z.right;
        boolean inY = y >= z.top  && y <= z.bottom;
        if (inX && y >= z.top    - HP/2 && y <= z.top    + HP) return TOP;
        if (inX && y >= z.bottom - HP   && y <= z.bottom + HP/2) return BOTTOM;
        if (inY && x >= z.left   - HP/2 && x <= z.left   + HP) return LEFT;
        if (inY && x >= z.right  - HP   && x <= z.right  + HP/2) return RIGHT;
        if (inX && inY) return MOVE;
        return NONE;
    }

    private int clamp(int v, int mn, int mx) { return Math.max(mn, Math.min(mx, v)); }
}
