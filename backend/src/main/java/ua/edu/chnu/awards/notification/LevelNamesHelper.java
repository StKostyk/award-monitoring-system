package ua.edu.chnu.awards.notification;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

/**
 * Names of the approval levels in mail texts.
 */
final class LevelNamesHelper {

    private LevelNamesHelper() {
    }

    /**
     * The Ukrainian name in the nominative case, as in "рівень: декан".
     *
     * @param level the level
     * @return the name
     */
    static String uk(ApprovalLevel level) {
        return switch (level) {
            case FACULTY_SECRETARY -> "секретар факультету";
            case DEAN -> "декан";
            case RECTOR_SECRETARY -> "секретар ректора";
            case RECTOR -> "ректор";
        };
    }

    /**
     * The Ukrainian name in the dative case, as in "передав(ла) нагороду декану".
     *
     * @param level the level
     * @return the name
     */
    static String toUk(ApprovalLevel level) {
        return switch (level) {
            case FACULTY_SECRETARY -> "секретарю факультету";
            case DEAN -> "декану";
            case RECTOR_SECRETARY -> "секретарю ректора";
            case RECTOR -> "ректору";
        };
    }

    /**
     * The English name without an article, as in "level: dean".
     *
     * @param level the level
     * @return the name
     */
    static String en(ApprovalLevel level) {
        return switch (level) {
            case FACULTY_SECRETARY -> "faculty secretary";
            case DEAN -> "dean";
            case RECTOR_SECRETARY -> "rector's secretary";
            case RECTOR -> "rector";
        };
    }
}
