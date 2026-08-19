package com.travel_plan.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.user_service.domain.Address;
import com.travel_plan.user_service.domain.Role;
import com.travel_plan.user_service.domain.User;
import com.travel_plan.user_service.exception.UserNotFoundException;
import com.travel_plan.user_service.repository.UserRepository;
import com.travel_plan.user_service.web.AddressRequest;
import com.travel_plan.user_service.web.UserRequest;
import com.travel_plan.user_service.web.UserResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(userRepository);

    @Test
    void findAllDelegatesToRepository() {
        when(userRepository.findAll()).thenReturn(List.of(existingUser(null)));

        List<UserResponse> result = userService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("Ada");
    }

    @Test
    void findByIdReturnsUserWhenPresent() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserResponse response = userService.findById(id);

        assertThat(response.email()).isEqualTo("ada@travel-plan.com");
        assertThat(response.address()).isNull();
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void createBuildsUserWithAddressWhenAddressProvided() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AddressRequest addressRequest = new AddressRequest("10 Downing St", "London", "SW1A 2AA", "UK");
        UserRequest request =
                new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", "0102030405", Role.TRAVELER, addressRequest);

        UserResponse response = userService.create(request);

        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.address()).isNotNull();
        assertThat(response.address().city()).isEqualTo("London");
    }

    @Test
    void createBuildsUserWithoutAddressWhenAddressAbsent() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        UserResponse response = userService.create(request);

        assertThat(response.address()).isNull();
    }

    @Test
    void updateThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        assertThatThrownBy(() -> userService.update(id, request)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateAddsAddressWhenNoneExistedBefore() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AddressRequest addressRequest = new AddressRequest("221B Baker St", "London", "NW1 6XE", "UK");
        UserRequest request =
                new UserRequest("Ada", "Byron", "ada.byron@travel-plan.com", "0605040302", Role.ADMIN, addressRequest);

        UserResponse response = userService.update(id, request);

        assertThat(response.lastName()).isEqualTo("Byron");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.address().street()).isEqualTo("221B Baker St");
    }

    @Test
    void updateReplacesFieldsOnExistingAddress() {
        UUID id = UUID.randomUUID();
        User user = existingUser(new Address());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AddressRequest addressRequest = new AddressRequest("1 Rue de Rivoli", "Paris", "75001", "France");
        UserRequest request =
                new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, addressRequest);

        UserResponse response = userService.update(id, request);

        assertThat(response.address().city()).isEqualTo("Paris");
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void updateRemovesAddressWhenRequestAddressIsNull() {
        UUID id = UUID.randomUUID();
        User user = existingUser(new Address());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        UserResponse response = userService.update(id, request);

        assertThat(response.address()).isNull();
        assertThat(user.getAddress()).isNull();
    }

    @Test
    void deleteRemovesExistingUser() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.delete(id);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(id)).isInstanceOf(UserNotFoundException.class);
    }

    private User existingUser(Address address) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@travel-plan.com")
                .role(Role.TRAVELER)
                .build();
        if (address != null) {
            address.setStreet("Old street");
            address.setCity("Old city");
            address.setPostalCode("Old postal");
            address.setCountry("Old country");
            address.setUser(user);
            user.setAddress(address);
        }
        return user;
    }
}
