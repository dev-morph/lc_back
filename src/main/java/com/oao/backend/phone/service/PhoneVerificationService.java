package com.oao.backend.phone.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.phone.domain.PhoneVerificationCode;
import com.oao.backend.phone.domain.PhoneVerificationCode.PhoneVerificationStatus;
import com.oao.backend.phone.repository.PhoneVerificationCodeRepository;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneVerificationService {

	private static final Duration CODE_TTL = Duration.ofMinutes(3);
	private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
	private static final int MAX_ATTEMPTS = 5;
	private static final int DAILY_SEND_LIMIT = 5;

	private final PhoneVerificationCodeRepository verificationRepository;
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final VerificationMessageSender messageSender;
	private final SecureRandom secureRandom = new SecureRandom();
	private final boolean devCodeResponseEnabled;

	public PhoneVerificationService(
		PhoneVerificationCodeRepository verificationRepository,
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		VerificationMessageSender messageSender,
		@Value("${oao.verification.message.dev-code-response-enabled:true}") boolean devCodeResponseEnabled
	) {
		this.verificationRepository = verificationRepository;
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.messageSender = messageSender;
		this.devCodeResponseEnabled = devCodeResponseEnabled;
	}

	@Transactional(readOnly = true)
	public PhoneVerificationStatusView status(Long userId) {
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		boolean verified = profile != null && profile.getPhoneNumber() != null && profile.getPhoneVerifiedAt() != null;
		return new PhoneVerificationStatusView(
			verified,
			verified ? maskPhone(profile.getPhoneNumber()) : null,
			verified ? profile.getPhoneVerifiedAt() : null
		);
	}

	@Transactional
	public SendPhoneVerificationResult send(Long userId, String rawPhoneNumber) {
		if (!userAccountRepository.existsById(userId)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, "User not found.");
		}
		String phoneNumber = normalizePhoneNumber(rawPhoneNumber);
		Instant now = Instant.now();
		enforceSendLimit(userId, phoneNumber, now);

		String code = String.format("%06d", secureRandom.nextInt(1_000_000));
		PhoneVerificationCode verification = verificationRepository.save(
			PhoneVerificationCode.issue(userId, phoneNumber, hash(code), now.plus(CODE_TTL), now)
		);
		VerificationMessageSender.SendResult sendResult = messageSender.send(phoneNumber, code);

		return new SendPhoneVerificationResult(
			verification.getId(),
			verification.getExpiresAt(),
			now.plus(RESEND_COOLDOWN),
			devCodeResponseEnabled ? code : null,
			sendResult.message()
		);
	}

	@Transactional
	public ConfirmPhoneVerificationResult confirm(Long userId, Long verificationId, String code) {
		PhoneVerificationCode verification = verificationRepository.findById(verificationId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Verification not found."));
		if (!verification.getUserId().equals(userId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "Verification owner mismatch.");
		}
		if (!verification.isPending()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Verification is not pending.");
		}
		if (verification.getExpiresAt().isBefore(Instant.now())) {
			verification.fail();
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Verification code expired.");
		}
		if (verification.getAttemptCount() >= MAX_ATTEMPTS) {
			verification.fail();
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Verification attempt limit exceeded.");
		}

		verification.increaseAttempt();
		if (code == null || !hash(code.trim()).equals(verification.getCodeHash())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Verification code does not match.");
		}

		verification.verify();
		UserProfile profile = userProfileRepository.findByUserId(userId)
			.orElseGet(() -> userProfileRepository.save(UserProfile.create(userId, null, null, null, null, null, null, null, null)));
		profile.verifyPhoneNumber(verification.getPhoneNumber());

		return new ConfirmPhoneVerificationResult(true, maskPhone(verification.getPhoneNumber()), "/terms/onboarding");
	}

	private void enforceSendLimit(Long userId, String phoneNumber, Instant now) {
		verificationRepository.findFirstByUserIdAndStatusOrderByCreatedAtDescIdDesc(userId, PhoneVerificationStatus.PENDING)
			.ifPresent(latest -> {
				if (latest.getLastSentAt().plus(RESEND_COOLDOWN).isAfter(now)) {
					throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code.");
				}
			});

		Instant today = now.minus(Duration.ofDays(1));
		if (verificationRepository.countByUserIdAndLastSentAtGreaterThanEqual(userId, today) >= DAILY_SEND_LIMIT
			|| verificationRepository.countByPhoneNumberAndLastSentAtGreaterThanEqual(phoneNumber, today) >= DAILY_SEND_LIMIT) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Daily verification send limit exceeded.");
		}
	}

	private String normalizePhoneNumber(String rawPhoneNumber) {
		String digits = rawPhoneNumber == null ? "" : rawPhoneNumber.replaceAll("\\D", "");
		if (!digits.matches("01[016789]\\d{7,8}")) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid phone number.");
		}
		return digits;
	}

	private String hash(String code) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is not available.", error);
		}
	}

	private String maskPhone(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.length() < 8) {
			return phoneNumber;
		}
		return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
	}

	public record PhoneVerificationStatusView(boolean phoneVerified, String maskedPhoneNumber, Instant verifiedAt) {
	}

	public record SendPhoneVerificationResult(
		Long verificationId,
		Instant expiresAt,
		Instant resendAvailableAt,
		String devCode,
		String message
	) {
	}

	public record ConfirmPhoneVerificationResult(boolean phoneVerified, String maskedPhoneNumber, String nextPath) {
	}
}
