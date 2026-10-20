# Changelog

## 0.2.0 - 2026-10-20

- Added the 苔阶 backdrop: a warm charcoal gradient, one soft light above the clock, four receding ridges, and a runtime-generated grain that stops dark gradients from banding. It is drawn with `Canvas` on the home screen, the quick-settings layer, and the settings screen, so no image asset ships and the same relative coordinates cover 720p through 8K.
- Fixed the optional IP label sitting inside the overscan area; it now clears the 5% safe margin it already claims.
- Fixed a focused option in settings losing its selected appearance, which made the current text size and clock format unreadable while navigating.
- Fixed the compact app row stretching when fewer than five apps are installed; the row now holds five equal slots.
- App tiles now reserve two label lines and center their content, so the focus ring hugs the tile instead of trailing empty space.
- The clock in the all-apps header now follows the text-size preset.
- The clock in the all-apps header is now the same view as the first-screen clock: it shrinks and rises while staying centered, instead of cross-fading into a second clock in the corner.
- The first screen's row of apps is now literally the first row of the all-apps matrix - one scrollable list rather than two grids. Pressing ↓ slides that row up under the shrinking clock, and pressing ↓ again carries it off the top like any other row.
- Fixed the focused row resting half off the bottom of the screen on 720p: the platform's own reveal for a focused child ignores the list's bottom inset here, so the matrix now positions the reveal itself and keeps every row inside the safe area.
- The clock now tracks the scroll offset instead of a separate expanded flag, so it can never disagree with the list - coming back up always returns it to full size.
- Pressing ↓ on the last row no longer pages the list: a plain ScrollView treats unhandled D-pad presses as page scrolls, which walked the whole matrix off the screen. The matrix now leaves those keys to focus navigation, so the last row simply stays where it is.
- Verified on an Android 5.1.1 TV box at 1080p and at a forced 720p, including a real reboot with Perch as the only home app.

## 0.1.0 - 2026-10-20

- Initial public release of 栖 Perch, package `io.github.duanechen520.perch`.
- Calm first screen: large 24-hour clock, locale-aware date, one row of five apps.
- ↓ expands to a five-column app grid; BACK returns to the large clock.
- ↑ opens a seven-item quick-settings sheet; the same settings entry is last in the app grid.
- Pin up to five apps with a long press of OK; pinned apps move to the front and carry a moss dot.
- Text-size presets, 24-hour or system clock, optional local IP display (off by default).
- Accessibility pass: 5% safe area, 3 dp focus ring, 16 sp minimum text, AA contrast, content descriptions, reduced-motion support.
- New 苔枝 palette, adaptive icon, TV banner, and Chinese/English resources.
- `targetSdkVersion` 34, `<queries>` for launcher discovery, explicit component exports.
- Dependency-free build script with debug and release signing.
- Apache-2.0 license.
