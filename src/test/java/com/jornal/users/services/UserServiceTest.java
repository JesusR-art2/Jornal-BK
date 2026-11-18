package com.jornal.users.services;

import com.jornal.users.CountryRepository;
import com.jornal.users.Rol;
import com.jornal.users.User;
import com.jornal.users.UserAccessChangedEvent;
import com.jornal.users.UserMapper;
import com.jornal.users.UserRepository;
import com.jornal.users.enums.VerificationState;
import com.jornal.users.LegalProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository repository;
    @Mock UserMapper mapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher events;
    @Mock CountryRepository countryRepository;
    @Mock PhoneNormalizer phoneNormalizer;
    @Mock LegalProperties legalProperties;

    @InjectMocks UserService service;

    @Test
    void deleteAccountAnonymizesUserAndPublishesIneligibleAccess() {
        UUID id = UUID.randomUUID();
        User user = user(id);
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", user.getPasswordHash())).thenReturn(true);

        service.deleteAccount(id, "current-password");

        assertThat(user.getEmail()).startsWith("deleted-").endsWith("@deleted.invalid");
        assertThat(user.getName()).isEqualTo("Deleted user");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo("!deleted");
        assertThat(user.getVerificationState()).isEqualTo(VerificationState.PENDING);
        assertThat(user.isActive()).isFalse();
        assertThat(user.getDeletedAt()).isNotNull();

        ArgumentCaptor<UserAccessChangedEvent> captor = ArgumentCaptor.forClass(UserAccessChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(id);
        assertThat(captor.getValue().eligible()).isFalse();
    }

    @Test
    void activatePublishesEligibleAccessForVerifiedActiveUser() {
        UUID id = UUID.randomUUID();
        User user = user(id);
        user.setActive(false);
        when(repository.findById(id)).thenReturn(Optional.of(user));

        service.activate(id);

        assertThat(user.isActive()).isTrue();
        verify(events).publishEvent(any(UserAccessChangedEvent.class));
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("person@example.com");
        user.setPasswordHash("hashed");
        user.setName("Person");
        user.setPhone("+573001234567");
        user.setRol(Rol.APPLICANT);
        user.setVerificationState(VerificationState.VERIFIED);
        user.setActive(true);
        user.setCountryCode("CO");
        user.setLocale("es-CO");
        return user;
    }
}
