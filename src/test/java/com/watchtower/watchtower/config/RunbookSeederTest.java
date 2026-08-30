package com.watchtower.watchtower.config;

import com.watchtower.watchtower.repository.RunbookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RunbookSeederTest {

    @Autowired
    private RunbookRepository runbookRepository;

    @Test
    void startupSeedsRunbookKnowledgeBaseWithContentAndTags() {
        assertThat(runbookRepository.count()).isGreaterThanOrEqualTo(10);
        assertThat(runbookRepository.findAll())
                .allSatisfy(runbook -> {
                    assertThat(runbook.getTitle()).isNotBlank();
                    assertThat(runbook.getContent()).isNotBlank();
                    assertThat(runbook.getTags()).isNotEmpty();
                });
    }
}
