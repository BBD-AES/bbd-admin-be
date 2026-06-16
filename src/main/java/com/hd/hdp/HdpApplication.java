package com.hd.hdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(HdpApplication.class, args);
    }

}
