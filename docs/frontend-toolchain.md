# Frontend Toolchain

Date: 2026-07-16

## Node Version

Use Node `22.23.1` with npm `10.9.8` for the default `erp-ui`
development and verification environment.

The frontend package declares:

```text
node >=22 <25
```

and `erp-ui/.nvmrc` contains:

```text
22.23.1
```

`packageManager` pins npm `10.9.8`; the wider engine range remains for local
compatibility experiments, while the release gate accepts only the exact
`.nvmrc` and `packageManager` versions. This matches Capacitor 8's Node 22+
requirement without allowing a different runtime to silently become the
candidate-build baseline.

## Known Engine Warning

`npm install` may still report an `EBADENGINE` warning for `@achrinza/node-ipc@9.2.2`. That package is pulled in through the Vue CLI 4 dependency chain and declares support only through Node 17.

Do not suppress this warning globally. Treat it as a build-stack migration item:

1. Keep normal frontend work on Node 22.
2. Do not move the project to Node 25 until the Vue CLI 4 stack is replaced or upgraded.
3. Resolve the transitive warning with the Vue/webpack build-stack migration, not by loosening the app's `engines` field.
