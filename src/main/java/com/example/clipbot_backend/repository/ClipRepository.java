package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.util.ClipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClipRepository extends JpaRepository<Clip, UUID> {
    Page<Clip> findByMediaOrderByCreatedAtDesc(Media media, Pageable pageable);
    Page<Clip> findByMediaAndStatusOrderByCreatedAtDesc(Media media, ClipStatus status, Pageable pageable);
    Page<Clip> findByMediaInOrderByCreatedAtDesc(Collection<Media> media, Pageable pageable);
    Page<Clip> findByMediaInAndStatusOrderByCreatedAtDesc(Collection<Media> media, ClipStatus status, Pageable pageable);
    @Query("select c.status as status, count(c) as cnt from Clip c where c.media in :media group by c.status")
    List<StatusCount> countByMediaInGroupByStatus(List<Media> media);
    interface StatusCount { ClipStatus getStatus(); long getCnt(); }

    /**
     * Loads a clip with its media and media owner eagerly fetched.
     *
     * @param id clip identifier.
     * @return optional clip including media and owner relations.
     */
    @Query("""
           select c
           from Clip c
           join fetch c.media m
           join fetch m.owner
           where c.id = :id
           """)
    Optional<Clip> findByIdWithMedia(@Param("id") UUID id);

    /**
     * Returns clips for the given media ordered by descending recommendation score.
     *
     * @param mediaId media identifier.
     * @param pageable pagination information.
     * @return clips sorted by score and creation time.
     */
    @Query("""
            select c
            from Clip c
            where c.media.id = :mediaId
            order by coalesce(c.score, 0) desc, c.createdAt desc
            """)
    List<Clip> findByMediaIdOrderByScoreDesc(@Param("mediaId") UUID mediaId, Pageable pageable);

    /**
     * Looks up a clip by media range and profile hash combination.
     *
     * @param mediaId media identifier.
     * @param startMs start offset in milliseconds.
     * @param endMs end offset in milliseconds.
     * @param profileHash render profile hash.
     * @return optional clip matching the unique combination.
     */
    Optional<Clip> findByMediaIdAndStartMsAndEndMsAndProfileHash(UUID mediaId, long startMs, long endMs, String profileHash);

}
