package com.zxylearn.build_guard_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.zxylearn.build_guard_server.mapper")
@EnableRabbit
@EnableScheduling
public class BuildGuardServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BuildGuardServerApplication.class, args);
	}

}
