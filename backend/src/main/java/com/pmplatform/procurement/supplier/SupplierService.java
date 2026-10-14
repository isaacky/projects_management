package com.pmplatform.procurement.supplier;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.procurement.supplier.SupplierImportParser.Field;
import com.pmplatform.procurement.supplier.SupplierImportParser.Row;
import com.pmplatform.procurement.supplier.api.SupplierDtos.ImportResult;
import com.pmplatform.procurement.supplier.api.SupplierDtos.ImportRow;
import com.pmplatform.procurement.supplier.api.SupplierDtos.SupplierRequest;
import com.pmplatform.procurement.supplier.api.SupplierDtos.SupplierResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    public static final int MAX_IMPORT_ROWS = 20_000;
    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    private final SupplierRepository suppliers;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final Clock clock;

    public SupplierService(SupplierRepository suppliers, CurrentUserService currentUser, AuditService audit,
            Clock clock) {
        this.suppliers = suppliers;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(UUID tenantId) {
        LocalDate today = LocalDate.now(clock.withZone(EAT));
        return suppliers.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(s -> SupplierResponse.from(s, today)).toList();
    }

    @Transactional(readOnly = true)
    public Supplier get(UUID tenantId, UUID id) {
        return suppliers.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Supplier " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Supplier> byIds(UUID tenantId, Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : suppliers.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
    }

    @Transactional
    public SupplierResponse create(UUID tenantId, SupplierRequest req) {
        Roles.requireAny(currentUser.require(), Roles.PROCUREMENT, "add suppliers");
        String pin = req.kraPin().toUpperCase(Locale.ROOT);
        if (suppliers.findByTenantIdAndKraPin(tenantId, pin).isPresent()) {
            throw new BusinessRuleException("A supplier with KRA PIN " + pin + " is already registered");
        }
        Instant now = Instant.now(clock);
        Supplier s = new Supplier(tenantId, pin, now);
        apply(s, req, now);
        suppliers.save(s);
        audit.record(tenantId, "supplier.created", "Supplier", s.getId(),
                JsonText.object("kraPin", pin, "name", s.getName(), "status", s.getStatus()));
        return SupplierResponse.from(s, LocalDate.now(clock.withZone(EAT)));
    }

    @Transactional
    public SupplierResponse update(UUID tenantId, UUID id, SupplierRequest req) {
        Roles.requireAny(currentUser.require(), Roles.PROCUREMENT, "change suppliers");
        Supplier s = get(tenantId, id);
        if (!s.getKraPin().equalsIgnoreCase(req.kraPin())) {
            throw new BusinessRuleException("The KRA PIN identifies the supplier and cannot be changed");
        }
        SupplierStatus before = s.getStatus();
        apply(s, req, Instant.now(clock));
        audit.record(tenantId, "supplier.updated", "Supplier", s.getId(),
                JsonText.object("kraPin", s.getKraPin(), "name", s.getName(), "statusBefore", before,
                        "status", s.getStatus()));
        return SupplierResponse.from(s, LocalDate.now(clock.withZone(EAT)));
    }

    /**
     * Imports a CSV supplier list. With {@code apply=false} nothing is saved and the
     * result is a preview. Rows are matched to existing suppliers by KRA PIN: new PINs
     * are created, known PINs are updated. Rows with errors are skipped.
     */
    @Transactional
    public ImportResult importCsv(UUID tenantId, String csv, boolean apply) {
        Roles.requireAny(currentUser.require(), Roles.PROCUREMENT, "import suppliers");
        SupplierImportParser.Result parsed = SupplierImportParser.parse(csv);
        Map<String, String> columns = new LinkedHashMap<>();
        parsed.columns().forEach((f, header) -> columns.put(f.name(), header));
        if (!parsed.fileErrors().isEmpty()) {
            return new ImportResult(false, columns, parsed.unmappedColumns(), parsed.fileErrors(), 0, 0, 0, List.of());
        }
        if (parsed.rows().size() > MAX_IMPORT_ROWS) {
            throw new BusinessRuleException("The file has " + parsed.rows().size() + " rows; import at most "
                    + MAX_IMPORT_ROWS + " at a time");
        }

        Map<String, Supplier> existing = suppliers.findByTenantIdAndKraPinIn(tenantId,
                parsed.rows().stream().map(r -> r.get(Field.KRA_PIN)).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Supplier::getKraPin, Function.identity()));

        Instant now = Instant.now(clock);
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<ImportRow> rows = new java.util.ArrayList<>();
        for (Row row : parsed.rows()) {
            Map<String, String> values = new LinkedHashMap<>();
            row.values().forEach((f, v) -> values.put(f.name(), v));
            if (!row.valid()) {
                skipped++;
                rows.add(new ImportRow(row.line(), "SKIP", values, row.errors(), row.warnings()));
                continue;
            }
            Supplier s = existing.get(row.get(Field.KRA_PIN));
            boolean isNew = s == null;
            if (apply) {
                if (isNew) {
                    s = new Supplier(tenantId, row.get(Field.KRA_PIN), now);
                }
                applyRow(s, row, now);
                suppliers.save(s);
            }
            if (isNew) {
                created++;
            } else {
                updated++;
            }
            rows.add(new ImportRow(row.line(), isNew ? "CREATE" : "UPDATE", values, row.errors(), row.warnings()));
        }
        if (apply) {
            audit.record(tenantId, "supplier.imported", "SupplierImport", UUID.nameUUIDFromBytes(
                            (tenantId + "/" + now).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    JsonText.object("created", created, "updated", updated, "skipped", skipped));
        }
        return new ImportResult(apply, columns, parsed.unmappedColumns(), List.of(), created, updated, skipped, rows);
    }

    private static void apply(Supplier s, SupplierRequest req, Instant now) {
        s.update(req.name().trim(), blank(req.registrationNo()), blank(req.email()), blank(req.phone()),
                blank(req.address()), blank(req.categories()), req.agpoCategory(), blank(req.agpoCertificateNo()),
                req.agpoExpiry(), req.status(), now);
    }

    /** Imported values replace stored ones only where the file has a value. */
    private static void applyRow(Supplier s, Row r, Instant now) {
        String agpo = r.get(Field.AGPO_CATEGORY);
        String expiry = r.get(Field.AGPO_EXPIRY);
        String status = r.get(Field.STATUS);
        s.update(r.get(Field.NAME),
                or(r.get(Field.REGISTRATION_NO), s.getRegistrationNo()),
                or(r.get(Field.EMAIL), s.getEmail()),
                or(r.get(Field.PHONE), s.getPhone()),
                or(r.get(Field.ADDRESS), s.getAddress()),
                or(r.get(Field.CATEGORIES), s.getCategories()),
                agpo != null ? AgpoCategory.valueOf(agpo) : s.getAgpoCategory(),
                or(r.get(Field.AGPO_CERTIFICATE_NO), s.getAgpoCertificateNo()),
                expiry != null ? LocalDate.parse(expiry) : s.getAgpoExpiry(),
                status != null ? SupplierStatus.valueOf(status) : s.getStatus(),
                now);
    }

    private static String or(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
