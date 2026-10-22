package com.labsys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling 打开定时任务：没有它，@Scheduled 标注的方法不会被调用，
 * 而且不会报错 —— 又是一个“不生效但不报错”的坑。
 */
@EnableScheduling
@SpringBootApplication
public class LabsysBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LabsysBackendApplication.class, args);
	}

}
