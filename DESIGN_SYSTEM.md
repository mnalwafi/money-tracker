# Buckwheat Design System & UI Architecture

This document defines the formal design system, layout rules, color tokens, typography hierarchy, input standards, and interaction physics for **Buckwheat**.

All UI components, drawers, sheets, and dialogs must adhere to these unified specifications.

---

## 1. Unified Sheet Backgrounds & Elevation Architecture

### Container Treatment
All bottom sheets and modal drawers (`DashboardDrawer`, `RecurringTransactionsSheet`, `Wallet`, `Settings`, `ViewerHistory`) share the exact same clean surface background:
- **Sheet Background**: `MaterialTheme.colorScheme.surface` (Token: `BuckwheatDesignSystem.Colors.sheetContainer`).
- **Elimination of Hardcoded Tints**: Static colors (`Color.White`, `Color.Black`, arbitrary hex values, or mismatched tonal tints) are forbidden for surface containers.
- **Top Corner Radius**: `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)` (`BuckwheatDesignSystem.Shapes.sheet`).
- **Drag Handle**: Centered pill affordance (`36.dp × 4.dp`, `CircleShape`) tinted with `onSurfaceVariant` at `0.40f` alpha, without redundant close (X) buttons.

### Drawer Height Hierarchy Rules (T-Shirt Sizing)
1. **`xl` Drawer (`92%` Screen Height)**:
   - Token: `BuckwheatDesignSystem.Drawers.xl = 0.92f`.
   - Applied to: **Settings Drawer**, **Dashboard Drawer**, and **Wallet / Budget Drawer**.
   - Leaves 8% top breathing room for background context.
2. **`lg` Drawer (`88%` Screen Height)**:
   - Token: `BuckwheatDesignSystem.Drawers.lg = 0.88f`.
   - Applied to: **Recurring & Subscriptions Drawer**, **Viewer History Drawer**, and **Analytics Drawer**.
   - Leaves 12% top breathing room.
3. **`default` Drawer (`75%` Screen Height)**:
   - Token: `BuckwheatDesignSystem.Drawers.default = 0.75f`.
   - Standard medium-height operational drawer.
4. **`sm` Drawer (`60%` Screen Height)**:
   - Token: `BuckwheatDesignSystem.Drawers.sm = 0.60f`.
   - Small partial drawer for compact selections.
5. **`xs` Drawer (`45%` Screen Height)**:
   - Token: `BuckwheatDesignSystem.Drawers.xs = 0.45f`.
   - Extra-small bottom drawer for concise actions.

> [!NOTE]
> **Dialogs & Pickers (Excluded from Drawer Sizing)**:
> Specialized content dialogs such as the **Calendar / DatePicker** (`FinishDateSelector`), TimePicker, and confirmation dialogs are not drawers. They size naturally to fit their intrinsic content without drawer height restrictions.

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
| `typography.displayMedium` | **Bold** | Hero Safe Daily Spend Allowance, Hero Commitment Numbers |
| `BuckwheatDesignSystem.Typography.drawerTitle` (`typography.titleLarge`) | **Bold** | Centered Drawer / Sheet Titles (Universal across all drawers) |
| `typography.headlineSmall` | **Bold** | Prominent Section Headers |
| `typography.titleMedium` | **SemiBold** | Card Primary Headings, Big Numbers |
| `typography.titleSmall` | **Bold** | List Item Headings, Section Labels |
| `typography.bodyMedium` | **Medium / Normal** | Card body text, transaction descriptions |
| `typography.bodySmall` | **Normal** | Secondary statistics, cycle progress |
| `typography.labelSmall` | **Bold / Medium** | Due date badges, interval pill labels |

### Universal Drawer Title Standard:
- All drawers and sheets must use `BuckwheatDesignSystem.Typography.drawerTitle` (`titleLarge`, `FontWeight.Bold`).
- Titles must be **centered** horizontally across the top of the drawer, accompanied by the standard pill drag handle above.
- Left-aligned drawer titles or disparate font sizes are strictly disallowed to maintain visual coherence across Dashboard, Recurring, Wallet, Viewer History, and Settings.

---

## 5. "Cool Card" Architecture (Wallet Fidelity Standard)

Cards across all drawers (specifically Dashboard and Recurring & Subscriptions) must match the visual depth, motion, and polish of the **Wallet Drawer**:

### 1. Hero Cards (`cardHero` - 28.dp Corner Radius):
- **Shape**: `BuckwheatDesignSystem.Shapes.cardHero = RoundedCornerShape(28.dp)`.
- **Dynamic Color Harmonization**: Background or liquid fill dynamically blended between semantic health tokens (`colorBad`, `colorNotGood`, `colorGood`) using `combineColors(...)` based on health or cycle percentage.
- **Continuous Animated Wave Fill**: Uses `WavyShape` driven by `rememberInfiniteTransition` to render smooth liquid wave motion.
- **Bold Display Hierarchy**: Main metric formatted in `MaterialTheme.typography.displayMedium` with bold weighting.
- **Rotated Badge Chip**: Key temporal or count indicators (e.g. days remaining, active subscriptions) housed in rotated pill badges (`-4.deg` to `-8.deg` rotation).

### 2. Item Cards (`cardItem` - 22.dp Corner Radius):
- **Shape**: `BuckwheatDesignSystem.Shapes.cardItem = RoundedCornerShape(22.dp)`.
- **Circular Icon Avatars**: Category or type icons placed in circular containers (`CircleShape`) with subtle tonal containers (`secondaryContainer`, `primaryContainer`).
- **Urgency-Coded Status Pills**: Badges use semantic urgency colors (overdue = `colorBad`, due soon = `colorNotGood`, normal = `primary`).
- **Surface Elevation & Outline**: `surfaceContainer` background with a subtle `1.dp` border (`outlineVariant.copy(alpha = 0.35f)`).

---

## 6. Input Controls & Auto-Formatting Standards

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

## 7. Gesture & Snap Physics Engine

Buckwheat's interactive model matches the **"Whole Budget Card"** standard:
- **1:1 Touch Tracking**: During upward or downward drag gestures, the sheet or card tracks touch movement synchronously without hitch or delay.
- **Effortless Dismissal Thresholds**:
  - `BuckwheatDesignSystem.Physics.dismissThreshold = 40.dp`.
  - `BuckwheatDesignSystem.Physics.dismissVelocityThreshold = 150f`.
  - Downward drags passing 40.dp or with velocity >= 150f immediately trigger `sheetState.hide()`.
- **Sticky Surface Drag Forwarding**: Bottom sticky action bars (such as the Dashboard quick add bar) must forward downward drag gestures to the sheet so pulling down anywhere near the bottom edge closes the sheet smoothly.
- **Snapping Specification**:
  - `SpringSpec(dampingRatio = 0.85f, stiffness = 380f)` (`BuckwheatDesignSystem.Physics.springSpec`).
  - Releases with an upward fling decisively snap to `Expanded`.
  - Releases with a downward fling decisively snap to `Hidden` / `Collapsed`.
- **Touch Slop Disambiguation**: Keypad taps are disambiguated with `touchSlop * 1.75f` directional checking so rapid digit entry never triggers unintentional sheet drags.
