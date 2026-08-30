---
title: Handling flaky test failures in CI
tags: tests, flaky, ci
---
A flaky test passes and fails intermittently without any code changes,
usually due to timing, ordering, or environmental assumptions.

**Common causes**
- Assertions that depend on wall-clock timing (e.g. assuming an operation
  finishes within an arbitrary number of milliseconds)
- Shared mutable state leaking between tests
- Non-deterministic ordering assumptions (e.g. relying on unordered
  collection iteration order)
- External dependencies (network calls, real clocks) not being mocked

**Steps**
1. Check the test's failure history - if it fails a small, consistent
   percentage of runs, it's a strong flaky-test signal rather than a real
   regression.
2. Look for timing-sensitive assertions or sleeps in the test.
3. Check for shared state (static fields, singletons) that could leak
   between test runs.
4. Quarantine the test (mark it as known-flaky) if it cannot be fixed
   immediately, so it stops blocking unrelated pipelines.
5. File a follow-up to fix the root cause - quarantining is a mitigation,
   not a resolution.
