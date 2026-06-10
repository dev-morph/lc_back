package com.oao.backend.phone.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oao.verification.message.provider", havingValue = "mock", matchIfMissing = true)
public class MockAlimtalkVerificationSender implements VerificationMessageSender {

	private static final Logger log = LoggerFactory.getLogger(MockAlimtalkVerificationSender.class);

	@Override
	public SendResult send(String phoneNumber, String code) {
		log.info("[MOCK_ALIMTALK] phone verification code sent. phone={}, code={}", phoneNumber, code);
		return new SendResult(false, null, "Mock Alimtalk sender skipped external delivery.");
	}
}
