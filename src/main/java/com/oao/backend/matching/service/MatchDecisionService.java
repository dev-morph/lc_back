package com.oao.backend.matching.service;

import com.oao.backend.chat.domain.ChatRoom;
import com.oao.backend.chat.repository.ChatRoomRepository;
import com.oao.backend.common.BusinessException;
import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.repository.MatchProposalRepository;
import com.oao.backend.notification.service.AppNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchDecisionService {

	private final MatchProposalRepository matchProposalRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final AppNotificationService notificationService;

	public MatchDecisionService(
		MatchProposalRepository matchProposalRepository,
		ChatRoomRepository chatRoomRepository,
		AppNotificationService notificationService
	) {
		this.matchProposalRepository = matchProposalRepository;
		this.chatRoomRepository = chatRoomRepository;
		this.notificationService = notificationService;
	}

	@Transactional
	public MatchDecisionResult accept(Long matchId, Long userId) {
		MatchProposal match = findMatch(matchId);
		match.accept(userId);
		boolean chatRoomCreated = false;
		if (match.isAccepted() && !chatRoomRepository.existsByMatchId(match.getId())) {
			chatRoomRepository.save(ChatRoom.open(match.getId()));
			chatRoomCreated = true;
		}
		if (match.isAccepted()) {
			notificationService.notifyMatchCompleted(match.getId(), match.getUserAId(), match.getUserBId());
		}
		return new MatchDecisionResult(match.getId(), match.getStatus().name(), chatRoomCreated);
	}

	@Transactional
	public MatchDecisionResult reject(Long matchId, Long userId) {
		MatchProposal match = findMatch(matchId);
		match.reject(userId);
		return new MatchDecisionResult(match.getId(), match.getStatus().name(), false);
	}

	private MatchProposal findMatch(Long matchId) {
		return matchProposalRepository.findById(matchId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Match not found."));
	}

	public record MatchDecisionResult(Long matchId, String status, boolean chatRoomCreated) {
	}
}
