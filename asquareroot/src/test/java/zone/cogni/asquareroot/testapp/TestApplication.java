package zone.cogni.asquareroot.testapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootApplication
public class TestApplication extends SpringBootServletInitializer {

  public static void main(String[] args) {
    SpringApplication.run(TestApplication.class, args);
  }
}