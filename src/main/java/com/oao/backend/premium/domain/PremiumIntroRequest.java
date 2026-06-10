package com.oao.backend.premium.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "premium_intro_request")
public class PremiumIntroRequest extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	@Enumerated(EnumType.STRING)
	private PremiumIntroStatus status = PremiumIntroStatus.REQUESTED;

	private Integer minAge;
	private Integer maxAge;
	private Integer minHeightCm;
	private Integer maxHeightCm;
	private Integer appearanceWeight;
	private Integer specWeight;
	private String appearancePreferenceText;
	private String preferredJobGroups;
	private String importantPointText;
	private Long assignedAdminId;

	@ElementCollection
	@CollectionTable(
		name = "premium_intro_request_keyword",
		joinColumns = @JoinColumn(name = "premium_intro_request_id")
	)
	@Column(name = "keyword_id")
	private List<Long> keywordIds = new ArrayList<>();

	protected PremiumIntroRequest() {
	}

	public static PremiumIntroRequest create(
		Long userId,
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
		PremiumIntroRequest request = new PremiumIntroRequest();
		request.userId = userId;
		request.minAge = minAge;
		request.maxAge = maxAge;
		request.minHeightCm = minHeightCm;
		request.maxHeightCm = maxHeightCm;
		request.appearanceWeight = appearanceWeight;
		request.specWeight = specWeight;
		request.appearancePreferenceText = appearancePreferenceText;
		request.preferredJobGroups = preferredJobGroups;
		request.importantPointText = importantPointText;
		request.keywordIds.addAll(keywordIds);
		return request;
	}

	public Long getId() {
		return id;
	}

	public enum PremiumIntroStatus {
		REQUESTED,
		IN_REVIEW,
		MATCHED,
		CANCELED
	}
}
