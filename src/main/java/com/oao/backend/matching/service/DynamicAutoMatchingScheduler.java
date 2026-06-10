package com.oao.backend.matching.service;

import com.oao.backend.matching.domain.MatchingScheduleConfig;
import com.oao.backend.matching.repository.MatchingScheduleConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class DynamicAutoMatchingScheduler {

	private static final Logger log = LoggerFactory.getLogger(DynamicAutoMatchingScheduler.class);
	private static final long DEFAULT_CONFIG_ID = 1L;

	private final TaskScheduler taskScheduler;
	private final MatchingScheduleConfigRepository scheduleConfigRepository;
	private final AutoMatchingService autoMatchingService;
	private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

	public DynamicAutoMatchingScheduler(
		TaskScheduler taskScheduler,
		MatchingScheduleConfigRepository scheduleConfigRepository,
		AutoMatchingService autoMatchingService
	) {
		this.taskScheduler = taskScheduler;
		this.scheduleConfigRepository = scheduleConfigRepository;
		this.autoMatchingService = autoMatchingService;
	}

	@PostConstruct
	public synchronized void start() {
		refreshSchedule();
	}

	public synchronized void refreshSchedule() {
		cancelCurrentTask();

		MatchingScheduleConfig config = findConfigOrNull();
		if (config == null) {
			log.warn("Matching schedule config is missing. Auto matching scheduler is not registered.");
			return;
		}
		if (!config.isEnabled()) {
			log.info("Auto matching scheduler is disabled.");
			return;
		}

		ZoneId zoneId = ZoneId.of(config.getTimezone());
		List<String> runTimes = runTimes(config);
		for (String runTime : runTimes) {
			LocalTime time = LocalTime.parse(runTime);
			String cronExpression = "0 " + time.getMinute() + " " + time.getHour() + " * * *";
			scheduledTasks.add(taskScheduler.schedule(this::runSafely, new CronTrigger(cronExpression, zoneId)));
		}
		log.info("Auto matching scheduler registered: runTimes={}, timezone={}", runTimes, config.getTimezone());
	}

	private void runSafely() {
		try {
			AutoMatchingService.AutoMatchingResult result = autoMatchingService.runAutoMatching();
			log.info("Auto matching completed. createdCount={}", result.createdCount());
		} catch (RuntimeException exception) {
			log.error("Auto matching failed.", exception);
		}
	}

	private void cancelCurrentTask() {
		scheduledTasks.forEach(task -> task.cancel(false));
		scheduledTasks.clear();
	}

	private List<String> runTimes(MatchingScheduleConfig config) {
		if (config.getRunTimes() != null && !config.getRunTimes().isBlank()) {
			return List.of(config.getRunTimes().split(","));
		}
		return List.of("09:00");
	}

	private MatchingScheduleConfig findConfigOrNull() {
		try {
			Optional<MatchingScheduleConfig> config = scheduleConfigRepository.findById(DEFAULT_CONFIG_ID);
			return config.orElse(null);
		} catch (DataAccessException exception) {
			log.warn("Matching schedule config could not be loaded. Auto matching scheduler is not registered.");
			return null;
		}
	}
}
