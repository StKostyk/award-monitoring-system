package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.repository.AwardRepository;

class AwardCreationTest {

    private static final UUID KEY = UUID.fromString("0b9c7c52-6f1e-4a8e-9d55-3f2f8b7a1c01");

    private final AwardService awards = mock(AwardService.class);
    private final AwardRepository repository = mock(AwardRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final AwardCreation creation = new AwardCreation(awards, repository, access, transactions);
    private final AwardForm form = new AwardForm("Letter", null, null, null, null, null, null, null, null, null);
    private final AwardResponse award = mock(AwardResponse.class);

    @BeforeEach
    void setUp() {
        when(access.callerId()).thenReturn(7L);
        when(award.id()).thenReturn(5L);
        when(awards.get(5L)).thenReturn(award);
        when(awards.create(form)).thenReturn(award);
        when(transactions.execute(any())).thenAnswer(call ->
            call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void ac4_3_withoutAKeyEveryRequestCreatesADraft() {
        AwardCreation.Created created = creation.create(form, null);

        assertThat(created.award()).isSameAs(award);
        assertThat(created.repeated()).isFalse();
        verify(repository, never()).findIdByCreationKey(anyLong(), any());
        verify(repository, never()).keepCreationKey(anyLong(), any());
    }

    @Test
    void ac4_1_aNewKeyCreatesTheDraftUnderIt() {
        when(repository.findIdByCreationKey(7L, KEY)).thenReturn(Optional.empty());

        AwardCreation.Created created = creation.create(form, KEY);

        assertThat(created.award()).isSameAs(award);
        assertThat(created.repeated()).isFalse();
        verify(repository).keepCreationKey(5L, KEY);
    }

    @Test
    void ac4_1_aKnownKeyAnswersTheEarlierDraftWithoutCreatingAnother() {
        when(repository.findIdByCreationKey(7L, KEY)).thenReturn(Optional.of(5L));

        AwardCreation.Created created = creation.create(form, KEY);

        assertThat(created.award()).isSameAs(award);
        assertThat(created.repeated()).isTrue();
        verify(awards, never()).create(any());
    }

    @Test
    void ac4_2_aKeyStoredByAConcurrentRequestAnswersThatDraft() {
        when(repository.findIdByCreationKey(7L, KEY)).thenReturn(Optional.empty(), Optional.of(5L));
        doThrow(new DataIntegrityViolationException("ux_awards_creation_key")).when(repository)
            .keepCreationKey(5L, KEY);

        AwardCreation.Created created = creation.create(form, KEY);

        assertThat(created.award()).isSameAs(award);
        assertThat(created.repeated()).isTrue();
    }

    @Test
    void anotherIntegrityFailureIsNotHidden() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("ck_awards_title");
        when(repository.findIdByCreationKey(7L, KEY)).thenReturn(Optional.empty());
        when(awards.create(form)).thenThrow(failure);

        assertThatThrownBy(() -> creation.create(form, KEY)).isSameAs(failure);
    }
}
