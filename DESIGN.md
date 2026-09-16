---
name: "校集 Campus Noticeboard"
description: "A tactile campus noticeboard for understanding, reserving, buying, and collecting limited campus releases."
colors:
  paper: "#F4F1E8"
  paper-bright: "#FFFDF7"
  ink: "#171717"
  ink-soft: "#4E4C46"
  coral: "#EF5A43"
  coral-dark: "#AA2E20"
  campus-blue: "#3B68A0"
  campus-blue-dark: "#173F73"
  campus-blue-soft: "#EAF2FF"
  notice-yellow: "#F2C94C"
  focus-blue: "#3B68A0"
  rule: "#BBB6AA"
  rule-dark: "#777269"
  muted: "#DEDAD0"
typography:
  display:
    fontFamily: "ZCOOL QingKe HuangYou, Barlow Condensed, Noto Sans SC, Microsoft YaHei, sans-serif"
    fontSize: "64px"
    fontWeight: 900
    lineHeight: 0.9
    letterSpacing: "0"
  headline:
    fontFamily: "ZCOOL QingKe HuangYou, Barlow Condensed, Noto Sans SC, Microsoft YaHei, sans-serif"
    fontSize: "42px"
    fontWeight: 850
    lineHeight: 0.95
    letterSpacing: "0"
  title:
    fontFamily: "Noto Sans SC, Microsoft YaHei, sans-serif"
    fontSize: "16px"
    fontWeight: 700
    lineHeight: 1.35
    letterSpacing: "0"
  body:
    fontFamily: "Noto Sans SC, Microsoft YaHei, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.7
    letterSpacing: "0"
  label:
    fontFamily: "Noto Sans SC, Microsoft YaHei, sans-serif"
    fontSize: "12px"
    fontWeight: 750
    lineHeight: 1
    letterSpacing: "0"
  numeric:
    fontFamily: "Barlow Condensed, Noto Sans SC, Microsoft YaHei, sans-serif"
    fontSize: "48px"
    fontWeight: 900
    lineHeight: 0.9
    letterSpacing: "0"
rounded:
  square: "0"
  stamp: "50%"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  "2xl": "28px"
  "3xl": "42px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper-bright}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
    padding: "10px 16px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.campus-blue}"
    textColor: "{colors.paper-bright}"
    rounded: "{rounded.square}"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
    padding: "10px 16px"
    height: "44px"
  icon-button:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    rounded: "{rounded.square}"
    size: "38px"
  input:
    backgroundColor: "{colors.paper-bright}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.square}"
    padding: "0 11px"
    height: "42px"
  status-ticket:
    backgroundColor: "{colors.paper-bright}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
    padding: "4px 9px"
    height: "26px"
  product-card:
    backgroundColor: "{colors.paper-bright}"
    textColor: "{colors.ink}"
    rounded: "{rounded.square}"
    padding: "0"
  desktop-nav:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink-soft}"
    typography: "{typography.body}"
    rounded: "{rounded.square}"
    height: "72px"
  mobile-nav:
    backgroundColor: "{colors.paper-bright}"
    textColor: "{colors.ink-soft}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
    height: "64px"
---

# Design System: 校集 Campus Noticeboard

## Overview

**Creative North Star: "校园公告栏 / The Campus Noticeboard"**

Each limited campus release behaves like a live campus notice poster: tactile, civic, urgent, and orderly. Recycled-paper surfaces, black rules, fluorescent notice colors, date stamps, compact labels, and truthful merchandise imagery make the experience feel posted on campus rather than assembled from generic commerce modules.

The interface is task-first despite its graphic voice. Timing, eligibility, reservation or purchase state, and pickup instructions must remain scannable in every viewport. Dense information is organized as a ledger of notices; color blocks communicate business state, while square controls make actions tactile and decisive.

**Key Characteristics:**

- Recycled-paper cream and paper-bright surfaces under crisp black ink.
- Coral, campus blue, and notice yellow used as functional signals rather than decoration.
- Poster-scale display type paired with condensed, tabular numerals and quiet system Chinese text.
- Flat, ruled sections with ambient lift reserved for the primary board, sticky shell, and interactive cards.
- Real merchandise imagery presented clearly, with explicit fallbacks when an image is unavailable.

## Colors

The palette feels printed and civic: warm paper and black ink form the base, while three notice colors identify urgency, confirmation, and attention.

### Primary

- **Black Ink:** The default text, rule, primary-action, and high-contrast identity color.
- **Coral Release Notice:** The immediate-action accent for release links, counts, and urgent sale emphasis.

### Secondary

- **Campus Blue:** The affirmative state color for active selection, confirmation, pickup guidance, and immersive poster fields.
- **Notice Yellow:** The attention color for demo notices, deadlines, verification instructions, and provisional information.

### Tertiary

- **Focus Treatment:** Keyboard focus uses the campus-blue outline with sufficient offset, while labels and icons keep focused controls distinguishable from business status.

### Neutral

- **Recycled-Paper Cream:** The textured application canvas and persistent shell background.
- **Paper Bright:** The clean reading surface inside cards, boards, fields, and active navigation.
- **Soft Ink:** Secondary copy, metadata, inactive navigation, and explanatory text.
- **Black Rule and Dark Rule:** Structural dividers, field strokes, and card outlines.
- **Muted Pulp:** Loading surfaces, unavailable imagery, and quiet inactive states.

### Named Rules

**The Signal-Color Rule.** Coral means immediate release action, blue means active, confirmed, or focused, and yellow means attention; do not swap these roles for decoration.

**The Paper-and-Ink Rule.** Warm paper carries the environment, paper-bright surfaces carry content, and black rules organize both before any shadow is added.

## Typography

**Display Font:** ZCOOL QingKe HuangYou with Barlow Condensed, Noto Sans SC, and system Chinese fallbacks  
**Body Font:** Noto Sans SC with Microsoft YaHei and sans-serif fallback  
**Numeric Font:** Barlow Condensed with tabular numerals and system Chinese fallback

**Character:** Display type should feel like a campus poster headline or date stamp: condensed, direct, and visually dense. Body text stays familiar and highly legible so operational details never inherit the display face's theatricality.

### Hierarchy

- **Display** (900, 64px, 0.9): Dominant poster titles and immersive activity headlines; mobile implementations step down to the observed 44-48px range.
- **Headline** (850, 42px, 0.95): Page, catalog, workspace, and section headings; compact rail headings use 34px.
- **Title** (700, 16px, 1.35): Product names and compact content titles.
- **Body** (400, 14px, 1.7): Explanations and operational content, with long reading measures held near 65ch.
- **Label** (750, 12px, 1): Status, metadata, field labels, controls, and dense navigation.
- **Numeric** (900, 48px, 0.9): Countdowns, prices, dates, queue counts, and other changing quantities; always use tabular numerals where alignment matters.

### Named Rules

**The Poster-and-Ledger Rule.** Display type announces the event; system Chinese explains the task; condensed tabular numerals record time, price, and inventory.

**The Zero-Tracking Rule.** Letter spacing remains zero across body text, labels, controls, and display type.

## Layout

The application canvas is capped at 1540px with 24px desktop gutters and a 28px top rhythm. The primary desktop noticeboard uses three ruled columns: a weekly release rail, a dominant activity poster, and an eligibility-action panel. Product discovery follows in a four-column grid with 18px gaps.

At 1180px the same desktop structure compresses and product grids move to three columns. At 900px, navigation moves into its own fixed shell row below the scrollable main region; the noticeboard becomes poster, action panel, then weekly rail, and grids become two columns. At 620px, page gutters become 10px, grids become one column, controls reflow without horizontal clipping, and poster type steps down rather than scaling fluidly.

**The Information-Density Rule.** Responsive layouts change order and density while preserving timing, eligibility, action, and pickup information.

**The Mobile-Shell Rule.** Bottom navigation occupies its own 64px plus safe-area shell row; it never overlays or obscures scrollable content.

## Elevation & Depth

The system is flat by default. One-pixel rules, dashed separators, paper tone changes, and full-color state fields establish hierarchy. Ambient shadows appear only on the sticky header, the primary noticeboard, the mobile navigation shell, and product-card hover; they create lift without making every section a floating card.

### Shadow Vocabulary

- **Sticky Shell** (`0 8px 22px rgba(23, 23, 23, .08)`): Keeps the desktop header legible over scrolling paper.
- **Primary Board** (`0 18px 38px rgba(31, 28, 24, .12)`): Gives the dominant noticeboard a single ambient lift.
- **Interactive Card** (`0 12px 25px rgba(23, 23, 23, .12)`): Appears with a 4px upward shift on product-card hover.
- **Mobile Shell** (`0 -10px 24px rgba(23, 23, 23, .1)`): Separates the dedicated bottom-navigation row from the scroll region.

### Named Rules

**The Flat-by-Default Rule.** If borders, paper tone, or layout can express structure, do not add a shadow.

**The Ambient-Lift Rule.** Shadows belong to shell separation, the dominant board, or an interactive lift state; they never decorate passive sections.

## Shapes

Controls, cards, fields, notices, and layout containers use square corners. Thin solid rules communicate structure; dashed rules signal tickets, metadata, and provisional notices. Circular geometry is exceptional and limited to rotated poster stamps, never used as a general card or button treatment.

**The Square-Control Rule.** Buttons, inputs, tags, cards, and navigation states use a 0 radius.

**The Stamp Exception.** A 50% radius is reserved for circular release or verification stamps that behave like printed marks, not containers.

## Components

### Buttons

- **Shape:** Square, black-rule controls with a 44px minimum action height.
- **Primary:** Black ink fill, paper-bright text, 10px by 16px padding, and heavy label weight; hover changes the full control to campus blue.
- **Hover / Focus:** Hover uses a decisive color-field change; keyboard focus uses the global 3px focus-blue outline with a 3px offset.
- **Secondary:** Transparent paper-ground control with black ink and the same dimensions; hover reveals paper-bright fill.
- **Icon:** A stable 38px square with a dark rule; hover reverses to black ink with paper-bright iconography.
- **Disabled:** Both action variants reduce to 45% opacity and keep their geometry stable.

### Chips

- **Style:** Status tickets are square, outlined in their current text color, and sized at 26px high with 4px by 9px padding; compact tickets use 22px height.
- **State:** Coral, blue, yellow, muted, and ink tones identify business state with coordinated pale backgrounds; labels remain explicit and never rely on color alone.

### Cards / Containers

- **Corner Style:** Square with no clipping radius.
- **Background:** Paper-bright on the recycled-paper canvas; immersive posters use campus-blue fallbacks behind truthful imagery.
- **Shadow Strategy:** Product cards lift 4px and gain the Interactive Card shadow on hover; passive cards remain flat.
- **Border:** One-pixel dark rules around the card, dashed rules for the metadata ledger.
- **Internal Padding:** Product copy uses 16px; board rails and action panels use 24px on full desktop and 18px in compressed desktop layouts.

### Inputs / Fields

- **Style:** Paper-bright fill, 1px dark-rule stroke, square corners, 42px height, and 11px horizontal padding.
- **Focus:** Focus shifts the border to focus blue and adds a 2px inset blue baseline; the global focus-visible outline remains available for keyboard users.
- **Error / Disabled:** Errors use dark red text on pale coral with a matching border; disabled action states use 45% opacity and a not-allowed cursor.

### Navigation

Desktop navigation lives in the 72px sticky paper header. Inactive items use soft ink; hover and active states become paper-bright with visible side rules, and the active item receives a 4px coral baseline. At 900px and below, desktop navigation is replaced by an icon-and-label bottom row in its own shell track; the active destination uses campus blue on a pale blue field.

### Noticeboard

The signature noticeboard binds the weekly rail, dominant activity poster, and eligibility-action panel into one ruled surface. On mobile it deliberately reorders to poster, action, then weekly rail so the live event and next action arrive before context, while the bottom navigation remains outside the scroll region.

### Product Card

Product cards expose an honest 4:3 merchandise image, explicit unavailable-image fallback, two-digit index, sale-status ticket, name, subtitle, price, stock, and purchase limit. Hover zooms the image only 1.035x while the entire card lifts 4px, preserving inspectability rather than turning the card into an animated promo tile.

## Do's and Don'ts

### Do:

- **Do** make timing, eligibility, reservation or purchase state, and pickup guidance visible before secondary discovery content.
- **Do** use truthful local merchandise imagery at inspectable crops, with an explicit unavailable-image fallback.
- **Do** use one-pixel black rules and paper-tone changes as the first hierarchy tools.
- **Do** keep controls square, labels direct, numerals tabular, and focus visibly blue.
- **Do** reorder and condense information at the 900px and 620px breakpoints while preserving the complete task path.

### Don't:

- **Don't** fall back to a generic ecommerce carousel, rounded promo-card composition, or detached floating sections.
- **Don't** use coral, blue, or yellow interchangeably; each color has a stable functional role.
- **Don't** place the mobile bottom navigation over content or inside the scrolling region.
- **Don't** add shadows to passive sections when rules and tonal layering already establish hierarchy.
- **Don't** hide availability, eligibility, deadlines, or pickup requirements behind imagery or decorative poster effects.
