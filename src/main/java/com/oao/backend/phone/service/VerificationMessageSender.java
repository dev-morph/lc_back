package com.oao.backend.phone.service;

public interface VerificationMessageSender {

	SendResult send(String phoneNumber, String code);

	record SendResult(boolean sent, String providerMessageId, String message) {
	}
}
