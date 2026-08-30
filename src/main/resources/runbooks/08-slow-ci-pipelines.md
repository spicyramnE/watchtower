---
title: Investigating slow CI pipeline runs
tags: ci, performance
---
A pipeline that used to take minutes and now takes much longer, without
failing outright, is usually a resource or caching regression.

**Steps**
1. Compare stage-by-stage timings against a recent known-good run to find
   which stage regressed.
2. Check whether a dependency cache (build cache, package manager cache)
   stopped hitting - a full re-download of dependencies is a common silent
   cause.
3. Check CI runner resource allocation - a shared/contended runner pool can
   silently slow every job down.
4. If a specific test suite regressed, check for recently added
   slow/integration tests that could be split out or parallelized.
