package ua.edu.chnu.awards.award.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A ready-made reviewer comment for one decision, in Ukrainian and optionally English. Maintained by migrations.
 */
@Entity
@Immutable
@Table(name = "review_templates")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReviewTemplate {

    @Id
    @Column(name = "template_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 10)
    private Decision decision;

    @Column(name = "title_uk", nullable = false, length = 120)
    private String titleUk;

    @Column(name = "title_en", length = 120)
    private String titleEn;

    @Column(name = "body_uk", nullable = false, length = 2000)
    private String bodyUk;

    @Column(name = "body_en", length = 2000)
    private String bodyEn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;
}
