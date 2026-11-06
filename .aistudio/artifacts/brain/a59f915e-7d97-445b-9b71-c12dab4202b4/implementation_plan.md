# Implementation Plan - Cash Flow Graph X & Y Axis Values

Add labeled monthly X-axis values (`Dec 26`, `Jan 27`, `Feb 27`) and a dynamic step scale for Y-axis currency values with gridlines to the Cash Flow projection curve.

## User Review Required

> [!NOTE]
> The Y-axis will use a dynamic "nice step" algorithm (e.g. increments of $500, $1,000, $2,000, or $5,000 based on the user's projected cash range) to ensure 4–6 readable, evenly spaced gridlines on all phone screens.

- **X-axis format**: Monthly labels formatted as `MMM yy` (e.g. `Dec 26`, `Jan 27`, `Feb 27`) positioned at each month boundary.
- **Y-axis placement**: Left margin with aligned currency labels (`$5,000`, `$2,500`, `$0`, etc.), with a distinct red badge/label for the Safety Threshold line.
- **Scrubber alignment**: Touch gesture interaction updated to align with the padded plotting area.

---

## Proposed Changes

### UI Components

#### `CashFlowChart.kt`
- **Dynamic Y-Axis Scale**:
  - Implement a `calculateNiceStep(range: Double): Double` helper that computes readable increments (e.g. $500, $1,000, $2,000, $5,000, $10,000) targeting 4 to 6 tick lines.
  - Round `minY` and `maxY` to nice tick multiples.
  - Generate tick values: `generateSequence(startTick) { it + step }.takeWhile { it <= endTick }`.
- **Monthly X-Axis Ticks**:
  - Scan `dailyPoints` to find the first day of each month (`date.dayOfMonth == 1` or the first point in a new `YearMonth`).
  - Format label using `DateTimeFormatter.ofPattern("MMM yy", Locale.US)`.
  - Calculate label spacing; on compact screens with 6+ months, stagger or select appropriate monthly steps to prevent text overlap.
- **Canvas Layout & Text Rendering**:
  - Use `rememberTextMeasurer()` from Compose Foundation to render crisp, native text labels inside `Canvas`.
  - Introduce layout margins:
    - `paddingLeft`: ~52.dp for Y-axis labels.
    - `paddingBottom`: ~26.dp for X-axis monthly labels.
    - `paddingTop`: ~12.dp.
    - `paddingRight`: ~12.dp.
  - Draw light horizontal grid lines at each dynamic Y-axis tick.
  - Draw Y-axis text labels right-aligned in the left gutter.
  - Draw special red-accented label for the Safety Threshold line.
  - Draw small tick marks and centered month labels along the X-axis baseline.
  - Update `pointerInput` touch calculation: `touchFraction = ((offset.x - plotLeft) / plotWidth).coerceIn(0f, 1f)` so touch inspection aligns with the graph curves.

---

## Verification Plan

### Automated Tests
- Run unit and Robolectric tests: `gradle :app:testDebugUnitTest`.
- Compile applet with `compile_applet` to verify clean compilation without lint or build warnings.

### Manual Verification Points
- Verify X-axis shows monthly labels in `MMM yy` format across 1-month, 3-month, 6-month, and 12-month projection horizons.
- Verify Y-axis adapts dynamically when transactions change the balance scale.
- Verify scrubber dragging aligns with the curves and doesn't drift due to the left label gutter.
- Verify dark and light theme legibility for axis text and gridlines.
