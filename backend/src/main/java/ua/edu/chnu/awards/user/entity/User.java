package ua.edu.chnu.awards.user.entity;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

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

import ua.edu.chnu.awards.organization.entity.Organization;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * System user: identity, credentials and primary organisation.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = {"passwordHash", "organization"})
public class User {

    /**
     * The reserved domain of placeholder addresses.
     */
    public static final String PLACEHOLDER_DOMAIN = "unclaimed.invalid";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email_address", nullable = false, unique = true, length = 255)
    private String emailAddress;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "added_by_id")
    private Long addedById;

    @Column(name = "invited_at")
    private Instant invitedAt;

    /**
     * A placeholder address for a person whose address is unknown; it never collides and no mail goes to it.
     *
     * @return a unique address under the reserved {@code .invalid} domain
     */
    public static String placeholderAddress() {
        return "unclaimed-" + UUID.randomUUID() + "@" + PLACEHOLDER_DOMAIN;
    }

    /**
     * Whether mail can reach the person: false for the placeholder address of a person added without one and for
     * the address of an erased account.
     *
     * @return true for a real address
     */
    public boolean hasMailbox() {
        return emailAddress != null && !emailAddress.toLowerCase(Locale.ROOT).endsWith(".invalid");
    }

    /**
     * Whether the person has no account of their own yet: added by a faculty secretary or dean and not yet
     * verified.
     *
     * @return true for {@code UNCLAIMED}, and for {@code PENDING} when someone else added the person
     */
    public boolean isWithoutAccount() {
        return accountStatus == AccountStatus.UNCLAIMED
            || accountStatus == AccountStatus.PENDING && addedById != null;
    }

    /**
     * First and last name separated by a space.
     *
     * @return display name
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Whether the account is in the {@link AccountStatus#ACTIVE} state.
     *
     * @return true when active
     */
    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    /**
     * Whether the account is in the {@link AccountStatus#DELETED} state.
     *
     * @return true when deleted
     */
    public boolean isDeleted() {
        return accountStatus == AccountStatus.DELETED;
    }
}
