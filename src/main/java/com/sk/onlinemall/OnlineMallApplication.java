package com.sk.onlinemall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.sk.onlinemall")
@EnableScheduling
public class OnlineMallApplication {

    /**
     * 启动校园文创商城应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OnlineMallApplication.class, args);
    }

}
