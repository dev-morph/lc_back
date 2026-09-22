package com.oao.backend.interest.service;

import com.oao.backend.chat.domain.ChatRoom;
import com.oao.backend.chat.repository.ChatRoomRepository;
import com.oao.backend.common.BusinessException;
import com.oao.backend.heart.domain.HeartTransaction;
import com.oao.backend.heart.domain.HeartWallet;
import com.oao.backend.heart.repository.HeartTransactionRepository;
import com.oao.backend.heart.repository.HeartWalletRepository;
import com.oao.backend.interest.domain.UserInterest;
import com.oao.backend.interest.domain.UserInterest.ExpressDecision;
import com.oao.backend.interest.domain.UserInterest.InterestStatus;
import com.oao.backend.interest.domain.UserInterest.InterestType;
import com.oao.backend.interest.repository.UserInterestRepository;
import com.oao.backend.matching.repository.MatchingProfileRepository;
import com.oao.backend.notification.service.AppNotificationService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInterestService {
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
  private final com.oao.backend.matching.service.MatchingPolicyService matchingPolicy;
  private final com.oao.backend.common.UserLocks userLocks;
  private final com.oao.backend.matching.service.MatchConnectionService connections;
  private final InterestConsentPolicy consent;
  private final com.oao.backend.chat.service.ChatAvailabilityService chatAvailability;

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
  private static final int PRIVATE_ACCEPT_HEART_COST = 4;
  private static final int PUBLIC_ACCEPT_HEART_COST = 5;
  private static final int MAX_EXPRESS_MESSAGE_LENGTH = 100;

  private final UserInterestRepository interestRepository;
  private final UserAccountRepository userAccountRepository;
  private final UserProfileRepository userProfileRepository;
  private final ProfilePhotoRepository profilePhotoRepository;
  private final UserHobbyRepository userHobbyRepository;
  private final HobbyRepository hobbyRepository;
  private final MatchingProfileRepository matchingProfileRepository;
  private final HeartWalletRepository heartWalletRepository;
  private final HeartTransactionRepository heartTransactionRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final AppNotificationService notificationService;

  public UserInterestService(
      com.oao.backend.matching.service.MatchingPolicyService matchingPolicy,
      com.oao.backend.common.UserLocks userLocks,
      com.oao.backend.matching.service.MatchConnectionService connections,
      InterestConsentPolicy consent,
      com.oao.backend.chat.service.ChatAvailabilityService chatAvailability,
      UserInterestRepository interestRepository,
      UserAccountRepository userAccountRepository,
      UserProfileRepository userProfileRepository,
      ProfilePhotoRepository profilePhotoRepository,
      UserHobbyRepository userHobbyRepository,
      HobbyRepository hobbyRepository,
      MatchingProfileRepository matchingProfileRepository,
      HeartWalletRepository heartWalletRepository,
      HeartTransactionRepository heartTransactionRepository,
      ChatRoomRepository chatRoomRepository,
      AppNotificationService notificationService) {
    this.matchingPolicy = matchingPolicy;
    this.userLocks = userLocks;
    this.connections = connections;
    this.consent = consent;
    this.chatAvailability = chatAvailability;
    this.interestRepository = interestRepository;
    this.userAccountRepository = userAccountRepository;
    this.userProfileRepository = userProfileRepository;
    this.profilePhotoRepository = profilePhotoRepository;
    this.userHobbyRepository = userHobbyRepository;
    this.hobbyRepository = hobbyRepository;
    this.matchingProfileRepository = matchingProfileRepository;
    this.heartWalletRepository = heartWalletRepository;
    this.heartTransactionRepository = heartTransactionRepository;
    this.chatRoomRepository = chatRoomRepository;
    this.notificationService = notificationService;
  }

  @Transactional(readOnly = true)
  public List<InterestProfileView> received(Long userId) {
    return interestRepository
        .findByReceiverUserIdAndStatusAndInterestTypeOrderByCreatedAtDescIdDesc(
            userId, InterestStatus.ACTIVE, InterestType.EXPRESS)
        .stream()
        .filter(this::isVisiblePendingInterest)
        .filter(
            interest ->
                matchingPolicy.pairAllowed(
                    interest.getSenderUserId(), interest.getReceiverUserId()))
        .map(interest -> toProfileView(interest, interest.getSenderUserId()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<InterestProfileView> sent(Long userId) {
    return interestRepository
        .findBySenderUserIdAndStatusOrderByCreatedAtDescIdDesc(userId, InterestStatus.ACTIVE)
        .stream()
        .filter(this::isVisiblePendingInterest)
        .filter(
            interest ->
                matchingPolicy.pairAllowed(
                    interest.getSenderUserId(), interest.getReceiverUserId()))
        .map(interest -> toProfileView(interest, interest.getReceiverUserId()))
        .toList();
  }

  @Transactional
  public InterestActionResult send(
      Long senderUserId, Long receiverUserId, InterestType interestType, String message) {
    userLocks.pair(senderUserId, receiverUserId);
    matchingPolicy.requirePair(senderUserId, receiverUserId);
    if (!matchingPolicy.eligible(senderUserId) || !matchingPolicy.eligible(receiverUserId))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "매칭 설정과 사진 심사를 먼저 완료해주세요.");
    if (senderUserId.equals(receiverUserId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "Cannot send interest to yourself.");
    }
    if (!userAccountRepository.existsById(receiverUserId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "Receiver user not found.");
    }

    var activeRoom = connections.activeRoom(senderUserId, receiverUserId);
    if (activeRoom != null) {
      connections.connect(senderUserId, receiverUserId, "서로 수락했어요.");
      return new InterestActionResult(null, 0, currentBalance(senderUserId), false,
          activeRoom.getMatchId(), activeRoom.getId());
    }
    int heartCost = heartCost(interestType);
    String expressMessage = normalizeExpressMessage(interestType, message);
    UserInterest interest =
        interestRepository
            .findBySenderUserIdAndReceiverUserId(senderUserId, receiverUserId)
            .orElse(null);
    HeartWallet wallet =
        heartWalletRepository
            .findByUserId(senderUserId)
            .orElseGet(() -> heartWalletRepository.save(HeartWallet.create(senderUserId)));

    if (interest != null
        && consent.active(interest)
        && interest.getInterestType() == interestType) {
      boolean chatRoomCreated =
          maybeOpenMutualInterestChatRoom(interest);
      return new InterestActionResult(
          toProfileView(interest, receiverUserId), 0, wallet.getBalance(), chatRoomCreated);
    }
    if (interest != null
        && consent.active(interest)
        && interest.getInterestType() == InterestType.EXPRESS
        && interestType == InterestType.LIKE) {
      return new InterestActionResult(
          toProfileView(interest, receiverUserId), 0, wallet.getBalance(), false);
    }

    wallet.spend(heartCost);
    if (interest == null) {
      interest =
          interestRepository.save(
              UserInterest.send(
                  senderUserId, receiverUserId, interestType, heartCost, expressMessage));
    }
    if (interest.getId() != null) {
      interest.activate(interestType, heartCost, expressMessage);
    }

    entityManager.flush();
    boolean chatRoomCreated =
        maybeOpenMutualInterestChatRoom(interest);
    heartTransactionRepository.save(
        HeartTransaction.spend(
            senderUserId,
            heartCost,
            wallet.getBalance(),
            interestType == InterestType.LIKE ? "INTEREST_LIKE" : "INTEREST_EXPRESS",
            interest.getId()));
    if (interestType == InterestType.EXPRESS && !interest.isChatRoomCreated()) {
      notificationService.notifyExpressReceived(interest.getId(), receiverUserId, senderUserId);
    }
    return new InterestActionResult(
        toProfileView(interest, receiverUserId), heartCost, wallet.getBalance(), chatRoomCreated);
  }

  @Transactional
  public InterestActionResult acceptExpress(Long interestId, Long receiverUserId) {
    UserInterest interest = findInterest(interestId);
    userLocks.pair(interest.getSenderUserId(), interest.getReceiverUserId());
    entityManager.refresh(interest, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    matchingPolicy.requirePair(interest.getSenderUserId(), interest.getReceiverUserId());
    matchingPolicy.requireConnectionAge(interest.getSenderUserId(), interest.getReceiverUserId());
    if (!interest.getReceiverUserId().equals(receiverUserId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "Only receiver can accept express interest.");
    }
    if (interest.getInterestType() != InterestType.EXPRESS) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "Only express interest can be accepted.");
    }
    if (!interest.isChatRoomCreated() && !validIntent(interest))
      throw new BusinessException(HttpStatus.CONFLICT, "이미 종료된 수락이에요.");
    interest.acceptExpress();
    boolean chatRoomCreated = openInterestChatRoom(interest, "공개 수락을 받아들였어요.");
    return new InterestActionResult(
        toProfileView(interest, interest.getSenderUserId()),
        0,
        currentBalance(receiverUserId),
        chatRoomCreated);
  }

  @Transactional
  public InterestActionResult rejectExpress(Long interestId, Long receiverUserId) {
    UserInterest interest = findInterest(interestId);
    userLocks.pair(interest.getSenderUserId(), interest.getReceiverUserId());
    entityManager.refresh(interest, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    matchingPolicy.requirePair(interest.getSenderUserId(), interest.getReceiverUserId());
    if (!interest.getReceiverUserId().equals(receiverUserId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "Only receiver can reject express interest.");
    }
    if (interest.getInterestType() != InterestType.EXPRESS) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "Only express interest can be rejected.");
    }
    if (interest.isChatRoomCreated())
      throw new BusinessException(HttpStatus.CONFLICT, "이미 연결된 대화예요. 대화방에서 나가기를 이용해주세요.");
    interest.rejectExpress();
    return new InterestActionResult(
        toProfileView(interest, interest.getSenderUserId()),
        0,
        currentBalance(receiverUserId),
        false);
  }

  @Transactional(readOnly = true)
  public InterestProfileDetailView profileDetail(Long userId, Long profileUserId) {
    matchingPolicy.requirePair(userId, profileUserId);
    UserInterest incomingInterest =
        interestRepository
            .findBySenderUserIdAndReceiverUserIdAndStatus(
                profileUserId, userId, InterestStatus.ACTIVE)
            .filter(interest -> interest.getInterestType() == InterestType.EXPRESS)
            .orElse(null);
    UserInterest outgoingInterest =
        interestRepository
            .findBySenderUserIdAndReceiverUserIdAndStatus(
                userId, profileUserId, InterestStatus.ACTIVE)
            .orElse(null);
    UserInterest contextInterest = incomingInterest != null && consent.active(incomingInterest)
        ? incomingInterest : outgoingInterest != null ? outgoingInterest : incomingInterest;
    if (contextInterest == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "Interest profile not found.");
    }

    UserAccount user =
        userAccountRepository
            .findById(profileUserId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found."));
    UserProfile profile = userProfileRepository.findByUserId(profileUserId).orElse(null);
    ProfilePhoto photo =
        profilePhotoRepository
            .findFirstByUserIdAndReviewStatusOrderByDisplayOrderAscIdAsc(
                profileUserId,
                com.oao.backend.user.domain.UserVerificationDocument.ReviewStatus.APPROVED)
            .orElse(null);
    String intro =
        matchingProfileRepository
            .findByUserId(profileUserId)
            .map(matchingProfile -> matchingProfile.getJobIntro())
            .orElse(null);
    return new InterestProfileDetailView(
        contextInterest.getMatchId(),
        contextInterest.isChatRoomCreated() ? "ACCEPTED" : consent.active(contextInterest) ? "INTEREST" : "CLOSED",
        "NONE",
        "NONE",
        null,
        null,
        null,
        false,
        user.getId(),
        user.getName(),
        age(user.getBirthDate()),
        user.getGender() == null ? null : user.getGender().name(),
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
        photo == null ? null : photo.getImageUrl(),
        hobbyViews(userId, profileUserId),
        outgoingInterest != null && consent.active(outgoingInterest) && outgoingInterest.getInterestType() == InterestType.LIKE,
        outgoingInterest != null && consent.active(outgoingInterest) && outgoingInterest.getInterestType() == InterestType.EXPRESS,
        contextInterest.getId(),
        contextInterest.getInterestType().name(),
        contextInterest.getHeartCost(),
        contextInterest.isNotificationTarget(),
        contextInterest.getExpressDecision().name(),
        contextInterest.isChatRoomCreated(),
        incomingInterest != null && consent.active(incomingInterest),
        incomingInterest != null && consent.active(incomingInterest)
            ? incomingInterest.getExpressMessage()
            : null,
        chatAvailability.available(contextInterest.getMatchId(), userId, profileUserId));
  }

  private boolean isVisiblePendingInterest(UserInterest interest) {
    return consent.active(interest);
  }

  private List<InterestHobbyView> hobbyViews(Long userId, Long profileUserId) {
    Set<Long> myHobbyIds =
        userHobbyRepository.findByIdUserId(userId).stream()
            .map(userHobby -> userHobby.getId().getHobbyId())
            .collect(Collectors.toSet());
    List<Long> targetHobbyIds =
        userHobbyRepository.findByIdUserId(profileUserId).stream()
            .map(userHobby -> userHobby.getId().getHobbyId())
            .sorted()
            .toList();
    if (targetHobbyIds.isEmpty()) {
      return List.of();
    }

    Map<Long, com.oao.backend.user.domain.Hobby> hobbyById =
        hobbyRepository.findAllById(targetHobbyIds).stream()
            .collect(
                Collectors.toMap(com.oao.backend.user.domain.Hobby::getId, Function.identity()));
    return targetHobbyIds.stream()
        .map(hobbyById::get)
        .filter(hobby -> hobby != null)
        .map(hobby -> new InterestHobbyView(hobby.getName(), myHobbyIds.contains(hobby.getId())))
        .toList();
  }

  private UserInterest findInterest(Long interestId) {
    return interestRepository
        .findById(interestId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Interest not found."));
  }

  private int heartCost(InterestType interestType) {
    return interestType == InterestType.EXPRESS
        ? PUBLIC_ACCEPT_HEART_COST
        : PRIVATE_ACCEPT_HEART_COST;
  }

  private String normalizeExpressMessage(InterestType interestType, String message) {
    if (interestType != InterestType.EXPRESS) {
      return null;
    }
    String normalized = message == null ? "" : message.trim();
    if (normalized.length() > MAX_EXPRESS_MESSAGE_LENGTH) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "Express message is too long.");
    }
    return normalized.isBlank() ? null : normalized;
  }

  private boolean maybeOpenMutualInterestChatRoom(UserInterest interest) {
    if (!validIntent(interest)) return false;
    return interestRepository.findBySenderUserIdAndReceiverUserIdAndStatus(
        interest.getReceiverUserId(), interest.getSenderUserId(), InterestStatus.ACTIVE)
        .filter(this::validIntent)
        .map(reverse -> openInterestChatRoom(interest, "서로 관심을 표현했어요."))
        .orElse(false);
  }

  private boolean validIntent(UserInterest interest) {
    return consent.active(interest);
  }

  private boolean openInterestChatRoom(UserInterest interest, String reason) {
    if (interest.isChatRoomCreated()) return false;
    var connected = connections.connect(interest.getSenderUserId(), interest.getReceiverUserId(), reason);
    interest.markChatRoomCreated(connected.matchId());
    interestRepository.findBySenderUserIdAndReceiverUserIdAndStatus(
        interest.getReceiverUserId(), interest.getSenderUserId(), InterestStatus.ACTIVE)
        .filter(reverse -> reverse.getExpressDecision() != ExpressDecision.REJECTED)
        .ifPresent(reverse -> reverse.markChatRoomCreated(connected.matchId()));
    return connected.created();
  }

  private int currentBalance(Long userId) {
    return heartWalletRepository.findByUserId(userId).map(HeartWallet::getBalance).orElse(0);
  }

  private InterestProfileView toProfileView(UserInterest interest, Long profileUserId) {
    UserAccount user =
        userAccountRepository
            .findById(profileUserId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found."));
    UserProfile profile = userProfileRepository.findByUserId(profileUserId).orElse(null);
    ProfilePhoto photo =
        profilePhotoRepository
            .findFirstByUserIdAndReviewStatusOrderByDisplayOrderAscIdAsc(
                profileUserId,
                com.oao.backend.user.domain.UserVerificationDocument.ReviewStatus.APPROVED)
            .orElse(null);
    return new InterestProfileView(
        interest.getId(),
        profileUserId,
        user.getName(),
        age(user.getBirthDate()),
        user.getGender() == null ? null : user.getGender().name(),
        profile == null ? null : profile.getJob(),
        profile == null ? null : profile.getActivityRegion(),
        photo == null ? null : photo.getImageUrl(),
        interest.getCreatedAt(),
        interest.getInterestType().name(),
        interest.getHeartCost(),
        interest.isNotificationTarget(),
        interest.getExpressDecision().name(),
        interest.isChatRoomCreated(), interest.getMatchId(),
        interest.getMatchId() == null ? null : chatRoomRepository.findByMatchId(interest.getMatchId()).map(ChatRoom::getId).orElse(null));
  }

  private Integer age(LocalDate birthDate) {
    if (birthDate == null) {
      return null;
    }
    int age = Period.between(birthDate, LocalDate.now(DEFAULT_ZONE)).getYears();
    return age > 0 ? age : null;
  }

  public record InterestProfileView(
      Long interestId,
      Long userId,
      String name,
      Integer age,
      String gender,
      String job,
      String activityRegion,
      String photoUrl,
      Instant interestedAt,
      String interestType,
      int heartCost,
      boolean notificationTarget,
      String expressDecision,
      boolean chatRoomCreated,
      Long matchId,
      Long chatRoomId) {}

  public record InterestActionResult(
      InterestProfileView profile, int spentHearts, int remainingHearts, boolean chatRoomCreated,
      Long matchId, Long chatRoomId) {
    public InterestActionResult(InterestProfileView profile, int spentHearts, int remainingHearts, boolean created) {
      this(profile, spentHearts, remainingHearts, created, profile.matchId(), profile.chatRoomId());
    }
  }

  public record InterestProfileDetailView(
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
      String photoUrl,
      List<InterestHobbyView> hobbies,
      boolean hasLiked,
      boolean hasExpressed,
      Long interestId,
      String interestType,
      int heartCost,
      boolean notificationTarget,
      String expressDecision,
      boolean chatRoomCreated,
      boolean receivedInterest,
      String receivedExpressMessage,
      boolean chatAvailable) {}

  public record InterestHobbyView(String name, boolean common) {}
}
