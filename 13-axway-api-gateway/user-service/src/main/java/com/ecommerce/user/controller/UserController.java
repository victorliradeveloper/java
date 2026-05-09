package com.ecommerce.user.controller;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.UserProfileRequest;
import com.ecommerce.user.dto.UserProfileResponse;
import com.ecommerce.user.service.JwtService;
import com.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(userService.findByUserId(userId));
    }

    @PostMapping("/me")
    public ResponseEntity<UserProfileResponse> createProfile(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UserProfileRequest request
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createProfile(userId, request));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UserProfileRequest request
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<UserProfileResponse> addAddress(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AddressRequest request
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addAddress(userId, request));
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return jwtService.extractUserId(token);
    }
}
