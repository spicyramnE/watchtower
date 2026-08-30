---
title: Handling secret/credential expiration failures
tags: secrets, credentials, auth
---
A pipeline or deployment failing with authentication/authorization errors
against an external system (registry, cloud provider, third-party API) often
means a credential has expired or been rotated.

**Steps**
1. Confirm the failure is actually an auth error (401/403) rather than a
   network or availability issue.
2. Check the credential's expiration date/rotation policy in the secret
   manager.
3. If rotated elsewhere, confirm the new value was propagated to every
   consumer (CI secrets, runtime environment, any cached copies).
4. Rotate/reissue the credential if it has genuinely expired, and update
   all consumers in the same change to avoid repeat failures.
5. Add expiry alerting for this credential going forward if none exists.
