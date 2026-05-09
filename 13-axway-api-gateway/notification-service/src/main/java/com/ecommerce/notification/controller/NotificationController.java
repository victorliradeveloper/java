package com.ecommerce.notification.controller;

import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.service.JwtService;
import com.ecommerce.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtService jwtService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<List<NotificationResponse>> myNotifications(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtService.extractClaim(authHeader.substring(7),
                claims -> claims.get("userId", Long.class));
        return ResponseEntity.ok(notificationService.findByUserId(userId));
    }
}
