package vikoba.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VikobaApplication {

	public static void main(String[] args) {
		SpringApplication.run(VikobaApplication.class, args);
	}

}
