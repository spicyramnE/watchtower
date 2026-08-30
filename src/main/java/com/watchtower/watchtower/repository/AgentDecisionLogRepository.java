package com.watchtower.watchtower.repository;

import com.watchtower.watchtower.entity.AgentDecisionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentDecisionLogRepository extends JpaRepository<AgentDecisionLog, Long> {

    List<AgentDecisionLog> findByIncidentIdOrderByStepNumberAsc(Long incidentId);
}
