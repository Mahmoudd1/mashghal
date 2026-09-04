package com.apparel.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApparelTrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApparelTrackingApplication.class, args);
    }
}
