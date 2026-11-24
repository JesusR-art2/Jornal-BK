package com.jornal.users.services;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.jornal.shared.ApiException;
import com.jornal.users.enums.ErrorUser;
import org.springframework.stereotype.Component;

@Component
public class PhoneNormalizer {

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    public String toE164(String raw, String countryCode){

        try {
            Phonenumber.PhoneNumber n = util.parse(raw, countryCode);
            if (!util.isValidNumberForRegion(n, countryCode)){
                throw new ApiException(ErrorUser.USER008_INVALID_PHONE);
            }
            return util.format(n, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e){

            throw  new ApiException(ErrorUser.USER008_INVALID_PHONE);
        }

    }

}
