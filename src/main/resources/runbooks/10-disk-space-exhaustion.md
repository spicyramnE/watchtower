---
title: Responding to disk space exhaustion on build agents
tags: infrastructure, disk, ci
---
Build agents running out of disk space typically fail with cryptic
"no space left on device" errors that can look unrelated to the actual
build/test step that surfaces them.

**Steps**
1. Check the agent's disk usage at the time of failure.
2. Look for unbounded accumulation - old Docker images/layers, build caches,
   or log files that are never cleaned up.
3. Add or fix a cleanup step (e.g. `docker system prune`, cache eviction)
   in the pipeline if one is missing or broken.
4. If this is a shared, persistent runner, consider moving to ephemeral
   runners so disk state can't accumulate across runs.
