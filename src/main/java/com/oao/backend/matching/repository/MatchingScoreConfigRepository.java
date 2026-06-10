package com.oao.backend.matching.repository;

import com.oao.backend.matching.domain.MatchingScoreConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingScoreConfigRepository extends JpaRepository<MatchingScoreConfig, Long> {
}
