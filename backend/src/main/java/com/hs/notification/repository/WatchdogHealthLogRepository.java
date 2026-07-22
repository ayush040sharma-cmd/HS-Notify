package com.hs.notification.repository;

import com.hs.notification.model.WatchdogHealthLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchdogHealthLogRepository extends JpaRepository<WatchdogHealthLog, Long> {
    Page<WatchdogHealthLog> findAllByOrderByCheckedAtDesc(Pageable pageable);
}
