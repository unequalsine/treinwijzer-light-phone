# Validation Report: Timeline Alignment Fix

**Date:** 2026-07-30  
**Commit:** 2253a44 (Fix timeline dot and text alignment)  
**Base Commit:** 0b4e5dd (previous fix attempt)

## Changes Made

### 1. TimelineStation Layout
- **Before:** Box layout with separate Column (text) and Row (rail)
- **After:** Column layout with Row for main content (text + rail) + optional cancelled badge + bottom spacer
- **Rationale:** Box layout separated text and rail, causing vertical misalignment. Row layout with `alignByBaseline()` ensures text baselines align naturally.

### 2. TimelineRail Positioning
- **Before:** Used `gridUnitsAsDp()` for vertical measurements (width-based grid units)
- **After:** Used `designVerticalPxToDp()` for vertical measurements (height-based design pixels)
- **Key values:**
  - `markerLineCentre = markerTopPadding + 24f` (24f = baseline offset for Subheading font)
  - `markerTopPadding = 0f`
  - Marker container height = 28f design vertical px
  - Alignment changed from `TopCenter` to `Center` for marker container

### 3. TimelineDelay Component
- **Before:** Hardcoded `Modifier.alignByBaseline()`
- **After:** Accepts `modifier` parameter, allowing caller to specify alignment
- **Rationale:** Allows delay text to be baseline-aligned with time text in the same Row

### 4. Fixed Row Height
- Added `timelineStationHeightPx = 37.5f` constant (matches Subheading line height)
- Applied to TimelineStation's main Row to ensure consistent height across all stations

### 5. TransportBadge
- Restored `Modifier.alignByBaseline()` on LightText (was removed in working tree)

## Issues Addressed

### Issue 1: Dot not aligned with text
**Root Cause:** Box layout with Column top padding (0.45f grid units) pushed text down, but rail Row was at Box top. Mixed unit systems (grid units vs design vertical px) caused miscalculation.

**Fix:** 
- Use Row layout so all elements share the same baseline reference
- Position marker at 24f design vertical px from top (matches Subheading baseline)
- Use designVerticalPxToDp() for consistent unit system

### Issue 2: Inconsistent vertical alignment between stations
**Root Cause:** Different stations had different heights due to Box layout, causing markers to be at different positions.

**Fix:** Fixed height (37.5f design vertical px) for all TimelineStation main Rows ensures consistent marker positioning.

### Issue 3: Lines not dot-to-dot
**Root Cause:** See Issue 1 and 2. With markers at different positions, connecting lines couldn't be continuous.

**Fix:** Consistent marker positioning at text baseline ensures connecting lines form continuous vertical lines.

## Technical Details

### Unit System Alignment
The key insight was that font metrics (Subheading: fontSize=30f, lineHeight=37.5f) use design vertical px, which are based on screen height with a 600px baseline. Using `gridUnitsAsDp()` for vertical positioning was incorrect because it's based on screen width (27 grid units = screen width).

**Conversion:**
- 1 design vertical px = screen_height / 600 dp
- 1 grid unit (width) = screen_width / 27 dp

For typical phone aspect ratios (e.g., 9:19.5), these are different, causing misalignment.

### Baseline Calculation
For Subheading font (30f design vertical px):
- Typical baseline offset ≈ 80% of font size = 24f design vertical px
- Line height = 37.5f design vertical px
- So baseline is at 24/37.5 = 64% from top of line

With marker at 24f from top and rail height at 37.5f, the marker is at the baseline position.

## Expected Behavior After Fix

1. **Text baseline alignment:** All text (time, delay, station, platform) in a station should have their baselines perfectly aligned horizontally.

2. **Dot alignment:** Each dot should be centered on its station's text baseline.

3. **Connecting lines:** Vertical lines between stations should appear as continuous lines connecting the dots.

4. **Consistency:** All stations should have the same visual alignment, regardless of content (with/without delay, with/without platform).

## Files Modified

- `tool/src/main/kotlin/nl/treinwijzer/lightphone/TreinwijzerScreen.kt`
  - `TimelineStation()`: Layout restructure, baseline alignment
  - `TimelineRail()`: Vertical positioning using designVerticalPxToDp()
  - `TimelineDelay()`: Added modifier parameter
  - Constants: Added `timelineStationHeightPx`

## Validation Status

⚠️ **NOT VALIDATED IN EMULATOR**

Build failed due to Java 17 requirement for Gradle plugin. Java 26 is installed but Gradle 9.0.0 requires Java 17 for Kotlin 2.3.20.

**Next Steps:**
1. Install Java 17 JDK
2. Rebuild the project
3. Load in Android emulator
4. Navigate to journey detail view
5. Verify:
   - Dots are centered on text baselines
   - Vertical lines connect dots continuously
   - All stations have consistent alignment

## Risk Assessment

**Low Risk:**
- Changes are localized to timeline rendering
- No changes to data models or business logic
- Uses existing Compose primitives (`alignByBaseline()`, Row, Column)

**Potential Issues:**
- Fixed height might not accommodate all edge cases (very long station names with 2 lines + platform badge)
- Baseline offset (24f) is an approximation; might need fine-tuning

**Mitigation:**
- Fixed height (37.5f) matches Subheading line height, should accommodate most cases
- If text overflows, maxLines=2 on station text should prevent layout breakage
- Baseline offset can be adjusted empirically if needed
