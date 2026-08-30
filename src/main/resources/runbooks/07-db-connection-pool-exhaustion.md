---
title: Handling database connection pool exhaustion
tags: database, connections, performance
---
Connection pool exhaustion presents as requests timing out while waiting to
acquire a database connection, even though the database itself is healthy.

**Common causes**
- A slow query holding connections longer than expected under load
- A connection leak (a code path that acquires a connection but never
  releases it, e.g. on an exception path)
- Pool size configured too small for current traffic
- A downstream dependency slowing down and causing requests (and their
  held connections) to pile up

**Steps**
1. Check current pool utilization metrics (active vs. idle vs. max
   connections).
2. Look for a recent deploy that could have introduced a connection leak.
3. Identify any slow queries running concurrently with the incident window.
4. As a short-term mitigation, consider a rolling restart to release leaked
   connections while the root cause is fixed.
5. If pool size is simply undersized for current traffic, increase it -
   but only after ruling out a leak, since a leak will just refill a
   larger pool over time.
