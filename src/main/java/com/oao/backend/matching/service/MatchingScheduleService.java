package com.oao.backend.matching.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.matching.domain.MatchingScheduleConfig;
import com.oao.backend.matching.repository.MatchingScheduleConfigRepository;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingScheduleService {

	private static final long DEFAULT_CONFIG_ID = 1L;

	private final MatchingScheduleConfigRepository repository;
	private final DynamicAutoMatchingScheduler scheduler;

	public MatchingScheduleService(
		MatchingScheduleConfigRepository repository,
		DynamicAutoMatchingScheduler scheduler
	) {
		this.repository = repository;
		this.scheduler = scheduler;
	}

	@Transactional(readOnly = true)
	public MatchingScheduleView findSchedule() {
		return MatchingScheduleView.from(findConfig());
	}

	@Transactional
	public MatchingScheduleView updateSchedule(MatchingScheduleUpdateCommand command, Long adminId) {
		MatchingScheduleConfig config = findConfig();
		String timezone = normalizeTimezone(command.timezone());
		List<LocalTime> runTimes = normalizeRunTimes(command.dailyTimes(), command.dailyTime());
		String runTimesText = runTimes.stream().map(LocalTime::toString).collect(Collectors.joining(","));
		String cronExpression = runTimes.stream()
			.map(this::toDailyCron)
			.collect(Collectors.joining(";"));

		config.update(command.enabled(), cronExpression, runTimesText, timezone, adminId);
		scheduler.refreshSchedule();
		return MatchingScheduleView.from(config);
	}

	private MatchingScheduleConfig findConfig() {
		return repository.findById(DEFAULT_CONFIG_ID)
			.orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Matching schedule config is missing."));
	}

	private String normalizeTimezone(String timezone) {
		String normalized = timezone == null || timezone.isBlank() ? "Asia/Seoul" : timezone.trim();
		try {
			ZoneId.of(normalized);
		} catch (DateTimeException exception) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Timezone is invalid.");
		}
		return normalized;
	}

	private String normalizeCron(String cronExpression) {
		if (cronExpression == null || cronExpression.isBlank()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Cron expression is required.");
		}
		String normalized = cronExpression.trim();
		if (!CronExpression.isValidExpression(normalized)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Cron expression is invalid.");
		}
		return normalized;
	}

	private String toDailyCron(LocalTime dailyTime) {
		return "0 " + dailyTime.getMinute() + " " + dailyTime.getHour() + " * * *";
	}

	private List<LocalTime> normalizeRunTimes(List<LocalTime> dailyTimes, LocalTime fallbackDailyTime) {
		List<LocalTime> runTimes = dailyTimes == null ? List.of() : dailyTimes;
		if (runTimes.isEmpty() && fallbackDailyTime != null) {
			runTimes = List.of(fallbackDailyTime);
		}
		if (runTimes.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "At least one run time is required.");
		}
		List<LocalTime> distinctRunTimes = runTimes.stream()
			.distinct()
			.sorted()
			.toList();
		if (distinctRunTimes.size() > 12) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Run times can be up to 12.");
		}
		return distinctRunTimes;
	}

	public record MatchingScheduleUpdateCommand(
		boolean enabled,
		LocalTime dailyTime,
		List<LocalTime> dailyTimes,
		Integer intervalHours,
		String cronExpression,
		String timezone
	) {
	}

	public record MatchingScheduleView(
		Long id,
		boolean enabled,
		String cronExpression,
		String timezone,
		String dailyTime,
		List<String> dailyTimes,
		Integer intervalHours,
		Long updatedByAdminId
	) {

		static MatchingScheduleView from(MatchingScheduleConfig config) {
			return new MatchingScheduleView(
				config.getId(),
				config.isEnabled(),
				config.getCronExpression(),
				config.getTimezone(),
				dailyTimeFromCron(config.getCronExpression()),
				dailyTimesFromConfig(config),
				intervalHoursFromCron(config.getCronExpression()),
				config.getUpdatedByAdminId()
			);
		}

		private static List<String> dailyTimesFromConfig(MatchingScheduleConfig config) {
			if (config.getRunTimes() == null || config.getRunTimes().isBlank()) {
				String dailyTime = dailyTimeFromCron(config.getCronExpression());
				return dailyTime == null ? List.of() : List.of(dailyTime);
			}
			return List.of(config.getRunTimes().split(","));
		}

		private static String dailyTimeFromCron(String cronExpression) {
			if (cronExpression == null) {
				return null;
			}
			String[] parts = cronExpression.trim().split("\\s+");
			if (parts.length == 6
				&& "0".equals(parts[0])
				&& "*".equals(parts[3])
				&& "*".equals(parts[4])
				&& "*".equals(parts[5])) {
				return "%02d:%02d".formatted(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]));
			}
			return null;
		}

		private static Integer intervalHoursFromCron(String cronExpression) {
			if (cronExpression == null) {
				return null;
			}
			String[] parts = cronExpression.trim().split("\\s+");
			if (parts.length == 6
				&& "0".equals(parts[0])
				&& "0".equals(parts[1])
				&& parts[2].startsWith("*/")
				&& "*".equals(parts[3])
				&& "*".equals(parts[4])
				&& "*".equals(parts[5])) {
				return Integer.parseInt(parts[2].substring(2));
			}
			if ("0 0 9 * * *".equals(cronExpression.trim())) {
				return 24;
			}
			return null;
		}
	}
}
