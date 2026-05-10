package com.ecommerce.notification.infrastructure.adapter.in.web;

import com.ecommerce.notification.domain.port.in.FindNotificationsUseCase;
import com.ecommerce.notification.infrastructure.adapter.in.web.dto.NotificationResponse;
import com.ecommerce.notification.infrastructure.config.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final FindNotificationsUseCase findNotificationsUseCase;
    private final JwtService jwtService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<List<NotificationResponse>> myNotifications(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtService.extractClaim(authHeader.substring(7),
                claims -> claims.get("userId", Long.class));

        var notifications = findNotificationsUseCase.findByUserId(userId).stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(notifications);
    }
}
