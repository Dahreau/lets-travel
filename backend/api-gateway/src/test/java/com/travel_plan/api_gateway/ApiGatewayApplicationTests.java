package com.travel_plan.api_gateway;

import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ApiGatewayApplicationTests {

	@MockitoBean
	private SecretKey jwtSigningKey;

	@Test
	void contextLoads() {
	}

}
