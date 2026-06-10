package com.oao.backend.matching.service;

import com.oao.backend.matching.domain.MatchingScoreConfig;
import com.oao.backend.matching.repository.MatchingScoreConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingScoreConfigService {

	private final MatchingScoreConfigRepository configRepository;

	public MatchingScoreConfigService(MatchingScoreConfigRepository configRepository) {
		this.configRepository = configRepository;
	}

	@Transactional(readOnly = true)
	public MatchingScoreConfigView findConfig() {
		return MatchingScoreConfigView.from(findOrDefault());
	}

	@Transactional
	public MatchingScoreConfigView updateConfig(MatchingScoreConfigUpdateCommand command, Long adminId) {
		MatchingScoreConfig config = configRepository.findById(MatchingScoreConfig.DEFAULT_ID)
			.orElseGet(() -> configRepository.save(MatchingScoreConfig.defaultConfig()));
		config.update(
			command.hobbyPointPerMatch(),
			command.hobbyMaxPoint(),
			command.sameSmokingPoint(),
			command.sameDrinkingPoint(),
			command.sameReligionPoint(),
			command.sameGradePoint(),
			command.adjacentGradePoint(),
			command.allowPreviousAutoMatch(),
			adminId
		);
		return MatchingScoreConfigView.from(config);
	}

	@Transactional(readOnly = true)
	public MatchingScoreConfig findOrDefault() {
		return configRepository.findById(MatchingScoreConfig.DEFAULT_ID)
			.orElseGet(MatchingScoreConfig::defaultConfig);
	}

	public record MatchingScoreConfigUpdateCommand(
		int hobbyPointPerMatch,
		int hobbyMaxPoint,
		int sameSmokingPoint,
		int sameDrinkingPoint,
		int sameReligionPoint,
		int sameGradePoint,
		int adjacentGradePoint,
		boolean allowPreviousAutoMatch
	) {
	}

	public record MatchingScoreConfigView(
		Long id,
		int hobbyPointPerMatch,
		int hobbyMaxPoint,
		int sameSmokingPoint,
		int sameDrinkingPoint,
		int sameReligionPoint,
		int sameGradePoint,
		int adjacentGradePoint,
		boolean allowPreviousAutoMatch,
		Long updatedByAdminId
	) {

		static MatchingScoreConfigView from(MatchingScoreConfig config) {
			return new MatchingScoreConfigView(
				config.getId(),
				config.getHobbyPointPerMatch(),
				config.getHobbyMaxPoint(),
				config.getSameSmokingPoint(),
				config.getSameDrinkingPoint(),
				config.getSameReligionPoint(),
				config.getSameGradePoint(),
				config.getAdjacentGradePoint(),
				config.isAllowPreviousAutoMatch(),
				config.getUpdatedByAdminId()
			);
		}
	}
}
