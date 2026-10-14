package com.pmplatform.procurement.evaluation;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A committee member: their role, conflict-of-interest declaration and report signature. */
@Entity
@Table(schema = "procurement", name = "evaluation_members")
public class EvaluationMember {

    @Id
    private UUID id;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommitteeRole role;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "declared_at")
    private Instant declaredAt;

    @Column(name = "has_conflict")
    private Boolean hasConflict;

    private String declaration;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signed_round")
    private Integer signedRound;

    @Column(name = "sign_note")
    private String signNote;

    @Version
    private Long version;

    protected EvaluationMember() {
    }

    public EvaluationMember(UUID evaluationId, UUID userId, CommitteeRole role, Instant now) {
        this.id = UuidV7.generate();
        this.evaluationId = evaluationId;
        this.userId = userId;
        this.role = role;
        this.addedAt = now;
    }

    public void changeRole(CommitteeRole role) {
        this.role = role;
    }

    /**
     * Declares, once, whether the member has a conflict of interest with any bidder. A member
     * with a conflict takes no further part in the evaluation.
     */
    public void declare(boolean conflict, String text, Instant now) {
        if (declaredAt != null) {
            throw new BusinessRuleException("You have already made your declaration");
        }
        if (conflict && (text == null || text.isBlank())) {
            throw new BusinessRuleException("Describe the conflict of interest (which bidder and how)");
        }
        this.hasConflict = conflict;
        this.declaration = text == null || text.isBlank() ? null : text.trim();
        this.declaredAt = now;
    }

    public boolean isConflicted() {
        return Boolean.TRUE.equals(hasConflict);
    }

    /** May see bid contents and take part: declared without a conflict (or no declaration is required). */
    public boolean isCleared(boolean declarationRequired) {
        return !isConflicted() && (declaredAt != null || !declarationRequired);
    }

    public boolean mayScore(boolean declarationRequired) {
        return role.isVoting() && isCleared(declarationRequired);
    }

    public void sign(int round, String note, Instant now) {
        if (signedFor(round)) {
            throw new BusinessRuleException("You have already signed this report");
        }
        this.signedRound = round;
        this.signedAt = now;
        this.signNote = note == null || note.isBlank() ? null : note.trim();
    }

    public boolean signedFor(int round) {
        return signedRound != null && signedRound == round;
    }

    public UUID getId() { return id; }
    public UUID getEvaluationId() { return evaluationId; }
    public UUID getUserId() { return userId; }
    public CommitteeRole getRole() { return role; }
    public Instant getAddedAt() { return addedAt; }
    public Instant getDeclaredAt() { return declaredAt; }
    public Boolean getHasConflict() { return hasConflict; }
    public String getDeclaration() { return declaration; }
    public Instant getSignedAt() { return signedAt; }
    public Integer getSignedRound() { return signedRound; }
    public String getSignNote() { return signNote; }
}
