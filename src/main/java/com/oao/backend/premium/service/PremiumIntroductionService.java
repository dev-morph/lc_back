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
  @org.springframework.beans.factory.annotation.Autowired private com.oao.backend.common.DbRows db;

  private final PremiumIntroRequestRepository premiumIntroRequestRepository;

  public PremiumIntroductionService(PremiumIntroRequestRepository premiumIntroRequestRepository) {
    this.premiumIntroRequestRepository = premiumIntroRequestRepository;
  }

  @Transactional
  public PremiumIntroRequest create(Long userId, CreatePremiumIntroductionCommand command) {
    db.jdbc.queryForList("select id from user_account where id=? for update", userId);
    if (db.count(
            "select count(*) from user_account where id=? and approval_status='APPROVED' and"
                + " status='ACTIVE'",
            userId)
        == 0) throw new BusinessException(HttpStatus.FORBIDDEN, "가입 승인 후 신청할 수 있습니다.");
    if (db.count(
            "select count(*) from premium_intro_request where user_id=? and status in"
                + " ('REQUESTED','IN_REVIEW')",
            userId)
        > 0) throw new BusinessException(HttpStatus.CONFLICT, "진행 중인 프리미엄 신청이 있습니다.");
    if (command.minAge() > command.maxAge() || command.minHeightCm() > command.maxHeightCm())
      throw new BusinessException(HttpStatus.BAD_REQUEST, "최소값과 최대값을 확인해주세요.");

    if (command.appearanceWeight() == null
        || command.specWeight() == null
        || command.appearanceWeight() + command.specWeight() != 100)
      throw new BusinessException(HttpStatus.BAD_REQUEST, "외모와 조건 중요도의 합계는 100이어야 합니다.");
    if (command.keywordIds().stream().distinct().count() != command.keywordIds().size()
        || command.keywordIds().stream()
            .anyMatch(
                id ->
                    id == null
                        || db.count("select count(*) from personality_keyword where id=?", id)
                            == 0))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "성격 키워드를 확인해주세요.");

    if (command.keywordIds().size() > 3) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "Personality keywords can be up to 3.");
    }

    PremiumIntroRequest request =
        PremiumIntroRequest.create(
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
            command.keywordIds());
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
      List<Long> keywordIds) {}
}
