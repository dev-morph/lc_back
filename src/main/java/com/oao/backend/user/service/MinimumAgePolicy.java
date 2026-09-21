package com.oao.backend.user.service;

import com.oao.backend.common.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MinimumAgePolicy {
  public static final int MINIMUM_AGE = 19;
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private final Clock clock;

  @Autowired
  public MinimumAgePolicy() { this(Clock.systemUTC()); }

  public MinimumAgePolicy(Clock clock) { this.clock = clock; }

  public LocalDate latestBirthDate() {
    return LocalDate.now(clock.withZone(ZONE)).minusYears(MINIMUM_AGE);
  }

  public boolean eligible(LocalDate birthDate) {
    return birthDate != null && !birthDate.isBefore(LocalDate.of(1900, 1, 1))
        && !birthDate.isAfter(latestBirthDate());
  }

  public void requireEligible(LocalDate birthDate) {
    if (!eligible(birthDate))
      throw new BusinessException(HttpStatus.BAD_REQUEST,
          "만 19세부터 이용할 수 있어요. 생년월일을 확인해주세요.");
  }
}
