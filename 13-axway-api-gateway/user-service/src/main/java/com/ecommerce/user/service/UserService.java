package com.ecommerce.user.service;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.UserProfileRequest;
import com.ecommerce.user.dto.UserProfileResponse;
import com.ecommerce.user.entity.Address;
import com.ecommerce.user.entity.UserProfile;
import com.ecommerce.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileResponse findByUserId(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for user: " + userId));
    }

    @Transactional
    public UserProfileResponse createProfile(Long userId, UserProfileRequest request) {
        if (userProfileRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("Profile already exists for user: " + userId);
        }

        var profile = UserProfile.builder()
                .userId(userId)
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .build();

        return UserProfileResponse.from(userProfileRepository.save(profile));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserProfileRequest request) {
        var profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for user: " + userId));

        profile.setName(request.name());
        profile.setEmail(request.email());
        profile.setPhone(request.phone());

        return UserProfileResponse.from(userProfileRepository.save(profile));
    }

    @Transactional
    public UserProfileResponse addAddress(Long userId, AddressRequest request) {
        var profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for user: " + userId));

        if (request.main()) {
            profile.getAddresses().forEach(a -> a.setMain(false));
        }

        var address = Address.builder()
                .userProfile(profile)
                .street(request.street())
                .city(request.city())
                .state(request.state())
                .zipCode(request.zipCode())
                .country(request.country())
                .main(request.main())
                .build();

        profile.getAddresses().add(address);

        return UserProfileResponse.from(userProfileRepository.save(profile));
    }
}
