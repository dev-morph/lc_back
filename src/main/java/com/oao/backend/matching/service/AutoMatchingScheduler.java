package com.oao.backend.matching.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy-static-matching-scheduler")
public class AutoMatchingScheduler {
}
