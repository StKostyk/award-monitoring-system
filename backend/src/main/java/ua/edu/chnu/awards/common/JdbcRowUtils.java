package ua.edu.chnu.awards.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Timestamp conversions between JDBC rows and parameters and {@link Instant}.
 */
public final class JdbcRowUtils {

    private JdbcRowUtils() {
    }

    /**
     * Reads a {@code timestamptz} column.
     *
     * @param row    the current row
     * @param column the column label
     * @return the moment, null for SQL null
     * @throws SQLException when the column cannot be read
     */
    public static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * The moment as a {@code timestamptz} parameter.
     *
     * @param at the moment
     * @return the moment at offset UTC
     */
    public static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}
