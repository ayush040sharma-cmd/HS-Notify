package com.hs.notification.repository;

import com.hs.notification.model.WatchdogConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WatchdogConfigRepository extends JpaRepository<WatchdogConfig, Long> {
    Optional<WatchdogConfig> findFirstByOrderByWatchdogConfigIdAsc();
}
