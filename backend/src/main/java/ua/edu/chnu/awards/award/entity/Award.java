package ua.edu.chnu.awards.award.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * An award of one person or of a faculty or department, from the first draft to the approved record. Title,
 * category, awarding organisation and date may be empty while the award is a draft.
 */
@Entity
@Table(name = "awards")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = {"owner", "category", "organization"})
@SuppressWarnings("PMD.TooManyFields")
public class Award {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "award_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "recipient_org_id")
    private Long recipientOrganizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private AwardCategory category;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "title_uk", length = 500)
    private String titleUk;

    @Column(name = "description")
    private String description;

    @Column(name = "description_uk")
    private String descriptionUk;

    @Column(name = "awarding_organization", length = 255)
    private String awardingOrganization;

    @Column(name = "award_date")
    private LocalDate awardDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AwardStatus status = AwardStatus.DRAFT;

    @Column(name = "verification_badge", nullable = false)
    private boolean verificationBadge;

    @Column(name = "impact_score")
    private Integer impactScore;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, updatable = false, length = 20)
    private AwardVisibility visibility = AwardVisibility.PRIVATE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 1L;

    /**
     * Whether the owner may still change or delete the award.
     *
     * @return true while it is a draft
     */
    public boolean isDraft() {
        return status == AwardStatus.DRAFT;
    }

    /**
     * Whether a faculty or department received the award; its organisation is then that unit.
     *
     * @return true for a unit award
     */
    public boolean isUnitAward() {
        return recipientOrganizationId != null;
    }

    /**
     * The English title for messages, the Ukrainian one when no English title was given.
     *
     * @return the title to show in English
     */
    public String titleInEnglish() {
        return title == null ? titleUk : title;
    }

    /**
     * The Ukrainian title for messages, the English one when no Ukrainian title was given.
     *
     * @return the title to show in Ukrainian
     */
    public String titleInUkrainian() {
        return titleUk == null ? title : titleUk;
    }
}
