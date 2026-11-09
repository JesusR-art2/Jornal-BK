package com.jornal.Users.Controllers;

import com.jornal.Users.DTO.LoginRequest;
import com.jornal.Users.DTO.RegisterRequest;
import com.jornal.Users.DTO.TokenResponse;
import com.jornal.Users.DTO.UserResponse;
import com.jornal.Users.Services.AuthService;
import com.jornal.Users.Services.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request){
        return userService.save(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request){
        return authService.login(request);
    }
}
