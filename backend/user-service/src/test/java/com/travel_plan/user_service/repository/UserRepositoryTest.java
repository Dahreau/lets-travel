package com.travel_plan.user_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.user_service.domain.Address;
import com.travel_plan.user_service.domain.Role;
import com.travel_plan.user_service.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsUserByEmail() {
        User user = userRepository.save(newTraveler("traveler@travel-plan.com"));

        assertThat(userRepository.findByEmail("traveler@travel-plan.com")).contains(user);
        assertThat(userRepository.findByEmail("unknown@travel-plan.com")).isEmpty();
    }

    @Test
    void deletingUserCascadesToAddress() {
        User user = newTraveler("cascade@travel-plan.com");
        Address address = Address.builder()
                .street("221B Baker Street")
                .city("London")
                .postalCode("NW16XE")
                .country("UK")
                .user(user)
                .build();
        user.setAddress(address);
        user = userRepository.save(user);
        entityManager.flush();

        UUID addressId = user.getAddress().getId();
        userRepository.delete(user);
        entityManager.flush();

        assertThat(entityManager.find(Address.class, addressId)).isNull();
    }

    private User newTraveler(String email) {
        return User.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .email(email)
                .role(Role.TRAVELER)
                .build();
    }
}
