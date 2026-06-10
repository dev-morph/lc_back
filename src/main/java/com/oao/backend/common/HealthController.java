package com.oao.backend.common;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

	@GetMapping
	ApiResponse<HealthResponse> health() {
		return ApiResponse.ok(new HealthResponse("ok", Instant.now()));
	}

	record HealthResponse(String status, Instant checkedAt) {
	}
}
