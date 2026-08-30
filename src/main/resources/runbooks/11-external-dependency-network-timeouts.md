---
title: Handling network timeout errors to external dependencies
tags: network, timeout, dependencies
---
Timeouts calling an external dependency (a third-party API, another
internal service) can originate from either side of the connection.

**Steps**
1. Check the external dependency's own status page/health metrics first -
   rule out an outage on their end before investigating locally.
2. Check whether the timeout budget configured on the client is realistic
   for the operation being performed (e.g. too aggressive for a
   known-slow endpoint).
3. Look for a recent change to network policy, firewall rules, or DNS that
   could have affected reachability.
4. If the dependency is degraded but not fully down, verify a circuit
   breaker or retry-with-backoff is in place so the failure doesn't cascade.
