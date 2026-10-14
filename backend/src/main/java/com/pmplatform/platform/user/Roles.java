package com.pmplatform.platform.user;

import com.pmplatform.platform.web.ForbiddenActionException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Role codes used by the application, and a guard for role checks. */
public final class Roles {

    public static final String PROCUREMENT_OFFICER = "PROCUREMENT_OFFICER";
    public static final String HEAD_OF_PROCUREMENT = "HEAD_OF_PROCUREMENT";
    /** Appoints evaluation committees and, in the sample settings, approves awards. */
    public static final String ACCOUNTING_OFFICER = "ACCOUNTING_OFFICER";
    /** Manages delivery of contracts: accepts milestones, certifies invoices. */
    public static final String CONTRACT_MANAGER = "CONTRACT_MANAGER";
    /** Plans and runs projects: schedule, progress, risks, issues and status reports. */
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    /** Portfolio office: everything a project manager can do, plus re-baselining and report review. */
    public static final String PMO_LEAD = "PMO_LEAD";
    public static final Set<String> PLAN_EDITORS = Set.of(PROJECT_MANAGER, PMO_LEAD);
    /** Approves payment certificates and records payments. */
    public static final String FINANCE_OFFICER = "FINANCE_OFFICER";

    /** Approves requisitions for their department (in the sample approval thresholds). */
    public static final String HEAD_OF_DEPARTMENT = "HEAD_OF_DEPARTMENT";
    /** Adds people, assigns their roles, and deactivates leavers. Grants no project or procurement rights. */
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    /** Role codes: upper case letters, digits and underscores. */
    public static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]{1,63}$";

    /**
     * The roles the application itself checks, with what each allows. Approval thresholds may name
     * other role codes too (procurement settings); administrators can assign those as well.
     */
    public static final Map<String, String> CATALOGUE = catalogue();

    private static Map<String, String> catalogue() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(PMO_LEAD, "Portfolio office: everything a project manager can do, plus re-baselining, status report "
                + "review, closeout acceptance and the procurement plan.");
        m.put(PROJECT_MANAGER, "Plans and runs projects: schedule, progress, risks, issues, status reports, benefits.");
        m.put(HEAD_OF_DEPARTMENT, "Approves requisitions (as set in the approval thresholds).");
        m.put(PROCUREMENT_OFFICER, "Manages suppliers, tenders, bids and contracts paperwork.");
        m.put(HEAD_OF_PROCUREMENT, "Procurement officer rights, plus professional opinions, evaluation reviews and the "
                + "procurement settings (methods, approval thresholds, committee rules).");
        m.put(ACCOUNTING_OFFICER, "Appoints evaluation committees; approves awards, unplanned requisitions and "
                + "variations (as set in the approval thresholds).");
        m.put(CONTRACT_MANAGER, "Manages contract delivery: accepts milestones, certifies invoices.");
        m.put(FINANCE_OFFICER, "Approves payment certificates and records payments.");
        m.put(SYSTEM_ADMIN, "Adds people, assigns roles and deactivates leavers.");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** May manage suppliers, tenders and bids. */
    public static final Set<String> PROCUREMENT = Set.of(PROCUREMENT_OFFICER, HEAD_OF_PROCUREMENT);

    private Roles() {
    }

    public static void requireAny(User user, Set<String> roles, String action) {
        if (user.getRoles().stream().noneMatch(roles::contains)) {
            throw new ForbiddenActionException("You need one of these roles to " + action + ": "
                    + String.join(", ", roles.stream().sorted().toList()));
        }
    }

    public static boolean hasAny(User user, String... roles) {
        return Arrays.stream(roles).anyMatch(user::hasRole);
    }
}
