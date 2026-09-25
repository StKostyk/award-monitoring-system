package ua.edu.chnu.awards.delegation.controller;

import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.delegation.entity.DelegationState;

/**
 * Reads the {@code state} query parameter, which the API spells in lower case.
 */
@Component
public class DelegationStateConverter implements Converter<String, DelegationState> {

    @Override
    public DelegationState convert(String source) {
        return DelegationState.valueOf(source.toUpperCase(Locale.ROOT));
    }
}
