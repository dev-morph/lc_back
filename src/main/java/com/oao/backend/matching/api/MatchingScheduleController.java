package com.oao.backend.matching.api;

import com.oao.backend.common.ApiResponse;
import com.oao.backend.matching.service.MatchingScheduleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matching")
public class MatchingScheduleController {

  private final MatchingScheduleService scheduleService;

  public MatchingScheduleController(MatchingScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @GetMapping("/schedule")
  ApiResponse<PublicSchedule> schedule() {
    var current = scheduleService.findSchedule();
    return ApiResponse.ok(
        new PublicSchedule(current.enabled(), current.timezone(), current.dailyTimes()));
  }

  public record PublicSchedule(boolean enabled, String timezone, List<String> dailyTimes) {}
}
