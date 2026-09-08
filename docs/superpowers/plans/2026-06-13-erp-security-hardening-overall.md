# ERP Security Hardening Overall Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the remaining high-risk security and data-scope gaps, then reduce duplicated authorization logic and product-maintenance debt without destabilizing existing inventory/OA workflows.

**Architecture:** Treat P0 as deployment safety and runtime fail-fast work that can ship first. Treat P1 as backend authorization consistency work with focused tests. Treat P2/P3 as lower-risk cleanup and product-roadmap work that should not delay P0.

**Tech Stack:** Java, Spring Boot, Spring Cloud Gateway, Nacos, Redis, MyBatis, Docker Compose, Vue 2, Element UI, Maven.

---

## Scope And Order

Implement in this order:

1. P0: runtime secrets, profile safety, Redis password, Nacos DB credentials, file access policy, admin audit and bypass controls.
2. P1: OA idempotency, controller permission scanner, shared shop scope service, FK production verification.
3. P2: duplicate file cleanup, transfer-candidate mapper double guard, mobile route configuration.
4. P3: master data, unified approval, notifications, AR/AP/funds, business-list batch actions.

Do not start P1 until P0 tests and config checks pass. Do not start P3 until P0 and P1 are deployed or accepted as separate backlog work.

## File Map

P0 files:

- Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/JwtUtils.java`
- Modify: `erp-common/erp-common-core/src/test/java/com/erp/common/core/utils/JwtUtilsTest.java`
- Modify: `erp-common/erp-common-core/src/test/java/com/erp/common/core/config/JwtSecretStartupValidatorTest.java`
- Modify: `docker/docker-compose.yml`
- Modify: `docker/nacos/conf/application.properties`
- Modify: `docker/redis/conf/redis.conf`
- Modify after file-access decision: `erp-gateway/src/main/resources/bootstrap.yml`
- Modify: `erp-common/erp-common-security/src/main/java/com/erp/common/security/auth/AuthLogic.java`
- Modify: `erp-common/erp-common-security/src/main/java/com/erp/common/security/utils/SecurityUtils.java`
- Create: `erp-common/erp-common-security/src/main/java/com/erp/common/security/utils/SecurityBypassUtils.java`
- Create: `erp-common/erp-common-security/src/test/java/com/erp/common/security/utils/SecurityBypassUtilsTest.java`
- Create: `sql/security_admin_privilege_audit_20260613.sql`
- Create: `docs/file-access-policy.md`
- Modify: `docs/production-env-example.md`
- Modify: `docs/production-security-checklist.md`

P1 files:

- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaAttendanceController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaIdempotentSubmitAnnotationTest.java`
- Create: `bin/check-controller-security.sh`
- Create: `erp-common/erp-common-security/src/main/java/com/erp/common/security/shop/ShopScopeService.java`
- Create: `erp-common/erp-common-security/src/main/java/com/erp/common/security/shop/AbstractShopScopeService.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InventoryShopScopeService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaShopScopeService.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvBaseService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaPurchaseServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaAttendanceServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSalaryServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaLaborContractServiceImpl.java`
- Modify: `erp-ui/src/views/oa/attendance/index.vue`
- Modify: `erp-ui/src/views/oa/salary/index.vue`
- Modify: `erp-ui/src/views/mobile/feature/featureService.js`

P2 files:

- Delete: `erp-ui/src/views/system/salary/index 2.vue`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml`
- Modify: `erp-ui/src/views/mobile/mobileNavigation.js`
- Modify: `erp-ui/src/router/index.js`
- Create: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`

## Release Gates

P0 release gate:

```bash
mvn -f erp-common/erp-common-core/pom.xml test
mvn -f erp-gateway/pom.xml test
docker compose -f docker/docker-compose.yml config >/tmp/erp-compose-rendered.yml
rg -n "TokenConstants.SECRET|SPRING_PROFILES_ACTIVE|SPRING_DATA_REDIS_PASSWORD|NACOS_DB_PASSWORD" /tmp/erp-compose-rendered.yml
```

P1 release gate:

```bash
mvn -f erp-common/erp-common-security/pom.xml test
mvn -f erp-modules/erp-oa/pom.xml test
mvn -f erp-modules/erp-inventory/pom.xml test
bash bin/check-controller-security.sh
```

P2 release gate:

```bash
npm --prefix erp-ui run build:prod
test ! -e "erp-ui/src/views/system/salary/index 2.vue"
```

---

### Task 1: P0 JWT Profile And Secret Fail-Fast

**Files:**
- Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/JwtUtils.java`
- Modify: `erp-common/erp-common-core/src/test/java/com/erp/common/core/utils/JwtUtilsTest.java`
- Modify: `erp-common/erp-common-core/src/test/java/com/erp/common/core/config/JwtSecretStartupValidatorTest.java`

- [ ] **Step 1: Add failing tests for non-dev profiles**

Add these cases to `JwtUtilsTest` in package `com.erp.common.core.utils`:

```java
@Test
void shouldRejectMissingExternalSecretForUnknownNonDevelopmentProfile()
{
    System.setProperty("spring.profiles.active", "prd");
    assertThatThrownBy(() -> JwtUtils.resolveSecret((String) null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("生产环境必须配置外部JWT密钥");
}

@Test
void shouldAllowDevelopmentFallbackForDevLocalAndTestProfiles()
{
    for (String profile : new String[] { "dev", "local", "test" })
    {
        System.setProperty("spring.profiles.active", profile);
        assertThat(JwtUtils.resolveSecret((String) null)).isEqualTo(TokenConstants.SECRET);
    }
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```bash
mvn -f erp-common/erp-common-core/pom.xml -Dtest=JwtUtilsTest test
```

Expected before implementation: the `prd` case does not throw.

- [ ] **Step 3: Replace production-only detection with development-allowlist detection**

In `JwtUtils.java`, keep local fallback only when every active profile is a known development alias:

```java
private static final String[] DEVELOPMENT_PROFILES = { "dev", "local", "test" };

private static boolean requiresExternalSecret(Environment environment)
{
    String[] activeProfiles = environment == null ? null : environment.getActiveProfiles();
    String configuredProfiles = firstNotBlank(System.getProperty(ACTIVE_PROFILE_PROPERTY),
            environmentProperty(environment, ACTIVE_PROFILE_PROPERTY));
    if (activeProfiles != null && activeProfiles.length > 0)
    {
        return !allDevelopmentProfiles(activeProfiles);
    }
    if (configuredProfiles != null)
    {
        return !allDevelopmentProfiles(configuredProfiles.split("[,;\\s]+"));
    }
    return true;
}

private static boolean allDevelopmentProfiles(String[] profiles)
{
    if (profiles == null || profiles.length == 0)
    {
        return false;
    }
    for (String profile : profiles)
    {
        if (!isDevelopmentProfile(profile))
        {
            return false;
        }
    }
    return true;
}

private static boolean isDevelopmentProfile(String profile)
{
    if (profile == null)
    {
        return false;
    }
    for (String developmentProfile : DEVELOPMENT_PROFILES)
    {
        if (developmentProfile.equalsIgnoreCase(profile.trim()))
        {
            return true;
        }
    }
    return false;
}
```

Then change the missing-secret guard:

```java
if (configuredSecret == null && requiresExternalSecret(environment))
{
    throw new IllegalStateException(REQUIRED_SECRET_MESSAGE);
}
```

- [ ] **Step 4: Update startup-validator tests**

In `JwtSecretStartupValidatorTest`, keep the existing `prod`/`production` cases and add one case for `prd`:

```java
new ApplicationContextRunner()
        .withPropertyValues("spring.profiles.active=prd")
        .withUserConfiguration(JwtSecretStartupValidator.class)
        .run(context -> assertThat(context).hasFailed());
```

- [ ] **Step 5: Verify**

Run:

```bash
mvn -f erp-common/erp-common-core/pom.xml test
```

Expected: all common-core tests pass.

- [ ] **Step 6: Commit**

```bash
git add erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/JwtUtils.java \
  erp-common/erp-common-core/src/main/java/com/erp/common/core/config/JwtSecretStartupValidator.java \
  erp-common/erp-common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
  erp-common/erp-common-core/src/test/java/com/erp/common/core/utils/JwtUtilsTest.java \
  erp-common/erp-common-core/src/test/java/com/erp/common/core/config/JwtSecretStartupValidatorTest.java
git commit -m "fix(security): require external jwt secret outside dev profiles"
```

---

### Task 2: P0 Docker Runtime Secrets And Redis Authentication

**Files:**
- Modify: `docker/docker-compose.yml`
- Modify: `docker/redis/conf/redis.conf`
- Modify: `docs/production-env-example.md`
- Modify: `docs/production-security-checklist.md`

- [ ] **Step 1: Add shared app environment to Docker Compose**

At the top of `docker/docker-compose.yml`, add a shared anchor:

```yaml
x-java-common-env: &java-common-env
  SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:?set SPRING_PROFILES_ACTIVE in .env}
  ERP_JWT_SECRET: ${ERP_JWT_SECRET:?set ERP_JWT_SECRET in .env}
  SPRING_DATA_REDIS_HOST: erp-redis
  SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD:?set REDIS_PASSWORD in .env}
```

For `erp-gateway`, `erp-auth`, `erp-modules-system`, `erp-modules-gen`, `erp-modules-job`, `erp-modules-oa`, `erp-modules-inventory`, and `erp-modules-file`, add or merge:

```yaml
environment:
  <<: *java-common-env
```

For services that already have an `environment:` block, keep their existing keys and add the merge line first:

```yaml
environment:
  <<: *java-common-env
  SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR: erp-nacos:8848
  SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR: erp-nacos:8848
```

- [ ] **Step 2: Enable Redis password through container command**

For `erp-redis`, add:

```yaml
environment:
  REDIS_PASSWORD: ${REDIS_PASSWORD:?set REDIS_PASSWORD in .env}
command: ["sh", "-c", "redis-server /home/erp/redis/redis.conf --requirepass \"$${REDIS_PASSWORD}\""]
```

Keep `docker/redis/conf/redis.conf` without a committed plaintext password. Update its first lines to:

```conf
# Redis is started by docker-compose with --requirepass from REDIS_PASSWORD.
# Do not commit a plaintext requirepass value in this file.
```

- [ ] **Step 3: Align production docs with Spring variable names**

In `docs/production-env-example.md`, keep `ERP_REDIS_PASSWORD` only if a deployment script maps it. Otherwise use:

```bash
export SPRING_DATA_REDIS_PASSWORD="replace-with-strong-password"
export REDIS_PASSWORD="$SPRING_DATA_REDIS_PASSWORD"
```

In `docs/production-security-checklist.md`, add these exact checks:

```markdown
- [ ] `SPRING_PROFILES_ACTIVE` is `prod` or `production` in Docker/Kubernetes production deployments.
- [ ] `ERP_JWT_SECRET` is provided by a secret store and is not equal to `TokenConstants.SECRET`.
- [ ] Redis starts with `--requirepass` and all Java services receive `SPRING_DATA_REDIS_PASSWORD`.
```

- [ ] **Step 4: Verify rendered compose**

Run with safe sample values:

```bash
SPRING_PROFILES_ACTIVE=prod \
ERP_JWT_SECRET=sample-jwt-secret-for-compose-check-only \
REDIS_PASSWORD=sample-redis-secret-for-compose-check-only \
MYSQL_ROOT_PASSWORD=sample-mysql-root \
NACOS_AUTH_TOKEN=sample-nacos-token \
NACOS_AUTH_IDENTITY_KEY=serverIdentity \
NACOS_AUTH_IDENTITY_VALUE=sampleIdentity \
APP_DATASOURCE_URL=jdbc:mysql://erp-mysql:3306/erp-cloud \
APP_DATASOURCE_USERNAME=erp_app \
APP_DATASOURCE_PASSWORD=sample-app-db \
docker compose -f docker/docker-compose.yml config >/tmp/erp-compose-rendered.yml
```

Then run:

```bash
rg -n "SPRING_PROFILES_ACTIVE: prod|ERP_JWT_SECRET|SPRING_DATA_REDIS_PASSWORD|requirepass" /tmp/erp-compose-rendered.yml
```

Expected: every Java service has `SPRING_PROFILES_ACTIVE`, `ERP_JWT_SECRET`, and `SPRING_DATA_REDIS_PASSWORD`; Redis command includes `requirepass`.

- [ ] **Step 5: Commit**

```bash
git add docker/docker-compose.yml docker/redis/conf/redis.conf docs/production-env-example.md docs/production-security-checklist.md
git commit -m "chore(security): require production profile and redis password"
```

---

### Task 3: P0 Nacos DB Credential Injection

**Files:**
- Modify: `docker/nacos/conf/application.properties`
- Modify: `docker/docker-compose.yml`
- Modify: `docs/production-env-example.md`
- Modify: `docs/production-security-checklist.md`

- [ ] **Step 1: Replace hardcoded Nacos DB credentials**

Change `docker/nacos/conf/application.properties`:

```properties
db.user=${NACOS_DB_USER}
db.password=${NACOS_DB_PASSWORD}
```

- [ ] **Step 2: Add Nacos DB env to compose**

In `erp-nacos.environment`, add:

```yaml
- NACOS_DB_USER=${NACOS_DB_USER:?set NACOS_DB_USER in .env}
- NACOS_DB_PASSWORD=${NACOS_DB_PASSWORD:?set NACOS_DB_PASSWORD in .env}
```

- [ ] **Step 3: Document the required DB user**

In `docs/production-env-example.md`, add:

```bash
export NACOS_DB_USER="nacos_app"
export NACOS_DB_PASSWORD="replace-with-strong-password"
```

In `docs/production-security-checklist.md`, add:

```markdown
- [ ] Nacos connects to MySQL with `NACOS_DB_USER`, not `root`.
- [ ] `docker/nacos/conf/application.properties` has no literal `db.password=password`.
```

- [ ] **Step 4: Verify no literal default remains**

Run:

```bash
rg -n "db.user=root|db.password=password" docker/nacos docs
```

Expected: no matches.

- [ ] **Step 5: Commit**

```bash
git add docker/nacos/conf/application.properties docker/docker-compose.yml docs/production-env-example.md docs/production-security-checklist.md
git commit -m "chore(security): externalize nacos database credentials"
```

---

### Task 4: P0 File Access Policy And Gateway Decision

**Files:**
- Create: `docs/file-access-policy.md`
- Modify after decision: `erp-gateway/src/main/resources/bootstrap.yml`
- Modify after decision: `erp-ui/src/views/oa/laborContract/index.vue`
- Modify after decision: frontend components that render uploaded URLs in `<img>` or `<iframe>`
- Create or modify after decision: `erp-gateway/src/test/java/com/erp/gateway/config/properties/IgnoreWhitePropertiesTest.java`

- [ ] **Step 1: Write the file access decision record**

Create `docs/file-access-policy.md`:

```markdown
# File Access Policy

## Current State

Uploaded files are served by `erp-file-static` through `/file/**`.
`/file/upload` and `/file/delete` are protected by Gateway excludes and backend `@RequiresPermissions`.
Static URLs are consumed by browser-native elements such as `<img>` and `<iframe>`, which do not attach the application's `Authorization` header.

## Decision

Default implementation path: keep existing `/file/**` static URLs public until the frontend has an authenticated file-rendering path. Do not remove `/file/**` from `security.ignore.whites` in the same change that only edits Gateway configuration.

## Accepted Paths

Path A - non-breaking public static:
- Keep `/file/**` whitelisted.
- Use it only for assets that are acceptable as bearer-link resources, such as public product images and avatars.
- Do not store labor contract archives, seal images, salary exports, or private employee documents under the public static path.

Path B - authenticated private files:
- Add a private file endpoint that streams bytes through an authenticated controller.
- Frontend must render private images through blob URLs created by authenticated XHR/fetch.
- Frontend must render private document previews through authenticated blob/object URLs or a signed short-lived preview URL.
- Only after migration may Gateway remove `/file/**` from `security.ignore.whites` or narrow it to `/file/public/**`.

## Immediate Rule

If a file contains labor, salary, employee identity, contract, seal, finance, or approval evidence data, it must not be exposed through public `/file/**`.
```

- [ ] **Step 2: Audit frontend browser-native file consumers**

Run:

```bash
rg -n "<img|<iframe|previewFileUrl|archiveFileUrl|seal|fileUrl|imageUrl|/file/" erp-ui/src
```

Expected: each hit is classified in `docs/file-access-policy.md` as public-static acceptable or private-file required.

- [ ] **Step 3: Choose implementation path**

Use this decision table:

```markdown
| Requirement | Choose Path A | Choose Path B |
| --- | --- | --- |
| Keep current avatars/product images working with no UI changes | yes | no |
| Protect labor contracts, seals, salary, and employee documents | no | yes |
| Can change frontend file rendering now | no | yes |
```

If Path A is chosen for the current release, do not edit Gateway whitelist yet. Add a P1 follow-up task for private-file migration.

If Path B is chosen, continue with steps 4-7.

- [ ] **Step 4: For Path B, write whitelist behavior tests**

Add these assertions:

```java
@Test
void shouldNotWhitelistPrivateFilePath()
{
    IgnoreWhiteProperties properties = new IgnoreWhiteProperties();
    properties.setWhites(Arrays.asList("/auth/login", "/file/public/**"));
    properties.setExcludes(Arrays.asList("/file/upload", "/file/delete"));
    assertThat(properties.isWhitelisted("/file/private/2026/06/contract.pdf")).isFalse();
}

@Test
void shouldWhitelistPublicFilePathOnly()
{
    IgnoreWhiteProperties properties = new IgnoreWhiteProperties();
    properties.setWhites(Arrays.asList("/file/public/**"));
    properties.setExcludes(Arrays.asList("/file/upload", "/file/delete"));
    assertThat(properties.isWhitelisted("/file/public/avatar.png")).isTrue();
    assertThat(properties.isWhitelisted("/file/upload")).isFalse();
    assertThat(properties.isWhitelisted("/file/delete")).isFalse();
}
```

- [ ] **Step 5: For Path B, narrow Gateway whitelist**

In `erp-gateway/src/main/resources/bootstrap.yml`, replace:

```yaml
- /file/**
```

with:

```yaml
- /file/public/**
```

Keep upload/delete in `excludes`:

```yaml
excludes:
  - /file/upload
  - /file/delete
```

- [ ] **Step 6: For Path B, adapt private frontend consumers**

Do not use raw `<img :src="privateFileUrl">` or `<iframe :src="privateFileUrl">` for private files. Create an authenticated blob helper in the affected frontend module:

```js
import request from "@/utils/request"

export function loadPrivateFileBlob(url) {
  return request({
    url,
    method: "get",
    responseType: "blob"
  }).then(response => URL.createObjectURL(response))
}
```

Replace private preview rendering with object URLs and call `URL.revokeObjectURL` when dialogs close.

- [ ] **Step 7: Verify**

Run:

```bash
mvn -f erp-gateway/pom.xml test
npm --prefix erp-ui run build:prod
```

Expected:
- Path A: docs are updated and no Gateway whitelist change is made.
- Path B: Gateway tests pass, frontend build succeeds, `/file/public/**` is anonymous, and private file URLs require login.

- [ ] **Step 8: Commit**

```bash
git add docs/file-access-policy.md erp-gateway/src/main/resources/bootstrap.yml erp-gateway/src/test/java/com/erp/gateway/config/properties/IgnoreWhitePropertiesTest.java erp-ui/src
git commit -m "fix(security): define file access policy"
```

---

### Task 5: P0 Admin And Wildcard Permission Audit And Controls

**Files:**
- Modify: `erp-common/erp-common-security/src/main/java/com/erp/common/security/auth/AuthLogic.java`
- Modify: `erp-common/erp-common-security/src/main/java/com/erp/common/security/utils/SecurityUtils.java`
- Create: `erp-common/erp-common-security/src/main/java/com/erp/common/security/utils/SecurityBypassUtils.java`
- Create: `erp-common/erp-common-security/src/test/java/com/erp/common/security/utils/SecurityBypassUtilsTest.java`
- Create: `sql/security_admin_privilege_audit_20260613.sql`
- Modify: `docs/production-security-checklist.md`

- [ ] **Step 1: Add configurable bypass helper**

Create `SecurityBypassUtils`:

```java
package com.erp.common.security.utils;

import com.erp.common.core.utils.StringUtils;

public final class SecurityBypassUtils
{
    private static final String ADMIN_ROLE_BYPASS_PROPERTY = "erp.security.admin-role-bypass-enabled";
    private static final String ADMIN_ROLE_BYPASS_ENV = "ERP_SECURITY_ADMIN_ROLE_BYPASS_ENABLED";
    private static final String WILDCARD_PERMISSION_BYPASS_PROPERTY = "erp.security.wildcard-permission-bypass-enabled";
    private static final String WILDCARD_PERMISSION_BYPASS_ENV = "ERP_SECURITY_WILDCARD_PERMISSION_BYPASS_ENABLED";

    private SecurityBypassUtils()
    {
    }

    public static boolean adminRoleBypassEnabled()
    {
        return enabled(ADMIN_ROLE_BYPASS_PROPERTY, ADMIN_ROLE_BYPASS_ENV);
    }

    public static boolean wildcardPermissionBypassEnabled()
    {
        return enabled(WILDCARD_PERMISSION_BYPASS_PROPERTY, WILDCARD_PERMISSION_BYPASS_ENV);
    }

    private static boolean enabled(String property, String env)
    {
        String configured = System.getProperty(property);
        if (StringUtils.isEmpty(configured))
        {
            configured = System.getenv(env);
        }
        return configured != null && Boolean.parseBoolean(configured);
    }
}
```

This intentionally defaults to disabled. Production can no longer get global bypass behavior by merely having role key `admin` or permission `*:*:*`; a deployment must explicitly opt in.

- [ ] **Step 2: Add tests for disabled default and explicit opt-in**

Create `SecurityBypassUtilsTest`:

```java
package com.erp.common.security.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SecurityBypassUtilsTest
{
    @AfterEach
    void tearDown()
    {
        System.clearProperty("erp.security.admin-role-bypass-enabled");
        System.clearProperty("erp.security.wildcard-permission-bypass-enabled");
    }

    @Test
    void bypassesShouldBeDisabledByDefault()
    {
        assertThat(SecurityBypassUtils.adminRoleBypassEnabled()).isFalse();
        assertThat(SecurityBypassUtils.wildcardPermissionBypassEnabled()).isFalse();
    }

    @Test
    void bypassesShouldRequireExplicitOptIn()
    {
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");
        System.setProperty("erp.security.wildcard-permission-bypass-enabled", "true");

        assertThat(SecurityBypassUtils.adminRoleBypassEnabled()).isTrue();
        assertThat(SecurityBypassUtils.wildcardPermissionBypassEnabled()).isTrue();
    }
}
```

- [ ] **Step 3: Wire bypass helper into backend checks**

In `SecurityUtils.isAdmin()`, change role and permission checks to:

```java
if (SecurityBypassUtils.adminRoleBypassEnabled()
        && loginUser.getRoles() != null
        && loginUser.getRoles().contains(UserConstants.SUPER_ADMIN_ROLE_KEY))
{
    return true;
}
if (SecurityBypassUtils.wildcardPermissionBypassEnabled()
        && loginUser.getPermissions() != null
        && loginUser.getPermissions().contains(ALL_PERMISSION))
{
    return true;
}
```

In `AuthLogic.hasPermi(...)`, change:

```java
.anyMatch(x -> ALL_PERMISSION.equals(x) || PatternMatchUtils.simpleMatch(x, permission));
```

to:

```java
.anyMatch(x -> (SecurityBypassUtils.wildcardPermissionBypassEnabled() && ALL_PERMISSION.equals(x))
        || PatternMatchUtils.simpleMatch(x, permission));
```

In `AuthLogic.hasRole(...)`, change:

```java
.anyMatch(x -> SUPER_ADMIN.equals(x) || PatternMatchUtils.simpleMatch(x, role));
```

to:

```java
.anyMatch(x -> (SecurityBypassUtils.adminRoleBypassEnabled() && SUPER_ADMIN.equals(x))
        || PatternMatchUtils.simpleMatch(x, role));
```

- [ ] **Step 4: Document break-glass opt-in**

Add to `docs/production-security-checklist.md`:

```markdown
- [ ] `ERP_SECURITY_ADMIN_ROLE_BYPASS_ENABLED` is unset or `false` in normal production.
- [ ] `ERP_SECURITY_WILDCARD_PERMISSION_BYPASS_ENABLED` is unset or `false` in normal production.
- [ ] If either bypass is temporarily enabled, the change is tied to an incident ticket and reversed after use.
```

- [ ] **Step 5: Add audit SQL**

Create `sql/security_admin_privilege_audit_20260613.sql`:

```sql
SELECT 'admin_role_users' AS check_name,
       u.user_id,
       u.user_name,
       r.role_id,
       r.role_key
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE u.del_flag = '0'
  AND r.role_key = 'admin';

SELECT 'wildcard_permission_roles' AS check_name,
       r.role_id,
       r.role_name,
       r.role_key,
       m.menu_id,
       m.perms
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms = '*:*:*';
```

- [ ] **Step 6: Add production checklist**

Add:

```markdown
- [ ] `sql/security_admin_privilege_audit_20260613.sql` returns only approved break-glass admin users.
- [ ] No daily operations role has `*:*:*`.
- [ ] `ERP_SECURITY_LEGACY_USER_ID_ADMIN` is unset or `false`.
```

- [ ] **Step 7: Verify locally against an available database**

Run with the actual database credentials:

```bash
mysql -h "$APP_DB_HOST" -u "$APP_DB_USER" -p"$APP_DB_PASSWORD" "$APP_DB_NAME" < sql/security_admin_privilege_audit_20260613.sql
```

Expected: only the documented break-glass admin account appears.

- [ ] **Step 8: Run backend tests**

Run:

```bash
mvn -f erp-common/erp-common-security/pom.xml test
```

Expected: security tests pass.

- [ ] **Step 9: Commit**

```bash
git add erp-common/erp-common-security/src/main/java/com/erp/common/security/auth/AuthLogic.java \
  erp-common/erp-common-security/src/main/java/com/erp/common/security/utils/SecurityUtils.java \
  erp-common/erp-common-security/src/main/java/com/erp/common/security/utils/SecurityBypassUtils.java \
  erp-common/erp-common-security/src/test/java/com/erp/common/security/utils/SecurityBypassUtilsTest.java \
  sql/security_admin_privilege_audit_20260613.sql docs/production-security-checklist.md
git commit -m "fix(security): make global admin bypass explicit"
```

---

### Task 6: P1 OA Write Idempotency

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaAttendanceController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaIdempotentSubmitAnnotationTest.java`

- [ ] **Step 1: Write annotation tests**

Create `OaIdempotentSubmitAnnotationTest`:

```java
package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.common.security.annotation.IdempotentSubmit;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class OaIdempotentSubmitAnnotationTest
{
    @Test
    void purchaseWriteEndpointsShouldBeIdempotent() throws Exception
    {
        assertIdempotent(OaPurchaseController.class, "save", com.erp.oa.domain.OaPurchase.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertIdempotent(OaPurchaseController.class, "submit", com.erp.oa.domain.OaPurchase.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertIdempotent(OaPurchaseController.class, "approve", com.erp.oa.domain.dto.OaApproveRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);
    }

    @Test
    void attendanceWriteEndpointsShouldBeIdempotent() throws Exception
    {
        assertIdempotent(OaAttendanceController.class, "checkIn", com.erp.oa.domain.OaAttendanceRecord.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertIdempotent(OaAttendanceController.class, "checkOut", com.erp.oa.domain.OaAttendanceRecord.class,
                jakarta.servlet.http.HttpServletRequest.class);
    }

    @Test
    void salaryWriteEndpointsShouldBeIdempotent() throws Exception
    {
        assertIdempotent(OaSalaryController.class, "saveConfig", com.erp.oa.domain.OaSalaryConfig.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertIdempotent(OaSalaryController.class, "calculate", com.erp.oa.domain.OaSalaryRecord.class,
                jakarta.servlet.http.HttpServletRequest.class);
    }

    private static void assertIdempotent(Class<?> controllerClass, String methodName, Class<?>... parameterTypes)
            throws Exception
    {
        Method method = controllerClass.getMethod(methodName, parameterTypes);
        IdempotentSubmit annotation = method.getAnnotation(IdempotentSubmit.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.timeout()).isEqualTo(30);
    }
}
```

- [ ] **Step 2: Run test and verify failure**

```bash
mvn -f erp-modules/erp-oa/pom.xml -Dtest=OaIdempotentSubmitAnnotationTest test
```

Expected before implementation: missing annotation assertions fail.

- [ ] **Step 3: Add annotations**

Add import to each controller:

```java
import com.erp.common.security.annotation.IdempotentSubmit;
```

Add this annotation to `save`, `submit`, `approve`, `checkIn`, `checkOut`, `saveConfig`, and `calculate`:

```java
@IdempotentSubmit(timeout = 30)
```

- [ ] **Step 4: Verify**

```bash
mvn -f erp-modules/erp-oa/pom.xml test
```

Expected: OA tests pass.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaAttendanceController.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaIdempotentSubmitAnnotationTest.java
git commit -m "fix(oa): add idempotency to write endpoints"
```

---

### Task 7: P1 Controller Permission Scanner

**Files:**
- Create: `bin/check-controller-security.sh`
- Modify: `docs/production-security-checklist.md`

- [ ] **Step 1: Create scanner script**

Create executable `bin/check-controller-security.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

roots = [
    Path("erp-auth/src/main/java"),
    Path("erp-gateway/src/main/java"),
    Path("erp-modules"),
]

mapping_tokens = ("@GetMapping", "@PostMapping", "@PutMapping", "@DeleteMapping", "@RequestMapping")
security_tokens = ("@RequiresLogin", "@RequiresPermissions")
class_tokens = ("class ", "interface ")
excluded_files = {
    "InvBaseController.java",
    "OaBaseController.java",
}
allowed_public_mappings = {
    # Example format:
    # "erp-auth/src/main/java/com/erp/auth/controller/TokenController.java:@PostMapping(\"login\")": "login endpoint must be public",
}

def stripped(line):
    return line.strip()

def is_security_annotation(line):
    return stripped(line).startswith(security_tokens)

def class_has_security(lines, class_index):
    start = max(0, class_index - 40)
    for line in lines[start:class_index]:
        if is_security_annotation(line):
            return True
    return False

def method_has_security(lines, mapping_index):
    start = max(0, mapping_index - 40)
    for index in range(mapping_index - 1, start - 1, -1):
        line = stripped(lines[index])
        if is_security_annotation(line):
            return True
        if line.startswith("@"):
            continue
        if not line:
            continue
        if line.startswith(("public ", "protected ", "private ")):
            break
    return False

violations = []
for root in roots:
    if not root.exists():
        continue
    for path in root.rglob("*Controller.java"):
        if path.name in excluded_files:
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        class_index = next((i for i, line in enumerate(lines) if any(token in line for token in class_tokens)
                            and "Controller" in line), -1)
        class_secured = class_index >= 0 and class_has_security(lines, class_index)
        for index, line in enumerate(lines):
            line_text = stripped(line)
            if not line_text.startswith(mapping_tokens):
                continue
            mapping_key = f"{path}:{line_text}"
            if mapping_key in allowed_public_mappings:
                continue
            if class_secured:
                continue
            if not method_has_security(lines, index):
                violations.append(f"{path}:{index + 1}: {line_text}")

if violations:
    print("Controller mappings without @RequiresLogin or @RequiresPermissions:")
    print("\n".join(violations))
    raise SystemExit(1)
PY
```

Run:

```bash
chmod +x bin/check-controller-security.sh
```

- [ ] **Step 2: Run scanner and handle findings**

Run:

```bash
bash bin/check-controller-security.sh
```

Expected: zero findings. If it finds a deliberate public endpoint, add it to a hardcoded `allowed_public_mappings` list inside the script with an inline reason string.

- [ ] **Step 3: Add checklist**

Add:

```markdown
- [ ] `bash bin/check-controller-security.sh` passes in CI before merge.
```

- [ ] **Step 4: Commit**

```bash
git add bin/check-controller-security.sh docs/production-security-checklist.md
git commit -m "test(security): scan controllers for missing auth annotations"
```

---

### Task 8: P1 Shared ShopScopeService

**Files:**
- Create: `erp-common/erp-common-security/src/main/java/com/erp/common/security/shop/ShopScopeService.java`
- Create: `erp-common/erp-common-security/src/main/java/com/erp/common/security/shop/AbstractShopScopeService.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InventoryShopScopeService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaShopScopeService.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvBaseService.java`
- Modify: OA services that currently define `resolveAndValidateShopDept` or `appendShopScope`.

- [ ] **Step 1: Define common contract**

Create:

```java
package com.erp.common.security.shop;

import com.erp.common.core.web.domain.BaseEntity;
import java.util.List;
import java.util.Map;

public interface ShopScopeService
{
    Long resolveRequiredShopDept(Long selectedShopDeptId);

    List<Long> resolveScopeDeptIds(Long selectedShopDeptId);

    boolean hasUserShopScope(Long userId, Long deptId);

    default void appendShopScope(BaseEntity entity, Long selectedShopDeptId)
    {
        if (entity != null)
        {
            appendShopScope(entity.getParams(), selectedShopDeptId);
        }
    }

    void appendShopScope(Map<String, Object> params, Long selectedShopDeptId);
}
```

- [ ] **Step 2: Add common abstract implementation without Mapper/XML**

Do not put `ShopScopeMapper.xml` in `erp-common-security`. The business services already load `classpath:mapper/**/*.xml` from their own modules, and putting mapper XML in the common jar can fail depending on service mapper-location setup.

Create `AbstractShopScopeService`:

```java
package com.erp.common.security.shop;

import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.common.security.utils.SecurityUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractShopScopeService implements ShopScopeService
{
    @Override
    public Long resolveRequiredShopDept(Long selectedShopDeptId)
    {
        if (selectedShopDeptId == null)
        {
            throw new ServiceException("请选择门店或仓库");
        }
        if (SecurityUtils.isAdmin())
        {
            return selectedShopDeptId;
        }
        Long userId = SecurityUtils.getUserId();
        if (!hasUserShopScope(userId, selectedShopDeptId))
        {
            throw new ServiceException("无权限访问当前门店");
        }
        return selectedShopDeptId;
    }

    @Override
    public List<Long> resolveScopeDeptIds(Long selectedShopDeptId)
    {
        if (SecurityUtils.isAdmin())
        {
            List<Long> scopes = new ArrayList<>();
            if (selectedShopDeptId != null)
            {
                scopes.add(selectedShopDeptId);
            }
            return scopes;
        }
        return selectUserShopDeptIds(SecurityUtils.getUserId(), selectedShopDeptId);
    }

    @Override
    public boolean hasUserShopScope(Long userId, Long deptId)
    {
        return userId != null && deptId != null && countUserShopScope(userId, deptId) > 0;
    }

    @Override
    public void appendShopScope(BaseEntity entity, Long selectedShopDeptId)
    {
        if (entity != null)
        {
            appendShopScope(entity.getParams(), selectedShopDeptId);
        }
    }

    @Override
    public void appendShopScope(Map<String, Object> params, Long selectedShopDeptId)
    {
        if (params == null)
        {
            return;
        }
        List<Long> scopeDeptIds = resolveScopeDeptIds(selectedShopDeptId);
        if (scopeDeptIds != null && !scopeDeptIds.isEmpty())
        {
            params.put("scopeDeptIds", scopeDeptIds);
        }
    }

    protected abstract int countUserShopScope(Long userId, Long deptId);

    protected abstract List<Long> selectUserShopDeptIds(Long userId, Long selectedShopDeptId);
}
```

- [ ] **Step 3: Reuse module-local mappers**

Inventory already has `InvDeptScopeMapper` and `InvDeptScopeMapper.xml`. OA already has `OaDeptScopeMapper` and `OaDeptScopeMapper.xml`. Extend those module-local mapper interfaces only if they do not already expose both methods:

```java
int countUserShopScope(@Param("userId") Long userId, @Param("deptId") Long deptId);

List<Long> selectUserShopDeptIds(@Param("userId") Long userId, @Param("selectedShopDeptId") Long selectedShopDeptId);
```

Keep XML under:

- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeptScopeMapper.xml`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaDeptScopeMapper.xml`

Do not create `erp-common/erp-common-security/src/main/resources/mapper/security/ShopScopeMapper.xml`.

- [ ] **Step 4: Implement module adapters**

Create `InventoryShopScopeService`:

```java
package com.erp.inventory.service.impl;

import com.erp.common.security.shop.AbstractShopScopeService;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryShopScopeService extends AbstractShopScopeService
{
    @Autowired
    private InvDeptScopeMapper deptScopeMapper;

    @Override
    protected int countUserShopScope(Long userId, Long deptId)
    {
        return deptScopeMapper.countUserShopScope(userId, deptId);
    }

    @Override
    protected List<Long> selectUserShopDeptIds(Long userId, Long selectedShopDeptId)
    {
        return deptScopeMapper.selectUserShopDeptIds(userId, selectedShopDeptId);
    }
}
```

Create `OaShopScopeService`:

```java
package com.erp.oa.service.impl;

import com.erp.common.security.shop.AbstractShopScopeService;
import com.erp.oa.mapper.OaDeptScopeMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OaShopScopeService extends AbstractShopScopeService
{
    @Autowired
    private OaDeptScopeMapper deptScopeMapper;

    @Override
    protected int countUserShopScope(Long userId, Long deptId)
    {
        return deptScopeMapper.countUserShopScope(userId, deptId);
    }

    @Override
    protected List<Long> selectUserShopDeptIds(Long userId, Long selectedShopDeptId)
    {
        return deptScopeMapper.selectUserShopDeptIds(userId, selectedShopDeptId);
    }
}
```

- [ ] **Step 5: Refactor inventory base service first**

In `InvBaseService`, inject `ShopScopeService` and delegate existing protected methods to it. Keep method names stable:

```java
@Autowired
protected ShopScopeService shopScopeService;

protected Long resolveAndValidateShopDept(Long selectedShopDeptId)
{
    return shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
}

protected void appendShopScope(BaseEntity entity, Long selectedShopDeptId)
{
    shopScopeService.appendShopScope(entity, selectedShopDeptId);
}
```

- [ ] **Step 6: Refactor OA services**

For `OaPurchaseServiceImpl`, `OaAttendanceServiceImpl`, `OaSalaryServiceImpl`, and `OaLaborContractServiceImpl`, remove their private duplicated `resolveAndValidateShopDept` and `appendShopScope` methods. Inject `ShopScopeService` and call it directly.

- [ ] **Step 7: Verify**

Run:

```bash
mvn -f erp-common/erp-common-security/pom.xml test
mvn -f erp-modules/erp-inventory/pom.xml test
mvn -f erp-modules/erp-oa/pom.xml test
```

Expected: all tests pass and `rg -n "private Long resolveAndValidateShopDept|private void appendShopScope" erp-modules/erp-oa erp-modules/erp-inventory` returns no duplicated private implementations except any explicitly justified special case.

- [ ] **Step 8: Commit**

```bash
git add erp-common/erp-common-security/src/main/java/com/erp/common/security/shop \
  erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InventoryShopScopeService.java \
  erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvBaseService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaShopScopeService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl
git commit -m "refactor(security): centralize shop scope checks"
```

---

### Task 9: P1 Foreign Key Production Verification

**Files:**
- Use existing: `sql/erp_inventory_fk_preflight_orphan_check_20260612.sql`
- Use existing: `sql/erp_inventory_fk_constraints_20260612.sql`
- Use existing: `sql/erp_inventory_master_warehouse_fk_constraints_20260612.sql`
- Modify: `docs/production-security-checklist.md`

- [ ] **Step 1: Run orphan preflight on target DB**

Run:

```bash
mysql -h "$APP_DB_HOST" -u "$APP_DB_USER" -p"$APP_DB_PASSWORD" "$APP_DB_NAME" \
  < sql/erp_inventory_fk_preflight_orphan_check_20260612.sql \
  > /tmp/erp_fk_preflight.txt
```

Expected: each check has `orphan_count = 0`.

- [ ] **Step 2: Apply FK scripts only after zero-orphan result**

Run:

```bash
mysql -h "$APP_DB_HOST" -u "$APP_DB_USER" -p"$APP_DB_PASSWORD" "$APP_DB_NAME" \
  < sql/erp_inventory_fk_constraints_20260612.sql
mysql -h "$APP_DB_HOST" -u "$APP_DB_USER" -p"$APP_DB_PASSWORD" "$APP_DB_NAME" \
  < sql/erp_inventory_master_warehouse_fk_constraints_20260612.sql
```

- [ ] **Step 3: Verify FK presence**

Run:

```bash
mysql -h "$APP_DB_HOST" -u "$APP_DB_USER" -p"$APP_DB_PASSWORD" "$APP_DB_NAME" -e "
SELECT CONSTRAINT_NAME, TABLE_NAME, REFERENCED_TABLE_NAME
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME LIKE 'fk_inv_%'
ORDER BY CONSTRAINT_NAME;"
```

Expected: constraints from both FK scripts appear.

- [ ] **Step 4: Add checklist**

Add:

```markdown
- [ ] FK preflight output has zero orphans before FK scripts are applied.
- [ ] `information_schema.REFERENTIAL_CONSTRAINTS` contains the expected `fk_inv_%` constraints.
```

- [ ] **Step 5: Commit documentation only**

```bash
git add docs/production-security-checklist.md
git commit -m "docs(db): record inventory foreign key rollout checks"
```

---

### Task 10: P2 Transfer Candidate Mapper Double Guard

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml`
- Modify or create: inventory mapper/service tests that cover candidate selection.

- [ ] **Step 1: Add SQL-level shop-scope filter**

Change `selectUsersByDeptAndPostCode` to join `sys_user_shop`:

```xml
select distinct u.user_id as userId, u.user_name as userName
from sys_user u
join sys_user_post up on up.user_id = u.user_id
join sys_post p on p.post_id = up.post_id
join sys_user_shop us on us.user_id = u.user_id and us.dept_id = #{deptId}
where u.del_flag = '0'
  and u.status = '0'
  and u.dept_id = #{deptId}
  and p.post_code = #{postCode}
order by u.user_id
```

- [ ] **Step 2: Keep service-level filter**

Do not remove `filterAuthorizedCandidates(...)` or `hasUserShopScope(...)` in `InvTransferApprovalServiceImpl`. The mapper join is a double guard, not a replacement.

- [ ] **Step 3: Verify**

Run:

```bash
mvn -f erp-modules/erp-inventory/pom.xml test
```

Expected: inventory tests pass.

- [ ] **Step 4: Commit**

```bash
git add erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml
git commit -m "fix(inventory): filter transfer approval candidates by shop scope"
```

---

### Task 11: P2 Duplicate Salary View Cleanup

**Files:**
- Delete: `erp-ui/src/views/system/salary/index 2.vue`

- [ ] **Step 1: Confirm no imports reference the duplicate file**

Run:

```bash
rg -n "index 2\\.vue|system/salary/index 2" erp-ui/src
```

Expected: only the duplicate file path appears from filesystem commands; no import references.

- [ ] **Step 2: Delete duplicate file**

Run:

```bash
rm "erp-ui/src/views/system/salary/index 2.vue"
```

- [ ] **Step 3: Verify frontend build**

```bash
npm --prefix erp-ui run build:prod
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add -u "erp-ui/src/views/system/salary/index 2.vue"
git commit -m "chore(ui): remove duplicate salary view file"
```

---

### Task 11A: P1 OA Attendance And Salary Admin Views

**Files:**
- Modify: `erp-ui/src/views/oa/attendance/index.vue`
- Modify: `erp-ui/src/views/oa/salary/index.vue`
- Modify: `erp-ui/src/views/mobile/feature/featureService.js`

- [ ] **Step 1: Add desktop attendance scope toggle**

In `erp-ui/src/views/oa/attendance/index.vue`, change import:

```js
import { checkIn, checkOut, listMyRecords, listAllRecords } from "@/api/oa/attendance"
```

Add data:

```js
viewScope: "my"
```

Add a radio group to the search form:

```vue
<el-form-item label="范围">
  <el-radio-group v-model="viewScope" size="mini" @change="getList">
    <el-radio-button label="my">我的</el-radio-button>
    <el-radio-button v-if="$auth.hasPermi('oa:attendance:list')" label="all">全部</el-radio-button>
  </el-radio-group>
</el-form-item>
```

Change the list call:

```js
const request = this.viewScope === "all" && this.$auth.hasPermi("oa:attendance:list")
  ? listAllRecords
  : listMyRecords
request(params).then(res => {
  this.list = res.rows || []
  this.total = res.total || 0
  this.findTodayRecord()
}).finally(() => { this.loading = false })
```

- [ ] **Step 2: Add desktop salary scope toggle**

In `erp-ui/src/views/oa/salary/index.vue`, change import:

```js
import { listMySalary, listAllSalary, calculateSalary, getSalaryConfig, saveSalaryConfig } from "@/api/oa/salary"
```

Add data:

```js
viewScope: "my"
```

Add a radio group to the form:

```vue
<el-form-item label="范围">
  <el-radio-group v-model="viewScope" size="mini" @change="getList">
    <el-radio-button label="my">我的</el-radio-button>
    <el-radio-button v-if="$auth.hasPermi('oa:salary:list')" label="all">全部</el-radio-button>
  </el-radio-group>
</el-form-item>
```

Change the list call:

```js
const request = this.viewScope === "all" && this.$auth.hasPermi("oa:salary:list")
  ? listAllSalary
  : listMySalary
request(params).then(res => {
  this.list = res.rows || []
  this.total = res.total || 0
}).finally(() => { this.loading = false })
```

- [ ] **Step 3: Add mobile admin list behavior**

In `erp-ui/src/views/mobile/feature/featureService.js`, import list-all APIs if not already imported:

```js
import { getRecord as getAttendanceRecord, listMyRecords, listAllRecords } from "@/api/oa/attendance"
import { getSalary, listMySalary, listAllSalary } from "@/api/oa/salary"
import auth from "@/plugins/auth"
```

Change list calls:

```js
if (featureKey === "attendance") {
  const request = auth.hasPermi("oa:attendance:list") ? listAllRecords : listMyRecords
  return resolveList(request(normalizeAttendanceQuery(query)))
}

if (featureKey === "salary") {
  const request = auth.hasPermi("oa:salary:list") ? listAllSalary : listMySalary
  return resolveList(request(normalizeSalaryQuery(query)))
}
```

- [ ] **Step 4: Verify**

Run:

```bash
npm --prefix erp-ui run build:prod
```

Expected: build succeeds; users with list permissions can switch to all records on desktop and see all records on mobile, while ordinary users continue to see only their own records.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/oa/attendance/index.vue erp-ui/src/views/oa/salary/index.vue erp-ui/src/views/mobile/feature/featureService.js
git commit -m "feat(oa): expose attendance and salary admin views"
```

---

### Task 12: P2 Mobile Route Configuration

**Files:**
- Create: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- Modify: `erp-ui/src/views/mobile/mobileNavigation.js`
- Modify: `erp-ui/src/router/index.js`

- [ ] **Step 1: Capture route baseline before moving code**

Before editing, capture the baseline:

```bash
python3 - <<'NODE'
from pathlib import Path
lines = Path('erp-ui/src/router/index.js').read_text().splitlines()
print(sum(1 for line in lines if line.startswith("    path: '/mobile")))
print(Path('erp-ui/src/router/index.js').read_text().count("path: '/mobile"))
NODE
```

Expected baseline before refactor:
- top-level mobile route entries: `43`
- total `/mobile` path string occurrences: `54`

- [ ] **Step 2: Move all mobile route definitions into one source**

Create `mobileRouteDefinitions.js` and move every top-level `/mobile` route object from `erp-ui/src/router/index.js` into this file before deleting any of them from the router. The first implementation must preserve all `43` route entries, not a selected subset.

Use this CommonJS export shape so the existing Node-based UI tests can require the route metadata directly:

```js
const ROUTE_KEYS_BY_PATH = {
  "/mobile/store": "storeWorkbench",
  "/mobile/warehouse": "warehouseWorkbench",
  "/mobile/inventory": "workbench",
  "/mobile/contract": "contract"
}

const mobileRouteDefinitions = [
  /* 43 existing route objects moved from erp-ui/src/router/index.js */
]

function getMobileRouteKey(definition) {
  return ROUTE_KEYS_BY_PATH[definition.path] ||
    definition.key ||
    definition.meta?.mobileFeature?.featureKey ||
    definition.meta?.featureKey
}

const MOBILE_ROUTES = mobileRouteDefinitions.reduce((routes, definition) => {
  const key = getMobileRouteKey(definition)
  if (key) {
    routes[key] = definition.path
  }
  return routes
}, {})

module.exports = { mobileRouteDefinitions, MOBILE_ROUTES }
```

Each moved definition must preserve its existing `path`, `meta.title`, `meta.mobileFeature.featureKey`, permission metadata, action buttons, tabs, sample data, and component.

- [ ] **Step 3: Use `MOBILE_ROUTES` from definitions**

In `mobileNavigation.js`, import:

```js
const { MOBILE_ROUTES } = require("./mobileRouteDefinitions")
```

Remove the local hardcoded `const MOBILE_ROUTES = { ... }`.

- [ ] **Step 4: Use route definitions directly in router**

In `erp-ui/src/router/index.js`, import `mobileRouteDefinitions`:

```js
const { mobileRouteDefinitions } = require("@/views/mobile/mobileRouteDefinitions")
```

Keep the moved route objects intact instead of rebuilding simplified objects; rebuilding would risk dropping `hidden`, `meta.mobileFeature`, action buttons, tabs, and sample data.

Preserve explicit components for the non-feature mobile pages:

```js
{ key: "storeWorkbench", path: "/mobile/store", component: () => import("@/views/mobile/store/index") }
{ key: "warehouseWorkbench", path: "/mobile/warehouse", component: () => import("@/views/mobile/warehouse/index") }
{ key: "workbench", path: "/mobile/inventory", component: () => import("@/views/mobile/inventory/index") }
{ key: "contract", path: "/mobile/contract", component: () => import("@/views/mobile/contract/index") }
```

Replace the repeated mobile route block with:

```js
...mobileRouteDefinitions
```

- [ ] **Step 5: Verify no path count drift**

Run:

```bash
node - <<'NODE'
const { mobileRouteDefinitions, MOBILE_ROUTES } = require('./erp-ui/src/views/mobile/mobileRouteDefinitions.js')
const paths = new Set(mobileRouteDefinitions.map(route => route.path))
if (paths.size !== mobileRouteDefinitions.length) {
  throw new Error('duplicate mobile route path')
}
if (mobileRouteDefinitions.length !== 43) {
  throw new Error(`expected 43 top-level mobile routes, got ${mobileRouteDefinitions.length}`)
}
if (MOBILE_ROUTES.workbench !== '/mobile/inventory') {
  throw new Error('missing stable mobile workbench route alias')
}
console.log(`mobile route definitions: ${mobileRouteDefinitions.length}`)
NODE
npm --prefix erp-ui run build:prod
```

Expected: no duplicate path error; production build succeeds.

- [ ] **Step 6: Commit**

```bash
git add erp-ui/src/views/mobile/mobileRouteDefinitions.js erp-ui/src/views/mobile/mobileNavigation.js erp-ui/src/router/index.js
git commit -m "refactor(ui): centralize mobile route definitions"
```

---

### Task 13: P3 Product Capability Roadmap

**Files:**
- Create: `docs/erp-product-capability-roadmap.md`

- [ ] **Step 1: Document master-data decision**

Create a section:

```markdown
## Master Data

Decision: introduce headquarters-level master records for product, customer, and supplier while preserving current shop-scoped operational records.

Initial tables:
- `inv_product_master`
- `inv_customer_master`
- `inv_supplier_master`

Current shop-scoped tables keep `shop_dept_id` and reference the matching master ID after migration.
```

- [ ] **Step 2: Document finance module scope**

Add:

```markdown
## Finance Scope

First finance slice:
- Accounts receivable generated from approved/delivered sales orders.
- Accounts payable generated from received purchase orders.
- Fund ledger entries for receipts and payments.
- Gross profit report joining sales revenue, stock cost, returns, and adjustments.
```

- [ ] **Step 3: Document approval unification**

Add:

```markdown
## Approval Unification

Decision: keep Flowable for formal OA workflows and define whether inventory transfer approval migrates to Flowable or remains a lightweight rule engine.

Evaluation criteria:
- audit trail completeness
- mobile approval experience
- configurability by shop/warehouse
- migration cost for existing transfer records
```

- [ ] **Step 4: Document notifications and batch actions**

Add:

```markdown
## Notifications

First notification slice:
- approval pending
- approval completed/rejected
- low stock warning
- arrival/receiving pending

Delivery channels:
- in-app notice center first
- WebSocket or SSE after the notice domain model is stable

## Batch Actions

Priority business lists:
- inventory product
- inventory stock
- purchase order
- sales order
- transfer order
- OA purchase
```

- [ ] **Step 5: Commit**

```bash
git add docs/erp-product-capability-roadmap.md
git commit -m "docs(product): outline erp capability roadmap"
```

---

## Final Verification

Run before marking the whole plan complete:

```bash
mvn -f erp-common/erp-common-core/pom.xml test
mvn -f erp-common/erp-common-security/pom.xml test
mvn -f erp-gateway/pom.xml test
mvn -f erp-modules/erp-oa/pom.xml test
mvn -f erp-modules/erp-inventory/pom.xml test
bash bin/check-controller-security.sh
npm --prefix erp-ui run build:prod
docker compose -f docker/docker-compose.yml config >/tmp/erp-compose-rendered.yml
```

Manual production checks:

```bash
mysql -h "$APP_DB_HOST" -u "$APP_DB_USER" -p"$APP_DB_PASSWORD" "$APP_DB_NAME" < sql/security_admin_privilege_audit_20260613.sql
mysql -h "$APP_DB_HOST" -u "$APP_DB_USER" -p"$APP_DB_PASSWORD" "$APP_DB_NAME" < sql/erp_inventory_fk_preflight_orphan_check_20260612.sql
```

Completion criteria:

- No Java service can start outside `dev`, `local`, or `test` without an external JWT secret.
- Docker Compose renders `SPRING_PROFILES_ACTIVE`, `ERP_JWT_SECRET`, and `SPRING_DATA_REDIS_PASSWORD` for every Java service.
- Redis requires a password and every Java service knows that password.
- Nacos DB credentials are environment-driven and not hardcoded as `root/password`.
- File access policy is documented; if Path B is chosen, Gateway no longer globally whitelists `/file/**`.
- OA write endpoints have backend idempotency.
- Controller scanner passes.
- Shop scope logic is centralized for new OA/inventory changes.
- FK scripts are either applied in production or blocked by documented orphan rows.
- Duplicate salary view file is removed.
