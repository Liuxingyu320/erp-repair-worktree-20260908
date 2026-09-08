# Desktop ERP Design QA — soft neutral and semantic icon pass

## Scope and intent

- Primary visual direction: keep the approved BossERP desktop structure and Apple-like light surfaces, while improving readability and avoiding a lifeless pure-white/near-black result.
- Supplemental example: the user-management screenshot supplied on 2026-08-02 is treated only as evidence that different functions need visual differentiation. Its saturated blue/purple palette and dark header are not a fidelity target.
- Routes reviewed: `/index`, `/select-shop`, `/inventory/customer`, `/system/user`, `/system/dept`, and `/system/notice`.
- Browser: Codex in-app Browser.
- Viewport and implementation pixels: 1224 × 768 at 1× density.
- Density normalization: none.
- State: authenticated desktop session with warehouse context `主仓库`.

## Visual evidence

- Customer service card before/final: `output/contrast-audit/comparison-customer-balanced.jpg`
- Organization selection before/final: `output/contrast-audit/comparison-select-balanced.jpg`
- Notification popover before/final: `output/contrast-audit/comparison-notice-balanced.jpg`
- System user before/final semantic pass: `output/contrast-audit/comparison-system-semantic-final.jpg`
- Home before/final neutral pass: `output/contrast-audit/comparison-home-balanced.jpg`
- Semantic module comparison: `output/contrast-audit/comparison-semantic-module-tones.jpg`
- Final system user state: `output/contrast-audit/18-system-user-semantic-icons.jpg`
- Final organization state: `output/contrast-audit/19-system-dept-sage.jpg`
- Final notice state: `output/contrast-audit/20-system-notice-amber.jpg`

## Full-view findings

- The original inner pages used very light placeholders, disabled labels, toolbar icons, and empty-state copy. Several controls were technically present but visually disappeared against white surfaces.
- The first correction overcompensated with large pure-white surfaces and near-black controls. The user correctly identified this as visually rigid and uncomfortable.
- The balanced pass now uses a mist blue-gray canvas, off-white cards, charcoal text and primary controls, and low-saturation semantic accents. Layout, density, content order, and business behavior remain unchanged.
- The customer empty state now spans the content width and sits inside a quiet blue-gray panel instead of floating faintly in a large blank canvas.

## Semantic differentiation

- Navigation icons derive a category tone: workflow/system uses mist blue, organization uses sage, commerce/inventory uses amber, people/roles uses violet, and risk/audit uses muted rose.
- Reusable system page headers derive the same quiet tones from their functional icon. The comparison confirms people (`用户管理`) in violet, organization (`部门管理`) in sage, and notice (`通知公告`) in amber.
- Organization tree icons distinguish folders, warehouses, and leaf records; the selected item still uses the interactive blue state.
- Toolbar actions remain semantic: create/info blue, edit/success sage, export/warning amber, delete/sensitive muted rose. Table row edit/delete/more icons no longer share one color.
- Success, warning, danger, info, and default tags retain distinct low-saturation treatments instead of being flattened to gray.
- Header utilities are differentiated without becoming decorative: organization/todo sage, search/message blue, display size/notice amber, and fullscreen violet.

## Fidelity and accessibility checks

- Typography: passed. Existing Apple system/PingFang stack, hierarchy, wrapping, and density are preserved.
- Layout: passed. No navigation height, card geometry, grid order, table structure, or route flow was changed.
- Color: passed. Accent colors are restricted to icons, semantic status, borders, small surfaces, and interaction feedback.
- Readability: passed. Placeholder, disabled, secondary, table-header, empty-state, and notification text remain legible on the softened surfaces.
- Interaction: passed. Organization search/selection/entry, navigation, notification popover, and representative management routes were exercised.
- Responsive scope: passed. The refinement remains desktop-scoped and does not replace the mobile system.
- Assets: passed. Existing room imagery, avatars, project icon set, and real application content are retained; no fake data or decorative placeholder asset was introduced.

## Comparison history

1. Initial [P2]: controls and empty states were too faint to read. Fixed with stronger text, borders, placeholders, header icons, popovers, and empty-state treatment.
2. Follow-up [P2]: the high-contrast correction felt lifeless and visually tiring. Fixed by replacing pure-white/near-black dominance with mist blue-gray canvas, off-white surfaces, charcoal controls, restored imagery, and restrained blue/sage/amber accents.
3. Follow-up [P2]: different icons and functions still looked too uniform. Fixed with reusable semantic icon tones, semantic tags, tree-node differentiation, toolbar action colors, and module-specific page-header identities.

## Verification

- `desktopAppleRefinement.test.js`: passed.
- `desktopContrastReadability.test.js`: passed.
- `desktopVisualSystem.test.js`: passed.
- `desktopUiUxAccessibility.test.js`: passed.
- Staging production build: passed.
- Runtime note: the local inventory service can emit transient data-load errors while its backend is unavailable; visual evidence was captured after transient toasts cleared. This does not alter the reviewed UI states.

final result: passed
