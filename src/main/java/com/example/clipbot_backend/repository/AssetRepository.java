package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.util.AssetKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Page<Asset> findByOwnerOrderByCreatedAtDesc(Account owner, Pageable pageable);
    Page<Asset> findByRelatedClipOrderByCreatedAtDesc(Clip clip, Pageable pageable);
    Page<Asset> findByRelatedMediaOrderByCreatedAtDesc(Media media, Pageable pageable);
    Page<Asset> findByOwnerAndKindOrderByCreatedAtDesc(Account owner, AssetKind kind, Pageable pageable);
    Optional<Asset> findTopByRelatedClipAndKindOrderByCreatedAtDesc(Clip clip, AssetKind kind);
    Optional<Asset> findTopByRelatedMediaAndKindOrderByCreatedAtDesc(Media media, AssetKind kind);
    Page<Asset> findByRelatedClipAndKindOrderByCreatedAtDesc(Clip clip, AssetKind kind, Pageable pageable);
    Page<Asset> findByRelatedMediaAndKindOrderByCreatedAtDesc(Media media, AssetKind kind, Pageable pageable);
    List<Asset> findByRelatedMedia(Media media);
    List<Asset> findByRelatedClipIn(Collection<Clip> clips);

    @Transactional
    void deleteByRelatedClipIn(Collection<Clip> clips);

}
