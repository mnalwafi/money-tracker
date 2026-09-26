# Buckwheat Design System & UI Architecture

This document defines the formal design system, layout rules, color tokens, typography hierarchy, input standards, and interaction physics for **Buckwheat**.

All UI components, drawers, sheets, and dialogs must adhere to these unified specifications.

---

## 1. Unified Sheet Backgrounds & Elevation Architecture

### Container Treatment
All bottom sheets and modal drawers (`DashboardDrawer`, `RecurringTransactionsSheet`, `Wallet`, `Settings`) must share the exact same surface background:
- **Sheet Background**: `MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)` (Token: `BuckwheatDesignSystem.Colors.sheetContainer`).
- **Elimination of Hardcoded Tints**: Static colors (`Color.White`, `Color.Black`, arbitrary hex values) are forbidden for surface containers.
- **Top Corner Radius**: `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)` (`BuckwheatDesignSystem.Shapes.sheet`).
- **Maximum Height Bound**: `maxHeight * 0.88f` (`BuckwheatDesignSystem.Physics.sheetMaxHeightRatio`) to preserve clear top breathing room and visible backdrop context.
- **Drag Handle**: Centered pill affordance (`36.dp × 4.dp`, `CircleShape`) tinted with `onSurfaceVariant` at `0.40f` alpha.

---

## 2. Dynamic Light & Dark Theme Contract

Buckwheat leverages Material 3 dynamic color tokens that harmonize with Android 12+ wallpaper palettes and degrade gracefully on earlier OS versions.

| Semantic Purpose | Dynamic Token | Usage Context |
| :--- | :--- | :--- |
| **Primary Text** | `MaterialTheme.colorScheme.onSurface` | Section headers, primary amounts, active titles |
| **Secondary Text** | `MaterialTheme.colorScheme.onSurfaceVariant` | Subtitles, due date captions, hints, intervals |
| **Primary Accent** | `MaterialTheme.colorScheme.primary` | Floating actions, primary buttons, active icons |
| **Primary Text on Accent** | `MaterialTheme.colorScheme.onPrimary` | Text and icons placed inside primary buttons |
| **Secondary Pill Container** | `MaterialTheme.colorScheme.secondaryContainer` | Interval tags, status indicators |
| **Tertiary Pill Container** | `MaterialTheme.colorScheme.tertiaryContainer` | "Auto" deduct pills, automated task badges |
| **Card Surface** | `MaterialTheme.colorScheme.surfaceContainer` | Hero cards, transaction rows, bill cards |
| **Card Border** | `outlineVariant.copy(alpha = 0.35f)` | 1.dp stroke for unified definition |
| **Divider** | `outlineVariant.copy(alpha = 0.25f - 0.35f)` | Horizontal list separators |
| **Destructive / Error** | `MaterialTheme.colorScheme.error` | Overdue bills, over-budget badge, delete buttons |

---

## 3. Spacing & Grid Rules

All layout spacing must be derived from the 4.dp / 8.dp base grid:

```text
xxs: 2.dp   |  xs: 4.dp   |  s: 8.dp    |  m: 12.dp
l:   16.dp  |  xl: 20.dp  |  xxl: 24.dp |  xxxl: 32.dp
```

### Layout Gaps:
- **Screen Edge Inset**: `16.dp` horizontal on mobile devices (`20.dp` on large displays).
- **Internal Card Padding**: `16.dp` for standard cards, `20.dp` for hero allowance card.
- **Card-to-Card Vertical Gap**: `12.dp`–`16.dp`.
- **Form Field Gap**: `16.dp` vertical between input rows.
- **Label-to-Input Gap**: `8.dp` vertical.
- **Section Header to Body Gap**: `4.dp`–`6.dp`.

---

## 4. Typography Hierarchy

| Style Token | Font Weight | Target Usage |
| :--- | :--- | :--- |
| `typography.displayMedium` | **Bold** | Hero Safe Daily Spend Allowance |
| `typography.headlineSmall` | **Bold** | Drawer / Sheet Titles |
| `typography.titleMedium` | **SemiBold** | Card Primary Headings, Big Numbers |
| `typography.titleSmall` | **Bold** | List Item Headings, Section Labels |
| `typography.bodyMedium` | **Medium / Normal** | Card body text, transaction descriptions |
| `typography.bodySmall` | **Normal** | Secondary statistics, cycle progress |
| `typography.labelSmall` | **Bold / Medium** | Due date badges, interval pill labels |

---

## 5. Input Controls & Auto-Formatting Standards

### Currency Input Architecture:
- Amount fields must accept clean string/decimal input and auto-format using `visualTransformationAsCurrency(context, currency, hintColor)` from `com.danilkinkin.buckwheat.util`.
- Number sanitization is performed via `fixedNumberString(input)` to preserve valid numeric structures.
- Internal state holds raw unformatted `BigDecimal` or clean digits, while the display presents formatted thousands separators and localized currency symbols.

### Form Field Dimensions:
- Standard Outlined Field Height: `56.dp`.
- Corner Radius: `RoundedCornerShape(14.dp)` (`BuckwheatDesignSystem.Shapes.input`).
- Primary Action Button Height: `52.dp`, `RoundedCornerShape(16.dp)`.
- Secondary / Dialog Button Height: `40.dp`–`48.dp`, `RoundedCornerShape(12.dp)`–`14.dp`.

---

## 6. Gesture & Snap Physics Engine

Buckwheat's interactive model matches the **"Whole Budget Card"** standard:
- **1:1 Touch Tracking**: During upward or downward drag gestures, the sheet or card tracks touch movement synchronously without hitch or delay.
- **Velocity Threshold**: `125.dp/s` (`BuckwheatDesignSystem.Physics.velocityThreshold`).
- **Snapping Specification**:
  - `SpringSpec(dampingRatio = 0.85f, stiffness = 380f)` (`BuckwheatDesignSystem.Physics`).
  - Releases with an upward fling decisively snap to `Expanded`.
  - Releases with a downward fling decisively snap to `Hidden` / `Collapsed`.
  - Releases with low velocity snap to the closest anchor based on a 50% displacement threshold.
- **Touch Slop Disambiguation**: Keypad taps are disambiguated with `touchSlop * 1.75f` directional checking so rapid digit entry never triggers unintentional sheet drags.
