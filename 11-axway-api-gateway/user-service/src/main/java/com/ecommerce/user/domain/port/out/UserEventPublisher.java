package com.ecommerce.user.domain.port.out;

import com.ecommerce.user.domain.model.event.AddressAddedEvent;
import com.ecommerce.user.domain.model.event.UserProfileCreatedEvent;
import com.ecommerce.user.domain.model.event.UserProfileUpdatedEvent;

public interface UserEventPublisher {
    void publishProfileCreated(UserProfileCreatedEvent event);

    void publishProfileUpdated(UserProfileUpdatedEvent event);

    void publishAddressAdded(AddressAddedEvent event);
}
