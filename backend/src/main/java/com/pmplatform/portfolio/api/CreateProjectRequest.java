package com.pmplatform.portfolio.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]*",
                message = "letters, digits and hyphens only") String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @Size(max = 200) String sponsor,
        @PositiveOrZero long budgetMinor,
        @Pattern(regexp = "[A-Z]{3}", message = "ISO 4217 code, for example KES") String currency) {

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "KES" : currency;
    }
}
