package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.RenderStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RenderStatsRepositoryTest {

    @Autowired
    private RenderStatsRepository renderStatsRepository;

    @Test
    void saveAndFindByKindWorks() {
        RenderStats stats = new RenderStats("CLIP_720P", 2_500L, 3L);
        renderStatsRepository.saveAndFlush(stats);

        assertThat(renderStatsRepository.findByKind("CLIP_720P"))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getAvgMs()).isEqualTo(2_500L);
                    assertThat(found.getCount()).isEqualTo(3L);
                });
    }
}
