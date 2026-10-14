package com.pmplatform.procurement.config.api;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.procurement.config.ApprovalStage;
import com.pmplatform.procurement.config.ProcurementSettingsService;
import com.pmplatform.procurement.config.api.SettingsDtos.CheckResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.CommitteeRuleRequest;
import com.pmplatform.procurement.config.api.SettingsDtos.CommitteeRuleResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.MethodRequest;
import com.pmplatform.procurement.config.api.SettingsDtos.MethodResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.SettingsResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.ThresholdRequest;
import com.pmplatform.procurement.config.api.SettingsDtos.TemplateRequest;
import com.pmplatform.procurement.config.api.SettingsDtos.TemplateResponse;
import com.pmplatform.procurement.config.api.SettingsDtos.ThresholdResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Procurement settings per tenant: methods, approval thresholds and evaluation
 * committee rules. In committee-rule paths, {@code DEFAULT} means the default rule.
 */
@RestController
@RequestMapping("/api/v1/procurement/settings")
public class ProcurementSettingsController {

    private static final String DEFAULT_KEY = "DEFAULT";

    /**
     * Thresholds and methods decide who approves what, so only the head of procurement may change them
     * (not system administrators, who already decide who holds which role).
     */
    static final Set<String> EDITORS = Set.of(Roles.HEAD_OF_PROCUREMENT);

    private final ProcurementSettingsService service;
    private final CurrentUserService currentUser;

    public ProcurementSettingsController(ProcurementSettingsService service, CurrentUserService currentUser) {
        this.currentUser = currentUser;
        this.service = service;
    }

    @GetMapping
    public SettingsResponse all() {
        return service.all(TenantContext.require());
    }

    /** Example: {@code /check?stage=AWARD&method=RFQ&amountMinor=150000000} */
    @GetMapping("/check")
    public CheckResponse check(@RequestParam ApprovalStage stage,
            @RequestParam(required = false) String method,
            @RequestParam long amountMinor,
            @RequestParam(defaultValue = "KES") String currency) {
        return service.check(TenantContext.require(), stage,
                method == null || method.isBlank() ? null : code(method), amountMinor,
                currency.toUpperCase(Locale.ROOT));
    }

    // ---- methods (PUT creates or updates)
    @PutMapping("/methods/{code}")
    public MethodResponse saveMethod(@PathVariable String code, @Valid @RequestBody MethodRequest body) {
        requireEditor();
        return service.saveMethod(TenantContext.require(), code(code), body);
    }

    // ---- approval thresholds
    @PostMapping("/approval-thresholds")
    public ResponseEntity<ThresholdResponse> createThreshold(@Valid @RequestBody ThresholdRequest body) {
        requireEditor();
        ThresholdResponse created = service.createThreshold(TenantContext.require(), body);
        return ResponseEntity
                .created(URI.create("/api/v1/procurement/settings/approval-thresholds/" + created.id()))
                .body(created);
    }

    @PutMapping("/approval-thresholds/{id}")
    public ThresholdResponse updateThreshold(@PathVariable UUID id,
            @Valid @RequestBody ThresholdRequest body) {
        requireEditor();
        return service.updateThreshold(TenantContext.require(), id, body);
    }

    @DeleteMapping("/approval-thresholds/{id}")
    public ResponseEntity<Void> deleteThreshold(@PathVariable UUID id) {
        requireEditor();
        service.deleteThreshold(TenantContext.require(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- evaluation committee rules (PUT creates or updates)
    @PutMapping("/committee-rules/{methodCode}")
    public CommitteeRuleResponse saveCommitteeRule(@PathVariable String methodCode,
            @Valid @RequestBody CommitteeRuleRequest body) {
        requireEditor();
        return service.saveCommitteeRule(TenantContext.require(), methodKey(methodCode), body);
    }

    @DeleteMapping("/committee-rules/{methodCode}")
    public ResponseEntity<Void> deleteCommitteeRule(@PathVariable String methodCode) {
        requireEditor();
        service.deleteCommitteeRule(TenantContext.require(), methodKey(methodCode));
        return ResponseEntity.noContent().build();
    }

    // ---- evaluation criteria templates (PUT creates or updates)
    @PutMapping("/evaluation-templates/{code}")
    public TemplateResponse saveTemplate(@PathVariable String code, @Valid @RequestBody TemplateRequest body) {
        requireEditor();
        return service.saveTemplate(TenantContext.require(), code(code), body);
    }

    @DeleteMapping("/evaluation-templates/{code}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String code) {
        requireEditor();
        service.deleteTemplate(TenantContext.require(), code(code));
        return ResponseEntity.noContent().build();
    }

    private static String methodKey(String pathValue) {
        String c = code(pathValue);
        return DEFAULT_KEY.equals(c) ? null : c;
    }

    private static String code(String raw) {
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z][A-Z0-9_]{0,39}")) {
            throw new BusinessRuleException(
                    "Method codes use capital letters, digits and underscores, e.g. OPEN_TENDER");
        }
        return c;
    }

    private void requireEditor() {
        Roles.requireAny(currentUser.require(), EDITORS, "change procurement settings");
    }
}
