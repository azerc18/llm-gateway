package com.ltld.app.llmgateway.repository;

import com.ltld.app.llmgateway.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {
    Optional<UsageLog> findByRequestId(String requestId);
}