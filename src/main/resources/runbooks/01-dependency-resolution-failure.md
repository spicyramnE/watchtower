---
title: Diagnosing dependency resolution / failed build errors
tags: build, dependencies, ci
---
Dependency resolution failures happen when a build cannot fetch or resolve one
or more declared dependencies from the configured repositories.

**Common causes**
- A dependency version was retracted or deleted from the repository
- A typo in the group/artifact/version coordinates
- The artifact repository is unreachable or rate-limiting requests
- A private repository credential expired

**Steps**
1. Read the exact failing coordinate from the error log.
2. Check whether the version still exists in the repository (via the
   repository's web UI or a manual fetch).
3. If the repository is unreachable, retry once - transient network issues
   are common in CI runners.
4. If credentials are involved, verify the secret has not expired or been
   rotated without updating CI configuration.
5. Pin the dependency to the last known-good version as a temporary
   mitigation while the root cause is investigated.
