---
title: Investigating out-of-memory crashes during test runs
tags: oom, memory, tests, ci
---
An OOMKilled or `OutOfMemoryError` during a test run means the process
exceeded its available heap or the container's memory limit.

**Common causes**
- A recently added test loads a large fixture or dataset into memory
- A memory leak in test setup/teardown (e.g. contexts not being closed)
- The CI runner's container memory limit is lower than the JVM's heap needs
- Parallel test execution multiplying per-worker memory usage

**Steps**
1. Identify which test class was running when the crash occurred.
2. Check whether the test was recently modified to use larger fixtures.
3. Compare the container memory limit against the configured `-Xmx`.
4. If parallel test workers are enabled, try reducing worker count to see if
   the crash still reproduces.
5. Add heap dump on OOM (`-XX:+HeapDumpOnOutOfMemoryError`) for future
   occurrences if this keeps recurring.
