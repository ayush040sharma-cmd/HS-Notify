package com.hs.notification.repository;

import com.hs.notification.model.NotificationAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationActionRepository extends JpaRepository<NotificationAction, Long> {

    List<NotificationAction> findAllByOrderByDisplayOrderAsc();

    Optional<NotificationAction> findByCode(String code);

    boolean existsByCode(String code);
}
