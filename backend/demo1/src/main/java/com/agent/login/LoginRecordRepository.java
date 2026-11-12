package com.agent.login;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface LoginRecordRepository extends JpaRepository<LoginRecord, Long>, JpaSpecificationExecutor<LoginRecord> {
    long countByCreatedAtGreaterThanEqual(Instant createdAt);
    long countByResult(String result);
    long countByRiskStatusNot(String riskStatus);
    long countByLoginIdentifierIgnoreCaseAndResultAndCreatedAtGreaterThanEqual(String loginIdentifier, String result, Instant createdAt);
    long countByLoginIdentifierIgnoreCaseAndResultAndRiskStatusNotAndCreatedAtGreaterThanEqual(String loginIdentifier, String result, String riskStatus, Instant createdAt);
    List<LoginRecord> findTop5ByUserIdAndResultOrderByCreatedAtDesc(Long userId, String result);
}
