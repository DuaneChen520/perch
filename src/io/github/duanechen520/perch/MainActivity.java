package io.github.duanechen520.perch;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
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
import java.net.NetworkInterface;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
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
    private static final int TILE_ICON_DP = 80;
    private static final int DOCK_BOTTOM_DP = 44;
    private static final int SLIDE_MS = 220;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppRepository.AppEntry> apps =
            new ArrayList<AppRepository.AppEntry>();
    private final List<View> tiles = new ArrayList<View>();
    private final List<TextView> appLabels = new ArrayList<TextView>();

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
    private LinearLayout heroClock;
    private Space leadSpace;
    private LinearLayout fullGrid;
    private Space tailSpace;
    private ScrollView fullScroll;
    private LinearLayout sheet;
    private LinearLayout appMenu;
    private TextView heroTime;
    private TextView heroDate;
    private TextView ipCompact;
    private ValueAnimator scrollAnimator;
    private AppRepository.AppEntry menuApp;
    private View menuAnchor;
    private int leadHeight;

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
    private boolean appMenuOpen = false;
    private boolean swallowCenterUntilUp = false;
    private boolean showIp = false;
    private int nextViewId = 0;

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
            focusArea(expanded);
        }

        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        if (appMenuOpen) {
            closeAppMenu(false);
        }
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
        if (appMenuOpen) {
            closeAppMenu(false);
        }
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
        if (appMenuOpen) {
            closeAppMenu(false);
        }
        if (sheetOpen) {
            hideSheetImmediately(false);
        }
        if (expanded) {
            setExpanded(false);
        } else {
            focusArea(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (appMenuOpen) {
            closeAppMenu(true);
            return;
        }
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The long press that opened the menu is still holding OK down. Its repeats
        // and final up must not land on whichever menu item just took focus.
        if (appMenuOpen && swallowCenterUntilUp
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                swallowCenterUntilUp = false;
            }
            return true;
        }
        // The dock row is a real row now, so the framework would happily move focus
        // down it. Entering the matrix has to win before that focus search runs.
        if (!expanded && !sheetOpen && !appMenuOpen
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            setExpanded(true);
            return true;
        }
        return super.dispatchKeyEvent(event);
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

        if (appMenuOpen) {
            // The menu is a small vertical list; leaving it upward or downward puts
            // focus back on the tile instead of wandering into its neighbours.
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    && event.getRepeatCount() == 0 && isLastMenuItemFocused()) {
                closeAppMenu(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                    && event.getRepeatCount() == 0 && isFirstMenuItemFocused()) {
                closeAppMenu(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        if (!expanded) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.getRepeatCount() == 0) {
                    openSheet();
                }
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        // Inside the matrix, left/right/down are plain focus navigation and the view
        // tree already owns them. Only the first row has somewhere to go that is not
        // another tile: back to the large clock.
        int index = tiles.indexOf(getCurrentFocus());
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && index >= 0 && index < COLUMNS) {
            setExpanded(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.bg));
        setContentView(root);

        root.addView(new WallpaperBackground(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        buildGrid();
        buildCompact();
        buildSheet();
        buildAppMenu();

        heroClock.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (bottom - top != oldBottom - oldTop) {
                    applyHeaderInset();
                    layoutLead();
                }
            }
        });
    }

    private void buildCompact() {
        compactContainer = new FrameLayout(this);
        root.addView(compactContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        heroClock = new LinearLayout(this);
        heroClock.setOrientation(LinearLayout.VERTICAL);
        heroClock.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams heroParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        heroParams.topMargin = dp(114);
        heroParams.leftMargin = dp(64);
        heroParams.rightMargin = dp(64);
        compactContainer.addView(heroClock, heroParams);

        heroTime = new TextView(this);
        heroTime.setGravity(Gravity.CENTER);
        heroTime.setTextColor(color(R.color.text_primary));
        heroTime.setTextSize(TypeScale.heroClock(this));
        heroTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        heroTime.setIncludeFontPadding(false);
        if (Build.VERSION.SDK_INT >= 21) {
            heroTime.setLetterSpacing(0.02f);
        }
        setAccessibility(heroTime, View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroClock.addView(heroTime, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        heroDate = new TextView(this);
        heroDate.setGravity(Gravity.CENTER);
        heroDate.setTextColor(color(R.color.text_secondary));
        heroDate.setTextSize(TypeScale.heroDate(this));
        heroDate.setIncludeFontPadding(false);
        setAccessibility(heroDate, View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams heroDateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        heroDateParams.topMargin = dp(10);
        heroClock.addView(heroDate, heroDateParams);

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

    /** One scrollable matrix. The first screen is this list resting at the bottom. */
    private void buildGrid() {
        fullScroll = new MatrixScroll(this);
        fullScroll.setFillViewport(true);
        fullScroll.setVerticalScrollBarEnabled(false);
        fullScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(fullScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        fullGrid = new LinearLayout(this);
        fullGrid.setOrientation(LinearLayout.VERTICAL);
        fullGrid.setPadding(dp(64), 0, dp(64), dp(48));
        fullScroll.addView(fullGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        leadSpace = new Space(this);
        fullGrid.addView(leadSpace, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0));

        tailSpace = new Space(this);
        fullGrid.addView(tailSpace, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0));
    }

    private void buildSheet() {
        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.HORIZONTAL);
        sheet.setGravity(Gravity.CENTER);
        sheet.setBackgroundColor(color(R.color.sheet));
        sheet.setPadding(dp(64), dp(40), dp(64), dp(20));
        sheet.setVisibility(View.GONE);
        root.addView(sheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));
    }

    /** A small action list that covers exactly the box of the tile it belongs to. */
    private void buildAppMenu() {
        appMenu = new LinearLayout(this);
        appMenu.setOrientation(LinearLayout.VERTICAL);
        appMenu.setBackgroundResource(R.drawable.app_menu_bg);
        appMenu.setPadding(dp(6), dp(6), dp(6), dp(6));
        appMenu.setVisibility(View.GONE);
        root.addView(appMenu, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
    }

    /** Rebuilt on every open: an entry that cannot be opened is simply not there. */
    private void populateSheet() {
        sheet.removeAllViews();
        Intent inputSource = inputSourceIntent();
        if (inputSource != null) {
            addSheetItem(R.string.quick_input_source, inputSource);
        }
        addSheetItem(R.string.quick_system_settings, new Intent(Settings.ACTION_SETTINGS));
        addSheetItem(R.string.quick_perch, new Intent(this, SettingsActivity.class));
        linkRowHorizontally(sheet);
    }

    private void addSheetItem(int labelRes, final Intent intent) {
        TextView item = new TextView(this);
        item.setText(labelRes);
        item.setTextColor(color(R.color.text_primary));
        item.setTextSize(TypeScale.sheet(this));
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackgroundResource(R.drawable.sheet_item_bg);
        item.setPadding(dp(8), dp(14), dp(8), dp(14));
        item.setMinWidth(dp(132));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), 0, dp(4), 0);
        item.setLayoutParams(params);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openQuick(intent);
            }
        });
        item.setId(newViewId());
        item.setOnFocusChangeListener(scaleFocus(1.03f));
        sheet.addView(item);
    }

    /**
     * Nothing in the platform opens an input picker, so try the AOSP TV entries and the
     * action some OEM TV settings register; anything that does not resolve stays hidden.
     */
    private Intent inputSourceIntent() {        Intent[] candidates = {
                new Intent().setClassName("com.android.tv.settings",
                        "com.android.tv.settings.system.InputsActivity"),
                new Intent().setClassName("com.android.tv", "com.android.tv.SelectInputActivity"),
                new Intent("android.settings.TV_INPUT_SETTINGS")
        };
        PackageManager manager = getPackageManager();
        for (Intent candidate : candidates) {
            if (manager.resolveActivity(candidate, 0) != null) {
                return candidate;
            }
        }
        return null;
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

    private void openAppMenu(View tile, AppRepository.AppEntry app, boolean fromKey) {
        if (appMenuOpen || tile == null || tile.getWidth() == 0 || tile.getHeight() == 0) {
            return;
        }
        if (sheetOpen) {
            hideSheetImmediately(false);
        }
        menuApp = app;
        menuAnchor = tile;
        appMenuOpen = true;
        swallowCenterUntilUp = fromKey;
        focusedPackage = app.packageName;
        populateAppMenu();
        positionAppMenu(tile);
        setAppAccessibilityBlocked(true);
        appMenu.setVisibility(View.VISIBLE);
        appMenu.setAlpha(0f);
        if (animationsEnabled()) {
            appMenu.animate().alpha(1f).setDuration(120L).start();
        } else {
            appMenu.setAlpha(1f);
        }
        View first = appMenu.getChildAt(0);
        if (first != null) {
            first.requestFocus();
        }
    }

    private void populateAppMenu() {
        appMenu.removeAllViews();
        final AppRepository.AppEntry app = menuApp;
        addAppMenuItem(R.string.menu_open, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!appMenuOpen) {
                    return;
                }
                closeAppMenu(false);
                launch(app);
            }
        });
        addAppMenuItem(app.pinned ? R.string.menu_unpin : R.string.menu_pin,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (appMenuOpen) {
                            togglePinFromMenu(app);
                        }
                    }
                });
        if (canUninstall(app)) {
            addAppMenuItem(R.string.menu_uninstall, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (appMenuOpen) {
                        uninstallFromMenu(app);
                    }
                }
            });
        }
        linkMenuVertically();
    }

    private void addAppMenuItem(int labelRes, View.OnClickListener listener) {
        TextView item = new TextView(this);
        item.setText(labelRes);
        item.setTextColor(color(R.color.text_primary));
        item.setTextSize(TypeScale.sheet(this));
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackgroundResource(R.drawable.sheet_item_bg);
        item.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        item.setLayoutParams(params);
        item.setOnClickListener(listener);
        item.setId(newViewId());
        appMenu.addView(item);
    }

    private void linkMenuVertically() {
        int count = appMenu.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = appMenu.getChildAt(i);
            View up = appMenu.getChildAt(i == 0 ? 0 : i - 1);
            View down = appMenu.getChildAt(i == count - 1 ? count - 1 : i + 1);
            child.setNextFocusUpId(up.getId());
            child.setNextFocusDownId(down.getId());
            child.setNextFocusLeftId(child.getId());
            child.setNextFocusRightId(child.getId());
        }
    }

    /** The card borrows the tile's own box, clamped to the grid's visible band. */
    private void positionAppMenu(View tile) {
        int[] tileLocation = new int[2];
        tile.getLocationInWindow(tileLocation);
        int[] rootLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        int left = tileLocation[0] - rootLocation[0];
        int top = tileLocation[1] - rootLocation[1];
        int topLimit = headerBottom();
        int bottomLimit = root.getHeight() - dp(DOCK_BOTTOM_DP);
        int height = Math.min(tile.getHeight(), Math.max(0, bottomLimit - topLimit));
        top = Math.max(topLimit, Math.min(top, bottomLimit - height));
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) appMenu.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = left;
        params.topMargin = top;
        params.width = tile.getWidth();
        params.height = height;
        appMenu.setLayoutParams(params);
    }

    private void closeAppMenu(boolean restoreFocus) {
        if (!appMenuOpen) {
            return;
        }
        appMenuOpen = false;
        swallowCenterUntilUp = false;
        menuApp = null;
        appMenu.animate().cancel();
        appMenu.setVisibility(View.GONE);
        appMenu.setAlpha(1f);
        setAppAccessibilityBlocked(false);
        final View anchor = menuAnchor;
        menuAnchor = null;
        if (restoreFocus && anchor != null && tiles.contains(anchor)) {
            anchor.post(new Runnable() {
                @Override
                public void run() {
                    anchor.requestFocus();
                }
            });
        }
    }

    private boolean isFirstMenuItemFocused() {
        return isMenuItemFocused(0);
    }

    private boolean isLastMenuItemFocused() {
        return isMenuItemFocused(appMenu.getChildCount() - 1);
    }

    private boolean isMenuItemFocused(int index) {
        View focus = getCurrentFocus();
        return index >= 0 && focus != null && focus.getParent() == appMenu
                && appMenu.indexOfChild(focus) == index;
    }

    private boolean canUninstall(AppRepository.AppEntry app) {
        if (app.packageName.equals(getPackageName())) {
            return false;
        }
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(app.packageName, 0);
            return (info.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void togglePinFromMenu(AppRepository.AppEntry app) {
        int result = AppRepository.togglePin(this, app.packageName);
        if (result < 0) {
            Toast.makeText(this, R.string.pin_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        closeAppMenu(false);
        focusedPackage = app.packageName;
        refreshApps(true);
        Toast.makeText(this,
                getString(result == 1 ? R.string.pinned_toast : R.string.unpinned_toast,
                        app.label),
                Toast.LENGTH_SHORT).show();
    }

    private void uninstallFromMenu(AppRepository.AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_DELETE,
                Uri.fromParts("package", app.packageName, null));
        closeAppMenu(false);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.uninstall_failed, app.label),
                    Toast.LENGTH_SHORT).show();
            focusArea(expanded);
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
        if (appMenuOpen) {
            closeAppMenu(false);
        }
        if (sheetOpen) {
            hideSheetImmediately(false);
        }
        cancelAnimations();

        fullGrid.removeViews(1, fullGrid.getChildCount() - 1);
        tiles.clear();
        appLabels.clear();

        LinearLayout row = null;
        for (int index = 0; index < apps.size(); index++) {
            if (index % COLUMNS == 0) {
                row = newGridRow();
            }
            View tile = createTile(apps.get(index));
            row.addView(tile, tileParams());
            tiles.add(tile);
        }
        fullGrid.addView(tailSpace);

        layoutLead();
        fullScroll.scrollTo(0, expanded ? leadHeight : 0);
        focusArea(expanded);
    }

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(6), dp(6), dp(6), dp(6));
        return params;
    }

    private LinearLayout newGridRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setWeightSum(COLUMNS);
        fullGrid.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** The blank above row one is what lets the matrix rest with its first row at the bottom. */
    private void layoutLead() {
        if (fullScroll.getHeight() == 0 || tiles.isEmpty()) {
            return;
        }
        View parent = (View) tiles.get(0).getParent();
        int rowHeight = parent != null && parent.getHeight() > 0 ? parent.getHeight() : dp(180);
        int viewport = fullScroll.getHeight()
                - fullScroll.getPaddingTop() - fullScroll.getPaddingBottom();
        leadHeight = Math.max(0, viewport - rowHeight);
        setSpacerHeight(leadSpace, leadHeight);
        // The tail keeps the lead inside the scroll range even when the grid is
        // too short to fill the screen; focus never reaches it.
        setSpacerHeight(tailSpace, viewport);
        applyHeroOffset(fullScroll.getScrollY());
    }

    private void setSpacerHeight(View spacer, int height) {
        ViewGroup.LayoutParams params = spacer.getLayoutParams();
        if (params != null && params.height != height) {
            params.height = height;
            spacer.setLayoutParams(params);
        }
    }

    /** The header inset clips rows away; the bottom one keeps the last row off the bezel. */
    private void applyHeaderInset() {
        if (fullScroll != null) {
            fullScroll.setPadding(0, headerBottom(), 0, dp(DOCK_BOTTOM_DP));
        }
    }

    private int headerBottom() {
        float height = heroClock != null && heroClock.getHeight() > 0
                ? heroClock.getHeight() : dp(HERO_FALLBACK_HEIGHT_DP);
        return Math.round(dp(HERO_EXPANDED_TOP_DP) + height * HERO_EXPANDED_SCALE)
                + dp(HERO_GRID_GAP_DP);
    }

    private View createTile(final AppRepository.AppEntry app) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackgroundResource(R.drawable.app_tile_bg);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        tile.setMinimumHeight(dp(168));
        tile.setTag(app.packageName);
        tile.setContentDescription(app.pinned
                ? getString(R.string.a11y_pinned_app, app.label)
                : app.label);

        FrameLayout iconFrame = new FrameLayout(this);
        LinearLayout.LayoutParams iconFrameParams = new LinearLayout.LayoutParams(
                dp(TILE_ICON_DP), dp(TILE_ICON_DP));
        iconFrameParams.gravity = Gravity.CENTER_HORIZONTAL;
        tile.addView(iconFrame, iconFrameParams);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable drawable = app.icon;
        if (drawable == null) {
            drawable = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        }
        icon.setImageDrawable(drawable);
        setAccessibility(icon, View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconFrame.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (app.pinned) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.pin_dot);
            setAccessibility(dot, View.IMPORTANT_FOR_ACCESSIBILITY_NO);
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
        setAccessibility(label, View.IMPORTANT_FOR_ACCESSIBILITY_NO);
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
                openAppMenu(view, app, false);
                return true;
            }
        });
        tile.setOnKeyListener(new CenterKeyListener(app));
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    focusedPackage = app.packageName;
                    revealTile(view);
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
                    openAppMenu(view, app, true);
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
                    openAppMenu(view, app, false);
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

    /** Entering the matrix is a scroll: row one rises to the header and can later scroll away. */
    private void setExpanded(boolean expand) {
        if (expanded == expand) {
            return;
        }
        if (appMenuOpen) {
            closeAppMenu(false);
        }
        if (sheetOpen) {
            hideSheetImmediately(false);
        }
        expanded = expand;
        cancelAnimations();
        final int start = fullScroll.getScrollY();
        layoutLead();

        final int target = expand ? leadHeight : 0;
        if (animationsEnabled()) {
            scrollAnimator = ValueAnimator.ofInt(start, target);
            scrollAnimator.setDuration(SLIDE_MS);
            scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    fullScroll.scrollTo(0, (Integer) animator.getAnimatedValue());
                }
            });
            scrollAnimator.start();
        } else {
            fullScroll.scrollTo(0, target);
        }
        focusArea(expanded);
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
        populateSheet();
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
                    .setDuration(160L).start();
            // withEndAction is API 16; a delayed hide covers the same window on 15.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!sheetOpen) {
                        sheet.setVisibility(View.GONE);
                        sheet.setAlpha(1f);
                        sheet.setTranslationY(0f);
                    }
                }
            }, 160L);
        } else {
            sheet.setVisibility(View.GONE);
            sheet.setAlpha(1f);
            sheet.setTranslationY(0f);
        }
        if (restoreFocus) {
            focusArea(expanded);
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
            focusArea(expanded);
        }
    }

    private void setAppAccessibilityBlocked(boolean blocked) {
        int mode = blocked
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        setAccessibility(compactContainer, mode);
        setAccessibility(fullScroll, mode);
    }

    private void openQuick(Intent intent) {
        hideSheetImmediately(false);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.quick_settings_unavailable,
                    Toast.LENGTH_SHORT).show();
            focusArea(expanded);
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

    /**
     * The platform reveals a focused tile against the raw view edge and ignores the
     * list's bottom inset, which leaves a row under the safe area. Take over that
     * scroll and aim it at the padded viewport instead.
     */
    private void revealTile(View tile) {
        if (fullScroll.getHeight() == 0 || !(tile.getParent() instanceof View)) {
            return;
        }
        View row = (View) tile.getParent();
        int viewport = fullScroll.getHeight()
                - fullScroll.getPaddingTop() - fullScroll.getPaddingBottom();
        int top = row.getTop() + tile.getTop();
        int bottom = row.getTop() + tile.getBottom();
        int scroll = fullScroll.getScrollY();
        int target = scroll;
        if (bottom > scroll + viewport) {
            target = bottom - viewport;
        } else if (top < scroll) {
            target = top;
        }
        if (target != scroll) {
            // smoothScrollTo, not scrollTo: the platform has already started its own
            // reveal and only replacing its scroller gets the last word.
            fullScroll.smoothScrollTo(0, target);
        }
    }

    private void focusArea(boolean full) {
        int limit = full ? tiles.size() : Math.min(COLUMNS, tiles.size());
        if (limit == 0) {
            return;
        }
        View target = findTile(focusedPackage, limit);
        if (target == null) {
            target = tiles.get(0);
        }
        final View finalTarget = target;
        finalTarget.post(new Runnable() {
            @Override
            public void run() {
                finalTarget.requestFocus();
            }
        });
    }

    private View findTile(String packageName, int limit) {
        if (packageName == null) {
            return null;
        }
        for (int i = 0; i < limit; i++) {
            if (packageName.equals(tiles.get(i).getTag())) {
                return tiles.get(i);
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

    private void cancelAnimations() {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        sheet.animate().cancel();
        appMenu.animate().cancel();
    }

    /** Keeps the clock centered while it shrinks toward the top of the screen. */
    private float heroExpandedTranslationY() {
        float height = heroClock.getHeight() > 0
                ? heroClock.getHeight() : dp(HERO_FALLBACK_HEIGHT_DP);
        return dp(HERO_EXPANDED_TOP_DP) - heroClock.getTop()
                - height * (1f - HERO_EXPANDED_SCALE) / 2f;
    }

    /** The clock is a function of the scroll, so it can never disagree with the list. */
    private void applyHeroOffset(int scrollY) {
        float t = leadHeight > 0
                ? Math.max(0f, Math.min(1f, (float) scrollY / leadHeight)) : 0f;
        float scale = 1f + (HERO_EXPANDED_SCALE - 1f) * t;
        heroClock.setScaleX(scale);
        heroClock.setScaleY(scale);
        heroClock.setTranslationY(heroExpandedTranslationY() * t);
    }

    private void applyTypeScale() {
        heroTime.setTextSize(TypeScale.heroClock(this));
        heroDate.setTextSize(TypeScale.heroDate(this));
        ipCompact.setTextSize(TypeScale.aux(this));
        for (TextView label : appLabels) {
            label.setTextSize(TypeScale.appLabel(this));
        }
        root.requestLayout();
        heroClock.post(new Runnable() {
            @Override
            public void run() {
                applyHeaderInset();
                layoutLead();
                if (expanded) {
                    fullScroll.scrollTo(0, leadHeight);
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
        String pattern;
        if ("zh".equals(locale.getLanguage())) {
            pattern = "M月d日 EEEE";
        } else if (Build.VERSION.SDK_INT >= 18) {
            pattern = android.text.format.DateFormat.getBestDateTimePattern(
                    locale, "EEEEMMMd");
        } else {
            pattern = "EEEE, MMM d";
        }
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
        if (ip.equals(lastIp) && ipCompact.getText().length() > 0) {
            return;
        }
        lastIp = ip;
        String text = ip.length() == 0
                ? getString(R.string.ip_none)
                : getString(R.string.ip_connected, ip);
        ipCompact.setText(text);
    }

    private String findLanIpv4() {
        String wifiIp = findWifiIpv4();
        if (wifiIp != null) {
            return wifiIp;
        }
        String preferredPrivate = null;
        String anyPrivate = null;
        String anyIpv4 = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (network == null || !network.isUp()) {
                    continue;
                }
                String name = network.getName();
                boolean preferred = name != null
                        && (name.startsWith("eth") || name.startsWith("wlan"));
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
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
            Log.w(TAG, "cannot read network interfaces", e);
        }
        if (preferredPrivate != null) {
            return preferredPrivate;
        }
        return anyPrivate != null ? anyPrivate : anyIpv4;
    }

    /** NetworkInterface throws on some API 15 stacks; Wi-Fi still reports its own address. */
    private String findWifiIpv4() {
        try {
            WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
            if (wifi == null) {
                return null;
            }
            WifiInfo info = wifi.getConnectionInfo();
            if (info == null) {
                return null;
            }
            int raw = info.getIpAddress();
            if (raw == 0) {
                return null;
            }
            return String.format(Locale.US, "%d.%d.%d.%d",
                    raw & 0xFF, (raw >> 8) & 0xFF, (raw >> 16) & 0xFF, (raw >> 24) & 0xFF);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean animationsEnabled() {
        try {
            float scale;
            if (Build.VERSION.SDK_INT >= 17) {
                scale = Settings.Global.getFloat(getContentResolver(),
                        Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            } else {
                scale = Settings.System.getFloat(getContentResolver(),
                        "animator_duration_scale", 1f);
            }
            return scale > 0f;
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

    /** View.generateViewId is API 17; these ids only have to be unique in this tree. */
    private int newViewId() {
        return 0x7F0F0000 | (++nextViewId & 0xFFFF);
    }

    private void setAccessibility(View view, int mode) {
        if (Build.VERSION.SDK_INT >= 16) {
            view.setImportantForAccessibility(mode);
        }
    }

    private class MatrixScroll extends ScrollView {

        MatrixScroll(Context context) {
            super(context);
        }

        @Override
        protected void onScrollChanged(int left, int top, int oldLeft, int oldTop) {
            super.onScrollChanged(left, top, oldLeft, oldTop);
            applyHeroOffset(top);
        }

        /** D-pad pages a plain ScrollView; here it moves focus, so the fallback is off. */
        @Override
        public boolean executeKeyEvent(KeyEvent event) {
            return false;
        }
    }
}
