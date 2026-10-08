package ua.edu.chnu.awards.award.service;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.event.ReviewMeasured;

/**
 * Review service-level meters: decisions per level and outcome, time to decide, open and overdue requests per
 * level, and overdue notices sent.
 */
@Component
public class ReviewMetrics {

    static final String DECISIONS = "awards.review.decisions";
    static final String DURATION = "awards.review.decision.duration";
    static final String OPEN = "awards.review.open";
    static final String OVERDUE = "awards.review.overdue";
    static final String NOTICES = "awards.review.overdue.notices";
    private static final String LEVEL = "level";

    private final MeterRegistry registry;
    private final Map<ApprovalLevel, AtomicLong> open = new EnumMap<>(ApprovalLevel.class);
    private final Map<ApprovalLevel, AtomicLong> overdue = new EnumMap<>(ApprovalLevel.class);
    private final Counter notices;

    /**
     * Registers the gauges per level and the notice counter.
     *
     * @param registry the meter registry
     */
    public ReviewMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (ApprovalLevel level : ApprovalLevel.values()) {
            open.put(level, gauge(OPEN, "Open review requests", level));
            overdue.put(level, gauge(OVERDUE, "Open review requests past their deadline", level));
        }
        notices = Counter.builder(NOTICES).description("Overdue notice e-mails sent").register(registry);
    }

    /**
     * Counts one committed decision and its duration at the level.
     *
     * @param event the decision
     */
    @TransactionalEventListener
    public void onMeasured(ReviewMeasured event) {
        String level = event.level().name();
        Counter.builder(DECISIONS)
            .description("Reviewer decisions")
            .tag(LEVEL, level)
            .tag("decision", event.decision().name().toLowerCase(Locale.ROOT))
            .tag("on_time", String.valueOf(event.onTime()))
            .register(registry)
            .increment();
        Timer.builder(DURATION)
            .description("Time from reaching a level to the decision there")
            .tag(LEVEL, level)
            .register(registry)
            .record(event.duration());
    }

    /**
     * Sets the open and overdue gauges from one count of the queue.
     *
     * @param openCounts    open requests per level, missing levels count zero
     * @param overdueCounts overdue requests per level, missing levels count zero
     */
    public void refresh(Map<ApprovalLevel, Long> openCounts, Map<ApprovalLevel, Long> overdueCounts) {
        open.forEach((level, value) -> value.set(openCounts.getOrDefault(level, 0L)));
        overdue.forEach((level, value) -> value.set(overdueCounts.getOrDefault(level, 0L)));
    }

    /**
     * Counts one overdue notice e-mail the mail server accepted.
     */
    public void noticeSent() {
        notices.increment();
    }

    private AtomicLong gauge(String name, String description, ApprovalLevel level) {
        AtomicLong value = new AtomicLong();
        Gauge.builder(name, value, AtomicLong::get)
            .description(description)
            .tag(LEVEL, level.name())
            .register(registry);
        return value;
    }
}
