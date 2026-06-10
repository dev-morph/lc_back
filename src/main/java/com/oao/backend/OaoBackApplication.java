package com.oao.backend;

import com.oao.backend.config.DevToolsProperties;
import com.oao.backend.config.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({UploadProperties.class, DevToolsProperties.class})
public class OaoBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(OaoBackApplication.class, args);
	}

}
