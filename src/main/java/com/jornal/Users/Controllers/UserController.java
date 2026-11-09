package com.jornal.Users.Controllers;

import com.jornal.Users.DTO.UserResponse;
import com.jornal.Users.Services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse findById(@AuthenticationPrincipal Jwt jwt){
        return userService.findById(UUID.fromString(jwt.getSubject()));
    }

}
