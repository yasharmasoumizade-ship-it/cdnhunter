---
name: design-systems
description: Reference and apply patterns from well-known design systems (Material Design 3, Apple Human Interface Guidelines, Fluent Design, and popular consumer apps like Windscribe, Revolut, Linear) when building or refining any UI. ALWAYS consult this skill before writing or editing Compose/XML UI code, choosing colors, spacing, motion, or component shapes — even if the user does not explicitly say "design system" or name a specific reference. Trigger on any request to design, redesign, polish, restyle, or "make it look like X" for screens, buttons, cards, navigation, or app icons.
---

# Design Systems Reference

Before writing or editing any UI code, ground the work in an established design system rather than inventing spacing/color/motion values from scratch.

## Step 1: Identify the closest reference

Pick ONE primary reference based on the task:

- **Material Design 3 (Material You)** — default for Android apps unless told otherwise. Use its type scale (Display/Headline/Title/Body/Label, each with Large/Medium/Small), 4dp spacing grid, elevation tokens, dynamic color roles (primary/secondary/tertiary/surface/error, each with "on-" pairs), and motion easing curves (emphasized, standard, decelerate/accelerate).
- **Apple Human Interface Guidelines** — for iOS-flavored aesthetics (SF Symbols equivalents, frosted glass/vibrancy, 8pt grid, San Francisco type scale) when the user explicitly wants an iOS-like feel on Android.
- **Fluent Design (Microsoft)** — for acrylic/frosted materials, reveal highlight, depth layering.
- **Consumer app references** — when the user names or shows a specific app (Windscribe, Revolut, Linear, Notion, Stripe Dashboard), treat that app's actual UI as the primary source of truth for THIS task, but still borrow underlying system tokens (spacing scale, elevation, type weights) from Material 3 to keep the implementation idiomatic Compose.

## Step 2: Apply concrete tokens, not vibes

When implementing, be explicit and consistent about:

- **Spacing**: stick to a 4dp (or 8dp) grid — 4, 8, 12, 16, 24, 32, 48dp. Never arbitrary values like 13dp or 22dp unless matching a specific pixel-measured reference.
- **Corner radius**: pick one scale and reuse it — e.g. Material 3 shape scale (none/extra-small 4dp/small 8dp/medium 12dp/large 16dp/extra-large 28dp/full). Don't mix random radii across components.
- **Type scale**: use a named scale (Material 3 type roles or a custom 2–3 weight system) rather than ad-hoc sp values per screen.
- **Color roles**: define semantic roles (background, surface, surfaceVariant, onSurface, primary, onPrimary, error states) rather than hardcoding hex per component. When copying a reference app's palette, extract exact hex values from the screenshot/description given, then map them to roles.
- **Elevation/shadow**: use a small fixed set of elevation levels (0/1/3/6/8/12dp equivalent) rather than inventing shadow blur per component.
- **Motion**: default to Material 3 easing (emphasized: cubic-bezier(0.2, 0.0, 0, 1.0) for entering, standard for simple transitions) and duration tokens (short 100-200ms, medium 250-400ms, long 450-600ms) unless the reference app clearly uses something else (e.g. spring/bouncy physics — match that instead).

## Step 3: Match, don't approximate, when a concrete reference exists

If the user gave a screenshot, HTML mockup, or named a specific app screen:
- Treat proportions, exact color transitions, and component shapes in that reference as ground truth — the design system above fills in the gaps it doesn't specify (e.g. what corner radius to use on a NEW element the reference doesn't show), not override what's explicitly shown.
- Call out any place you're extrapolating beyond what the reference shows, so the user can correct it.

## Step 4: Consistency check before finishing

Before considering a UI change done, verify:
- Every spacing value traces back to the grid
- Every color traces back to a named role, not a one-off hex
- Every corner radius is from the shape scale
- Component states (pressed/disabled/loading/error) are defined, not just the default state
