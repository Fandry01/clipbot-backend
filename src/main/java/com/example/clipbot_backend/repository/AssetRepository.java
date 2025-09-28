package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.util.AssetKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Page<Asset> findByOwnerOrderByCreatedAtDesc(Account owner, Pageable pageable);
    Page<Asset> findByRelatedClipOrderByCreatedAtDesc(Clip clip, Pageable pageable);
    Page<Asset> findByRelatedMediaOrderByCreatedAtDesc(Media media, Pageable pageable);
    Page<Asset> findByOwnerAndKindOrderByCreatedAtDesc(Account owner, AssetKind kind, Pageable pageable);
}
