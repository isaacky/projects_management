package com.pmplatform.procurement.evaluation;

import com.pmplatform.procurement.config.ProcurementRules.ApprovalStep;
import com.pmplatform.procurement.config.ReportSignatories;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * How the committee's individual marks become the committee's result. Pure functions,
 * tested without a database.
 *
 * <ul>
 *   <li>Preliminary (pass/fail): a criterion is met when more members say it is met than
 *       say it is not; on a tie the chair's mark decides (a tie without the chair fails).
 *       A bid passes when it meets every criterion.</li>
 *   <li>Technical: each criterion's score is the members' average; the bid's score is the
 *       sum of those averages, shown as a percentage of the maximum; it passes at or above
 *       the pass mark.</li>
 *   <li>Financial: bids are ranked by evaluated price, lowest first; equal prices share a rank.</li>
 * </ul>
 */
public final class EvaluationRules {

    private EvaluationRules() {
    }

    /** A member's mark for one criterion of one bid. */
    public record Mark(UUID criterionId, UUID bidId, UUID memberId, Boolean passed, BigDecimal score) {
    }

    public record PreliminaryResult(boolean passed, List<UUID> failedCriteria) {
    }

    public record TechnicalResult(BigDecimal total, BigDecimal pct, boolean passed) {
    }

    public record Step(int order, String role, boolean opinion) {
    }

    // ------------------------------------------------------------------ who takes part

    /**
     * Members who have marked every criterion for every bid of the stage. Only their marks
     * count; members who are part-way through are left out.
     */
    public static Set<UUID> completeMembers(Collection<UUID> scoringMembers, Collection<UUID> criteria,
            Collection<UUID> bids, Collection<Mark> marks) {
        Map<UUID, Set<String>> done = new HashMap<>();
        Set<UUID> criterionSet = Set.copyOf(criteria);
        Set<UUID> bidSet = Set.copyOf(bids);
        for (Mark m : marks) {
            if (criterionSet.contains(m.criterionId()) && bidSet.contains(m.bidId())
                    && (m.passed() != null || m.score() != null)) {
                done.computeIfAbsent(m.memberId(), k -> new java.util.HashSet<>())
                        .add(m.criterionId() + "/" + m.bidId());
            }
        }
        int needed = criterionSet.size() * bidSet.size();
        Set<UUID> complete = new LinkedHashSet<>();
        for (UUID member : scoringMembers) {
            if (done.getOrDefault(member, Set.of()).size() == needed) {
                complete.add(member);
            }
        }
        return complete;
    }

    // ------------------------------------------------------------------ preliminary

    public static PreliminaryResult preliminary(List<UUID> criteria, UUID bidId, Set<UUID> members, UUID chairId,
            Collection<Mark> marks) {
        List<UUID> failed = new ArrayList<>();
        for (UUID criterion : criteria) {
            int yes = 0;
            int no = 0;
            Boolean chairMark = null;
            for (Mark m : marks) {
                if (!m.criterionId().equals(criterion) || !m.bidId().equals(bidId) || !members.contains(m.memberId())
                        || m.passed() == null) {
                    continue;
                }
                if (m.passed()) {
                    yes++;
                } else {
                    no++;
                }
                if (m.memberId().equals(chairId)) {
                    chairMark = m.passed();
                }
            }
            boolean met = yes > no || (yes == no && yes > 0 && Boolean.TRUE.equals(chairMark));
            if (!met) {
                failed.add(criterion);
            }
        }
        return new PreliminaryResult(failed.isEmpty(), failed);
    }

    // ------------------------------------------------------------------ technical

    /**
     * @param maxScores the technical criteria and their maximum scores
     * @param passMarkPct pass mark as a percentage of the maximum total
     */
    public static TechnicalResult technical(Map<UUID, BigDecimal> maxScores, UUID bidId, Set<UUID> members,
            Collection<Mark> marks, int passMarkPct) {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("No member's marks to average");
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        BigDecimal count = BigDecimal.valueOf(members.size());
        for (Map.Entry<UUID, BigDecimal> c : maxScores.entrySet()) {
            BigDecimal sum = marks.stream()
                    .filter(m -> m.criterionId().equals(c.getKey()) && m.bidId().equals(bidId)
                            && members.contains(m.memberId()) && m.score() != null)
                    .map(Mark::score).reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(sum.divide(count, 2, RoundingMode.HALF_UP));
            max = max.add(c.getValue());
        }
        BigDecimal pct = max.signum() == 0 ? BigDecimal.ZERO
                : total.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP);
        return new TechnicalResult(total, pct, pct.compareTo(BigDecimal.valueOf(passMarkPct)) >= 0);
    }

    // ------------------------------------------------------------------ financial

    /** Rank 1 = lowest evaluated price; equal prices share a rank (1, 1, 3, ...). */
    public static Map<UUID, Integer> rank(Map<UUID, Long> evaluatedPrices) {
        List<Map.Entry<UUID, Long>> sorted = evaluatedPrices.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().thenComparing(e -> e.getKey().toString()))
                .toList();
        Map<UUID, Integer> ranks = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> e : sorted) {
            int rank = 1 + (int) sorted.stream().filter(o -> o.getValue() < e.getValue()).count();
            ranks.put(e.getKey(), rank);
        }
        return ranks;
    }

    // ------------------------------------------------------------------ report

    /** Who must sign the report. Members with a conflict, or who never cleared, are not asked to sign. */
    public static Set<UUID> signatories(ReportSignatories rule, List<EvaluationMember> members,
            boolean declarationRequired) {
        Set<UUID> out = new LinkedHashSet<>();
        for (EvaluationMember m : members) {
            if (!m.isCleared(declarationRequired)) {
                continue;
            }
            boolean signs = switch (rule) {
                case ALL_MEMBERS -> m.getRole().isVoting();
                case CHAIR_AND_SECRETARY -> m.getRole() == CommitteeRole.CHAIR || m.getRole() == CommitteeRole.SECRETARY;
                case CHAIR_ONLY -> m.getRole() == CommitteeRole.CHAIR;
            };
            if (signs) {
                out.add(m.getUserId());
            }
        }
        return out;
    }

    /**
     * The steps after signature: the award approval chain from the settings (or the committee
     * rule's award approver when no chain is configured), with the report reviewer's professional
     * opinion as the first step if the chain does not already include that role.
     */
    public static List<Step> approvalChain(List<ApprovalStep> awardChain, String reviewerRole, String awardApproverRole) {
        List<String> roles = new ArrayList<>(awardChain.stream()
                .sorted(Comparator.comparingInt(ApprovalStep::step))
                .map(ApprovalStep::approverRole).toList());
        if (roles.isEmpty()) {
            roles.add(Objects.requireNonNull(awardApproverRole, "award approver role"));
        }
        if (reviewerRole != null && !roles.contains(reviewerRole)) {
            roles.addFirst(reviewerRole);
        }
        List<Step> steps = new ArrayList<>();
        boolean opinionGiven = false;
        for (int i = 0; i < roles.size(); i++) {
            boolean opinion = !opinionGiven && roles.get(i).equals(reviewerRole);
            opinionGiven |= opinion;
            steps.add(new Step(i + 1, roles.get(i), opinion));
        }
        return steps;
    }
}
