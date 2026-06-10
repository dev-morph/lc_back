package com.oao.backend.matching.service;

public record MatchingScore(
	int totalScore,
	int sharedHobbyCount,
	int hobbyScore,
	int smokingScore,
	int drinkingScore,
	int religionScore,
	int gradeScore
) {
}
