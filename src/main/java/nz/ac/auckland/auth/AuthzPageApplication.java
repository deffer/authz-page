package nz.ac.auckland.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration;

//@SpringBootApplication(exclude = { ErrorMvcAutoConfiguration.class })
@SpringBootApplication
public class AuthzPageApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthzPageApplication.class, args);
    }
}
