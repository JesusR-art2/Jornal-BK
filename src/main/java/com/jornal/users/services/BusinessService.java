package com.jornal.users.services;

import com.jornal.region.DocumentKind;
import com.jornal.region.RegionApi;
import com.jornal.shared.ApiException;
import com.jornal.users.Address;
import com.jornal.users.BusinessProfile;
import com.jornal.users.BusinessProfileRepository;
import com.jornal.users.dto.BusinessRequest;
import com.jornal.users.dto.BusinessResponse;
import com.jornal.users.enums.ErrorUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Perfil empresarial del rol BUSINESS (comercio que contrata). */
@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessProfileRepository repository;
    private final AddressService addressService;
    private final RegionApi regionApi;

    /** Datos validados de un comercio, listos para guardar. */
    public record ValidBusiness(String legalName, String taxIdType, String taxIdNumber,
                                String billingLabel, Map<String, String> billingFields) {
    }

    /** Valida la identificación fiscal con la estrategia del país y la dirección de facturación. No guarda. */
    public ValidBusiness validate(BusinessRequest request, UUID userIdOrNull) {
        String taxId = regionApi.validateDocument(regionApi.currentCode(),
                request.taxIdType(), request.taxIdNumber(), DocumentKind.TAX);
        boolean taken = userIdOrNull == null
                ? repository.existsByTaxIdTypeAndTaxIdNumber(request.taxIdType(), taxId)
                : repository.existsByTaxIdTypeAndTaxIdNumberAndUserIdNot(request.taxIdType(), taxId, userIdOrNull);
        if (taken) {
            throw new ApiException(ErrorUser.USER013_TAX_ID_REGISTERED);
        }
        Map<String, String> billing = addressService.normalize(request.billingAddress());
        return new ValidBusiness(request.legalName(), request.taxIdType(), taxId,
                request.billingAddress().label(), billing);
    }

    @Transactional
    public BusinessResponse create(UUID userId, ValidBusiness data) {
        Address billing = addressService.create(userId, data.billingLabel(), data.billingFields());
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setLegalName(data.legalName());
        profile.setTaxIdType(data.taxIdType());
        profile.setTaxIdNumber(data.taxIdNumber());
        profile.setBillingAddressId(billing.getId());
        return toResponse(repository.save(profile), billing);
    }

    @Transactional(readOnly = true)
    public BusinessResponse get(UUID userId) {
        BusinessProfile profile = getOrThrow(userId);
        return toResponse(profile, addressService.getOwned(userId, profile.getBillingAddressId()));
    }

    @Transactional
    public BusinessResponse update(UUID userId, BusinessRequest request) {
        BusinessProfile profile = getOrThrow(userId);
        ValidBusiness data = validate(request, userId);
        profile.setLegalName(data.legalName());
        profile.setTaxIdType(data.taxIdType());
        profile.setTaxIdNumber(data.taxIdNumber());
        Address billing = addressService.getOwned(userId, profile.getBillingAddressId());
        addressService.replaceFields(billing, data.billingLabel(), data.billingFields());
        return toResponse(profile, billing);
    }

    @Transactional
    public void deleteOf(UUID userId) {
        repository.findById(userId).ifPresent(repository::delete);
        repository.flush();
    }

    private BusinessProfile getOrThrow(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorUser.USER017_BUSINESS_PROFILE_NOT_FOUND));
    }

    private BusinessResponse toResponse(BusinessProfile profile, Address billing) {
        return new BusinessResponse(profile.getLegalName(), profile.getTaxIdType(), profile.getTaxIdNumber(),
                addressService.toResponse(billing));
    }
}
