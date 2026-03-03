# CannyMinute MVP Spec and Architecture (Android 10+)

## Assumptions
- App codename: `CannyMinute`.
- Short description: `Checkout Pause`.
- Minimum SDK is 29 (Android 10), target modern devices.
- No backend is used for MVP; all logic and storage remain on device.
- Checkout interruption is launched from an `AccessibilityService` via a full-screen activity, with a high-priority notification fallback when direct launch is blocked. Overlay fallback remains future work.

## A) MVP Product Spec

### Problem statement
Users make unplanned purchases when reaching checkout quickly in shopping apps or browsers. The app should detect likely checkout intent and insert a short mandatory pause + reflection to reduce impulse actions.

### Primary user stories
1. As a user, I can enable protection once, and it works across many apps/browsers without per-app integrations.
2. As a user, when I reach a likely checkout moment, I get a cooldown screen before I continue.
3. As a user, I can configure cooldown duration and which apps are exempt.
4. As a user, I can temporarily bypass protection for a short period (e.g., 10 minutes).
5. As a privacy-conscious user, I can keep all processing on device and avoid storing raw screen text.

### UX flow (MVP)
1. Onboarding:
- Explain accessibility purpose, on-device detection, and privacy model.
- Deep link to Accessibility Settings.
2. Passive detection:
- Accessibility service inspects visible text + view IDs from active window events.
3. Trigger:
- If confidence threshold is met and guardrails pass (not allowlisted, not bypassed, not debounced), launch cooldown screen.
4. Cooldown screen:
- Blocking timer (default 60s).
- Reflection step (short statement confirmation or short text).
- Primary exit choices:
  - `Continue purchase` (enabled after timer + reflection complete).
  - `Cancel for now`.
  - `Pause protection 10 min` (temporary bypass).
5. Return:
- User returns to prior app context.

### Settings (MVP)
- Protection enabled/disabled.
- Cooldown duration (seconds).
- App allow list (package names).
- Diagnostics mode (off by default): if enabled, allow storing raw snippets for tuning.
- Clear local logs.

### Edge cases and behaviors
- Same checkout screen emits many accessibility events: use confirmation window + debounce.
- Non-shopping contexts with words like "checkout" (travel, food delivery): confidence scoring + per-app tuning.
- Web checkout in browser tabs: evaluate page text similarly, but reduce confidence if signals are weak.
- App in allow list: never interrupt.
- Accessibility service unavailable/revoked: app shows clear status + re-enable guidance.
- Background launch restrictions: fallback notification opens cooldown when direct launch fails.

### Temporary bypass
- `Pause protection 10 min` sets `temporaryBypassUntilEpochMillis`.
- Service suppresses triggers until that timestamp.
- UI shows active bypass status and remaining time.

## B) Technical Architecture

### Modules (single app module for MVP)
- `ui`: Compose screens (`MainActivity`, `CooldownActivity`).
- `accessibility`: `CheckoutAccessibilityService` + UI snapshot extraction.
- `detection`: rules engine, app heuristics, confidence scoring.
- `data.settings`: DataStore-backed settings repository.
- `data.log`: Room DB for local event telemetry.
- `di`: Hilt module wiring.

### Key classes
- `CheckoutAccessibilityService`: listens for window events, extracts snapshot, runs detection, enforces guardrails, launches cooldown activity.
- `CheckoutRulesEngine`: keyword + view-ID heuristic scoring with per-app adjustments.
- `CooldownActivity` + `CooldownViewModel`: full-screen interruption and timer/reflection logic.
- `AppSettingsRepository`: DataStore persistence for duration, allow list, bypass, diagnostics.
- `DetectionEventLogger`: writes local structured logs (no raw text unless diagnostics enabled).

### Data storage
- DataStore (Preferences):
  - `protection_enabled: Boolean`
  - `cooldown_duration_seconds: Int`
  - `allow_list_packages: Set<String>`
  - `temporary_bypass_until_ms: Long`
  - `diagnostics_enabled: Boolean`
- Room:
  - `detection_events` table with timestamp, packageName, confidence, action, matchedSignals, and optional diagnostic text.

### Background execution constraints
- Accessibility service is bound by system; no persistent background worker required for detection.
- Minimize work per event:
  - Node traversal cap.
  - Fast pre-filters.
  - Debounced trigger decisions.
- Avoid long-running operations in event callback; offload writes to coroutine scope.

### Permission strategy
- Required: Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`).
- Optional (future): overlay permission for non-activity fallback.
- Messaging must explain exact scope, local processing, and disable path.

## C) Risks and Mitigations

### Accessibility permission sensitivity
Risk:
- Users distrust broad accessibility access.
Mitigation:
- Honest onboarding text with concrete examples of what is read.
- Clear statement that content never leaves device.
- One-tap disable control inside app.

### Google Play policy
Risk:
- Misuse classification if functionality appears to monitor unrelated behavior.
Mitigation:
- Narrow declared use case: user-initiated digital wellbeing / self-control.
- In-app disclosures + privacy policy aligned with on-device-only behavior.
- No collection/transmission of personal content by default.

### Security and privacy
Risk:
- Sensitive screen text could be captured.
Mitigation:
- Process in-memory only for matching.
- Store only derived signals by default.
- Explicit diagnostics toggle for raw snippets, with auto-expiry TODO.

### UX annoyance / false positives
Risk:
- Frequent interruptions drive uninstalls.
Mitigation:
- Confidence thresholds, per-app tuning, debouncing, allow list, temporary bypass.
- Local telemetry to tune heuristics.

## D) Detection Design

### Signals
- UI text keywords (e.g., `checkout`, `place order`, `buy now`, `pay now`, `order summary`).
- View ID keywords (e.g., contains `checkout`, `buy`, `pay`, `place_order`).
- Supporting context terms (`cart`, `shipping`, `payment`, `total`).

### Heuristic scoring (0.0 - 1.0)
- Strong checkout phrase match: +0.45 each (cap).
- Checkout-like view ID match: +0.30 each.
- Supporting context (cart/payment/total): +0.10 each.
- Multi-signal bonus when both text and view IDs match: +0.15.
- Clamp score to 1.0.
- Trigger threshold default: `>= 0.70`.

### Per-app config
- Optional per-package threshold offsets and extra keywords (MVP supports structure + defaults).
- Future: user-facing per-app sensitivity.

### Debouncing and confirmation
- Require 2 positive detections within a short window (e.g., 4s) before interrupting.
- Cooldown between interrupts per package (e.g., 30s minimum).
- Skip while temporary bypass is active.

### Local event logging for tuning
- Log fields:
  - timestamp
  - packageName
  - confidence
  - action (`triggered`, `below_threshold`, `ignored_allowlist`, `ignored_bypass`, `ignored_debounce`)
  - matchedSignals (keyword IDs, not raw full text)
  - optional raw snippet only when diagnostics enabled
- This enables offline tuning without external analytics.

## E) Implementation Plan

### Hello-world first slice (Day 1)
- Working accessibility service receives window events.
- Extract visible text + view IDs.
- Run simple rules engine and print/log decision.
- Manual trigger launches basic cooldown activity with timer.

### 1-week milestone
- Complete detection pipeline with threshold + debounce + 2-hit confirmation.
- Cooldown UI with required wait + reflection checkbox.
- DataStore settings: duration + allow list + bypass.
- Room local event log schema + insertion.
- Basic settings screen with accessibility enable guidance.

### 2-week milestone
- Improve heuristics (browser patterns, noise filtering, per-app thresholds).
- Add diagnostics mode gating and local log viewer/export (local file only).
- Improve lifecycle robustness (process death restore, strict main-thread guards).
- Start instrumentation tests for rules engine and settings persistence.

### 4-week milestone
- Policy hardening: disclosure screens, consent copy, privacy policy alignment.
- UX polishing: progressive strictness modes, better bypass UX, richer reflection prompts.
- Reliability improvements for launch fallback (overlay/notification path).
- Internal beta, false-positive tuning loop, and release checklist.

## F) Initial Scaffold Recommendation

### Stack
- Kotlin
- Jetpack Compose + Material 3
- Hilt (DI)
- DataStore (Preferences)
- Room (local tuning logs)

### Package structure
- `com.projectz.cannyminute`
- `com.projectz.cannyminute.ui`
- `com.projectz.cannyminute.ui.cooldown`
- `com.projectz.cannyminute.accessibility`
- `com.projectz.cannyminute.detection`
- `com.projectz.cannyminute.data.settings`
- `com.projectz.cannyminute.data.log`
- `com.projectz.cannyminute.di`

## Open TODOs after scaffold
- Add overlay interruption path for OEM/Android variants where activity + notification fallback is insufficient.
- Add richer onboarding and explicit consent tracking.
- Add unit tests and instrumentation tests.
- Add log retention policy and diagnostics auto-expiry.

