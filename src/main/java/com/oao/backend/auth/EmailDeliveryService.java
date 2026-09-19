package com.oao.backend.auth;

import com.oao.backend.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Service
public class EmailDeliveryService {
  private final SesV2Client client;
  private final String from;

  public EmailDeliveryService(
      SesV2Client client, @Value("${oao.email.from:}") String from) {
    this.client = client;
    this.from = from;
  }

  public void sendCode(String email, String code) {
    if (from.isBlank())
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE, "이메일 인증 서비스 설정이 필요합니다. 운영자에게 문의해주세요.");
    try {
      var message =
          Message.builder()
              .subject(Content.builder().data("[LoveCatcher] 이메일 인증번호").charset("UTF-8").build())
              .body(
                  Body.builder()
                      .text(
                          Content.builder()
                              .data(
                                  "인증번호: "
                                      + code
                                      + "\n15분 이내에 입력해주세요. 요청하지 않으셨다면 무시해주세요.")
                              .charset("UTF-8")
                              .build())
                      .build())
              .build();

      client.sendEmail(
          SendEmailRequest.builder()
              .fromEmailAddress(from)
              .destination(Destination.builder().toAddresses(email).build())
              .content(EmailContent.builder().simple(message).build())
              .build());
    } catch (SdkException e) {
      throw new BusinessException(HttpStatus.BAD_GATEWAY, "인증 이메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.");
    }
  }
}
