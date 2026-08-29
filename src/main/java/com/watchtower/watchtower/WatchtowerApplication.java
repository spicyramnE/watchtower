package com.watchtower.watchtower;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class WatchtowerApplication {

	public static void main(String[] args) {
		// Windows JREs can resolve the host's default zone to the deprecated
		// "Asia/Calcutta" alias, which the Postgres JDBC driver forwards verbatim
		// as a startup parameter and Postgres 17 rejects. Force UTC so every
		// environment (dev, CI, container) behaves identically.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(WatchtowerApplication.class, args);
	}

}
