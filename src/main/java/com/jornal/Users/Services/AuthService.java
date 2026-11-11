package com.jornal.Users.Services;

import com.jornal.Shared.ApiException;
import com.jornal.Users.DTO.LoginRequest;
import com.jornal.Users.DTO.TokenResponse;
import com.jornal.Users.User;
import com.jornal.Users.UserRepository;
import com.jornal.Users.enums.ErrorUser;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private String dummyHash;

    @PostConstruct
    void init(){
        dummyHash = passwordEncoder.encode("jornal-dummy-password");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request){
        User user = repository.findByEmail(request.email()).orElse(null);

        //se compara siempre con un hash exista o no el usuario para que el tiempo de respuesta no revele emails

        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.password(),hash);

        if (user == null || !passwordOk || !user.isActive()){
            throw new ApiException(ErrorUser.USER003_INVALID_CREDENTIALS);
        }
        return jwtService.issue(user);
    }
}

