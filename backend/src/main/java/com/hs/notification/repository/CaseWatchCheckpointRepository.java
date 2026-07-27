package com.hs.notification.repository;

import com.hs.notification.model.CaseWatchCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaseWatchCheckpointRepository extends JpaRepository<CaseWatchCheckpoint, Long> {
    Optional<CaseWatchCheckpoint> findFirstByOrderByCheckpointIdAsc();
}
