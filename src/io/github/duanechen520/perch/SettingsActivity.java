package io.github.duanechen520.perch;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
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

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final String KEY_SHOW_IP = "show_ip";
    private static final String KEY_FOLLOW_SYSTEM_CLOCK = "time_follow_system";

    private final List<TextView> fontOptions = new ArrayList<TextView>();
    private final List<TextView> clockOptions = new ArrayList<TextView>();

    private SharedPreferences prefs;
    private LinearLayout pinnedList;
    private TextView homeStatus;
    private TextView setHomeButton;
    private TextView ipState;
    private TextView ipRow;
    private TextView backButton;
    private String pinnedSignature;
    private String restoredFocusTag;
    private boolean initialFocusDone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = AppRepository.prefs(this);
        if (savedInstanceState != null) {
            restoredFocusTag = savedInstanceState.getString("focus_tag");
        }
        buildContent();
        hideSystemUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        View focus = getCurrentFocus();
        if (focus != null && focus.getTag() != null) {
            outState.putString("focus_tag", String.valueOf(focus.getTag()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        updateHomeStatus();
        updateOptionSelections();
        updateIpRow();
        renderPinnedIfChanged();

        if (!initialFocusDone) {
            initialFocusDone = true;
            View target = findFocusTarget(restoredFocusTag);
            restoredFocusTag = null;
            if (target == null) {
                if (setHomeButton.getVisibility() == View.VISIBLE) {
                    target = setHomeButton;
                } else if (!fontOptions.isEmpty()) {
                    target = selectedFontOption();
                }
            }
            if (target == null) {
                target = backButton;
            }
            final View finalTarget = target;
            finalTarget.post(new Runnable() {
                @Override
                public void run() {
                    finalTarget.requestFocus();
                }
            });
        }
    }

    private View findFocusTarget(String tag) {
        if (tag == null) {
            return null;
        }
        for (TextView option : fontOptions) {
            if (tag.equals(String.valueOf(option.getTag()))) {
                return option;
            }
        }
        for (TextView option : clockOptions) {
            if (tag.equals(String.valueOf(option.getTag()))) {
                return option;
            }
        }
        return null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.bg));
        setContentView(root);

        root.addView(new WallpaperBackground(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        int widthDp = getResources().getConfiguration().screenWidthDp;
        int sidePadding = Math.max(64, (widthDp - 960) / 2);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(sidePadding), dp(38), dp(sidePadding), dp(56));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextColor(color(R.color.text_primary));
        title.setTextSize(36f);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        backButton = optionButton(R.string.settings_back);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        backParams.leftMargin = dp(16);
        header.addView(backButton, backParams);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        TextView tagline = new TextView(this);
        tagline.setText(R.string.tagline);
        tagline.setTextColor(color(R.color.text_secondary));
        tagline.setTextSize(TypeScale.aux(this));
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        taglineParams.topMargin = dp(6);
        content.addView(tagline, taglineParams);

        sectionHeading(content, R.string.settings_heading_default_home);

        homeStatus = bodyText();
        content.addView(homeStatus);

        setHomeButton = optionButton(R.string.settings_open_home_settings);
        LinearLayout.LayoutParams setHomeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        setHomeParams.topMargin = dp(14);
        content.addView(setHomeButton, setHomeParams);
        setHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openHomeSettings();
            }
        });

        sectionHeading(content, R.string.settings_heading_pins);
        TextView pinsHint = auxText();
        pinsHint.setText(R.string.settings_pins_hint);
        content.addView(pinsHint);

        pinnedList = new LinearLayout(this);
        pinnedList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pinsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pinsParams.topMargin = dp(6);
        content.addView(pinnedList, pinsParams);

        sectionHeading(content, R.string.settings_heading_text);
        LinearLayout fontRow = optionRow(content);
        addFontOption(fontRow, TypeScale.SMALL, R.string.text_small);
        addFontOption(fontRow, TypeScale.STANDARD, R.string.text_standard);
        addFontOption(fontRow, TypeScale.LARGE, R.string.text_large);
        linkRowHorizontally(fontRow);

        sectionHeading(content, R.string.settings_heading_clock);
        LinearLayout clockRow = optionRow(content);
        addClockOption(clockRow, false, R.string.clock_24h);
        addClockOption(clockRow, true, R.string.clock_system);
        linkRowHorizontally(clockRow);

        sectionHeading(content, R.string.settings_heading_diagnostics);
        ipRow = new TextView(this);
        ipRow.setTextSize(TypeScale.body(this));
        ipRow.setFocusable(true);
        ipRow.setClickable(true);
        ipRow.setBackgroundResource(R.drawable.row_bg);
        ipRow.setPadding(dp(18), dp(14), dp(18), dp(14));
        ipRow.setGravity(Gravity.CENTER_VERTICAL);
        ipRow.setMinHeight(dp(56));
        content.addView(ipRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        ipRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean show = prefs.getBoolean(KEY_SHOW_IP, false);
                prefs.edit().putBoolean(KEY_SHOW_IP, !show).apply();
                updateIpRow();
            }
        });

        TextView ipHint = auxText();
        ipHint.setText(R.string.settings_ip_hint);
        LinearLayout.LayoutParams ipHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ipHintParams.topMargin = dp(8);
        content.addView(ipHint, ipHintParams);

        sectionHeading(content, R.string.settings_heading_controls);
        TextView controls = auxText();
        controls.setText(R.string.settings_controls);
        content.addView(controls);

        sectionHeading(content, R.string.settings_heading_about);
        TextView about = auxText();
        about.setText(getString(R.string.settings_about, versionName()));
        content.addView(about);
    }

    private void addFontOption(LinearLayout row, final String preset, int label) {
        TextView option = optionButton(label);
        option.setTag(preset);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(option, params);
        option.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!preset.equals(TypeScale.get(SettingsActivity.this))) {
                    TypeScale.set(SettingsActivity.this, preset);
                    recreate();
                }
            }
        });
        fontOptions.add(option);
    }

    private void addClockOption(LinearLayout row, final boolean followSystem, int label) {
        TextView option = optionButton(label);
        option.setTag(followSystem ? Boolean.TRUE : Boolean.FALSE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(option, params);
        option.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit().putBoolean(KEY_FOLLOW_SYSTEM_CLOCK, followSystem).apply();
                updateClockSelection();
                updateOptionSelections();
            }
        });
        clockOptions.add(option);
    }

    private TextView selectedFontOption() {
        String preset = TypeScale.get(this);
        for (TextView option : fontOptions) {
            if (preset.equals(option.getTag())) {
                return option;
            }
        }
        return fontOptions.isEmpty() ? backButton : fontOptions.get(0);
    }

    private void updateOptionSelections() {
        String preset = TypeScale.get(this);
        for (TextView option : fontOptions) {
            option.setSelected(preset.equals(option.getTag()));
        }
        updateClockSelection();
    }

    private void updateClockSelection() {
        boolean followSystem = prefs.getBoolean(KEY_FOLLOW_SYSTEM_CLOCK, false);
        for (TextView option : clockOptions) {
            option.setSelected(Boolean.valueOf(followSystem).equals(option.getTag()));
        }
    }

    private void updateIpRow() {
        boolean show = prefs.getBoolean(KEY_SHOW_IP, false);
        String state = getString(show ? R.string.settings_state_on : R.string.settings_state_off);
        ipRow.setText(getString(R.string.settings_show_ip) + "    " + state);
        ipRow.setTextColor(color(R.color.text_primary));
        ipRow.setContentDescription(
                getString(R.string.settings_show_ip) + ", " + state);
    }

    private void updateHomeStatus() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(
                homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        boolean isDefault = resolved != null && resolved.activityInfo != null
                && getPackageName().equals(resolved.activityInfo.packageName);
        homeStatus.setText(isDefault
                ? R.string.settings_home_default
                : R.string.settings_home_not_default);
        homeStatus.setTextColor(color(
                isDefault ? R.color.status_ok : R.color.status_warn));
        setHomeButton.setVisibility(isDefault ? View.GONE : View.VISIBLE);
    }

    private void renderPinnedIfChanged() {
        List<AppRepository.AppEntry> all = AppRepository.load(this);
        List<AppRepository.AppEntry> pinned =
                new ArrayList<AppRepository.AppEntry>();
        StringBuilder signature = new StringBuilder();
        for (AppRepository.AppEntry app : all) {
            if (app.pinned) {
                pinned.add(app);
                signature.append(app.packageName).append('/')
                        .append(app.label).append(';');
            }
        }
        if (signature.toString().equals(pinnedSignature)) {
            return;
        }
        pinnedSignature = signature.toString();
        pinnedList.removeAllViews();
        if (pinned.isEmpty()) {
            TextView empty = auxText();
            empty.setText(R.string.settings_pins_empty);
            pinnedList.addView(empty);
            return;
        }
        for (final AppRepository.AppEntry app : pinned) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackgroundResource(R.drawable.row_bg);
            row.setPadding(dp(14), dp(8), dp(18), dp(8));
            row.setMinimumHeight(dp(62));
            row.setContentDescription(app.label + ", " + getString(R.string.settings_unpin));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(6);
            pinnedList.addView(row, rowParams);

            ImageView icon = new ImageView(this);
            Drawable drawable = app.icon;
            if (drawable == null) {
                drawable = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            }
            icon.setImageDrawable(drawable);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

            TextView label = new TextView(this);
            label.setText(app.label);
            label.setTextColor(color(R.color.text_primary));
            label.setTextSize(TypeScale.body(this));
            label.setMaxLines(1);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.leftMargin = dp(14);
            row.addView(label, labelParams);

            TextView action = new TextView(this);
            action.setText(R.string.settings_unpin);
            action.setTextColor(color(R.color.accent));
            action.setTextSize(TypeScale.aux(this));
            action.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(action);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AppRepository.togglePin(SettingsActivity.this, app.packageName);
                    renderPinnedIfChanged();
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.settings_pinned_removed, app.label),
                            Toast.LENGTH_SHORT).show();
                    focusPinnedOrOptions();
                }
            });
        }
    }

    private void focusPinnedOrOptions() {
        View target = null;
        for (int i = 0; i < pinnedList.getChildCount(); i++) {
            View child = pinnedList.getChildAt(i);
            if (child.isFocusable()) {
                target = child;
                break;
            }
        }
        if (target == null && !fontOptions.isEmpty()) {
            target = selectedFontOption();
        }
        if (target == null) {
            target = backButton;
        }
        final View finalTarget = target;
        finalTarget.post(new Runnable() {
            @Override
            public void run() {
                finalTarget.requestFocus();
            }
        });
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, R.string.settings_home_settings_missing,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void sectionHeading(LinearLayout content, int labelResource) {
        View divider = new View(this);
        divider.setBackgroundColor(color(R.color.hairline));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(28);
        content.addView(divider, dividerParams);

        TextView heading = new TextView(this);
        heading.setText(labelResource);
        heading.setTextColor(color(R.color.accent));
        heading.setTextSize(18f);
        heading.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(16);
        content.addView(heading, headingParams);
    }

    private LinearLayout optionRow(LinearLayout content) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        content.addView(row, params);
        return row;
    }

    private TextView optionButton(int labelResource) {
        TextView option = new TextView(this);
        option.setText(labelResource);
        option.setTextColor(getResources().getColorStateList(R.color.option_text));
        option.setTextSize(TypeScale.body(this));
        option.setGravity(Gravity.CENTER);
        option.setFocusable(true);
        option.setClickable(true);
        option.setBackgroundResource(R.drawable.option_bg);
        option.setPadding(dp(16), dp(10), dp(16), dp(10));
        option.setId(View.generateViewId());
        return option;
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

    private TextView bodyText() {
        TextView text = new TextView(this);
        text.setTextColor(color(R.color.text_secondary));
        text.setTextSize(TypeScale.body(this));
        text.setLineSpacing(dp(4), 1f);
        return text;
    }

    private TextView auxText() {
        TextView text = new TextView(this);
        text.setTextColor(color(R.color.text_secondary));
        text.setTextSize(TypeScale.aux(this));
        text.setLineSpacing(dp(4), 1f);
        return text;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.1.0";
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
