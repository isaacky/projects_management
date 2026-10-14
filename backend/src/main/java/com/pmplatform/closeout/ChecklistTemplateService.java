package com.pmplatform.closeout;

import com.pmplatform.closeout.api.CloseoutDtos.ItemRequest;
import com.pmplatform.closeout.api.CloseoutDtos.TemplateItemView;
import com.pmplatform.closeout.api.CloseoutDtos.TemplateView;
import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The organisation's standard completion checklist, copied into each project when closeout starts. */
@Service
public class ChecklistTemplateService {

    private final ChecklistTemplateRepository templates;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final Clock clock;
    private final CloseoutLocks locks;

    public ChecklistTemplateService(ChecklistTemplateRepository templates, CurrentUserService currentUser,
            AuditService audit, Clock clock, CloseoutLocks locks) {
        this.templates = templates;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.locks = locks;
    }

    @Transactional(readOnly = true)
    public TemplateView get(UUID tenantId) {
        User me = currentUser.require();
        return view(tenantId, me.hasRole(Roles.PMO_LEAD));
    }

    /** Replaces the whole list (changes apply to closeouts started afterwards). */
    @Transactional
    public TemplateView save(UUID tenantId, List<ItemRequest> items) {
        User me = currentUser.require();
        Roles.requireAny(me, Set.of(Roles.PMO_LEAD), "change the standard completion checklist");
        locks.template(tenantId);
        templates.deleteAll(templates.findByTenantIdOrderBySortOrderAsc(tenantId));
        templates.flush();
        Instant now = Instant.now(clock);
        int order = 1;
        for (ItemRequest in : items) {
            templates.save(new ChecklistTemplateItem(tenantId, order++, in.title(), in.description(),
                    in.responsibleRole(), Boolean.TRUE.equals(in.evidenceRequired()), now));
        }
        audit.record(tenantId, "closeout.template_saved", "Tenant", tenantId, JsonText.object("items", items.size()));
        templates.flush();
        return view(tenantId, true);
    }

    /** What a new closeout starts with: the organisation's list, or the built-in one if it has none. */
    @Transactional(readOnly = true)
    public List<ChecklistDefaults.Item> itemsFor(UUID tenantId) {
        List<ChecklistTemplateItem> own = templates.findByTenantIdOrderBySortOrderAsc(tenantId);
        if (own.isEmpty()) {
            return ChecklistDefaults.ITEMS;
        }
        return own.stream().map(t -> new ChecklistDefaults.Item(t.getTitle(), t.getDescription(),
                t.getResponsibleRole(), t.isEvidenceRequired())).toList();
    }

    private TemplateView view(UUID tenantId, boolean canEdit) {
        List<ChecklistTemplateItem> own = templates.findByTenantIdOrderBySortOrderAsc(tenantId);
        List<TemplateItemView> items = new ArrayList<>();
        if (own.isEmpty()) {
            int i = 1;
            for (ChecklistDefaults.Item d : ChecklistDefaults.ITEMS) {
                items.add(new TemplateItemView(null, i++, d.title(), d.description(), d.responsibleRole(),
                        d.evidenceRequired()));
            }
        } else {
            own.forEach(t -> items.add(new TemplateItemView(t.getId(), t.getSortOrder(), t.getTitle(),
                    t.getDescription(), t.getResponsibleRole(), t.isEvidenceRequired())));
        }
        return new TemplateView(own.isEmpty(), items, canEdit);
    }
}
