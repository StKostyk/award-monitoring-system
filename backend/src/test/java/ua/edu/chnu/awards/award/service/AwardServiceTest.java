package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.AwardSpecifications;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AwardServiceTest {

    private static final long OWNER_ID = 21L;

    private final AwardRepository awards = mock(AwardRepository.class);
    private final AwardRequestRepository requests = mock(AwardRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AwardSpecifications specifications = mock(AwardSpecifications.class);
    private final AwardInputRules rules = mock(AwardInputRules.class);
    private final AccessScope access = mock(AccessScope.class);
    private final AwardOwnership ownership = new AwardOwnership(awards, access);
    private final AwardService service = new AwardService(awards, requests, users, specifications, rules,
        ownership, new AwardMapper(), access);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(OWNER_ID, "owner@chnu.edu.ua", department);

    @BeforeEach
    void setUp() {
        when(access.callerId()).thenReturn(OWNER_ID);
        when(rules.normalize(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rules.check(any(), any())).thenReturn(Optional.empty());
        when(awards.saveAndFlush(any(Award.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ac1_1_aDraftBelongsToTheCallerAndTheirDepartment() {
        when(users.findById(OWNER_ID)).thenReturn(Optional.of(owner));

        AwardResponse created = service.create(form(null));

        assertThat(created.status()).isEqualTo(AwardStatus.DRAFT);
        assertThat(created.owner().id()).isEqualTo(OWNER_ID);
        assertThat(created.organization().id()).isEqualTo(64L);
        assertThat(created.title()).isEqualTo("Letter");
        assertThat(created.request()).isNull();
        assertThat(created.warnings()).isEmpty();
    }

    @Test
    void ac1_3_anUpdateReplacesTheDraftFields() {
        Award draft = award(OWNER_ID, AwardStatus.DRAFT);
        when(awards.findForUpdate(5L)).thenReturn(Optional.of(draft));

        service.update(5L, new AwardForm(null, "Подяка", null, null, null, "МОН", null, null, 4L));

        assertThat(draft.getTitle()).isNull();
        assertThat(draft.getTitleUk()).isEqualTo("Подяка");
        assertThat(draft.getAwardingOrganization()).isEqualTo("МОН");
    }

    @Test
    void ac1_4_deletingRemovesTheDraft() {
        Award draft = award(OWNER_ID, AwardStatus.DRAFT);
        when(awards.findForUpdate(5L)).thenReturn(Optional.of(draft));

        service.delete(5L);

        verify(awards).delete(draft);
    }

    @Test
    void ac1_8_theOwnerReadsTheirDraftAndNobodyElseDoes() {
        when(awards.findWithDetailsById(5L)).thenReturn(Optional.of(award(OWNER_ID, AwardStatus.DRAFT)));
        assertThat(service.get(5L).id()).isEqualTo(5L);

        when(access.callerId()).thenReturn(7L);
        when(access.canReadAwards(anyLong())).thenReturn(true);
        assertThatThrownBy(() -> service.get(5L)).isInstanceOf(AwardNotFoundException.class);
        verify(access, never()).canReadAwards(anyLong());
    }

    @Test
    void ac1_8_aSubmittedAwardIsReadInsideTheScopeOnly() {
        Award pending = award(OWNER_ID, AwardStatus.PENDING);
        when(awards.findWithDetailsById(5L)).thenReturn(Optional.of(pending));
        when(requests.findByAwardId(5L)).thenReturn(Optional.of(request(pending)));
        when(access.callerId()).thenReturn(7L);

        when(access.canReadAwards(64L)).thenReturn(true);
        assertThat(service.get(5L).request().currentLevel()).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);

        when(access.canReadAwards(64L)).thenReturn(false);
        assertThatThrownBy(() -> service.get(5L)).isInstanceOf(AwardNotFoundException.class);
        assertThatThrownBy(() -> service.get(6L)).isInstanceOf(AwardNotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac1_7_theOwnListIsNewestFirstWithRequestsAndCappedAt100() {
        Award draft = award(OWNER_ID, AwardStatus.DRAFT);
        Award pending = Award.builder().id(6L).owner(owner).organization(department).status(AwardStatus.PENDING)
            .version(2L).build();
        AwardQuery query = new AwardQuery(null, null, null, null);
        Specification<Award> own = mock(Specification.class);
        when(specifications.ownedBy(OWNER_ID, query)).thenReturn(own);
        when(awards.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(pending, draft)));
        when(requests.findByAwardIdIn(List.of(6L, 5L))).thenReturn(List.of(request(pending)));

        Page<AwardResponse> page = service.listOwn(query, -1, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(awards).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AwardService.MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
            .isEqualTo(Sort.Direction.DESC);
        assertThat(page.getContent()).extracting(AwardResponse::id).containsExactly(6L, 5L);
        assertThat(page.getContent().get(0).request().status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(page.getContent().get(1).request()).isNull();
    }

    private Award award(long ownerId, AwardStatus status) {
        User awardOwner = ownerId == OWNER_ID ? owner : TestUsers.person(ownerId, "other@chnu.edu.ua", department);
        return Award.builder().id(5L).owner(awardOwner).organization(department).title("Letter").status(status)
            .version(4L).build();
    }

    private static AwardRequest request(Award award) {
        return AwardRequest.builder().id(40L).award(award).submitter(award.getOwner())
            .status(RequestStatus.SUBMITTED).currentLevel(ApprovalLevel.FACULTY_SECRETARY).build();
    }

    private static AwardForm form(Long version) {
        return new AwardForm("Letter", null, null, null, null, null, null, null, version);
    }
}
