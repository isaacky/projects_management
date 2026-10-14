package com.pmplatform.procurement.supplier.api;

import com.pmplatform.procurement.supplier.AgpoCategory;
import com.pmplatform.procurement.supplier.Supplier;
import com.pmplatform.procurement.supplier.SupplierStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record SupplierRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z]\\d{9}[A-Za-z]", message = "letter, 9 digits, letter (e.g. P051234567X)")
            String kraPin,
            @Size(max = 64) String registrationNo,
            @Email @Size(max = 320) String email,
            @Size(max = 32) String phone,
            @Size(max = 500) String address,
            @Size(max = 500) String categories,
            AgpoCategory agpoCategory,
            @Size(max = 64) String agpoCertificateNo,
            LocalDate agpoExpiry,
            @NotNull SupplierStatus status) {
    }

    public record SupplierResponse(UUID id, String name, String kraPin, String registrationNo, String email,
            String phone, String address, String categories, AgpoCategory agpoCategory, String agpoCertificateNo,
            LocalDate agpoExpiry, boolean agpoValid, SupplierStatus status, Instant updatedAt, long version) {

        public static SupplierResponse from(Supplier s, LocalDate today) {
            boolean agpoValid = s.agpoValidOn(today);
            return new SupplierResponse(s.getId(), s.getName(), s.getKraPin(), s.getRegistrationNo(), s.getEmail(),
                    s.getPhone(), s.getAddress(), s.getCategories(), s.getAgpoCategory(), s.getAgpoCertificateNo(),
                    s.getAgpoExpiry(), agpoValid, s.getStatus(), s.getUpdatedAt(), s.getVersion());
        }
    }

    /** One row of an import preview. {@code action}: CREATE, UPDATE or SKIP (has errors). */
    public record ImportRow(int line, String action, Map<String, String> values, List<String> errors,
            List<String> warnings) {
    }

    public record ImportResult(boolean applied, Map<String, String> columns, List<String> unmappedColumns,
            List<String> fileErrors, int toCreate, int toUpdate, int skipped, List<ImportRow> rows) {
    }
}
