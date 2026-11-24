package com.jornal.users.services;

import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import com.jornal.shared.ApiException;
import com.jornal.shared.Locales;
import com.jornal.shared.PageResponse;
import com.jornal.users.*;
import com.jornal.users.dto.ChangePasswordRequest;
import com.jornal.users.dto.RegisterRequest;
import com.jornal.users.dto.UpdateUserRequest;
import com.jornal.users.dto.UserResponse;
import com.jornal.users.enums.ErrorUser;
import com.jornal.users.enums.VerificationState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements UserApi {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final CountryRepository countryRepository;
    private final PhoneNormalizer phoneNormalizer;
    private final RegionApi regionApi;
    private final AddressService addressService;
    private final BusinessService businessService;
    private final Clock clock;

    /**
     * Registra un usuario en la región de esta instancia. Todo lo que depende del país (teléfono, orden del
     * nombre, dirección, identificación fiscal, versiones legales) sale del perfil regional.
     */
    @Transactional
    public UserResponse save(RegisterRequest request) {
        if (request.rol() == Rol.ADMIN) {
            throw new ApiException(ErrorUser.USER004_ROLE_NOT_ALLOWED);
        }
        boolean isBusiness = request.rol() == Rol.BUSINESS;
        if (isBusiness && request.business() == null) {
            throw new ApiException(ErrorUser.USER011_BUSINESS_REQUIRED);
        }
        if (!isBusiness && request.business() != null) {
            throw new ApiException(ErrorUser.USER012_BUSINESS_NOT_ALLOWED);
        }

        RegionProfileView profile = regionApi.current();
        if (request.country() != null && !request.country().equalsIgnoreCase(profile.code())) {
            throw new ApiException(ErrorUser.USER010_REGION_MISMATCH, profile.code());
        }
        countryRepository.findByCodeAndEnabledTrue(profile.code())
                .orElseThrow(() -> new ApiException(ErrorUser.USER007_COUNTRY_NOT_AVAILABLE));
        if (repository.existsByEmail(request.email())) {
            throw new ApiException(ErrorUser.USER001_EMAIL_REGISTERED);
        }

        // Validaciones por país antes de guardar nada.
        String phone = request.phone() == null || request.phone().isBlank()
                ? null : phoneNormalizer.toE164(request.phone(), profile.code());
        Map<String, String> address = request.address() == null ? null : addressService.normalize(request.address());
        BusinessService.ValidBusiness business = isBusiness ? businessService.validate(request.business(), null) : null;

        User user = mapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(profile.nameOrder().compose(request.givenName(), request.familyName()));
        user.setPhone(phone);
        user.setCountryCode(profile.code());
        user.setLocale(Locales.normalize(request.locale(), profile.defaultLocale()));

        Instant now = clock.instant();
        user.setTermsVersion(profile.termsVersion());
        user.setTermsAcceptedAt(now);
        user.setPrivacyVersion(profile.privacyVersion());
        user.setPrivacyAcceptedAt(now);
        User saved = repository.save(user);

        if (address != null) {
            addressService.create(saved.getId(), request.address().label(), address);
        }
        if (business != null) {
            businessService.create(saved.getId(), business);
        }
        return toResponseWithConsent(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorUser.USER002_USER_NOTFOUND, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(Rol rol, Pageable pageable) {
        Page<User> page = rol == null
                ? repository.findByDeletedAtIsNull(pageable)
                : repository.findByRolAndDeletedAtIsNull(rol, pageable);
        return PageResponse.from(page.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public UserResponse findMe(UUID id) {
        return toResponseWithConsent(getActiveOrThrow(id));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = getActiveOrThrow(id);
        RegionProfileView profile = profileOf(user);
        if (request.givenName() != null) user.setGivenName(request.givenName());
        if (request.familyName() != null) user.setFamilyName(request.familyName());
        user.setName(profile.nameOrder().compose(user.getGivenName(), user.getFamilyName()));
        if (request.locale() != null) {
            user.setLocale(Locales.normalize(request.locale(), profile.defaultLocale()));
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank()
                    ? null
                    : phoneNormalizer.toE164(request.phone(), user.getCountryCode()));
        }
        return toResponseWithConsent(user);
    }

    @Transactional
    public void updatePassword(UUID id, ChangePasswordRequest request) {
        User user = getActiveOrThrow(id);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorUser.USER005_WRONG_PASSWORD);
        } else if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorUser.USER006_SAME_PASSWORD);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void deactivate(UUID id) {
        User user = getOrThrow(id);
        if (user.getDeletedAt() == null) {
            user.setActive(false);
            publishAccess(user);
        }
    }

    @Transactional
    public void activate(UUID id) {
        User user = getOrThrow(id);
        if (user.getDeletedAt() != null) {
            throw new ApiException(ErrorUser.USER002_USER_NOTFOUND, id);
        }
        user.setActive(true);
        publishAccess(user);
    }

    @Transactional
    public UserResponse updateVerification(UUID id, VerificationState state) {
        User user = getOrThrow(id);
        if (user.getDeletedAt() != null) {
            throw new ApiException(ErrorUser.USER002_USER_NOTFOUND, id);
        }
        user.setVerificationState(state);
        publishAccess(user);
        return mapper.toResponse(user);
    }

    @Transactional
    public void deleteAccount(UUID id, String currentPassword) {
        User user = getActiveOrThrow(id);
        requireNotAdmin(user);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorUser.USER005_WRONG_PASSWORD);
        }
        anonymize(user);
        publishAccess(user);
    }

    @Transactional
    public void adminDelete(UUID id) {
        User user = getOrThrow(id);
        if (user.getDeletedAt() == null) {
            requireNotAdmin(user);
            anonymize(user);
            publishAccess(user);
        }
    }

    /** Borrado seguro: anonimiza la cuenta y elimina direcciones y perfil empresarial. */
    private void anonymize(User user) {
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setName("Deleted user");
        user.setGivenName("Deleted user");
        user.setFamilyName(null);
        user.setPhone(null);
        user.setPasswordHash("!deleted");
        user.setVerificationState(VerificationState.PENDING);
        user.setActive(false);
        user.setDeletedAt(clock.instant());
        businessService.deleteOf(user.getId());
        addressService.deleteAllOf(user.getId());
    }

    private void requireNotAdmin(User user) {
        if (user.getRol() == Rol.ADMIN) {
            throw new ApiException(ErrorUser.USER009_ADMIN_DELETE_FORBIDDEN);
        }
    }

    private User getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorUser.USER002_USER_NOTFOUND, id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findSummaryById(UUID id) {
        return repository.findById(id)
                .filter(User::isActive)
                .map(u -> new UserSummary(u.getId(), u.getName(), u.getRol(),
                        u.getVerificationState() == VerificationState.VERIFIED,
                        u.getCountryCode(), u.getLocale()));
    }

    private void publishAccess(User user) {
        boolean eligible = user.isActive()
                && user.getVerificationState() == VerificationState.VERIFIED;
        events.publishEvent(new UserAccessChangedEvent(user.getId(), eligible));
    }

    private User getActiveOrThrow(UUID id) {
        User user = getOrThrow(id);
        if (!user.isActive() || user.getDeletedAt() != null) {
            throw new ApiException(ErrorUser.USER002_USER_NOTFOUND, id);
        }
        return user;
    }

    private RegionProfileView profileOf(User user) {
        return regionApi.find(user.getCountryCode().toUpperCase(Locale.ROOT)).orElseGet(regionApi::current);
    }

    private UserResponse toResponseWithConsent(User user) {
        RegionProfileView profile = profileOf(user);
        boolean upToDate = profile.termsVersion().equals(user.getTermsVersion())
                && profile.privacyVersion().equals(user.getPrivacyVersion());
        return mapper.toResponse(user).withConsentUpToDate(upToDate);
    }
}
