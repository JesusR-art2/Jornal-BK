package com.jornal.users.services;

import com.jornal.region.AddressField;
import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import com.jornal.shared.ApiException;
import com.jornal.users.Address;
import com.jornal.users.AddressRepository;
import com.jornal.users.BusinessProfileRepository;
import com.jornal.users.dto.AddressRequest;
import com.jornal.users.dto.AddressResponse;
import com.jornal.users.enums.ErrorUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Direcciones del usuario, validadas con el esquema del país de esta región. */
@Service
@RequiredArgsConstructor
public class AddressService {

    static final int MAX_ADDRESSES = 10;

    private final AddressRepository repository;
    private final BusinessProfileRepository businessRepository;
    private final RegionApi regionApi;

    /** Valida y normaliza los campos; no guarda nada. */
    public Map<String, String> normalize(AddressRequest request) {
        return regionApi.validateAddress(regionApi.currentCode(), request.fields());
    }

    @Transactional
    public Address create(UUID userId, String label, Map<String, String> normalizedFields) {
        if (repository.countByUserId(userId) >= MAX_ADDRESSES) {
            throw new ApiException(ErrorUser.USER015_ADDRESS_LIMIT, MAX_ADDRESSES);
        }
        Address address = new Address();
        address.setUserId(userId);
        address.setLabel(blankToNull(label));
        address.setCountryCode(regionApi.currentCode());
        address.getFields().putAll(normalizedFields);
        return repository.save(address);
    }

    @Transactional
    public AddressResponse add(UUID userId, AddressRequest request) {
        return toResponse(create(userId, request.label(), normalize(request)));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAt(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Address getOwned(UUID userId, UUID addressId) {
        return repository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApiException(ErrorUser.USER014_ADDRESS_NOT_FOUND, addressId));
    }

    @Transactional
    public void replaceFields(Address address, String label, Map<String, String> normalizedFields) {
        address.setLabel(blankToNull(label));
        address.getFields().clear();
        address.getFields().putAll(normalizedFields);
    }

    @Transactional
    public void delete(UUID userId, UUID addressId) {
        Address address = getOwned(userId, addressId);
        if (businessRepository.existsByBillingAddressId(address.getId())) {
            throw new ApiException(ErrorUser.USER016_ADDRESS_IN_USE);
        }
        repository.delete(address);
    }

    @Transactional
    public void deleteAllOf(UUID userId) {
        repository.deleteByUserId(userId);
    }

    public AddressResponse toResponse(Address address) {
        RegionProfileView profile = regionApi.find(address.getCountryCode()).orElseGet(regionApi::current);
        Map<String, String> ordered = new LinkedHashMap<>();
        for (AddressField field : profile.addressSchema()) {
            String value = address.getFields().get(field.fieldKey());
            if (value != null) ordered.put(field.fieldKey(), value);
        }
        address.getFields().forEach(ordered::putIfAbsent);   // campos de un esquema anterior
        return new AddressResponse(address.getId(), address.getLabel(), address.getCountryCode(), ordered,
                regionApi.formatAddress(address.getCountryCode(), ordered));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
