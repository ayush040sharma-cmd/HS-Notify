package com.hs.notification.repository;

import com.hs.notification.model.RateLimitBucket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitBucketRepository extends JpaRepository<RateLimitBucket, Long> {

    void deleteByRule_RuleId(Long ruleId);
}
