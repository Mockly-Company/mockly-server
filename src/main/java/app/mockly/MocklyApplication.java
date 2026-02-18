package app.mockly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MocklyApplication {

	public static void main(String[] args) {
		SpringApplication.run(MocklyApplication.class, args);
	}

}
