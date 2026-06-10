package com.oao.backend.interest.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_interest")
public class UserInterest extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long senderUserId;
	private Long receiverUserId;

	@Enumerated(EnumType.STRING)
	private InterestStatus status = InterestStatus.ACTIVE;

	@Enumerated(EnumType.STRING)
	private InterestType interestType = InterestType.LIKE;

	private int heartCost = 1;
	private boolean notificationTarget;
	private String expressMessage;

	@Enumerated(EnumType.STRING)
	private ExpressDecision expressDecision = ExpressDecision.PENDING;

	private Instant expressDecidedAt;
	private boolean chatRoomCreated;
	private Long matchId;

	protected UserInterest() {
	}

	public static UserInterest send(
		Long senderUserId,
		Long receiverUserId,
		InterestType interestType,
		int heartCost,
		String expressMessage
	) {
		UserInterest interest = new UserInterest();
		interest.senderUserId = senderUserId;
		interest.receiverUserId = receiverUserId;
		interest.status = InterestStatus.ACTIVE;
		interest.updateAction(interestType, heartCost, expressMessage);
		return interest;
	}

	public void activate(InterestType interestType, int heartCost, String expressMessage) {
		this.status = InterestStatus.ACTIVE;
		updateAction(interestType, heartCost, expressMessage);
	}

	private void updateAction(InterestType interestType, int heartCost, String expressMessage) {
		this.interestType = interestType;
		this.heartCost = heartCost;
		this.notificationTarget = interestType == InterestType.EXPRESS;
		this.expressMessage = interestType == InterestType.EXPRESS ? expressMessage : null;
		this.expressDecision = ExpressDecision.PENDING;
		this.expressDecidedAt = null;
	}

	public void acceptExpress() {
		this.expressDecision = ExpressDecision.ACCEPTED;
		this.expressDecidedAt = Instant.now();
	}

	public void rejectExpress() {
		this.expressDecision = ExpressDecision.REJECTED;
		this.expressDecidedAt = Instant.now();
	}

	public void markChatRoomCreated(Long matchId) {
		this.chatRoomCreated = true;
		this.matchId = matchId;
	}

	public Long getId() {
		return id;
	}

	public Long getSenderUserId() {
		return senderUserId;
	}

	public Long getReceiverUserId() {
		return receiverUserId;
	}

	public InterestStatus getStatus() {
		return status;
	}

	public InterestType getInterestType() {
		return interestType;
	}

	public int getHeartCost() {
		return heartCost;
	}

	public boolean isNotificationTarget() {
		return notificationTarget;
	}

	public String getExpressMessage() {
		return expressMessage;
	}

	public ExpressDecision getExpressDecision() {
		return expressDecision;
	}

	public boolean isChatRoomCreated() {
		return chatRoomCreated;
	}

	public Long getMatchId() {
		return matchId;
	}

	public enum InterestStatus {
		ACTIVE,
		CANCELED
	}

	public enum InterestType {
		LIKE,
		EXPRESS
	}

	public enum ExpressDecision {
		PENDING,
		ACCEPTED,
		REJECTED
	}
}
