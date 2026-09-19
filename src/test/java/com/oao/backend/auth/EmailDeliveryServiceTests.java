package com.oao.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oao.backend.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

class EmailDeliveryServiceTests {
  @Test
  void sendsUtf8VerificationEmailThroughSes() {
    var client = mock(SesV2Client.class);
    var service = new EmailDeliveryService(client, "LoveCatcher <hello@oao365.com>");

    service.sendCode("member@example.com", "123456");

    var request = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(client).sendEmail(request.capture());
    assertThat(request.getValue().fromEmailAddress())
        .isEqualTo("LoveCatcher <hello@oao365.com>");
    assertThat(request.getValue().destination().toAddresses()).containsExactly("member@example.com");
    assertThat(request.getValue().content().simple().subject().data())
        .isEqualTo("[LoveCatcher] 이메일 인증번호");
    assertThat(request.getValue().content().simple().body().text().data()).contains("123456", "15분");
  }

  @Test
  void rejectsMissingSenderBeforeCallingSes() {
    var client = mock(SesV2Client.class);
    var service = new EmailDeliveryService(client, " ");

    assertThatThrownBy(() -> service.sendCode("member@example.com", "123456"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    verify(client, never()).sendEmail(any(SendEmailRequest.class));
  }

  @Test
  void convertsAwsFailureToGatewayError() {
    var client = mock(SesV2Client.class);
    when(client.sendEmail(any(SendEmailRequest.class)))
        .thenThrow(SdkClientException.builder().message("credentials unavailable").build());
    var service = new EmailDeliveryService(client, "hello@oao365.com");

    assertThatThrownBy(() -> service.sendCode("member@example.com", "123456"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
  }
}
