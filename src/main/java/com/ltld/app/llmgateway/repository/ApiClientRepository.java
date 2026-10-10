package com.ltld.app.llmgateway.repository;

import com.ltld.app.llmgateway.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {
    Optional<ApiClient> findByApiKeyHash(String apiKeyHash);
}
