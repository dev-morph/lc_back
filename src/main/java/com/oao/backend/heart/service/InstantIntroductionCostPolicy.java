package com.oao.backend.heart.service;

import com.oao.backend.heart.domain.InstantIntroductionConfig;
import org.springframework.stereotype.Component;

@Component
public class InstantIntroductionCostPolicy {

	private final InstantIntroductionConfigService configService;

	public InstantIntroductionCostPolicy(InstantIntroductionConfigService configService) {
		this.configService = configService;
	}

	public InstantIntroductionConfig currentConfig() {
		return configService.findOrDefault();
	}

	public int costFor(int usageCountInWindow, InstantIntroductionConfig config) {
		return config.costFor(usageCountInWindow);
	}
}
