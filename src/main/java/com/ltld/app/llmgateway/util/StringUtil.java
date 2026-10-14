package com.ltld.app.llmgateway.util;

public final class StringUtil {

    private StringUtil() {}

    /**
     * Cắt chuỗi về tối đa {@code max} ký tự để ghi log an toàn.
     * Trả về "" nếu chuỗi null, để không bao giờ in ra chữ "null" vào log.
     */
    public static String truncate(String s, int max) {
        if (s == null || max <= 0) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}