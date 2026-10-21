package pe.edu.cibertec.platonmicroservicepayments;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PlatonMicroservicePaymentsApplicationTests {

	@Autowired
	private ConfigurableEnvironment environment;

	@Test
	void contextLoads() {
	}

	@Test
	void infrastructureConfigurationComesFromConfigServer() {
		assertTrue(environment.getPropertySources().stream()
				.anyMatch(source -> source.getName().startsWith("configserver:")
						&& source.containsProperty("app.rabbitmq.purchase.exchange")));
		assertEquals("never", environment.getProperty("spring.sql.init.mode"));
		assertNotNull(environment.getProperty("spring.datasource.url"));
	}

}
