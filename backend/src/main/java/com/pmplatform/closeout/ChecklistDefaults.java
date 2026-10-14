package com.pmplatform.closeout;

import java.util.List;

/** The completion checklist used when an organisation has not set up its own. */
public final class ChecklistDefaults {

    public record Item(String title, String description, String responsibleRole, boolean evidenceRequired) {
    }

    public static final List<Item> ITEMS = List.of(
            new Item("Practical completion certificate issued",
                    "Signed by the engineer or project manager for every works contract.", "PROJECT_MANAGER", true),
            new Item("Final inspection and commissioning tests passed",
                    "Test certificates and the energisation or commissioning report.", "PROJECT_MANAGER", true),
            new Item("As-built drawings received", "Drawings and line or network data updated in GIS.", null, true),
            new Item("Operation and maintenance manuals received", null, null, true),
            new Item("Spares and special tools handed over", null, null, false),
            new Item("Operators and maintenance staff trained", "Attendance register of the training.", null, false),
            new Item("Assets recorded for the asset register", "Asset list with values given to finance.",
                    "FINANCE_OFFICER", false),
            new Item("Wayleave and land documents filed", "Consents, compensation records and easements.", null, false),
            new Item("Final account agreed with each contractor", null, "CONTRACT_MANAGER", false),
            new Item("Site cleared and reinstated", null, null, false),
            new Item("Safety and environmental conditions closed out",
                    "Incidents closed; NEMA licence conditions met.", null, false),
            new Item("Project records archived", null, null, false));

    private ChecklistDefaults() {
    }
}
