package com.pmplatform.contracts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Money and consistency rules for contracts. Pure functions, tested without a database.
 */
public final class ContractRules {

    /**
     * Cumulative value increases by variation may not exceed 25% of the original contract price
     * (Public Procurement and Asset Disposal Act, 2015, s.139).
     */
    public static final int VARIATION_CAP_BP = 2500;

    private ContractRules() {
    }

    /** Payment position of a contract. All amounts in minor units. */
    public record Totals(long invoiced, long certified, long paid, long retentionHeld, long awaitingPayment) {
    }

    /** Retention withheld from a gross amount, rounded to the nearest cent. */
    public static long retention(long grossMinor, int retentionBp) {
        return (grossMinor * retentionBp + 5_000) / 10_000;
    }

    public static String variationCapProblem(long originalMinor, long approvedIncreasesMinor, long changeMinor) {
        if (changeMinor <= 0) {
            return null;
        }
        long cap = originalMinor * VARIATION_CAP_BP / 10_000;
        if (approvedIncreasesMinor + changeMinor > cap) {
            return "Increases by variation may not exceed 25% of the original contract price: "
                    + "the limit is " + cap + ", already approved " + approvedIncreasesMinor
                    + ", this variation " + changeMinor + " (minor units). A larger change needs a new procurement.";
        }
        return null;
    }

    /** A new invoice must fit within what is left of the contract value (ordinary invoices only). */
    public static String invoiceCapProblem(long currentValueMinor, Collection<Invoice> existing, long grossMinor) {
        long invoiced = existing.stream().filter(i -> i.getKind() == InvoiceKind.INVOICE && i.counts())
                .mapToLong(Invoice::getGrossMinor).sum();
        if (invoiced + grossMinor > currentValueMinor) {
            return "This invoice would bring the total invoiced to " + (invoiced + grossMinor)
                    + ", above the contract value of " + currentValueMinor + " (minor units)";
        }
        return null;
    }

    public static String milestoneCapProblem(Milestone m, Collection<Invoice> existing, long grossMinor) {
        long invoiced = existing.stream().filter(i -> m.getId().equals(i.getMilestoneId()) && i.counts())
                .mapToLong(Invoice::getGrossMinor).sum();
        if (invoiced + grossMinor > m.getAmountMinor()) {
            return "Milestone \"" + m.getTitle() + "\" is worth " + m.getAmountMinor() + "; " + invoiced
                    + " is already invoiced against it (minor units)";
        }
        return null;
    }

    /** Sum of the milestones that are not cancelled. */
    public static long scheduled(Collection<Milestone> milestones) {
        return milestones.stream().filter(m -> m.getStatus() != MilestoneStatus.CANCELLED)
                .mapToLong(Milestone::getAmountMinor).sum();
    }

    /** What still stops a draft contract from being signed, in plain words; empty when it can be signed. */
    public static List<String> signingProblems(Contract c, Collection<Milestone> milestones,
            Collection<Security> securities, boolean signedDocument, Instant now) {
        List<String> out = new ArrayList<>();
        if (now.isBefore(c.getStandstillEndsAt())) {
            out.add("The standstill period runs until " + c.getStandstillEndsAt());
        }
        if (c.getReviewStatus() == ReviewStatus.LODGED) {
            out.add("A request for review is pending before the Review Board");
        }
        if (c.getStartDate() == null || c.getEndDate() == null) {
            out.add("Set the start and end dates");
        }
        if (c.getContractManagerId() == null) {
            out.add("Assign the contract manager");
        }
        if (milestones.isEmpty()) {
            out.add("Add the payment schedule (milestones)");
        } else if (scheduled(milestones) != c.getCurrentValueMinor()) {
            out.add("The milestones add up to " + scheduled(milestones) + " but the contract value is "
                    + c.getCurrentValueMinor() + " (minor units)");
        }
        long held = securities.stream().filter(s -> s.getKind() == SecurityKind.PERFORMANCE
                && s.getStatus() == SecurityStatus.HELD).mapToLong(Security::getAmountMinor).sum();
        if (c.getPerformanceSecurityMinor() > 0 && held < c.getPerformanceSecurityMinor()) {
            out.add("Record the performance security (required " + c.getPerformanceSecurityMinor()
                    + ", held " + held + ")");
        }
        if (!signedDocument) {
            out.add("Upload the signed contract");
        }
        return out;
    }

    public static Totals totals(Collection<Invoice> invoices) {
        long invoiced = 0;
        long certified = 0;
        long paid = 0;
        long retained = 0;
        long released = 0;
        long awaiting = 0;
        for (Invoice i : invoices) {
            if (!i.counts()) {
                continue;
            }
            boolean ordinary = i.getKind() == InvoiceKind.INVOICE;
            if (ordinary) {
                invoiced += i.getGrossMinor();
            }
            boolean certifiedOrLater = i.getStatus() == InvoiceStatus.CERTIFIED
                    || i.getStatus() == InvoiceStatus.APPROVED || i.getStatus() == InvoiceStatus.PAID;
            if (ordinary && certifiedOrLater) {
                certified += i.getGrossMinor();
            }
            if (i.getStatus() == InvoiceStatus.PAID) {
                paid += i.netMinor();
                if (ordinary) {
                    retained += i.getRetentionMinor();
                } else {
                    released += i.getGrossMinor();
                }
            }
            if (i.getStatus() == InvoiceStatus.APPROVED) {
                awaiting += i.netMinor();
            }
        }
        return new Totals(invoiced, certified, paid, retained - released, awaiting);
    }
}
