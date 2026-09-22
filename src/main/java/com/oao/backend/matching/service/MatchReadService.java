package com.oao.backend.matching.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.interest.domain.UserInterest.InterestStatus;
import com.oao.backend.interest.domain.UserInterest.InterestType;
import com.oao.backend.interest.repository.UserInterestRepository;
import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.domain.MatchProposal.MatchDecision;
import com.oao.backend.matching.domain.MatchProposal.MatchStatus;
import com.oao.backend.matching.repository.MatchProposalRepository;
import com.oao.backend.matching.repository.MatchingProfileRepository;
import com.oao.backend.user.domain.Hobby;
import com.oao.backend.user.domain.ProfilePhoto;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.HobbyRepository;
import com.oao.backend.user.repository.ProfilePhotoRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserHobbyRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchReadService {
  @org.springframework.beans.factory.annotation.Autowired private com.oao.backend.user.service.VerificationBadgeService badges;
  private final com.oao.backend.matching.service.MatchingPolicyService matchingPolicy;

  private final MatchProposalRepository matchProposalRepository;
  private final UserAccountRepository userAccountRepository;
  private final UserProfileRepository userProfileRepository;
  private final MatchingProfileRepository matchingProfileRepository;
  private final ProfilePhotoRepository profilePhotoRepository;
  private final UserHobbyRepository userHobbyRepository;
  private final HobbyRepository hobbyRepository;
  private final UserInterestRepository userInterestRepository;
  private final com.oao.backend.interest.service.InterestConsentPolicy consent;
  private final com.oao.backend.chat.service.ChatAvailabilityService chatAvailability;

  public MatchReadService(
      com.oao.backend.matching.service.MatchingPolicyService matchingPolicy,
      MatchProposalRepository matchProposalRepository,
      UserAccountRepository userAccountRepository,
      UserProfileRepository userProfileRepository,
      MatchingProfileRepository matchingProfileRepository,
      ProfilePhotoRepository profilePhotoRepository,
      UserHobbyRepository userHobbyRepository,
      HobbyRepository hobbyRepository,
      UserInterestRepository userInterestRepository,
      com.oao.backend.interest.service.InterestConsentPolicy consent,
      com.oao.backend.chat.service.ChatAvailabilityService chatAvailability) {
    this.matchingPolicy = matchingPolicy;
    this.matchProposalRepository = matchProposalRepository;
    this.userAccountRepository = userAccountRepository;
    this.userProfileRepository = userProfileRepository;
    this.matchingProfileRepository = matchingProfileRepository;
    this.profilePhotoRepository = profilePhotoRepository;
    this.userHobbyRepository = userHobbyRepository;
    this.hobbyRepository = hobbyRepository;
    this.userInterestRepository = userInterestRepository;
    this.consent = consent;
    this.chatAvailability = chatAvailability;
  }

  @Transactional(readOnly = true)
  public List<MatchView> findPendingMatches(Long userId) {
    var matches = matchProposalRepository
        .findByStatusAndUserAIdOrStatusAndUserBId(
            MatchStatus.PENDING, userId, MatchStatus.PENDING, userId)
        .stream()
        .filter(
            match -> match.getExpiresAt() == null || match.getExpiresAt().isAfter(Instant.now()))
        .filter(match -> matchingPolicy.pairAllowed(match.getUserAId(), match.getUserBId()))
        .sorted(matchComparator())
        .toList();
    var verified=badges.forUsers(matches.stream().map(m -> counterpartUserId(m,userId)).toList());
    return matches.stream().map(m -> toMatchView(m,userId,verified.getOrDefault(counterpartUserId(m,userId), com.oao.backend.user.service.VerificationBadgeService.Badges.NONE))).toList();
  }

  @Transactional(readOnly = true)
  public List<MatchView> findCompletedMatches(Long userId) {
    var matches = matchProposalRepository
        .findByStatusAndUserAIdOrStatusAndUserBId(
            MatchStatus.ACCEPTED, userId, MatchStatus.ACCEPTED, userId)
        .stream()
        .sorted(completedMatchComparator())
        .toList();
    var verified=badges.forUsers(matches.stream().map(m -> counterpartUserId(m,userId)).toList());
    return matches.stream().map(m -> toMatchView(m,userId,verified.getOrDefault(counterpartUserId(m,userId), com.oao.backend.user.service.VerificationBadgeService.Badges.NONE))).toList();
  }

  @Transactional(readOnly = true)
  public MatchView findMatch(Long matchId, Long userId) {
    MatchProposal match =
        matchProposalRepository
            .findById(matchId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Match not found."));
    if (!isParticipant(match, userId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "Match not found.");
    }
    matchingPolicy.requirePair(match.getUserAId(), match.getUserBId());
    return toMatchView(match, userId, badges.forUser(counterpartUserId(match,userId)));
  }

  private MatchView toMatchView(MatchProposal match, Long userId, com.oao.backend.user.service.VerificationBadgeService.Badges verified) {
    Long counterpartUserId = counterpartUserId(match, userId);
    UserAccount counterpart =
        userAccountRepository
            .findById(counterpartUserId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Matched user not found."));
    UserProfile profile = userProfileRepository.findByUserId(counterpartUserId).orElse(null);
    var matchingProfile = matchingProfileRepository.findByUserId(counterpartUserId).orElse(null);
    String intro = matchingProfile == null ? null : matchingProfile.getJobIntro();
    List<ProfilePhoto> photos =
        profilePhotoRepository.findByUserIdOrderByDisplayOrderAscIdAsc(counterpartUserId).stream()
            .filter(
                photo ->
                    photo.getReviewStatus()
                        == com.oao.backend.user.domain.UserVerificationDocument.ReviewStatus
                            .APPROVED)
            .toList();
    String photoUrl = photos.stream().findFirst().map(ProfilePhoto::getImageUrl).orElse(null);
    int viewerPhotoCount =
        (int)
            profilePhotoRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId).stream()
                .filter(
                    p ->
                        p.getReviewStatus()
                            == com.oao.backend.user.domain.UserVerificationDocument.ReviewStatus
                                .APPROVED)
                .count();
    boolean hasLiked = hasActiveInterest(userId, counterpartUserId, InterestType.LIKE);
    boolean hasExpressed = hasActiveInterest(userId, counterpartUserId, InterestType.EXPRESS);

    return new MatchView(
        match.getId(),
        match.getStatus().name(),
        currentUserDecision(match, userId).name(),
        counterpartDecision(match, userId).name(),
        match.getExpiresAt(),
        match.getMatchedAt(),
        match.getMatchedReason(),
        match.isSGradeGuaranteed(),
        counterpart.getId(),
        counterpart.getName(),
        age(counterpart.getBirthDate()),
        counterpart.getGender() == null ? null : counterpart.getGender().name(),
        null,
        profile == null ? null : profile.getJob(),
        profile == null ? null : profile.getActivityRegion(),
        profile == null ? null : profile.getHeightCm(),
        profile == null ? null : profile.getMbti(),
        profile == null ? null : profile.getEducation(),
        profile == null ? null : profile.getSmokingStatus(),
        profile == null ? null : profile.getDrinkingStatus(),
        profile == null ? null : profile.getReligion(),
        intro,
        matchingProfile == null ? null : matchingProfile.getDatingStyle(),
        matchingProfile == null ? List.of() : matchingProfile.getPersonalityKeywords(),
        photoUrl,
        photoViews(photos),
        viewerPhotoCount,
        hobbyViews(userId, counterpartUserId),
        hasLiked,
        hasExpressed,
        chatAvailability.available(match.getId(), userId, counterpartUserId), verified.employmentVerified(), verified.educationVerified());
  }

  private boolean hasActiveInterest(
      Long senderUserId, Long receiverUserId, InterestType interestType) {
    return userInterestRepository.findBySenderUserIdAndReceiverUserIdAndStatusAndInterestType(
        senderUserId, receiverUserId, InterestStatus.ACTIVE, interestType).filter(consent::active).isPresent();
  }

  private List<MatchHobbyView> hobbyViews(Long userId, Long counterpartUserId) {
    Set<Long> myHobbyIds =
        userHobbyRepository.findByIdUserId(userId).stream()
            .map(userHobby -> userHobby.getId().getHobbyId())
            .collect(Collectors.toSet());
    List<Long> counterpartHobbyIds =
        userHobbyRepository.findByIdUserId(counterpartUserId).stream()
            .map(userHobby -> userHobby.getId().getHobbyId())
            .sorted()
            .toList();
    if (counterpartHobbyIds.isEmpty()) {
      return List.of();
    }

    Map<Long, Hobby> hobbyById =
        hobbyRepository.findAllById(counterpartHobbyIds).stream()
            .collect(Collectors.toMap(Hobby::getId, Function.identity()));
    return counterpartHobbyIds.stream()
        .map(hobbyById::get)
        .filter(hobby -> hobby != null)
        .map(hobby -> new MatchHobbyView(hobby.getName(), myHobbyIds.contains(hobby.getId())))
        .toList();
  }

  private List<MatchPhotoView> photoViews(List<ProfilePhoto> photos) {
    return photos.stream()
        .filter(photo -> photo.getImageUrl() != null && !photo.getImageUrl().isBlank())
        .map(photo -> new MatchPhotoView(photo.getImageUrl(), photo.getDisplayOrder()))
        .toList();
  }

  private Comparator<MatchProposal> matchComparator() {
    return Comparator.comparing(
            MatchProposal::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(MatchProposal::getMatchedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(MatchProposal::getId);
  }

  private Comparator<MatchProposal> completedMatchComparator() {
    return Comparator.comparing(
            MatchProposal::getMatchedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(MatchProposal::getId, Comparator.reverseOrder());
  }

  private boolean isParticipant(MatchProposal match, Long userId) {
    return match.getUserAId().equals(userId) || match.getUserBId().equals(userId);
  }

  private Long counterpartUserId(MatchProposal match, Long userId) {
    if (match.getUserAId().equals(userId)) {
      return match.getUserBId();
    }
    if (match.getUserBId().equals(userId)) {
      return match.getUserAId();
    }
    throw new BusinessException(HttpStatus.FORBIDDEN, "User is not a participant of this match.");
  }

  private MatchDecision currentUserDecision(MatchProposal match, Long userId) {
    return match.getUserAId().equals(userId) ? match.getUserADecision() : match.getUserBDecision();
  }

  private MatchDecision counterpartDecision(MatchProposal match, Long userId) {
    return match.getUserAId().equals(userId) ? match.getUserBDecision() : match.getUserADecision();
  }

  private Integer age(LocalDate birthDate) {
    if (birthDate == null) {
      return null;
    }
    int age = Period.between(birthDate, LocalDate.now()).getYears();
    return age > 0 ? age : null;
  }

  public record MatchView(
      Long matchId,
      String status,
      String myDecision,
      String counterpartDecision,
      Instant expiresAt,
      Instant matchedAt,
      String matchedReason,
      boolean sGradeGuaranteed,
      Long profileUserId,
      String name,
      Integer age,
      String gender,
      String grade,
      String job,
      String activityRegion,
      Integer heightCm,
      String mbti,
      String education,
      String smokingStatus,
      String drinkingStatus,
      String religion,
      String intro,
      String datingStyle,
      List<String> personalityKeywords,
      String photoUrl,
      List<MatchPhotoView> photos,
      int viewerPhotoCount,
      List<MatchHobbyView> hobbies,
      boolean hasLiked,
      boolean hasExpressed,
      boolean chatAvailable, boolean employmentVerified, boolean educationVerified) {}

  public record MatchPhotoView(String photoUrl, Integer displayOrder) {}

  public record MatchHobbyView(String name, boolean common) {}
}
