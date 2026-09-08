**Source visual truth**

- `/var/folders/8w/_dnk6m8j0p3cn2wbchy5gprw0000gn/T/codex-clipboard-6927e299-ffb1-4190-9690-49fe729103e6.png`

**Implementation evidence**

- `docs/audit-screenshots/hr-onboarding-reference-20260712/1488x1058-selected-v1.png`
- `docs/audit-screenshots/hr-onboarding-reference-20260712/1488x1058-selected-v2.png`
- `docs/audit-screenshots/hr-onboarding-reference-20260712/1488x1058-selected-final.png`
- `docs/audit-screenshots/hr-onboarding-reference-20260712/1440x1024-selected.png`
- `docs/audit-screenshots/hr-onboarding-reference-20260712/1366x768-selected.png`
- `docs/audit-screenshots/hr-onboarding-reference-20260712/1280x720-selected-v2.png`
- Viewport: 1488×1058
- State: signed-in administrator, DRAFT queue, first record selected, confirmation unavailable
- Full-view comparison: reference and implementation were opened together at original resolution.
- Focused-region comparison: not required for iteration 1 because identity, list, progress and missing-material text remained legible in the full-size captures.

**Findings — iteration 1**

- [P1] Master/detail split is too left-heavy
  - Location: `.hr-onboarding-workbench`.
  - Evidence: source left pane occupies about 35% of the workbench; implementation occupies about 40%.
  - Impact: the selected employee detail loses the spacious hierarchy that defines the reference.
  - Fix: change the grid to 35%/65%, with a 390px left minimum only at narrower breakpoints.
- [P2] Synthetic alphanumeric name produces a numeric avatar
  - Location: `HrOnboardingDetailPane.employeeInitial`.
  - Evidence: source avatar shows a meaningful Chinese name character; implementation shows “2”.
  - Impact: avatar looks like a placeholder/count instead of an employee identity.
  - Fix: select the first CJK character when present, otherwise the first letter; never use trailing digits.
- [P2] Backend material group code leaks into UI
  - Location: missing-material group title.
  - Evidence: implementation renders `EMPLOYMENT`; source uses a user-facing Chinese category.
  - Impact: visible implementation terminology breaks polish and comprehension.
  - Fix: map `IDENTITY / ORGANIZATION / EMPLOYMENT / CONTRACT / SOCIAL` to Chinese labels and merge contract/social rows where appropriate.
- [P2] Reference first-screen density is reduced by the global page tab strip and wider master pane
  - Location: route shell and workbench sizing.
  - Evidence: implementation workbench begins lower and shows less right-pane width than the source.
  - Impact: fewer reference sections fit naturally above the fold.
  - Fix: tighten route-local heading/workbench spacing and reduce master width without changing global navigation behavior.

**Required fidelity surfaces**

- Fonts/typography: system Chinese sans-serif family and hierarchy are close; numeric avatar and raw group code are actionable.
- Spacing/layout rhythm: core order matches; grid ratio and route-local vertical density require fixes.
- Colors/tokens: white surfaces, neutral borders, purple selected/progress accents and red missing tags match the source intent.
- Image quality/assets: no custom raster assets are present in the target content area; all icons use the existing Element UI icon family. No image substitutions are required.
- Copy/content: dynamic test data differs by design; static labels match except the raw backend group code.

**Comparison history**

- Iteration 1 evidence identified one P1 and three P2 findings; all were addressed in iteration 2.
- Iteration 2 changed the workbench to 35%/65%, localized backend group codes, derived a stable CJK/letter avatar initial, and tightened route-local horizontal spacing. The 1488×1058 capture now reproduces the source's master/detail hierarchy, identity summary, three facts, five-step progress, missing-material grouping and fixed confirmation action.
- Responsive captures at 1440×1024, 1366×768 and 1280×720 show no overlap, horizontal clipping or hidden primary actions. A first 1280 capture exposed wrapping in the completeness and material-group labels; keeping the 120px and 150px tracks fixed the P2 issue in `1280x720-selected-v2.png`.
- The filter popover was opened through the rendered filter button and verified to expose date, organization, store, employee-category and owner controls plus reset/apply actions.

**Implementation Checklist**

- [x] Change workbench grid ratio and tighten route-local horizontal spacing.
- [x] Derive a stable human avatar initial.
- [x] Localize missing-material group labels.
- [x] Recapture 1488×1058 and re-check responsive widths and filter interaction.

**Follow-up Polish**

- P3: status counts for tabs not yet visited are intentionally omitted because the current API does not provide all-state totals.
- P3: the existing ERP route-tab strip remains visible above the page; it belongs to the shared authenticated shell and was not changed by this page-scoped redesign.
- P3: dynamic QA data has one missing-material group rather than the three groups shown in the source; the component renders all groups returned by the real service.

final result: passed

# Desktop login restoration QA — 2026-08-02

**Source visual truth**

- `output/design-qa/login-reference-1672x941.png`
- Source pixels: 1672×941.
- State: desktop login, empty account/password/result fields, captcha enabled. The arithmetic challenge is dynamic and is not expected to reproduce the same numbers.

**Implementation evidence**

- `output/design-qa/login-detail-pass-final-1672x941.png`
- `output/design-qa/login-reference-vs-detail-pass-final.png`
- `output/design-qa/login-hero-reference-vs-restored-final-clean.png`
- `output/design-qa/login-form-reference-vs-restored-final-v2.png`
- Responsive evidence: `output/design-qa/login-responsive-1366x768.png` and `output/design-qa/login-safari-final-1224x696.png`.
- Implementation pixels/CSS viewport: 1672×941 at 1× density; no density normalization was required.
- Browser-rendered route: `http://127.0.0.1:8088/login?redirect=%2Findex`.

**Full-view comparison evidence**

- The 62.75%/37.25% split, warm-white left image treatment, graphite type, unboxed right-side form, input dimensions, captcha ratio and dark login button now match the source composition.
- At 1366×768 and the reported Safari-equivalent 1224×696 viewport, the page has no horizontal or vertical overflow and the primary button remains fully visible.

**Focused-region comparison evidence**

- Hero comparison confirms the BossERP lockup, title baseline, vertical rule, paragraph and three feature positions follow the source.
- Form comparison confirms a 436px form width; the title, inputs, checkbox and button align to the source. Account, password and captcha prefix icons are optically centered in the 70px controls.

**Required fidelity surfaces**

- Fonts and typography: existing system Chinese sans-serif stack retained; headline, form title, labels, body copy, line height and letter spacing visually match without extra display-font loading.
- Spacing and layout rhythm: reference-size measurements align the form at x=1138.6, title y=153.2, inputs y=318.8/456.8/594.8 and button y=735.8 after the 941px viewport normalization.
- Colors and visual tokens: rejected green treatment removed from the desktop login; graphite, warm ivory, warm-gray borders and neutral focus ring now match the source.
- Image quality and asset fidelity: the clean tea-room background was reconstructed from the selected source, fitted to the measured horizontal and vertical crop, saved as `erp-ui/src/assets/images/desktop-login-workspace-neutral-v3.jpg`, and placed without text, logos or UI baked into it.
- Copy and content: all source-visible static Chinese copy is preserved; captcha digits remain server-generated by design.
- Icons: the existing BossERP mark is preserved; field and feature icons use the existing Element UI/icon system. No placeholder or emoji icons were introduced.

**Comparison history**

- Iteration 1 found a P1 image mismatch: the existing tea-room asset showed a bright window and did not reproduce the selected interior framing. It also found P2 green/card treatment drift. The form card, green veil, green kicker, security callout and extra footer were removed, and a source-grounded clean background was produced.
- Iteration 2 found P2 spacing drift in the right form and a captcha ratio mismatch. The form was measured and aligned to the source; input height is 70px, captcha tracks are 254px/164px with an 18px gap, and the button is 436×68px.
- Iteration 3 found the right-side grid too prominent and the account icon too heavy/small. Grid opacity was reduced and the account icon was replaced and optically sized. Post-fix full-view and focused comparisons show no actionable P0/P1/P2 differences.
- Iteration 4 used the reported Safari window as a second visual check. It found a P1 exposed background strip from a positional offset, plus P2 crop, feature-icon, input-prefix and captcha-frame drift. The offset was removed, the background crop was fitted into the asset, the storefront glyph was converted to the source-like outline treatment, input prefixes were vertically centered, and the captcha edge was given a neutral overlay. The 1672×941 side-by-side comparison and 1224×696 responsive capture now show no actionable P0/P1/P2 differences.

**Interaction and implementation checks**

- [x] Account field accepts and clears text.
- [x] Empty submit produces three inline validation errors and does not send credentials.
- [x] Captcha refresh remains a native named button; the challenge was not solved or bypassed.
- [x] Console error check returned no errors.
- [x] Relevant desktop/mobile-auth/accessibility/motion/security tests passed.
- [x] `npm run build:stage` compiled successfully.

**Implementation Checklist**

- [x] Restore the approved warm-neutral desktop login composition.
- [x] Keep mobile login behavior and styles isolated.
- [x] Match reference dimensions and responsive desktop behavior.
- [x] Verify interactions, console, tests and staging build.

**Follow-up Polish**

- P3: live captcha handwriting and arithmetic naturally differ from the captured reference.
- P3: the document-copy glyph uses the closest available icon from the existing Element UI family; its inner line arrangement differs slightly from the captured reference.

final result: passed

---

# Mobile organization-selection footer alignment QA — 2026-08-03

**Source visual truth**

- `/var/folders/8w/_dnk6m8j0p3cn2wbchy5gprw0000gn/T/codex-clipboard-c105f78b-5815-4c81-8527-9cdddce4b3ee.png`
- Source pixels: 1320×2868.
- State: authenticated mobile organization selection, `主仓库` selected, bottom action row visibly overflowing the right viewport edge.

**Implementation evidence**

- Intended CSS viewport: 393×852 at 1× browser density; source browser chrome and density were excluded from layout measurements.
- The in-app browser reached `http://192.168.182.64:8088/login?redirect=%2Fselect-shop`; the authenticated organization-selection state could not be captured without completing the live CAPTCHA.
- Code evidence: `.selected-panel.selection-summary` now establishes a block formatting context and border-box width; `.footer-actions` uses a contained three-track grid; all Element UI sibling margins are reset locally.
- Automated evidence: `mobileAuthEntryPages.test.js`, `shopContextUx.test.js`, `mobileBeautificationAcceptance.test.js`, `mobileAccessibilityVisuals.test.js`, and `mobileAndroidAuditRegression.test.js` passed.

**Required fidelity surfaces**

- Fonts and typography: unchanged by this scoped fix.
- Spacing and layout rhythm: the P1 horizontal overflow source was corrected; the summary now stacks above the action grid and all three buttons fit within the panel content box.
- Colors and visual tokens: unchanged except the previously unreadable mobile business-context note now uses `#60746a`.
- Image quality and assets: no image assets are involved in the affected footer.
- Copy and content: unchanged; `退出`, `清除`, and `进入工作台` remain intact.

**Findings**

- [P1] Post-fix authenticated visual capture is unavailable.
  - Location: `/select-shop`, selected organization footer.
  - Evidence: the verification browser is correctly redirected to the login route and presents a live CAPTCHA.
  - Impact: source-to-implementation screenshot comparison cannot be completed in this run.
  - Fix: refresh the authenticated phone page and capture the same selected state for final visual comparison.

**Comparison history**

- Iteration 1 identified the desktop `.selection-summary { display: flex; }` rule and Element UI sibling-button margin as the combined overflow source.
- The footer was changed to a stacked summary plus bounded grid, mobile horizontal overflow is clipped, vertical content remains scrollable, and the sticky panel respects the bottom safe area.
- Post-fix code and regression tests pass; browser-rendered authenticated evidence remains pending.

**Implementation Checklist**

- [x] Stack the current-selection summary above its actions on mobile.
- [x] Contain all three action buttons within the available width at 320–480px.
- [x] Remove inherited sibling-button margins.
- [x] Preserve vertical scrolling and bottom safe-area spacing.
- [ ] Capture the authenticated selected state after refresh and complete the visual comparison.

final result: blocked

---

# Unified todo in-place approval detail QA — 2026-08-13

**Source visual truth**

- `/var/folders/8w/_dnk6m8j0p3cn2wbchy5gprw0000gn/T/codex-clipboard-8be6f96b-6339-45af-af95-b2a914822b34.png`
- Source pixels: 1280×720 PNG at 1× density.
- Source state: authenticated desktop todo center after pressing the standalone `通过` button. The source exposes an eight-field confirmation summary and a `查看完整详情` button.
- Clarified target: `查看审批` is the complete-detail entry; the standalone `通过` button remains a compact quick-confirmation entry. Neither entry requires a second details click.

**Implementation evidence**

- Complete-detail entry: `docs/evidence/2026-08-13-todo-view-approval-full-detail.jpg`
- Compact quick-confirmation entry: `docs/evidence/2026-08-13-todo-quick-confirm-compact.jpg`
- Implementation CSS viewport: 1440×900 at 1× density.
- Browser-rendered route: `http://127.0.0.1:4180/workbench/todo`, using the production build and local contract-shaped mock data.
- State: authenticated desktop todo center with both entry paths exercised independently against the first eligible transfer approval.

**Full-view comparison evidence**

- Clicking `查看审批` opens the 960px `调拨审批详情` dialog directly. It includes transfer basics, recipient and address, approval progress, all four material lines, totals, approval comment, and approval actions.
- Clicking the separate `通过` button opens the 620px `确认通过调拨审批` dialog. It includes only the compact eight-field summary and approval controls, plus guidance that material, receiving, and progress information is available through `查看审批`.
- The obsolete `查看完整详情` button is absent in both modes.

**Focused-region comparison evidence**

- `docs/evidence/2026-08-13-todo-view-approval-full-detail.jpg` verifies that the complete decision context is opened by `查看审批`, with all four material rows and the approval footer visible in the same dialog.
- `docs/evidence/2026-08-13-todo-quick-confirm-compact.jpg` verifies that `通过` does not masquerade as the detail entry and remains a compact confirmation flow.

**Findings**

- No actionable P0, P1, or P2 visual or interaction mismatch remains for the requested desktop state.

**Required fidelity surfaces**

- Fonts and typography: the existing ERP system Chinese sans-serif stack, heading weight, description labels, table text, and action hierarchy are retained; no wrapping or truncation hides decision data at 1280×720.
- Spacing and layout rhythm: full detail uses a viewport-bounded 960px frame with independently scrollable content; quick confirmation uses a compact 620px frame. Both retain fixed, right-aligned approval actions.
- Colors and visual tokens: existing neutral borders, muted labels, warning amber, status amber, and green approval actions are reused without introducing a second visual language.
- Image quality and asset fidelity: no image assets are involved in this dialog; existing BossERP shell assets and Element UI controls remain unchanged.
- Copy and content: full decision context is visible directly from `查看审批`; compact confirmation is visible directly from `通过`; `查看完整详情` and its second-navigation event were removed.

**Comparison history**

- Iteration 1 identified the source's P1 workflow issue: an approver could not see item lines, delivery context, or approval progress without leaving the confirmation flow through `查看完整详情`.
- Iteration 2 exposed an entry-role regression: full detail had been attached to `通过` instead of `查看审批`.
- The parent now routes eligible transfer rows to explicit `detail` mode and the standalone quick action to explicit `quick` mode. Both modes share guarded data loading and approval submission behavior while rendering different information density.
- Post-fix browser captures verify the two independent entry paths and no second-navigation dependency.

**Interaction and implementation checks**

- [x] Clicked the first `查看审批` action and verified the dialog title is `调拨审批详情`.
- [x] Verified the complete basic, receiving, approval-progress, material-line, totals, and action sections in that dialog.
- [x] Closed it, clicked the same row's standalone `通过` button, and verified the dialog title is `确认通过调拨审批`.
- [x] Verified quick mode contains the eight-field summary and no material table, recipient/address block, or approval-progress section.
- [x] Re-tested both independent entry paths at 2026-08-13 02:59 CST. Fresh evidence: `docs/evidence/2026-08-13-retest-view-approval-full-detail.jpg` and `docs/evidence/2026-08-13-retest-quick-confirm-compact.jpg`.
- [x] Targeted entry/dialog tests passed.
- [x] Canonical frontend tests: 285 passed; the sole remaining gate failure is the pre-existing duplicate-file hygiene check, which detects Finder-style ` 2.*` copies outside this change and was not bypassed or deleted.
- [x] Production build, secret scan, release metadata, and bundle-budget checks passed.

**Implementation Checklist**

- [x] Make `查看审批` open complete transfer and approval decision context directly.
- [x] Keep `通过` as the compact confirmation entry.
- [x] Remove the `查看完整详情` button and parent navigation event.
- [x] Preserve cancel, continuous approval, confirm approval, retry, and keyboard-focus behavior.
- [x] Verify both production entry paths visually and interactively.

**Follow-up Polish**

- Repository hygiene only: review the unrelated Finder-style duplicate copies separately before enforcing the full duplicate-file gate.

final result: passed
