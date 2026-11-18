package com.jornal.users.controllers;

import com.jornal.users.dto.ChangePasswordRequest;
import com.jornal.users.dto.DeleteAccountRequest;
import com.jornal.users.dto.UpdateUserRequest;
import com.jornal.users.dto.UserResponse;
import com.jornal.users.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping({"", "/me"})
    public UserResponse findById(@AuthenticationPrincipal Jwt jwt){
        return userService.findMe(userId(jwt));
    }

    @PatchMapping
    public UserResponse update(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody UpdateUserRequest request){
        return userService.update(userId(jwt), request);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(@AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody ChangePasswordRequest request){
        userService.updatePassword(userId(jwt), request);
    }

    @PostMapping("/me/deletion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody DeleteAccountRequest request){
        userService.deleteAccount(userId(jwt), request.currentPassword());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt){
        userService.deactivate(userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

}
