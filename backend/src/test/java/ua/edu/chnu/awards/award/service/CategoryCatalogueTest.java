package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ua.edu.chnu.awards.award.dto.AwardCategoryResponse;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.repository.AwardCategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryCatalogueTest {

    @Mock
    private AwardCategoryRepository repository;

    @InjectMocks
    private CategoryCatalogue catalogue;

    @Test
    void ac02_activeCategoriesAreNestedUnderTheirRootsInTheOrderGiven() {
        AwardCategory international = category(1L, "International Awards", RecognitionLevel.INTERNATIONAL, null);
        AwardCategory national = category(10L, "National Awards", RecognitionLevel.NATIONAL, null);
        AwardCategory grant = category(4L, "International Grant", RecognitionLevel.INTERNATIONAL, international);
        AwardCategory paper = category(3L, "Best Paper", RecognitionLevel.INTERNATIONAL, international);
        when(repository.findByActiveTrueOrderBySortOrderAscNameAsc())
            .thenReturn(List.of(international, paper, grant, national));

        List<AwardCategoryResponse> roots = catalogue.tree();

        assertThat(roots).extracting(AwardCategoryResponse::id).containsExactly(1L, 10L);
        assertThat(roots.get(0).children()).extracting(AwardCategoryResponse::id).containsExactly(3L, 4L);
        assertThat(roots.get(0).nameUk()).isEqualTo("uk International Awards");
        assertThat(roots.get(0).level()).isEqualTo(RecognitionLevel.INTERNATIONAL);
        assertThat(roots.get(1).children()).isEmpty();
    }

    @Test
    void ac02_childrenOfAnInactiveParentAreLeftOut() {
        AwardCategory hiddenParent = category(20L, "University Awards", RecognitionLevel.UNIVERSITY, null);
        AwardCategory orphan = category(21L, "Excellence", RecognitionLevel.UNIVERSITY, hiddenParent);
        AwardCategory faculty = category(30L, "Faculty Awards", RecognitionLevel.FACULTY, null);
        when(repository.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(orphan, faculty));

        List<AwardCategoryResponse> roots = catalogue.tree();

        assertThat(roots).extracting(AwardCategoryResponse::id).containsExactly(30L);
    }

    @Test
    void ac02_theEtagFollowsTheContent() {
        AwardCategory faculty = category(30L, "Faculty Awards", RecognitionLevel.FACULTY, null);
        when(repository.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(faculty));
        String first = catalogue.etag(catalogue.tree());
        String again = catalogue.etag(catalogue.tree());

        faculty.setName("Faculty Recognition");
        String changed = catalogue.etag(catalogue.tree());

        assertThat(first).isEqualTo(again).startsWith("\"").endsWith("\"");
        assertThat(changed).isNotEqualTo(first);
    }

    private static AwardCategory category(long id, String name, RecognitionLevel level, AwardCategory parent) {
        return AwardCategory.builder().id(id).name(name).nameUk("uk " + name).description(name + " description")
            .level(level).parent(parent).active(true).system(true).build();
    }
}
