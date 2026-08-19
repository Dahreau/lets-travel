package com.travel_plan.payment_service;

import com.travel_plan.payment_service.provider.PayPalCredentials;
import com.travel_plan.payment_service.provider.StripeCredentials;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.repository.PaymentRepository;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
		DataSourceAutoConfiguration.class,
		DataSourceInitializationAutoConfiguration.class,
		DataSourceTransactionManagerAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class
})
class PaymentServiceApplicationTests {

	@MockitoBean
	private PaymentMethodRepository paymentMethodRepository;

	@MockitoBean
	private PaymentRepository paymentRepository;

	@MockitoBean
	private SecretKey jwtSigningKey;

	@MockitoBean
	private StripeCredentials stripeCredentials;

	@MockitoBean
	private PayPalCredentials payPalCredentials;

	@Test
	void contextLoads() {
	}

}
