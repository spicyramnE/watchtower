---
title: Responding to a failed rollback
tags: rollback, deployment, ci-cd
---
A failed rollback means an attempt to revert to a previous revision did not
succeed - this is a high-severity situation since it means neither the new
nor the old revision may be healthy.

**Common causes**
- The previous revision's container image was already garbage-collected
  from the registry
- The previous revision's configuration/secrets have since been rotated
  and are no longer valid
- A database migration in the new revision was not backward-compatible,
  so the old revision can't run against the current schema

**Steps**
1. Confirm the previous image tag still exists in the registry before
   attempting rollback again.
2. Check whether any database migrations shipped with the failed revision
   are backward-incompatible; if so, a rollback may need a compensating
   migration rather than a simple revert.
3. If no safe previous revision is available, consider a forward fix
   instead of a rollback.
4. Retain images for at least N previous revisions going forward to avoid
   this situation (a follow-up policy fix, not an incident-time fix).
