package com.ecommerce.user.infrastructure.adapter.in.web;

import com.ecommerce.user.domain.port.in.FindUserProfileUseCase;
import com.ecommerce.user.domain.port.in.ManageUserProfileUseCase;
import com.ecommerce.user.domain.port.in.command.AddAddressCommand;
import com.ecommerce.user.domain.port.in.command.CreateProfileCommand;
import com.ecommerce.user.domain.port.in.command.UpdateProfileCommand;
import com.ecommerce.user.infrastructure.adapter.in.web.dto.AddressRequest;
import com.ecommerce.user.infrastructure.adapter.in.web.dto.UserProfileRequest;
import com.ecommerce.user.infrastructure.adapter.in.web.dto.UserProfileResponse;
import com.ecommerce.user.infrastructure.config.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final FindUserProfileUseCase findUserProfileUseCase;
    private final ManageUserProfileUseCase manageUserProfileUseCase;
    private final JwtService jwtService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(UserProfileResponse.from(findUserProfileUseCase.findByUserId(userId)));
    }

    @PostMapping("/me")
    public ResponseEntity<UserProfileResponse> createProfile(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UserProfileRequest request
    ) {
        Long userId = extractUserId(authHeader);
        var command = new CreateProfileCommand(userId, request.name(), request.email(), request.phone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserProfileResponse.from(manageUserProfileUseCase.create(command)));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UserProfileRequest request
    ) {
        Long userId = extractUserId(authHeader);
        var command = new UpdateProfileCommand(userId, request.name(), request.email(), request.phone());
        return ResponseEntity.ok(UserProfileResponse.from(manageUserProfileUseCase.update(command)));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<UserProfileResponse> addAddress(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AddressRequest request
    ) {
        Long userId = extractUserId(authHeader);
        var command = new AddAddressCommand(
                userId,
                request.street(), request.city(), request.state(),
                request.zipCode(), request.country(), request.main()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserProfileResponse.from(manageUserProfileUseCase.addAddress(command)));
    }

    private Long extractUserId(String authHeader) {
        return jwtService.extractUserId(authHeader.substring(7));
    }
}
