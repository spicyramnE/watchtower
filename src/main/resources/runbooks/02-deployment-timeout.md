---
title: What to do when a deployment times out
tags: deployment, timeout, ci-cd
---
A deployment timeout means the platform gave up waiting for the new revision
to report healthy within the configured window.

**Common causes**
- The new revision is crash-looping and never becomes ready
- A readiness probe is misconfigured (wrong port/path)
- The service is waiting on a slow downstream dependency at startup
- Insufficient compute resources are available to schedule the new replicas

**Steps**
1. Check the new revision's logs for crash loops or startup errors.
2. Verify the readiness/liveness probe configuration matches the actual
   health endpoint.
3. Confirm downstream dependencies (database, cache, other services) are
   reachable from the new revision's network context.
4. If resources are the bottleneck, check for quota limits or noisy
   neighbors on the same node pool.
5. If the previous revision is still healthy, consider rolling back while
   investigating.
