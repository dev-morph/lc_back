package com.oao.backend.matching.service;

import com.oao.backend.matching.domain.MatchingScoreConfig;
import com.oao.backend.user.domain.UserAccount.MemberGrade;
import org.springframework.stereotype.Component;

@Component
public class MatchingScoreCalculator {

	public MatchingScore calculate(MatchingCandidate first, MatchingCandidate second, MatchingScoreConfig config) {
		int sharedHobbyCount = sharedHobbyCount(first, second);
		int hobbyScore = Math.min(sharedHobbyCount * config.getHobbyPointPerMatch(), config.getHobbyMaxPoint());
		int smokingScore = sameNonBlank(first.smokingStatus(), second.smokingStatus()) ? config.getSameSmokingPoint() : 0;
		int drinkingScore = sameNonBlank(first.drinkingStatus(), second.drinkingStatus()) ? config.getSameDrinkingPoint() : 0;
		int religionScore = sameNonBlank(first.religion(), second.religion()) ? config.getSameReligionPoint() : 0;
		int gradeScore = gradeScore(first.grade(), second.grade(), config);
		return new MatchingScore(
			hobbyScore + smokingScore + drinkingScore + religionScore + gradeScore,
			sharedHobbyCount,
			hobbyScore,
			smokingScore,
			drinkingScore,
			religionScore,
			gradeScore
		);
	}

	private int sharedHobbyCount(MatchingCandidate first, MatchingCandidate second) {
		int count = 0;
		for (Long hobbyId : first.hobbyIds()) {
			if (second.hobbyIds().contains(hobbyId)) {
				count++;
			}
		}
		return count;
	}

	private boolean sameNonBlank(String first, String second) {
		return first != null && second != null && !first.isBlank() && first.equals(second);
	}

	private int gradeScore(MemberGrade first, MemberGrade second, MatchingScoreConfig config) {
		if (first == null || second == null) {
			return 0;
		}
		int distance = Math.abs(rank(first) - rank(second));
		if (distance == 0) {
			return config.getSameGradePoint();
		}
		if (distance == 1) {
			return config.getAdjacentGradePoint();
		}
		return 0;
	}

	private int rank(MemberGrade grade) {
		return switch (grade) {
			case S -> 3;
			case A -> 2;
			case B -> 1;
		};
	}
}
