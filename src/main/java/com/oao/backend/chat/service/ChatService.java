package com.oao.backend.chat.service;

import com.oao.backend.chat.domain.ChatMessage;
import com.oao.backend.chat.domain.ChatRoom;
import com.oao.backend.chat.domain.ChatRoom.ChatRoomStatus;
import com.oao.backend.chat.domain.ChatRoomMemberState;
import com.oao.backend.chat.repository.ChatMessageRepository;
import com.oao.backend.chat.repository.ChatRoomMemberStateRepository;
import com.oao.backend.chat.repository.ChatRoomRepository;
import com.oao.backend.common.BusinessException;
import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.repository.MatchProposalRepository;
import com.oao.backend.notification.service.AppNotificationService;
import com.oao.backend.user.domain.ProfilePhoto;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.ProfilePhotoRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
	private static final int MAX_MESSAGE_LENGTH = 1_000;

	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomMemberStateRepository chatRoomMemberStateRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final MatchProposalRepository matchProposalRepository;
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final ProfilePhotoRepository profilePhotoRepository;
	private final AppNotificationService notificationService;

	public ChatService(
		ChatRoomRepository chatRoomRepository,
		ChatRoomMemberStateRepository chatRoomMemberStateRepository,
		ChatMessageRepository chatMessageRepository,
		MatchProposalRepository matchProposalRepository,
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		ProfilePhotoRepository profilePhotoRepository,
		AppNotificationService notificationService
	) {
		this.chatRoomRepository = chatRoomRepository;
		this.chatRoomMemberStateRepository = chatRoomMemberStateRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.matchProposalRepository = matchProposalRepository;
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.profilePhotoRepository = profilePhotoRepository;
		this.notificationService = notificationService;
	}

	@Transactional(readOnly = true)
	public List<ChatRoomSummaryView> rooms(Long userId) {
		return chatRoomRepository.findActiveRoomsByParticipant(userId, ChatRoomStatus.ACTIVE).stream()
			.filter(room -> !hasLeftRoom(room, userId))
			.map(room -> toSummaryView(room, userId))
			.sorted(Comparator.comparing(ChatRoomSummaryView::sortAt).reversed())
			.toList();
	}

	@Transactional
	public ChatRoomDetailView findOrOpenByMatch(Long matchId, Long userId) {
		MatchProposal match = findAcceptedParticipantMatch(matchId, userId);
		ChatRoom room = chatRoomRepository.findByMatchId(match.getId())
			.orElseGet(() -> chatRoomRepository.save(ChatRoom.open(match.getId())));
		ensureNotLeft(room, userId);
		return toDetailView(room, match, userId);
	}

	@Transactional(readOnly = true)
	public ChatRoomDetailView room(Long roomId, Long userId) {
		ChatRoom room = findRoom(roomId);
		MatchProposal match = findAcceptedParticipantMatch(room.getMatchId(), userId);
		ensureNotLeft(room, userId);
		return toDetailView(room, match, userId);
	}

	@Transactional(readOnly = true)
	public List<ChatMessageView> messages(Long roomId, Long userId) {
		ChatRoom room = findRoom(roomId);
		findAcceptedParticipantMatch(room.getMatchId(), userId);
		ensureNotLeft(room, userId);
		return chatMessageRepository.findByChatRoomIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(roomId).stream()
			.map(this::toMessageView)
			.toList();
	}

	@Transactional
	public ChatMessageView sendTextMessage(Long roomId, Long senderUserId, String content) {
		ChatRoom room = findRoom(roomId);
		MatchProposal match = findAcceptedParticipantMatch(room.getMatchId(), senderUserId);
		ensureNotLeft(room, senderUserId);
		String normalizedContent = normalizeMessageContent(content);
		ChatMessage message = chatMessageRepository.save(ChatMessage.text(room.getId(), senderUserId, normalizedContent));
		notificationService.notifyMessageReceived(room.getId(), message.getId(), counterpartUserId(match, senderUserId), senderUserId);
		return toMessageView(message);
	}

	@Transactional
	public void leave(Long roomId, Long userId) {
		ChatRoom room = findRoom(roomId);
		findAcceptedParticipantMatch(room.getMatchId(), userId);
		chatRoomMemberStateRepository.findByChatRoomIdAndUserId(room.getId(), userId)
			.ifPresentOrElse(
				ChatRoomMemberState::leave,
				() -> chatRoomMemberStateRepository.save(ChatRoomMemberState.leave(room.getId(), userId))
			);
	}

	private ChatRoom findRoom(Long roomId) {
		return chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Chat room not found."));
	}

	private MatchProposal findAcceptedParticipantMatch(Long matchId, Long userId) {
		MatchProposal match = matchProposalRepository.findById(matchId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Match not found."));
		if (!isParticipant(match, userId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "Only participants can access this chat room.");
		}
		if (!match.isAccepted()) {
			throw new BusinessException(HttpStatus.CONFLICT, "Chat room is available only after matching is completed.");
		}
		return match;
	}

	private ChatRoomSummaryView toSummaryView(ChatRoom room, Long userId) {
		MatchProposal match = matchProposalRepository.findById(room.getMatchId())
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Match not found."));
		ChatMessage lastMessage = chatMessageRepository
			.findFirstByChatRoomIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(room.getId())
			.orElse(null);
		ProfileSummaryView counterpart = counterpartSummary(match, userId);
		Instant lastMessageAt = lastMessage == null ? null : lastMessage.getCreatedAt();
		return new ChatRoomSummaryView(
			room.getId(),
			match.getId(),
			counterpart,
			lastMessage == null ? null : lastMessage.getContent(),
			lastMessageAt,
			lastMessageAt == null ? room.getCreatedAt() : lastMessageAt
		);
	}

	private ChatRoomDetailView toDetailView(ChatRoom room, MatchProposal match, Long userId) {
		return new ChatRoomDetailView(room.getId(), match.getId(), userId, counterpartSummary(match, userId));
	}

	private ProfileSummaryView counterpartSummary(MatchProposal match, Long userId) {
		Long counterpartUserId = counterpartUserId(match, userId);
		UserAccount user = userAccountRepository.findById(counterpartUserId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found."));
		UserProfile profile = userProfileRepository.findByUserId(counterpartUserId).orElse(null);
		ProfilePhoto photo = profilePhotoRepository.findFirstByUserIdOrderByDisplayOrderAscIdAsc(counterpartUserId)
			.orElse(null);
		return new ProfileSummaryView(
			counterpartUserId,
			user.getName(),
			age(user.getBirthDate()),
			user.getGender() == null ? null : user.getGender().name(),
			profile == null ? null : profile.getJob(),
			profile == null ? null : profile.getActivityRegion(),
			photo == null ? null : photo.getImageUrl()
		);
	}

	private Long counterpartUserId(MatchProposal match, Long userId) {
		return match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
	}

	private ChatMessageView toMessageView(ChatMessage message) {
		return new ChatMessageView(
			message.getId(),
			message.getChatRoomId(),
			message.getSenderUserId(),
			message.getMessageType(),
			message.getContent(),
			message.getCreatedAt(),
			message.getReadAt()
		);
	}

	private boolean isParticipant(MatchProposal match, Long userId) {
		return match.getUserAId().equals(userId) || match.getUserBId().equals(userId);
	}

	private boolean hasLeftRoom(ChatRoom room, Long userId) {
		return chatRoomMemberStateRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNotNull(room.getId(), userId);
	}

	private void ensureNotLeft(ChatRoom room, Long userId) {
		if (hasLeftRoom(room, userId)) {
			throw new BusinessException(HttpStatus.GONE, "Chat room was left.");
		}
	}

	private String normalizeMessageContent(String content) {
		String normalized = content == null ? "" : content.trim();
		if (normalized.isBlank()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Message content is required.");
		}
		if (normalized.length() > MAX_MESSAGE_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Message content is too long.");
		}
		return normalized;
	}

	private Integer age(LocalDate birthDate) {
		if (birthDate == null) {
			return null;
		}
		int age = Period.between(birthDate, LocalDate.now(DEFAULT_ZONE)).getYears();
		return age > 0 ? age : null;
	}

	public record ChatRoomSummaryView(
		Long roomId,
		Long matchId,
		ProfileSummaryView counterpart,
		String lastMessage,
		Instant lastMessageAt,
		Instant sortAt
	) {
	}

	public record ChatRoomDetailView(
		Long roomId,
		Long matchId,
		Long viewerUserId,
		ProfileSummaryView counterpart
	) {
	}

	public record ChatMessageView(
		Long messageId,
		Long roomId,
		Long senderUserId,
		String messageType,
		String content,
		Instant createdAt,
		Instant readAt
	) {
	}

	public record ProfileSummaryView(
		Long userId,
		String name,
		Integer age,
		String gender,
		String job,
		String activityRegion,
		String photoUrl
	) {
	}
}
