package ua.edu.chnu.awards.award.repository;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;

/**
 * SQL functions of the award search, registered with Hibernate through the service loader.
 */
public class AwardSearchFunctions implements FunctionContributor {

    /** Full-text match of the search vector: vector, tsquery text. */
    public static final String FULL_TEXT = "award_full_text";

    /** Full-text rank of the search vector: vector, tsquery text. */
    public static final String RANK = "award_rank";

    /** Typo match of the search text at the transaction's word similarity threshold: text, search words. */
    public static final String TYPO = "award_typo";

    /** Word similarity of the search text: text, search words. */
    public static final String SIMILARITY = "award_similarity";

    /** Owner name match: owner id, case-insensitive pattern. */
    public static final String OWNER_NAMED = "award_owner_named";

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        SqmFunctionRegistry registry = contributions.getFunctionRegistry();
        BasicTypeRegistry types = contributions.getTypeConfiguration().getBasicTypeRegistry();
        BasicType<Boolean> bool = types.resolve(StandardBasicTypes.BOOLEAN);
        BasicType<Double> real = types.resolve(StandardBasicTypes.DOUBLE);
        registry.registerPattern(FULL_TEXT, "(?1 @@ to_tsquery('simple', ?2))", bool);
        registry.registerPattern(RANK, "ts_rank_cd(?1, to_tsquery('simple', ?2))", real);
        registry.registerPattern(TYPO, "(?1 %> ?2)", bool);
        registry.registerPattern(SIMILARITY, "word_similarity(?2, ?1)", real);
        registry.registerPattern(OWNER_NAMED, "(?1 = any(array(select u.user_id from users u"
            + " where u.first_name || ' ' || u.last_name ilike ?2)))", bool);
    }
}
