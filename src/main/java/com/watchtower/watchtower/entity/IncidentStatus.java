package com.watchtower.watchtower.entity;

/**
 * Incident lifecycle: NEW -&gt; DIAGNOSING -&gt; AWAITING_APPROVAL -&gt; RESOLVED / REJECTED.
 */
public enum IncidentStatus {
    NEW,
    DIAGNOSING,
    AWAITING_APPROVAL,
    RESOLVED,
    REJECTED
}
