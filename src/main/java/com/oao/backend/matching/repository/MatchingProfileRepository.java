package com.oao.backend.matching.repository;

import com.oao.backend.matching.domain.MatchingProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingProfileRepository extends JpaRepository<MatchingProfile, Long> {

	Optional<MatchingProfile> findByUserId(Long userId);
}
