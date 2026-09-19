package com.oao.backend.phone.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.oao.backend.common.BusinessException;
import com.solapi.sdk.message.dto.request.SendRequestConfig;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SolapiSmsVerificationSenderTests {
  private final DefaultMessageService provider = mock(DefaultMessageService.class);
  private final SolapiSmsVerificationSender sender =
      new SolapiSmsVerificationSender("test-key", "test-secret", "01000000000", () -> provider);

  @Test
  void requestsOptionalMessageListSoAcceptedSmsIsNotMistakenForFailure() throws Exception {
    when(provider.send(any(Message.class), any(SendRequestConfig.class))).thenAnswer(call -> {
      var config = call.getArgument(1, SendRequestConfig.class);
      var response = new MultipleDetailMessageSentResponse();
      // SOLAPI omits this list unless explicitly requested.
      if (config.getShowMessageList()) response.setMessageList(List.of(
          new MultipleDetailMessageSentResponse.MessageList("test-message", "2000", null, null)));
      return response;
    });
    assertThat(sender.send("010-1111-2222", "123456").sent()).isTrue();
    verify(provider, times(1)).send(any(Message.class), any(SendRequestConfig.class));
  }

  @Test
  void doesNotReportSuccessForEmptyResponse() throws Exception {
    when(provider.send(any(Message.class), any(SendRequestConfig.class)))
        .thenReturn(new MultipleDetailMessageSentResponse());
    assertThatThrownBy(() -> sender.send("01011112222", "123456"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("문자 발송 요청을 완료하지 못했습니다.");
  }

  @Test
  void providerExceptionDoesNotExposeSecretsOrAutomaticallyRetry() throws Exception {
    when(provider.send(any(Message.class), any(SendRequestConfig.class)))
        .thenThrow(new IllegalStateException("secret verification data"));
    assertThatThrownBy(() -> sender.send("01011112222", "123456"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("문자 발송 결과를 확인하지 못했어요. 잠시 후 다시 시도해주세요.");
    verify(provider, times(1)).send(any(Message.class), any(SendRequestConfig.class));
  }
}
