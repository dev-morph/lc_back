package com.oao.backend.notification.service;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.dto.request.SendRequestConfig;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.model.kakao.KakaoOption;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AlimtalkDeliveryClient {
  public Delivery send(
      String key,
      String secret,
      String from,
      String phone,
      String pfId,
      String template,
      String targetUrl)
      throws Exception {
    var option = new KakaoOption();
    option.setPfId(pfId);
    option.setTemplateId(template);
    option.setDisableSms(true);
    option.variables = Map.of("#{serviceName}", "LoveCatcher", "#{targetUrl}", targetUrl);
    var message = new Message();
    message.setTo(phone);
    message.setFrom(from);
    message.setKakaoOptions(option);
    var config = new SendRequestConfig();
    config.setShowMessageList(true);
    var result = SolapiClient.INSTANCE.createInstance(key, secret).send(message, config);
    return new Delivery(
        result.getFailedMessageList().isEmpty() && !result.getMessageList().isEmpty(),
        result.getMessageList().isEmpty() ? null : result.getMessageList().get(0).getMessageId());
  }

  public record Delivery(boolean accepted, String messageId) {}
}
