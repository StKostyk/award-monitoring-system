package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.dto.CategorySuggestion;
import ua.edu.chnu.awards.award.dto.SuggestionReason;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.repository.CategorySuggestionQueries;
import ua.edu.chnu.awards.award.repository.CategorySuggestionQueries.Candidate;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class CategorySuggestionIT extends AbstractJpaSliceTest {

    private static final String FIXTURE = "/suggestions/labelled-awards.csv";
    private static final int REQUIRED_HITS = 24;
    private static final String INSERT = "insert into awards (user_id, organization_id, title, status, category_id,"
        + " awarding_organization, award_date) values (?, 64, ?, 'DRAFT', ?, 'ЧНУ', ?)";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private final AccessScope access = mock(AccessScope.class);
    private CategorySuggestionQueries queries;
    private CategorySuggester suggester;

    @BeforeEach
    void setUp() {
        queries = new CategorySuggestionQueries(new NamedParameterJdbcTemplate(jdbc));
        OrganizationTree tree = new OrganizationTree(organizationRepository);
        tree.refresh();
        suggester = new CategorySuggester(queries, new OrganizationMatcher(organizationRepository, tree), access);
        when(access.callerId()).thenReturn(-1L);
    }

    @Test
    void ac3_3_everySeededCategoryHasKeywordsAndEveryLevelHasUkrainianAndEnglishStems() {
        assertThat(jdbc.queryForObject("select count(*) from award_categories where is_system and is_active"
            + " and cardinality(keywords) = 0", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from award_categories where parent_category_id is null"
            + " and is_active and exists (select 1 from unnest(keywords) k where k ~ '[а-яіїєґ]')"
            + " and exists (select 1 from unnest(keywords) k where k ~ '[a-z]')", Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("select count(*) from award_categories, unnest(keywords) k"
            + " where k <> lower(btrim(k))", Integer.class)).isZero();
    }

    @Test
    void ac3_3_activeCategoriesAreReadWithTheirKeywords() {
        jdbc.update("update award_categories set is_active = false where category_id = 5");

        List<Candidate> candidates = queries.activeCategories();

        assertThat(candidates).extracting(Candidate::id).contains(1L, 3L, 52L).doesNotContain(5L);
        assertThat(candidates).filteredOn(candidate -> candidate.id() == 80L).singleElement()
            .satisfies(regional -> {
                assertThat(regional.level()).isEqualTo(RecognitionLevel.REGIONAL);
                assertThat(regional.parentId()).isNull();
                assertThat(regional.keywords()).contains("обласн", "region");
            });
        assertThat(candidates).filteredOn(candidate -> candidate.id() == 81L).singleElement()
            .extracting(Candidate::parentId).isEqualTo(80L);
    }

    @Test
    void ac3_1_historyReadsOnlyTheCallersAwards() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        User caller = userRepository.saveAndFlush(TestUsers.user("suggestion.caller@chnu.edu.ua", department));
        User colleague = userRepository.saveAndFlush(TestUsers.user("suggestion.colleague@chnu.edu.ua",
            department));
        LocalDate date = LocalDate.of(2025, 3, 1);
        for (int i = 0; i < 3; i++) {
            jdbc.update(INSERT, colleague.getId(), "Колега " + i, 52L, date);
        }
        jdbc.update(INSERT, caller.getId(), "Відзнака міськради", 71L, date);
        jdbc.update(INSERT, caller.getId(), "Друга відзнака", 71L, date.plusDays(1));
        jdbc.update(INSERT, caller.getId(), "Премія", 83L, date);
        jdbc.update("insert into awards (user_id, organization_id, title, status, award_date)"
            + " values (?, 64, 'Без категорії', 'DRAFT', ?)", caller.getId(), date);
        when(access.callerId()).thenReturn(caller.getId());

        assertThat(queries.mostUsedCategories(caller.getId(), 3)).containsExactly(71L, 83L);
        jdbc.update("update award_categories set is_active = false where category_id = 71");
        assertThat(queries.mostUsedCategories(caller.getId(), 3)).containsExactly(83L);
        jdbc.update("update award_categories set is_active = true where category_id = 71");
        assertThat(suggester.suggest("Something else", null))
            .extracting(CategorySuggestion::id, CategorySuggestion::reasons)
            .containsExactly(tuple(71L, List.of(SuggestionReason.HISTORY)),
                tuple(83L, List.of(SuggestionReason.HISTORY)));
    }

    @Test
    void ac3_1_theUniversityNamedInTheTitleRanksAboveTheNationalLevel() {
        assertThat(suggester.suggest("Грамота Чернівецького національного університету", null).get(0).level())
            .isEqualTo(RecognitionLevel.UNIVERSITY);
        assertThat(suggester.suggest("Подяка", "Кафедра математичного аналізу Чернівецького національного"
            + " університету імені Юрія Федьковича").get(0).level()).isEqualTo(RecognitionLevel.DEPARTMENT);
    }

    @Test
    void ac3_2_theExpectedLevelIsAmongTheTopThreeForAtLeast24Of30LabelledAwards() throws IOException {
        List<String> misses = new ArrayList<>();
        List<String[]> rows = fixture();
        for (String[] row : rows) {
            List<RecognitionLevel> levels = suggester.suggest(row[1], row[2]).stream()
                .map(CategorySuggestion::level).toList();
            if (!levels.contains(RecognitionLevel.valueOf(row[0]))) {
                misses.add(String.join(" | ", row) + " -> " + levels);
            }
        }

        assertThat(rows).hasSize(30);
        assertThat(rows).extracting(row -> row[0]).containsAll(
            List.of("SPECIALITY", "DEPARTMENT", "COLLEGE", "FACULTY", "LOCAL", "UNIVERSITY", "REGIONAL", "NATIONAL",
                "INTERNATIONAL"));
        assertThat(rows.size() - misses.size()).as("misses: %s", misses).isGreaterThanOrEqualTo(REQUIRED_HITS);
    }

    private static List<String[]> fixture() throws IOException {
        try (InputStream in = Objects.requireNonNull(CategorySuggestionIT.class.getResourceAsStream(FIXTURE))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\\|", -1))
                .toList();
        }
    }
}
