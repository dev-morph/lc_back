package com.oao.backend.premium.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.premium.domain.PremiumIntroRequest;
import com.oao.backend.premium.repository.PremiumIntroRequestRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PremiumIntroductionService {

	private final PremiumIntroRequestRepository premiumIntroRequestRepository;

	public PremiumIntroductionService(PremiumIntroRequestRepository premiumIntroRequestRepository) {
		this.premiumIntroRequestRepository = premiumIntroRequestRepository;
	}

	@Transactional
	public PremiumIntroRequest create(Long userId, CreatePremiumIntroductionCommand command) {
		if (command.keywordIds().size() > 3) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Personality keywords can be up to 3.");
		}

		PremiumIntroRequest request = PremiumIntroRequest.create(
			userId,
			command.minAge(),
			command.maxAge(),
			command.minHeightCm(),
			command.maxHeightCm(),
			command.appearanceWeight(),
			command.specWeight(),
			command.appearancePreferenceText(),
			command.preferredJobGroups(),
			command.importantPointText(),
			command.keywordIds()
		);
		return premiumIntroRequestRepository.save(request);
	}

	public record CreatePremiumIntroductionCommand(
		Integer minAge,
		Integer maxAge,
		Integer minHeightCm,
		Integer maxHeightCm,
		Integer appearanceWeight,
		Integer specWeight,
		String appearancePreferenceText,
		String preferredJobGroups,
		String importantPointText,
		List<Long> keywordIds
	) {
	}
}
