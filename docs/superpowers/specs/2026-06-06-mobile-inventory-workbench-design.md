# Mobile Inventory Workbench Design

## Goal

Build a phone-browser web page for the existing ERP frontend that presents a polished Apple-style liquid-glass `进销存工作台`. The page must keep the accepted visual style and cover both broader purchase-sales-inventory management and warehouse management.

## Accepted Concept

Reference image:

- `docs/previews/inventory-mobile-liquid-glass-concept.png`

Visual direction:

- Bright blurred modern tea room background.
- Large Chinese title.
- Frosted-glass cards with white translucent fill, soft borders, and inner highlights.
- Blue and teal accent icons, with amber/red/green status dots.
- Rounded bottom navigation.
- Phone browser feel, not a desktop admin table.

## Scope

The first implementation is a visual and interaction-light mobile web surface, not a complete mobile ERP rewrite. The visual backdrop should feel like a refined tea room while the information architecture still covers inventory, warehouse, purchase, and sales workflows.

Included:

- Independent route: `/mobile/inventory`.
- Public preview access without backend login, using static demo data.
- `进销存工作台` as the first screen.
- Business coverage:
  - Sales: `今日销售`, `销售待发货`, `销售开单`.
  - Purchase: `待入库`, `采购待入库`, `采购入库`.
  - Inventory: `低库存`, `低库存预警`, `查库存`.
  - Warehouse: `待发货`, `盘点差异`, `盘点`.
- Bottom navigation labels: `工作台 / 销售 / 采购 / 库存 / 我的`.
- A search/selection-style warehouse pill: `总仓 A区`.

Excluded for this pass:

- Full mobile CRUD flows.
- Real API integration.
- Authentication-specific mobile behavior.
- Separate `/mobile/warehouse` detail page.

## Information Architecture

Top browser-like chrome:

- Status bar time.
- Address bar `erp.example.com/ops`.

Hero:

- Title `进销存工作台`.
- Notification glass button.
- Selector pill `总仓 A区`.
- Main overview card:
  - `今日经营`
  - `采购 · 销售 · 库存协同`
  - Metrics:
    - `今日销售 86 单`
    - `待入库 12 单`
    - `低库存 38 种`
    - `待发货 16 单`

Priority list:

- `销售待发货`, order `SO20260606018`, meta `客户订单 · 6 件商品`, status `待发货`.
- `采购待入库`, order `PO20260606003`, meta `供应商到货 · 4 单`, status `待入库`.
- `低库存预警`, order `RK20260606012`, meta `扫码枪 X2 · 剩余 6 件`, status `待补货`.
- `盘点差异`, order `PD20260605017`, meta `A区货架 · 2 个货位`, status `待复核`.

Quick actions:

- `销售开单`
- `采购入库`
- `查库存`
- `盘点`

## Implementation Notes

Use the existing Vue 2 application and SCSS. Keep the page self-contained so it does not disturb the existing desktop layout.

The page can use inline SVG icons and CSS background treatments because the accepted concept is a UI mockup, not a required raster asset. The generated concept image remains the fidelity reference.

For testability, keep the display data in a small CommonJS-compatible module that can be checked with the existing Node assert style.

## Verification

Required checks:

- Data test confirms the mobile workbench covers sales, purchase, inventory, and warehouse operations.
- Route whitelist test confirms `/mobile/inventory` is preview-accessible.
- Production build succeeds.
- Browser verification at phone-sized viewport confirms the rendered page matches the accepted concept structure and liquid-glass style.
