package io.github.duanechen520.perch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AppRepository {

    static final String PREFS = "settings";
    static final String KEY_PINNED = "pinned";
    static final int MAX_PINNED = 5;

    private AppRepository() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, RawEntry> found = new LinkedHashMap<String, RawEntry>();
        collect(pm, found, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(pm, found, Intent.CATEGORY_LAUNCHER);

        final Collator collator = Collator.getInstance(Locale.getDefault());
        Comparator<RawEntry> byLabel = new Comparator<RawEntry>() {
            @Override
            public int compare(RawEntry left, RawEntry right) {
                int result = collator.compare(left.label, right.label);
                if (result != 0) {
                    return result;
                }
                result = left.packageName.compareTo(right.packageName);
                return result != 0 ? result : left.activityName.compareTo(right.activityName);
            }
        };
        List<RawEntry> all = new ArrayList<RawEntry>(found.values());
        Collections.sort(all, byLabel);

        LinkedHashSet<String> pins = readPins(context);
        boolean pinsChanged = false;
        for (Iterator<String> iterator = pins.iterator(); iterator.hasNext(); ) {
            if (!found.containsKey(iterator.next())) {
                iterator.remove();
                pinsChanged = true;
            }
        }

        List<RawEntry> pinned = new ArrayList<RawEntry>();
        for (RawEntry entry : all) {
            if (pins.contains(entry.packageName)) {
                pinned.add(entry);
            }
        }
        if (pinned.size() > MAX_PINNED) {
            pinned = new ArrayList<RawEntry>(pinned.subList(0, MAX_PINNED));
            pins.clear();
            for (RawEntry entry : pinned) {
                pins.add(entry.packageName);
            }
            pinsChanged = true;
        }

        List<RawEntry> rest = new ArrayList<RawEntry>();
        RawEntry own = null;
        for (RawEntry entry : all) {
            if (pins.contains(entry.packageName)) {
                continue;
            }
            if (entry.packageName.equals(context.getPackageName())) {
                own = entry;
            } else {
                rest.add(entry);
            }
        }
        if (own != null) {
            rest.add(own);
        }

        if (pinsChanged) {
            writePins(context, pins);
        }

        List<AppEntry> result = new ArrayList<AppEntry>();
        for (RawEntry entry : pinned) {
            result.add(new AppEntry(entry, true));
        }
        for (RawEntry entry : rest) {
            result.add(new AppEntry(entry, false));
        }
        return result;
    }

    static int togglePin(Context context, String packageName) {
        LinkedHashSet<String> pins = readPins(context);
        if (pins.remove(packageName)) {
            writePins(context, pins);
            return 0;
        }
        if (pins.size() >= MAX_PINNED) {
            return -1;
        }
        pins.add(packageName);
        writePins(context, pins);
        return 1;
    }

    private static void collect(PackageManager pm, Map<String, RawEntry> out, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(intent, 0);
        } catch (Exception e) {
            return;
        }
        if (resolved == null) {
            return;
        }
        for (ResolveInfo info : resolved) {
            if (info == null || info.activityInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            String activityName = info.activityInfo.name;
            if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(activityName)
                    || out.containsKey(packageName)) {
                continue;
            }
            String label;
            try {
                CharSequence loaded = info.loadLabel(pm);
                label = loaded == null ? "" : loaded.toString().trim();
            } catch (Exception e) {
                label = "";
            }
            if (label.length() == 0) {
                label = activityName.substring(activityName.lastIndexOf('.') + 1);
            }
            Drawable icon = null;
            try {
                icon = info.loadIcon(pm);
            } catch (Exception ignored) {
            }
            out.put(packageName,
                    new RawEntry(packageName, activityName, category, label, icon));
        }
    }

    private static LinkedHashSet<String> readPins(Context context) {
        String raw = prefs(context).getString(KEY_PINNED, "");
        LinkedHashSet<String> pins = new LinkedHashSet<String>();
        if (raw != null) {
            for (String value : raw.split("\n")) {
                String pin = value.trim();
                if (pin.length() > 0) {
                    pins.add(pin);
                }
            }
        }
        return pins;
    }

    private static void writePins(Context context, Collection<String> pins) {
        StringBuilder joined = new StringBuilder();
        for (String pin : pins) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(pin);
        }
        prefs(context).edit().putString(KEY_PINNED, joined.toString()).apply();
    }

    static final class AppEntry {
        final String packageName;
        final String activityName;
        final String category;
        final String label;
        final Drawable icon;
        final boolean pinned;

        AppEntry(RawEntry raw, boolean pinned) {
            this.packageName = raw.packageName;
            this.activityName = raw.activityName;
            this.category = raw.category;
            this.label = raw.label;
            this.icon = raw.icon;
            this.pinned = pinned;
        }
    }

    private static final class RawEntry {
        final String packageName;
        final String activityName;
        final String category;
        final String label;
        final Drawable icon;

        RawEntry(String packageName, String activityName, String category,
                 String label, Drawable icon) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.category = category;
            this.label = label;
            this.icon = icon;
        }
    }
}
