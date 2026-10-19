package ua.edu.chnu.awards.award.dto;

/**
 * Where a count of awards stands among the active accounts of a department.
 *
 * @param groupSize active accounts of the department
 * @param ahead     members with more awards
 * @param tied      other members with the same number of awards
 */
public record DepartmentStanding(long groupSize, long ahead, long tied) {
}
