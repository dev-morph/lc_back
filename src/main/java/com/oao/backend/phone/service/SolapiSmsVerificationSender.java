package com.oao.backend.phone.service;

import com.oao.backend.common.BusinessException;
import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.dto.request.SendRequestConfig;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oao.verification.message.provider", havingValue = "solapi-sms")
public class SolapiSmsVerificationSender implements VerificationMessageSender {

  private static final Logger log = LoggerFactory.getLogger(SolapiSmsVerificationSender.class);
  private final Supplier<DefaultMessageService> serviceFactory;
  private final String apiKey;
  private final String apiSecret;
  private final String fromNumber;

  @Autowired
  public SolapiSmsVerificationSender(
      @Value("${oao.verification.message.solapi.api-key}") String apiKey,
      @Value("${oao.verification.message.solapi.api-secret}") String apiSecret,
      @Value("${oao.verification.message.solapi.from-number}") String fromNumber) {
    this(apiKey, apiSecret, fromNumber,
        () -> SolapiClient.INSTANCE.createInstance(apiKey, apiSecret));
  }

  SolapiSmsVerificationSender(String apiKey, String apiSecret, String fromNumber,
      Supplier<DefaultMessageService> serviceFactory) {
    this.serviceFactory = serviceFactory;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.fromNumber = normalizePhoneNumber(fromNumber);
  }

  @Override
  public SendResult send(String phoneNumber, String code) {
    validateConfiguration();

    Message message = new Message();
    message.setFrom(fromNumber);
    message.setTo(normalizePhoneNumber(phoneNumber));
    message.setText("[LoveCatcher] 인증번호 " + code);

    try {
      var config = new SendRequestConfig();
      config.setShowMessageList(true);
      var response = serviceFactory.get().send(message, config);
      if (!response.getFailedMessageList().isEmpty() || response.getMessageList().isEmpty())
        throw new BusinessException(HttpStatus.BAD_GATEWAY, "문자 발송 요청을 완료하지 못했습니다.");
      return new SendResult(true, null, "인증번호를 보냈습니다.");
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      // Provider exception messages can contain the phone number or verification code.
      log.warn("SMS request failed: type={}", exception.getClass().getSimpleName());
      throw new BusinessException(HttpStatus.BAD_GATEWAY,
          "문자 발송 결과를 확인하지 못했어요. 잠시 후 다시 시도해주세요.");
    }
  }

  private void validateConfiguration() {
    if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR, "문자 인증 서비스 설정이 필요합니다. 운영자에게 문의해주세요.");
    }
    if (fromNumber == null || fromNumber.isBlank()) {
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR, "문자 발신번호 설정이 필요합니다. 운영자에게 문의해주세요.");
    }
  }

  private String normalizePhoneNumber(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }
}
