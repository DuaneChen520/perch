package io.github.duanechen520.perch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "Perch";
    private static final String KEY_SHOW_IP = "show_ip";
    private static final String KEY_FOLLOW_SYSTEM_CLOCK = "time_follow_system";
    private static final long IP_REFRESH_MS = 15000L;
    private static final long LONG_PRESS_MS = 550L;
    private static final int COLUMNS = 5;
    private static final float HERO_EXPANDED_SCALE = 0.62f;
    private static final int HERO_EXPANDED_TOP_DP = 28;
    private static final int HERO_GRID_GAP_DP = 16;
    private static final int HERO_FALLBACK_HEIGHT_DP = 150;

    private static final int QUICK_NETWORK = 1;
    private static final int QUICK_BLUETOOTH = 2;
    private static final int QUICK_DISPLAY = 3;
    private static final int QUICK_SOUND = 4;
    private static final int QUICK_ACCESSIBILITY = 5;
    private static final int QUICK_ALL_SETTINGS = 6;
    private static final int QUICK_PERCH = 7;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppRepository.AppEntry> apps =
            new ArrayList<AppRepository.AppEntry>();
    private final List<View> compactTiles = new ArrayList<View>();
    private final List<View> fullTiles = new ArrayList<View>();
    private final List<TextView> appLabels = new ArrayList<TextView>();
    private final List<TextView> sheetLabels = new ArrayList<TextView>();

    private final Runnable hideCompactRow = new Runnable() {
        @Override
        public void run() {
            if (expanded) {
                compactRow.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            if (showIp) {
                long now = System.currentTimeMillis();
                if (now - lastIpCheck >= IP_REFRESH_MS) {
                    lastIpCheck = now;
                    updateIp();
                }
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private FrameLayout root;
    private FrameLayout compactContainer;
    private LinearLayout compactColumn;
    private LinearLayout heroClock;
    private LinearLayout compactRow;
    private LinearLayout fullLayer;
    private LinearLayout fullGrid;
    private ScrollView fullScroll;
    private LinearLayout sheet;
    private TextView heroTime;
    private TextView heroDate;
    private TextView ipCompact;

    private SharedPreferences prefs;
    private DateFormat timeFormat;
    private DateFormat dateFormat;
    private String fontPreset;
    private String focusedPackage;
    private String lastSignature;
    private String lastIp = "";
    private long lastIpCheck = 0L;
    private boolean expanded = false;
    private boolean sheetOpen = false;
    private boolean showIp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = AppRepository.prefs(this);
        fontPreset = TypeScale.get(this);
        buildUi();
        hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();

        boolean newShowIp = prefs.getBoolean(KEY_SHOW_IP, false);
        if (newShowIp != showIp) {
            lastIpCheck = 0L;
        }
        showIp = newShowIp;
        updateIpVisibility();

        updateTimeFormats();
        updateClock();

        String preset = TypeScale.get(this);
        if (!preset.equals(fontPreset)) {
            fontPreset = preset;
            applyTypeScale();
        }

        refreshApps(false);

        View current = getCurrentFocus();
        if (current == null || !(current.getTag() instanceof String)) {
            focusArea(expanded, focusedPackage);
        }

        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        if (sheetOpen) {
            hideSheetImmediately(false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateTimeFormats();
        updateClock();
        applyTypeScale();
        refreshApps(false);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (sheetOpen) {
            hideSheetImmediately(false);
        }
        if (expanded) {
            setExpanded(false);
        } else {
            focusArea(false, focusedPackage);
        }
    }

    @Override
    public void onBackPressed() {
        if (sheetOpen) {
            closeSheet(true);
            return;
        }
        if (expanded) {
            setExpanded(false);
            return;
        }
        // The home screen stays put.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (sheetOpen) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                closeSheet(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        if (!expanded) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                setExpanded(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.getRepeatCount() == 0) {
                    openSheet();
                }
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        int index = fullTiles.indexOf(getCurrentFocus());
        if (index < 0) {
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (index % COLUMNS > 0) {
                    focusFull(index - 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (index % COLUMNS < COLUMNS - 1 && index + 1 < fullTiles.size()) {
                    focusFull(index + 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (index < COLUMNS) {
                    setExpanded(false);
                } else {
                    focusFull(index - COLUMNS);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (index + COLUMNS < fullTiles.size()) {
                    focusFull(index + COLUMNS);
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.bg));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        setContentView(root);

        root.addView(new WallpaperBackground(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        buildCompact();
        buildExpanded();
        buildSheet();
    }

    private void buildCompact() {
        compactContainer = new FrameLayout(this);
        root.addView(compactContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        compactColumn = new LinearLayout(this);
        compactColumn.setOrientation(LinearLayout.VERTICAL);
        compactColumn.setPadding(dp(64), dp(36), dp(64), dp(44));
        compactContainer.addView(compactColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        heroClock = new LinearLayout(this);
        heroClock.setOrientation(LinearLayout.VERTICAL);
        heroClock.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        heroParams.topMargin = dp(78);
        compactColumn.addView(heroClock, heroParams);

        heroTime = new TextView(this);
        heroTime.setGravity(Gravity.CENTER);
        heroTime.setTextColor(color(R.color.text_primary));
        heroTime.setTextSize(TypeScale.heroClock(this));
        heroTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        heroTime.setIncludeFontPadding(false);
        heroTime.setLetterSpacing(0.02f);
        heroTime.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroClock.addView(heroTime, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        heroDate = new TextView(this);
        heroDate.setGravity(Gravity.CENTER);
        heroDate.setTextColor(color(R.color.text_secondary));
        heroDate.setTextSize(TypeScale.heroDate(this));
        heroDate.setIncludeFontPadding(false);
        heroDate.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams heroDateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        heroDateParams.topMargin = dp(10);
        heroClock.addView(heroDate, heroDateParams);

        Space spacer = new Space(this);
        compactColumn.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        compactRow = new LinearLayout(this);
        compactRow.setOrientation(LinearLayout.HORIZONTAL);
        compactRow.setGravity(Gravity.CENTER);
        compactRow.setWeightSum(COLUMNS);
        compactColumn.addView(compactRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ipCompact = new TextView(this);
        ipCompact.setTextSize(TypeScale.aux(this));
        ipCompact.setTextColor(color(R.color.text_secondary));
        ipCompact.setVisibility(View.GONE);
        FrameLayout.LayoutParams ipParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        ipParams.setMargins(0, 0, dp(64), dp(44));
        compactContainer.addView(ipCompact, ipParams);
    }

    private void buildExpanded() {
        fullLayer = new LinearLayout(this);
        fullLayer.setOrientation(LinearLayout.VERTICAL);
        fullLayer.setAlpha(0f);
        fullLayer.setVisibility(View.GONE);
        applyGridOffset();
        root.addView(fullLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        fullScroll = new ScrollView(this);
        fullScroll.setFillViewport(true);
        fullScroll.setVerticalScrollBarEnabled(false);
        fullScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        fullLayer.addView(fullScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        fullGrid = new LinearLayout(this);
        fullGrid.setOrientation(LinearLayout.VERTICAL);
        fullGrid.setPadding(dp(64), dp(16), dp(64), dp(48));
        fullScroll.addView(fullGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildSheet() {
        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.HORIZONTAL);
        sheet.setGravity(Gravity.CENTER_VERTICAL);
        sheet.setBackgroundColor(color(R.color.sheet));
        sheet.setPadding(dp(64), dp(40), dp(64), dp(20));
        sheet.setVisibility(View.GONE);
        root.addView(sheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        int[] actions = {
                QUICK_NETWORK, QUICK_BLUETOOTH, QUICK_DISPLAY, QUICK_SOUND,
                QUICK_ACCESSIBILITY, QUICK_ALL_SETTINGS, QUICK_PERCH
        };
        int[] labels = {
                R.string.quick_network, R.string.quick_bluetooth,
                R.string.quick_display, R.string.quick_sound,
                R.string.quick_accessibility, R.string.quick_all_settings,
                R.string.quick_perch
        };
        for (int i = 0; i < actions.length; i++) {
            final int action = actions[i];
            TextView item = new TextView(this);
            item.setText(labels[i]);
            item.setTextColor(color(R.color.text_primary));
            item.setTextSize(TypeScale.sheet(this));
            item.setGravity(Gravity.CENTER);
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackgroundResource(R.drawable.sheet_item_bg);
            item.setPadding(dp(8), dp(14), dp(8), dp(14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(4), 0, dp(4), 0);
            item.setLayoutParams(params);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openQuick(action);
                }
            });
            item.setId(View.generateViewId());
            item.setOnFocusChangeListener(scaleFocus(1.03f));
            sheet.addView(item);
            sheetLabels.add(item);
        }
        linkRowHorizontally(sheet);
    }

    private void linkRowHorizontally(LinearLayout row) {
        int count = row.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = row.getChildAt(i);
            View left = row.getChildAt(i == 0 ? 0 : i - 1);
            View right = row.getChildAt(i == count - 1 ? count - 1 : i + 1);
            child.setNextFocusLeftId(left.getId());
            child.setNextFocusRightId(right.getId());
        }
    }

    private void refreshApps(boolean force) {
        List<AppRepository.AppEntry> loaded = AppRepository.load(this);
        StringBuilder builder = new StringBuilder();
        for (AppRepository.AppEntry app : loaded) {
            builder.append(app.packageName).append('/')
                    .append(app.activityName).append('/')
                    .append(app.pinned ? '1' : '0').append('/')
                    .append(app.label).append(';');
        }
        String signature = builder.toString();
        if (!force && lastSignature != null && signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;
        apps.clear();
        apps.addAll(loaded);
        if (findApp(focusedPackage) == null) {
            focusedPackage = null;
        }
        renderApps();
    }

    private void renderApps() {
        if (sheetOpen) {
            hideSheetImmediately(false);
        }
        cancelAnimations();
        handler.removeCallbacks(hideCompactRow);

        compactRow.removeAllViews();
        fullGrid.removeAllViews();
        compactTiles.clear();
        fullTiles.clear();
        appLabels.clear();

        int compactCount = Math.min(COLUMNS, apps.size());
        for (int i = 0; i < compactCount; i++) {
            View tile = createTile(apps.get(i), true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(6), 0, dp(6), 0);
            compactRow.addView(tile, params);
            compactTiles.add(tile);
        }

        for (int start = 0; start < apps.size(); start += COLUMNS) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < COLUMNS; column++) {
                int index = start + column;
                if (index < apps.size()) {
                    View tile = createTile(apps.get(index), false);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    params.setMargins(dp(6), dp(6), dp(6), dp(6));
                    row.addView(tile, params);
                    fullTiles.add(tile);
                } else {
                    Space filler = new Space(this);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            0, dp(168), 1f);
                    params.setMargins(dp(6), dp(6), dp(6), dp(6));
                    row.addView(filler, params);
                }
            }
            fullGrid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        compactContainer.setVisibility(View.VISIBLE);
        compactRow.setVisibility(expanded ? View.GONE : View.VISIBLE);
        fullLayer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        fullLayer.setAlpha(expanded ? 1f : 0f);
        if (expanded) {
            applyGridOffset();
            setCompactCollapsedVisuals();
        } else {
            resetCompactVisuals();
        }
        focusArea(expanded, focusedPackage);
    }

    private View createTile(final AppRepository.AppEntry app, boolean compact) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackgroundResource(R.drawable.app_tile_bg);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        if (!compact) {
            tile.setMinimumHeight(dp(168));
        }
        tile.setTag(app.packageName);
        tile.setContentDescription(app.pinned
                ? getString(R.string.a11y_pinned_app, app.label)
                : app.label);

        FrameLayout iconFrame = new FrameLayout(this);
        LinearLayout.LayoutParams iconFrameParams = new LinearLayout.LayoutParams(
                dp(compact ? 88 : 72), dp(compact ? 88 : 72));
        iconFrameParams.gravity = Gravity.CENTER_HORIZONTAL;
        tile.addView(iconFrame, iconFrameParams);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable drawable = app.icon;
        if (drawable == null) {
            drawable = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        }
        icon.setImageDrawable(drawable);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconFrame.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (app.pinned) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.pin_dot);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                    dp(12), dp(12), Gravity.TOP | Gravity.END);
            iconFrame.addView(dot, dotParams);
        }

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(getResources().getColorStateList(R.color.tile_label));
        label.setTextSize(TypeScale.appLabel(this));
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setMinLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setLineSpacing(dp(2), 1f);
        label.setIncludeFontPadding(false);
        label.setDuplicateParentStateEnabled(true);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(8);
        tile.addView(label, labelParams);
        appLabels.add(label);

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launch(app);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                togglePin(app);
                return true;
            }
        });
        tile.setOnKeyListener(new CenterKeyListener(app));
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    focusedPackage = app.packageName;
                    ensureVisible(view);
                }
                float scale = hasFocus ? 1.04f : 1f;
                if (animationsEnabled()) {
                    view.animate().scaleX(scale).scaleY(scale)
                            .setDuration(120L).start();
                } else {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                }
            }
        });

        return tile;
    }

    private final class CenterKeyListener implements View.OnKeyListener {
        private final AppRepository.AppEntry app;
        private long downAt;
        private boolean longPressHandled;

        CenterKeyListener(AppRepository.AppEntry app) {
            this.app = app;
        }

        @Override
        public boolean onKey(View view, int keyCode, KeyEvent event) {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER
                    && keyCode != KeyEvent.KEYCODE_ENTER) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    downAt = SystemClock.uptimeMillis();
                    longPressHandled = false;
                } else if (!longPressHandled) {
                    longPressHandled = true;
                    togglePin(app);
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (longPressHandled || downAt == 0L) {
                    downAt = 0L;
                    longPressHandled = false;
                    return true;
                }
                long held = SystemClock.uptimeMillis() - downAt;
                downAt = 0L;
                if (held >= LONG_PRESS_MS) {
                    togglePin(app);
                } else {
                    launch(app);
                }
                return true;
            }
            return false;
        }
    }

    private View.OnFocusChangeListener scaleFocus(final float focusedScale) {
        return new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                float scale = hasFocus ? focusedScale : 1f;
                if (animationsEnabled()) {
                    view.animate().scaleX(scale).scaleY(scale)
                            .setDuration(120L).start();
                } else {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                }
            }
        };
    }

    private void setExpanded(boolean expand) {
        if (expanded == expand) {
            return;
        }
        if (sheetOpen) {
            hideSheetImmediately(false);
        }
        expanded = expand;
        cancelAnimations();
        handler.removeCallbacks(hideCompactRow);

        final boolean animate = animationsEnabled();
        if (expand) {
            applyGridOffset();
            compactRow.setVisibility(View.VISIBLE);
            fullLayer.setVisibility(View.VISIBLE);
            fullLayer.setAlpha(animate ? 0f : 1f);
            if (animate) {
                heroClock.animate().scaleX(HERO_EXPANDED_SCALE).scaleY(HERO_EXPANDED_SCALE)
                        .translationY(heroExpandedTranslationY())
                        .setDuration(220L).start();
                compactRow.animate().alpha(0f).translationY(dp(24))
                        .setDuration(180L).start();
                fullLayer.animate().alpha(1f)
                        .setStartDelay(60L).setDuration(220L).start();
            } else {
                setCompactCollapsedVisuals();
            }
            handler.postDelayed(hideCompactRow, animate ? 240L : 0L);
            fullScroll.scrollTo(0, 0);
            focusArea(true, focusedPackage);
        } else {
            compactRow.setVisibility(View.VISIBLE);
            if (animate) {
                heroClock.animate().scaleX(1f).scaleY(1f)
                        .translationY(0f).setDuration(220L).start();
                compactRow.animate().alpha(1f).translationY(0f)
                        .setDuration(220L).start();
                fullLayer.animate().alpha(0f).setDuration(180L)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (!expanded) {
                                    fullLayer.setVisibility(View.GONE);
                                }
                            }
                        }).start();
            } else {
                resetCompactVisuals();
                fullLayer.setVisibility(View.GONE);
                fullLayer.setAlpha(0f);
            }
            focusArea(false, focusedPackage);
        }
    }

    private void openSheet() {
        if (sheetOpen) {
            return;
        }
        View current = getCurrentFocus();
        if (current != null && current.getTag() instanceof String) {
            focusedPackage = (String) current.getTag();
        }
        sheetOpen = true;
        sheet.setVisibility(View.VISIBLE);
        sheet.setAlpha(0f);
        sheet.setTranslationY(-dp(28));
        setAppAccessibilityBlocked(true);
        if (animationsEnabled()) {
            sheet.animate().alpha(1f).translationY(0f)
                    .setDuration(180L).start();
        } else {
            sheet.setAlpha(1f);
            sheet.setTranslationY(0f);
        }
        final View first = sheet.getChildAt(0);
        if (first != null) {
            first.post(new Runnable() {
                @Override
                public void run() {
                    first.requestFocus();
                }
            });
        }
    }

    private void closeSheet(boolean restoreFocus) {
        if (!sheetOpen) {
            return;
        }
        sheetOpen = false;
        setAppAccessibilityBlocked(false);
        if (animationsEnabled()) {
            sheet.animate().alpha(0f).translationY(-dp(28))
                    .setDuration(160L)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (!sheetOpen) {
                                sheet.setVisibility(View.GONE);
                                sheet.setAlpha(1f);
                                sheet.setTranslationY(0f);
                            }
                        }
                    }).start();
        } else {
            sheet.setVisibility(View.GONE);
            sheet.setAlpha(1f);
            sheet.setTranslationY(0f);
        }
        if (restoreFocus) {
            focusArea(expanded, focusedPackage);
        }
    }

    private void hideSheetImmediately(boolean restoreFocus) {
        if (sheetOpen) {
            sheetOpen = false;
            sheet.animate().cancel();
            sheet.setVisibility(View.GONE);
            sheet.setAlpha(1f);
            sheet.setTranslationY(0f);
            setAppAccessibilityBlocked(false);
        } else {
            sheet.setVisibility(View.GONE);
        }
        if (restoreFocus) {
            focusArea(expanded, focusedPackage);
        }
    }

    private void setAppAccessibilityBlocked(boolean blocked) {
        int mode = blocked
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        compactContainer.setImportantForAccessibility(mode);
        fullLayer.setImportantForAccessibility(mode);
    }

    private void openQuick(int action) {
        Intent intent;
        switch (action) {
            case QUICK_NETWORK:
                intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                break;
            case QUICK_BLUETOOTH:
                intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                break;
            case QUICK_DISPLAY:
                intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                break;
            case QUICK_SOUND:
                intent = new Intent(Settings.ACTION_SOUND_SETTINGS);
                break;
            case QUICK_ACCESSIBILITY:
                intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
            case QUICK_ALL_SETTINGS:
                intent = new Intent(Settings.ACTION_SETTINGS);
                break;
            case QUICK_PERCH:
                intent = new Intent(this, SettingsActivity.class);
                break;
            default:
                return;
        }
        hideSheetImmediately(false);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.quick_settings_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void launch(AppRepository.AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(app.category);
        intent.setComponent(new ComponentName(app.packageName, app.activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.launch_failed, app.label),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePin(AppRepository.AppEntry app) {
        int result = AppRepository.togglePin(this, app.packageName);
        if (result < 0) {
            Toast.makeText(this, R.string.pin_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        focusedPackage = app.packageName;
        refreshApps(true);
        Toast.makeText(this,
                getString(result == 1 ? R.string.pinned_toast : R.string.unpinned_toast,
                        app.label),
                Toast.LENGTH_SHORT).show();
    }

    private void focusFull(int index) {
        if (index < 0 || index >= fullTiles.size()) {
            return;
        }
        final View target = fullTiles.get(index);
        target.requestFocus();
        ensureVisible(target);
    }

    private void focusArea(boolean full, String preferredPackage) {
        List<View> tiles = full ? fullTiles : compactTiles;
        if (tiles.isEmpty()) {
            root.post(new Runnable() {
                @Override
                public void run() {
                    root.requestFocus();
                }
            });
            return;
        }
        View target = findTile(tiles, preferredPackage);
        if (target == null) {
            target = tiles.get(0);
        }
        final View finalTarget = target;
        finalTarget.post(new Runnable() {
            @Override
            public void run() {
                finalTarget.requestFocus();
                ensureVisible(finalTarget);
            }
        });
    }

    private View findTile(List<View> tiles, String packageName) {
        if (packageName == null) {
            return null;
        }
        for (View tile : tiles) {
            if (packageName.equals(tile.getTag())) {
                return tile;
            }
        }
        return null;
    }

    private AppRepository.AppEntry findApp(String packageName) {
        if (packageName == null) {
            return null;
        }
        for (AppRepository.AppEntry app : apps) {
            if (packageName.equals(app.packageName)) {
                return app;
            }
        }
        return null;
    }

    private void ensureVisible(final View view) {
        view.post(new Runnable() {
            @Override
            public void run() {
                Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
                int inset = dp(10);
                rect.inset(-inset, -inset);
                view.requestRectangleOnScreen(rect, true);
            }
        });
    }

    private void cancelAnimations() {
        compactContainer.animate().cancel();
        heroClock.animate().cancel();
        compactRow.animate().cancel();
        fullLayer.animate().cancel();
        sheet.animate().cancel();
    }

    private void resetCompactVisuals() {
        heroClock.setScaleX(1f);
        heroClock.setScaleY(1f);
        heroClock.setAlpha(1f);
        heroClock.setTranslationY(0f);
        compactRow.setVisibility(View.VISIBLE);
        compactRow.setAlpha(1f);
        compactRow.setTranslationY(0f);
    }

    private void setCompactCollapsedVisuals() {
        heroClock.setScaleX(HERO_EXPANDED_SCALE);
        heroClock.setScaleY(HERO_EXPANDED_SCALE);
        heroClock.setAlpha(1f);
        heroClock.setTranslationY(heroExpandedTranslationY());
        compactRow.setAlpha(0f);
        compactRow.setTranslationY(dp(24));
    }

    /** Keeps the clock centered while it shrinks toward the top of the screen. */
    private float heroExpandedTranslationY() {
        float height = heroClock.getHeight() > 0
                ? heroClock.getHeight() : dp(HERO_FALLBACK_HEIGHT_DP);
        return dp(HERO_EXPANDED_TOP_DP) - heroClock.getTop()
                - height * (1f - HERO_EXPANDED_SCALE) / 2f;
    }

    /** The grid starts below the shrunk clock so scrolled tiles never run under it. */
    private void applyGridOffset() {
        if (fullLayer == null) {
            return;
        }
        float height = heroClock != null && heroClock.getHeight() > 0
                ? heroClock.getHeight() : dp(HERO_FALLBACK_HEIGHT_DP);
        int offset = Math.round(dp(HERO_EXPANDED_TOP_DP) + height * HERO_EXPANDED_SCALE);
        fullLayer.setPadding(0, offset + dp(HERO_GRID_GAP_DP), 0, 0);
    }

    private void applyTypeScale() {
        heroTime.setTextSize(TypeScale.heroClock(this));
        heroDate.setTextSize(TypeScale.heroDate(this));
        ipCompact.setTextSize(TypeScale.aux(this));
        for (TextView label : appLabels) {
            label.setTextSize(TypeScale.appLabel(this));
        }
        for (TextView label : sheetLabels) {
            label.setTextSize(TypeScale.sheet(this));
        }
        root.requestLayout();
        heroClock.post(new Runnable() {
            @Override
            public void run() {
                applyGridOffset();
                if (expanded) {
                    heroClock.setTranslationY(heroExpandedTranslationY());
                }
            }
        });
    }

    private void updateTimeFormats() {
        Locale locale = Locale.getDefault();
        boolean followSystem = prefs.getBoolean(KEY_FOLLOW_SYSTEM_CLOCK, false);
        if (followSystem) {
            timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        } else {
            timeFormat = new SimpleDateFormat("HH:mm", locale);
        }
        String pattern = "zh".equals(locale.getLanguage())
                ? "M月d日 EEEE"
                : android.text.format.DateFormat.getBestDateTimePattern(
                        locale, "EEEEMMMd");
        dateFormat = new SimpleDateFormat(pattern, locale);
    }

    private void updateClock() {
        if (timeFormat == null || dateFormat == null) {
            return;
        }
        Date now = new Date();
        String time = timeFormat.format(now);
        String date = dateFormat.format(now);
        if (!time.contentEquals(heroTime.getText())) {
            heroTime.setText(time);
        }
        if (!date.contentEquals(heroDate.getText())) {
            heroDate.setText(date);
        }
        String description = getString(R.string.a11y_clock, time, date);
        heroClock.setContentDescription(description);
    }

    private void updateIpVisibility() {
        int visibility = showIp ? View.VISIBLE : View.GONE;
        ipCompact.setVisibility(visibility);
        if (showIp) {
            updateIp();
        } else {
            lastIp = "";
        }
    }

    private void updateIp() {
        String ip = findLanIpv4();
        if (ip == null) {
            ip = "";
        }
        if (ip.equals(lastIp)) {
            return;
        }
        lastIp = ip;
        String text = ip.length() == 0
                ? getString(R.string.ip_none)
                : getString(R.string.ip_connected, ip);
        ipCompact.setText(text);
    }

    private String findLanIpv4() {
        String preferredPrivate = null;
        String anyPrivate = null;
        String anyIpv4 = null;
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager == null) {
                return null;
            }
            Network[] networks = manager.getAllNetworks();
            if (networks == null) {
                return null;
            }
            for (Network network : networks) {
                LinkProperties properties;
                try {
                    properties = manager.getLinkProperties(network);
                } catch (Exception e) {
                    continue;
                }
                if (properties == null) {
                    continue;
                }
                String name = properties.getInterfaceName();
                boolean preferred = name != null
                        && (name.startsWith("eth") || name.startsWith("wlan"));
                for (LinkAddress linkAddress : properties.getLinkAddresses()) {
                    InetAddress address = linkAddress.getAddress();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    boolean isPrivate = ip.startsWith("192.168.")
                            || ip.startsWith("10.") || ip.startsWith("172.");
                    if (preferred && isPrivate) {
                        preferredPrivate = ip;
                    }
                    if (isPrivate && anyPrivate == null) {
                        anyPrivate = ip;
                    }
                    if (anyIpv4 == null) {
                        anyIpv4 = ip;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "cannot read link properties", e);
        }
        if (preferredPrivate != null) {
            return preferredPrivate;
        }
        return anyPrivate != null ? anyPrivate : anyIpv4;
    }

    private boolean animationsEnabled() {
        try {
            return Settings.Global.getFloat(getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception e) {
            return true;
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private int color(int resource) {
        return getResources().getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
