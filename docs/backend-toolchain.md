# Backend Toolchain

Date: 2026-07-16

## Required versions

- JDK 17 (`.java-version`)
- Maven 3.9.16 through the repository wrapper (`./mvnw`)

The root Maven build enforces JDK `[17,18)` and Maven `[3.9.16,3.9.17)` before
compilation. The compiler uses `--release 17`, so a newer local JDK cannot
silently produce bytecode or link APIs unavailable in production.

The wrapper distribution is pinned by SHA-256 in
`.mvn/wrapper/maven-wrapper.properties`. Do not replace the checksum with a
value calculated from an untrusted mirror; verify wrapper upgrades against the
Apache release checksum first.

## Local verification

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"

./mvnw --version
./mvnw test
```

An immediate enforcer failure on JDK 18+ is intentional. Fix `JAVA_HOME`
instead of bypassing or disabling the rule.
