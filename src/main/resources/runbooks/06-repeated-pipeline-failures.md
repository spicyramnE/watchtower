---
title: Triaging repeated pipeline failures for a single service
tags: pipeline, triage, patterns
---
When the same service fails its pipeline multiple times in a short window,
treat it as a single systemic issue rather than N unrelated incidents.

**Steps**
1. Pull the last 5-10 pipeline runs for the service and compare failure
   stages and error messages.
2. If the same stage and error recur, the root cause is almost always
   upstream of the test/deploy step itself (e.g. a broken dependency, a bad
   merge, infrastructure).
3. If the errors vary between runs, suspect environmental flakiness
   (shared CI runner resource contention, network) rather than the code.
4. Escalate to the service owner with the aggregated pattern, not just the
   latest single failure - patterns are far more actionable than one-off
   logs.
