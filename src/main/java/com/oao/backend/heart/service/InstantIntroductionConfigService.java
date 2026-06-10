package com.oao.backend.heart.service;

import com.oao.backend.heart.domain.InstantIntroductionConfig;
import com.oao.backend.heart.repository.InstantIntroductionConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstantIntroductionConfigService {

	private final InstantIntroductionConfigRepository configRepository;

	public InstantIntroductionConfigService(InstantIntroductionConfigRepository configRepository) {
		this.configRepository = configRepository;
	}

	@Transactional(readOnly = true)
	public InstantIntroductionConfigView findConfig() {
		return InstantIntroductionConfigView.from(findOrDefault());
	}

	@Transactional
	public InstantIntroductionConfigView updateConfig(InstantIntroductionConfigUpdateCommand command, Long adminId) {
		InstantIntroductionConfig config = configRepository.findById(InstantIntroductionConfig.DEFAULT_ID)
			.orElseGet(() -> configRepository.save(InstantIntroductionConfig.defaultConfig()));
		config.update(
			command.firstUsageCost(),
			command.midTierEndCount(),
			command.midTierCost(),
			command.highTierCost(),
			command.usageWindowHours(),
			adminId
		);
		return InstantIntroductionConfigView.from(config);
	}

	@Transactional(readOnly = true)
	public InstantIntroductionConfig findOrDefault() {
		return configRepository.findById(InstantIntroductionConfig.DEFAULT_ID)
			.orElseGet(InstantIntroductionConfig::defaultConfig);
	}

	public record InstantIntroductionConfigUpdateCommand(
		int firstUsageCost,
		int midTierEndCount,
		int midTierCost,
		int highTierCost,
		int usageWindowHours
	) {
	}

	public record InstantIntroductionConfigView(
		Long id,
		int firstUsageCost,
		int midTierStartCount,
		int midTierEndCount,
		int midTierCost,
		int highTierStartCount,
		int highTierCost,
		int usageWindowHours,
		Long updatedByAdminId
	) {

		static InstantIntroductionConfigView from(InstantIntroductionConfig config) {
			return new InstantIntroductionConfigView(
				config.getId(),
				config.getFirstUsageCost(),
				config.getMidTierStartCount(),
				config.getMidTierEndCount(),
				config.getMidTierCost(),
				config.getHighTierStartCount(),
				config.getHighTierCost(),
				config.getUsageWindowHours(),
				config.getUpdatedByAdminId()
			);
		}
	}
}
