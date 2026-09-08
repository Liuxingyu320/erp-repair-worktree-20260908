# Frontend Vue 2 Low-Severity Advisory

Date: 2026-07-08

## Advisory

`npm audit` reports `GHSA-5j4c-8p2g-v4jx` against Vue 2 through the current Vue 2 ecosystem dependencies:

- `vue@2.6.12`
- `element-ui@2.15.14`
- `vuex@3.6.0`
- `@riophae/vue-treeselect@0.4.0`

The remaining audit finding is low severity. The suggested automatic fix is not acceptable for this codebase because `npm audit fix --force` proposes breaking dependency changes in the Vue/ElementUI stack instead of a narrow patch.

## Current Gate

The frontend release gate is `npm run audit:prod`, which fails on moderate or higher production dependency vulnerabilities. This keeps high and moderate dependency advisories blocking, while tracking the remaining Vue 2 advisory as a framework migration item.

## Follow-Up

Resolve this advisory in a dedicated Vue 2 retirement track:

1. Replace ElementUI with a Vue 3-compatible UI library.
2. Migrate Vuex 3 usage to a Vue 3-compatible state layer.
3. Replace `@riophae/vue-treeselect` or isolate it behind a maintained Vue 3-compatible component.
4. Upgrade Vue after route, store, form, table, and mobile shell smoke tests are in place.

Do not run `npm audit fix --force` for this advisory in a routine dependency patch.
