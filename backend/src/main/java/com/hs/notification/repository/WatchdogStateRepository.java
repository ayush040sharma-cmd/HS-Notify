package com.hs.notification.repository;

import com.hs.notification.model.WatchdogState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WatchdogStateRepository extends JpaRepository<WatchdogState, Long> {
    Optional<WatchdogState> findFirstByOrderByWatchdogStateIdAsc();
}
