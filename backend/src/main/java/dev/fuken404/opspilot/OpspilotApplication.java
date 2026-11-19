package dev.fuken404.opspilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OpspilotApplication {

	public static void main(String[] args) {
		try {
			SpringApplication.run(OpspilotApplication.class, args);
		} catch (Throwable error) {
			StartupFailure.serve(error);
			throw error;
		}
	}

}
