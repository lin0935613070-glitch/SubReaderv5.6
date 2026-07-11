package com.subreader.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

public class Prefs {
    private static final String FILE = "subreader_prefs";

    public static void save(Context ctx) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putFloat("speechRate",    SubtitleAccessibilityService.speechRate)
            .putFloat("maxRate",       SubtitleAccessibilityService.maxRate)
            .putFloat("speechPitch",   SubtitleAccessibilityService.speechPitch)
            .putFloat("speechVolume",  SubtitleAccessibilityService.speechVolume)
            .putBoolean("isActive",     SubtitleAccessibilityService.isActive)
            .putBoolean("filterShort", SubtitleAccessibilityService.filterShort)
            .putBoolean("useZone",     SubtitleAccessibilityService.useZone)
            .putInt("zoneTop",         SubtitleAccessibilityService.zoneTop)
            .putInt("zoneBottom",      SubtitleAccessibilityService.zoneBottom)
            .putInt("zoneLeft",        SubtitleAccessibilityService.zoneLeft)
            .putInt("zoneRight",       SubtitleAccessibilityService.zoneRight)
            .putInt("readDelay",       SubtitleAccessibilityService.readDelay)
            .putBoolean("showOverlay", FloatingService.showSubOverlay)
            .putInt("overlayAlpha",    FloatingService.overlayAlpha)
            .putString("localeLang",   SubtitleAccessibilityService.selectedLocale != null
                                       ? SubtitleAccessibilityService.selectedLocale.toLanguageTag() : "en")
            .putString("voiceName",    SubtitleAccessibilityService.savedVoiceName)
            .apply();
    }

    public static void load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        SubtitleAccessibilityService.speechRate    = p.getFloat("speechRate",    1.0f);
        SubtitleAccessibilityService.maxRate        = p.getFloat("maxRate",        2.5f);
        SubtitleAccessibilityService.speechPitch   = p.getFloat("speechPitch",   1.0f);
        SubtitleAccessibilityService.speechVolume  = p.getFloat("speechVolume",  0.8f);
        SubtitleAccessibilityService.isActive       = p.getBoolean("isActive",     false);
        SubtitleAccessibilityService.filterShort   = p.getBoolean("filterShort", true);
        SubtitleAccessibilityService.useZone       = p.getBoolean("useZone",     false);
        SubtitleAccessibilityService.zoneTop       = p.getInt("zoneTop",         60);
        SubtitleAccessibilityService.zoneBottom    = p.getInt("zoneBottom",      100);
        SubtitleAccessibilityService.zoneLeft      = p.getInt("zoneLeft",        0);
        SubtitleAccessibilityService.zoneRight     = p.getInt("zoneRight",       100);
        SubtitleAccessibilityService.readDelay     = p.getInt("readDelay",       0);
        FloatingService.showSubOverlay             = p.getBoolean("showOverlay", true);
        FloatingService.overlayAlpha               = p.getInt("overlayAlpha",    80);
        SubtitleAccessibilityService.savedVoiceName = p.getString("voiceName",   "");
        String lang = p.getString("localeLang", "en");
        SubtitleAccessibilityService.selectedLocale = Locale.forLanguageTag(lang);
    }
}
