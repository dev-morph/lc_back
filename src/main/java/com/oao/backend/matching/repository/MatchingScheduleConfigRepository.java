package com.oao.backend.matching.repository;

import com.oao.backend.matching.domain.MatchingScheduleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingScheduleConfigRepository extends JpaRepository<MatchingScheduleConfig, Long> {
}
