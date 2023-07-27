package com.coriander;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.coriander.mapper")
@SpringBootApplication
public class CorianderDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorianderDianPingApplication.class, args);
    }

}
