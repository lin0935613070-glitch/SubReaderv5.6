package com.subreader.app;

import java.util.Locale;

/**
 * Lọc ngôn ngữ cho sub phim.
 *
 * Nguyên tắc: CHỈ bỏ qua text khi nó RÕ RÀNG thuộc ngôn ngữ khác.
 * Không bao giờ bỏ qua text mơ hồ (Latin không dấu, ký tự số, v.v.)
 * vì tiếng Việt không dấu = tiếng Anh về mặt Unicode.
 */
public class LangDetector {

    // Nhóm ngôn ngữ dùng chữ Latin (không thể phân biệt nhau qua Unicode)
    private static final String[] LATIN_LANGS = {
        "en","vi","fr","de","es","pt","it","nl","pl","ro","sv","da","fi","no",
        "id","ms","tr","cs","sk","hu","hr","sl","et","lv","lt","sq","bs","af"
    };

    /**
     * Trả về true nếu nên đọc text này với ngôn ngữ đã chọn.
     *
     * Logic:
     * - Nếu targetLocale là Latin-based (en, vi, fr...):
     *     → Đọc tất cả text Latin (kể cả không dấu)
     *     → BỎ QUA text rõ ràng là CJK / Hangul / Thai / Arabic
     * - Nếu targetLocale là CJK/Hangul/Thai/Arabic:
     *     → Chỉ đọc text đúng script đó
     *     → BỎ QUA Latin thuần
     * - Nếu không detect được → cho phép đọc (safe)
     */
    public static boolean matches(String text, Locale targetLocale) {
        if (targetLocale == null || text == null || text.isEmpty()) return true;

        String lang = targetLocale.getLanguage().toLowerCase(Locale.ROOT);
        ScriptType textScript = detectScript(text);

        if (textScript == ScriptType.UNKNOWN) return true; // không chắc → cho phép

        // Ngôn ngữ đích dùng Latin
        if (isLatinLang(lang)) {
            // Cho phép: Latin (en, vi không dấu, vi có dấu đều là Latin)
            // Bỏ qua: rõ ràng là script khác
            return textScript == ScriptType.LATIN;
        }

        // Ngôn ngữ đích là tiếng Nhật
        if (lang.equals("ja")) {
            return textScript == ScriptType.JAPANESE || textScript == ScriptType.CJK;
        }

        // Ngôn ngữ đích là tiếng Trung
        if (lang.equals("zh")) {
            return textScript == ScriptType.CJK;
        }

        // Ngôn ngữ đích là tiếng Hàn
        if (lang.equals("ko")) {
            return textScript == ScriptType.HANGUL;
        }

        // Ngôn ngữ đích là tiếng Thái
        if (lang.equals("th")) {
            return textScript == ScriptType.THAI;
        }

        // Ngôn ngữ đích là tiếng Ả Rập
        if (lang.equals("ar")) {
            return textScript == ScriptType.ARABIC;
        }

        // Các ngôn ngữ khác → không filter
        return true;
    }

    enum ScriptType { LATIN, CJK, JAPANESE, HANGUL, THAI, ARABIC, UNKNOWN }

    /**
     * Detect script chính của text.
     * Chỉ kết luận khi có đủ bằng chứng rõ ràng (>20% ký tự đặc trưng).
     */
    private static ScriptType detectScript(String text) {
        int total = 0, latin = 0, cjk = 0, hira = 0, kata = 0,
            hangul = 0, thai = 0, arabic = 0;

        for (char c : text.toCharArray()) {
            if (c <= 0x20) continue; // bỏ space, control chars
            total++;

            if      ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))  latin++;
            else if (c >= 0x00C0 && c <= 0x024F) latin++;   // Latin Extended (có dấu)
            else if (c >= 0x1EA0 && c <= 0x1EF9) latin++;   // Vietnamese supplement
            else if (c >= 0x4E00 && c <= 0x9FFF) cjk++;
            else if (c >= 0x3400 && c <= 0x4DBF) cjk++;     // CJK Extension A
            else if (c >= 0x3040 && c <= 0x309F) hira++;
            else if (c >= 0x30A0 && c <= 0x30FF) kata++;
            else if (c >= 0xAC00 && c <= 0xD7AF) hangul++;
            else if (c >= 0x1100 && c <= 0x11FF) hangul++;  // Hangul Jamo
            else if (c >= 0x0E00 && c <= 0x0E7F) thai++;
            else if (c >= 0x0600 && c <= 0x06FF) arabic++;
        }

        if (total == 0) return ScriptType.UNKNOWN;

        float th = 0.20f; // ngưỡng 20%

        float japaneseR = (float)(hira + kata + cjk) / total;
        float cjkR      = (float) cjk    / total;
        float latinR    = (float) latin   / total;
        float hangulR   = (float) hangul  / total;
        float thaiR     = (float) thai    / total;
        float arabicR   = (float) arabic  / total;
        float hiraR     = (float)(hira + kata) / total;

        // Nhật: có Hiragana/Katakana là dấu hiệu chắc chắn nhất
        if (hiraR > 0.10f) return ScriptType.JAPANESE;

        // Hàn
        if (hangulR > th) return ScriptType.HANGUL;

        // Trung (CJK thuần, không có Hiragana)
        if (cjkR > th) return ScriptType.CJK;

        // Thái
        if (thaiR > th) return ScriptType.THAI;

        // Ả Rập
        if (arabicR > th) return ScriptType.ARABIC;

        // Latin (bao gồm tiếng Anh, Việt không dấu, Việt có dấu, Pháp...)
        if (latinR > th) return ScriptType.LATIN;

        return ScriptType.UNKNOWN;
    }

    private static boolean isLatinLang(String lang) {
        for (String l : LATIN_LANGS) if (l.equals(lang)) return true;
        return false;
    }
}
