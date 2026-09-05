package com.apparel.tracking;

import java.io.InputStream;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApparelTrackingApplication {

    public static void main(String[] args) {
        printBuildIdentity();
        SpringApplication.run(ApparelTrackingApplication.class, args);
    }

    /**
     * Announces which build this is, before anything can fail.
     *
     * <p>A deployment that keeps failing on a fixed bug looks identical to one
     * that is genuinely misconfigured, and telling them apart cost several
     * rounds of guessing. This prints first, on stdout, so it survives a
     * start-up that dies during context refresh — at which point the build
     * timestamp answers "is the platform serving a cached image?" outright.
     */
    private static void printBuildIdentity() {
        Properties buildInfo = new Properties();
        try (InputStream stream =
                ApparelTrackingApplication.class.getResourceAsStream("/META-INF/build-info.properties")) {
            if (stream != null) {
                buildInfo.load(stream);
            }
        } catch (Exception ex) {
            // Never let a diagnostic aid stop the application from starting.
        }
        System.out.println("Build: version=" + buildInfo.getProperty("build.version", "unknown")
                + " commit=" + buildInfo.getProperty("build.commit", "unknown")
                + " built=" + buildInfo.getProperty("build.time", "unknown"));
    }
}
