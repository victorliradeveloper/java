package com.ecommerce.user.application.service;

import com.ecommerce.user.domain.exception.UserProfileAlreadyExistsException;
import com.ecommerce.user.domain.exception.UserProfileNotFoundException;
import com.ecommerce.user.domain.model.Address;
import com.ecommerce.user.domain.model.UserProfile;
import com.ecommerce.user.domain.port.in.FindUserProfileUseCase;
import com.ecommerce.user.domain.port.in.ManageUserProfileUseCase;
import com.ecommerce.user.domain.port.in.command.AddAddressCommand;
import com.ecommerce.user.domain.port.in.command.CreateProfileCommand;
import com.ecommerce.user.domain.port.in.command.UpdateProfileCommand;
import com.ecommerce.user.domain.port.out.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService implements FindUserProfileUseCase, ManageUserProfileUseCase {

    private final UserProfileRepository userProfileRepository;

    @Override
    public UserProfile findByUserId(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
    }

    @Override
    @Transactional
    public UserProfile create(CreateProfileCommand command) {
        if (userProfileRepository.existsByUserId(command.userId())) {
            throw new UserProfileAlreadyExistsException(command.userId());
        }
        var profile = UserProfile.newProfile(
                command.userId(),
                command.name(),
                command.email(),
                command.phone()
        );
        return userProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public UserProfile update(UpdateProfileCommand command) {
        var profile = userProfileRepository.findByUserId(command.userId())
                .orElseThrow(() -> new UserProfileNotFoundException(command.userId()));
        profile.update(command.name(), command.email(), command.phone());
        return userProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public UserProfile addAddress(AddAddressCommand command) {
        var profile = userProfileRepository.findByUserId(command.userId())
                .orElseThrow(() -> new UserProfileNotFoundException(command.userId()));

        var address = Address.builder()
                .street(command.street())
                .city(command.city())
                .state(command.state())
                .zipCode(command.zipCode())
                .country(command.country())
                .main(command.main())
                .build();

        profile.addAddress(address);
        return userProfileRepository.save(profile);
    }
}
