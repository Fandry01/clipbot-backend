package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class ClipRepositoryTest {

    @Autowired
    private ClipRepository clipRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void duplicateRangeWithProfileHashTriggersUniqueViolation() {
        Account owner = accountRepository.save(new Account("ext-" + UUID.randomUUID(), "Owner"));
        Media media = new Media(owner, "obj.mp4");
        mediaRepository.saveAndFlush(media);

        Clip first = new Clip(media, 1_000, 5_000);
        first.setProfileHash("hash-A");
        clipRepository.saveAndFlush(first);

        Clip duplicate = new Clip(media, 1_000, 5_000);
        duplicate.setProfileHash("hash-A");

        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> clipRepository.saveAndFlush(duplicate));

        assertThat(ex).isNotNull();
    }
}
