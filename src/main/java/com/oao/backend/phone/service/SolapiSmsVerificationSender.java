package com.oao.backend.phone.service;

import com.oao.backend.common.BusinessException;
import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oao.verification.message.provider", havingValue = "solapi-sms")
public class SolapiSmsVerificationSender implements VerificationMessageSender {

	private final String apiKey;
	private final String apiSecret;
	private final String fromNumber;

	public SolapiSmsVerificationSender(
		@Value("${oao.verification.message.solapi.api-key}") String apiKey,
		@Value("${oao.verification.message.solapi.api-secret}") String apiSecret,
		@Value("${oao.verification.message.solapi.from-number}") String fromNumber
	) {
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
		this.fromNumber = normalizePhoneNumber(fromNumber);
	}

	@Override
	public SendResult send(String phoneNumber, String code) {
		validateConfiguration();

		DefaultMessageService messageService = SolapiClient.INSTANCE.createInstance(apiKey, apiSecret);
		Message message = new Message();
		message.setFrom(fromNumber);
		message.setTo(normalizePhoneNumber(phoneNumber));
		message.setText("[LoveCatcher] 인증번호 " + code);

		try {
			Object response = messageService.send(message);
			return new SendResult(true, null, response == null ? "SMS sent." : response.toString());
		} catch (Exception exception) {
			throw new BusinessException(HttpStatus.BAD_GATEWAY, "SMS provider request failed.");
		}
	}

	private void validateConfiguration() {
		if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
			throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "SOLAPI credentials are missing.");
		}
		if (fromNumber == null || fromNumber.isBlank()) {
			throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "SOLAPI sender number is missing.");
		}
	}

	private String normalizePhoneNumber(String value) {
		return value == null ? "" : value.replaceAll("\\D", "");
	}
}
