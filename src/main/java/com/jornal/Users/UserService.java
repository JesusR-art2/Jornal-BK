package com.jornal.Users;


import com.jornal.Shared.ApiException;
import com.jornal.Users.DTO.RegisterRequest;
import com.jornal.Users.DTO.UserResponse;
import com.jornal.Users.enums.ErrorUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse save(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmail(request.email())){
            throw new ApiException(ErrorUser.USER001_EMAIL_REGISTERED);
        }
        User user = mapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return mapper.toResponse(repository.save(user));
    }
    @Transactional(readOnly = true)
    public UserResponse findById(java.util.UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorUser.USER002_USER_NOTFOUND, id));
    }

}
