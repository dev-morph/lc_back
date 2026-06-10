package com.oao.backend.matching.service;

import com.oao.backend.user.domain.UserAccount.Gender;
import com.oao.backend.user.domain.UserAccount.MemberGrade;
import java.time.Instant;
import java.util.Set;

public record MatchingCandidate(
	Long userId,
	Gender gender,
	MemberGrade grade,
	String smokingStatus,
	String drinkingStatus,
	String religion,
	Set<Long> hobbyIds,
	Instant lastAutoMatchedAt,
	int autoMatchCount
) {
}
