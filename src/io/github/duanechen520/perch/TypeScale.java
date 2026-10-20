package io.github.duanechen520.perch;

import android.content.Context;

final class TypeScale {

    static final String KEY = "font_size";
    static final String SMALL = "small";
    static final String STANDARD = "standard";
    static final String LARGE = "large";

    private TypeScale() {
    }

    static String get(Context context) {
        String value = AppRepository.prefs(context).getString(KEY, STANDARD);
        if (SMALL.equals(value) || LARGE.equals(value)) {
            return value;
        }
        return STANDARD;
    }

    static void set(Context context, String value) {
        AppRepository.prefs(context).edit().putString(KEY, value).apply();
    }

    static float appLabel(Context context) {
        String preset = get(context);
        if (SMALL.equals(preset)) {
            return 18f;
        }
        if (LARGE.equals(preset)) {
            return 24f;
        }
        return 20f;
    }

    static float body(Context context) {
        String preset = get(context);
        if (SMALL.equals(preset)) {
            return 16f;
        }
        if (LARGE.equals(preset)) {
            return 22f;
        }
        return 18f;
    }

    static float aux(Context context) {
        String preset = get(context);
        if (SMALL.equals(preset)) {
            return 16f;
        }
        if (LARGE.equals(preset)) {
            return 19f;
        }
        return 17f;
    }

    static float sheet(Context context) {
        String preset = get(context);
        if (SMALL.equals(preset)) {
            return 16f;
        }
        if (LARGE.equals(preset)) {
            return 22f;
        }
        return 18f;
    }

    static float heroDate(Context context) {
        return appLabel(context);
    }

    static float heroClock(Context context) {
        int heightDp = context.getResources().getConfiguration().screenHeightDp;
        return heightDp < 650 ? 80f : 96f;
    }
}
