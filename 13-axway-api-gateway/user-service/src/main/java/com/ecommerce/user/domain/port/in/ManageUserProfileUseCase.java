package com.ecommerce.user.domain.port.in;

import com.ecommerce.user.domain.model.UserProfile;
import com.ecommerce.user.domain.port.in.command.AddAddressCommand;
import com.ecommerce.user.domain.port.in.command.CreateProfileCommand;
import com.ecommerce.user.domain.port.in.command.UpdateProfileCommand;

public interface ManageUserProfileUseCase {
    UserProfile create(CreateProfileCommand command);

    UserProfile update(UpdateProfileCommand command);

    UserProfile addAddress(AddAddressCommand command);
}
