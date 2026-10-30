package com.example.ferreteriamovil;

import java.text.DecimalFormat;

final class Formato {

    private static final DecimalFormat CANTIDAD = new DecimalFormat("#,##0.###");

    private Formato() {
    }

    static String cantidad(double v) {
        return CANTIDAD.format(v);
    }

    /** "2026-10-30T10:32:10" -> "2026-10-30 10:32" */
    static String fecha(String iso) {
        if (iso == null || iso.isEmpty() || iso.equals("null")) return "";
        String f = iso.replace('T', ' ');
        return f.length() > 16 ? f.substring(0, 16) : f;
    }
}
