package com.agent.admin.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface RiskRequestCounterRepository extends JpaRepository<RiskRequestCounter, Long> {
    Optional<RiskRequestCounter> findByIpAddressAndBucketStart(String ipAddress, Instant bucketStart);

    /**
     * Atomically creates or increments a request bucket. The security filter can
     * receive several concurrent requests from the same browser, so a
     * find-then-insert sequence is not safe against the unique key.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO risk_request_counters
                (ip_address, bucket_start, request_count, failure_count, updated_at)
            VALUES (:ipAddress, :bucketStart, 1, 0, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                request_count = request_count + 1,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int incrementRequestCount(@Param("ipAddress") String ipAddress, @Param("bucketStart") Instant bucketStart);

    /** Atomically creates or increments the failure bucket used by API monitoring. */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO risk_request_counters
                (ip_address, bucket_start, request_count, failure_count, updated_at)
            VALUES (:ipAddress, :bucketStart, 0, 1, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                failure_count = failure_count + 1,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int incrementFailureCount(@Param("ipAddress") String ipAddress, @Param("bucketStart") Instant bucketStart);

    @Query("select coalesce(sum(counter.requestCount), 0) from RiskRequestCounter counter "
            + "where counter.bucketStart >= :start and counter.ipAddress not like 'failure:%' and counter.ipAddress not like 'export:%'")
    long sumRequestCountByBucketStartGreaterThanEqual(@Param("start") Instant start);
}
