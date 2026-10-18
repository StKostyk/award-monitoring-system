package ua.edu.chnu.awards.gdpr.repository;

import static ua.edu.chnu.awards.common.JdbcRowUtils.instant;

import java.sql.ResultSet;
import java.sql.SQLException;

import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.gdpr.dto.LegalHold;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldEntry;

/**
 * Row mapping shared by the repositories of the module: people named in a row and the legal-hold select.
 */
final class GdprRowsHelper {

    /**
     * The legal holds with the held person, the officer who placed each and the one who lifted it; open for a
     * {@code where} clause.
     */
    static final String HOLD_SELECT = """
        select h.hold_id, h.reason, h.placed_at, h.lifted_at, h.lift_reason,
               u.user_id as person_id, u.first_name || ' ' || u.last_name as person_name,
               u.email_address as person_email,
               p.user_id as placed_by_id, p.first_name || ' ' || p.last_name as placed_by_name,
               p.email_address as placed_by_email,
               l.user_id as lifted_by_id, l.first_name || ' ' || l.last_name as lifted_by_name,
               l.email_address as lifted_by_email
          from legal_holds h
          join users u on u.user_id = h.user_id
          join users p on p.user_id = h.placed_by
          left join users l on l.user_id = h.lifted_by
        """;

    private GdprRowsHelper() {
    }

    /**
     * A person named by the {@code _id}, {@code _name} and {@code _email} columns of a prefix.
     *
     * @param row    the current row
     * @param prefix the column prefix
     * @return the person, null when the id is SQL null
     * @throws SQLException when a column cannot be read
     */
    static UserRef ref(ResultSet row, String prefix) throws SQLException {
        long id = row.getLong(prefix + "_id");
        return row.wasNull() ? null
            : new UserRef(id, row.getString(prefix + "_name"), row.getString(prefix + "_email"));
    }

    /**
     * A hold read from a {@link #HOLD_SELECT} row.
     *
     * @param row the current row
     * @return the hold
     * @throws SQLException when a column cannot be read
     */
    static LegalHold hold(ResultSet row) throws SQLException {
        return new LegalHold(row.getLong("hold_id"), row.getString("reason"), instant(row, "placed_at"),
            ref(row, "placed_by"), instant(row, "lifted_at"), ref(row, "lifted_by"), row.getString("lift_reason"));
    }

    /**
     * A hold with its person, read from a {@link #HOLD_SELECT} row.
     *
     * @param row the current row
     * @return the entry
     * @throws SQLException when a column cannot be read
     */
    static LegalHoldEntry holdEntry(ResultSet row) throws SQLException {
        LegalHold hold = hold(row);
        return new LegalHoldEntry(hold.id(), ref(row, "person"), hold.reason(), hold.placedAt(), hold.placedBy(),
            hold.liftedAt(), hold.liftedBy(), hold.liftReason());
    }
}
