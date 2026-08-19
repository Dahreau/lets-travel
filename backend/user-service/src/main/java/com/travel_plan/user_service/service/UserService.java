package com.travel_plan.user_service.service;

import com.travel_plan.user_service.domain.Address;
import com.travel_plan.user_service.domain.User;
import com.travel_plan.user_service.exception.UserNotFoundException;
import com.travel_plan.user_service.repository.UserRepository;
import com.travel_plan.user_service.web.AddressRequest;
import com.travel_plan.user_service.web.UserRequest;
import com.travel_plan.user_service.web.UserResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse findById(UUID id) {
        return UserResponse.from(getOrThrow(id));
    }

    public UserResponse create(UserRequest request) {
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .role(request.role())
                .build();
        attachAddress(user, request.address());
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse update(UUID id, UserRequest request) {
        User user = getOrThrow(id);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        attachAddress(user, request.address());
        // saveAndFlush : @PreUpdate ne s'execute qu'au flush, sinon updatedAt renvoye est perime.
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    public void delete(UUID id) {
        User user = getOrThrow(id);
        userRepository.delete(user);
    }

    private User getOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private void attachAddress(User user, AddressRequest request) {
        if (request == null) {
            user.setAddress(null);
            return;
        }

        Address address = user.getAddress();
        if (address == null) {
            address = new Address();
            address.setUser(user);
            user.setAddress(address);
        }
        address.setStreet(request.street());
        address.setCity(request.city());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
    }
}
