package ua.edu.chnu.awards.authz;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The organisation hierarchy held in memory for scope checks: loaded at start and reloaded every five minutes;
 * code that changes organisations calls {@link #refresh()} to shorten the lag. Lookups never touch the
 * database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationTree {

    static final long REFRESH_MS = 5 * 60 * 1000L;

    private final OrganizationRepository repository;
    private final AtomicReference<Map<Long, Node>> nodes = new AtomicReference<>(Map.of());

    /**
     * Rebuilds the tree from the database.
     */
    @PostConstruct
    @Scheduled(fixedDelay = REFRESH_MS, initialDelay = REFRESH_MS)
    public void refresh() {
        Map<Long, Node> fresh = new HashMap<>();
        for (Organization organization : repository.findAll()) {
            Organization parent = organization.getParent();
            fresh.put(organization.getId(), new Node(organization.getId(), organization.getOrgType(),
                parent == null ? null : parent.getId(), organization.getDepth(), organization.isActive()));
        }
        nodes.set(Map.copyOf(fresh));
        log.debug("Organisation tree loaded with {} nodes", fresh.size());
    }

    /**
     * A node of the tree.
     *
     * @param id organisation id
     * @return the node, empty for an unknown id
     */
    public Optional<Node> node(long id) {
        return Optional.ofNullable(nodes.get().get(id));
    }

    /**
     * Whether {@code organizationId} is {@code scopeId} itself or one of its descendants.
     *
     * @param scopeId        the root of a scope
     * @param organizationId the organisation to test
     * @return true when the scope covers the organisation
     */
    public boolean covers(long scopeId, long organizationId) {
        return covers(nodes.get(), scopeId, organizationId);
    }

    private static boolean covers(Map<Long, Node> snapshot, long scopeId, long organizationId) {
        Set<Long> visited = new HashSet<>();
        Node current = snapshot.get(organizationId);
        while (current != null && visited.add(current.id())) {
            if (current.id() == scopeId) {
                return true;
            }
            current = current.parentId() == null ? null : snapshot.get(current.parentId());
        }
        if (current != null) {
            log.error("Organisation {} is part of a parent cycle; scope checks treat it as outside every scope",
                current.id());
        }
        return false;
    }

    /**
     * Ids of an organisation and all its descendants, active or not.
     *
     * @param scopeId the root of the subtree
     * @return the ids; empty for an unknown root
     */
    public Set<Long> subtree(long scopeId) {
        Set<Long> result = new HashSet<>();
        Map<Long, Node> snapshot = nodes.get();
        if (snapshot.containsKey(scopeId)) {
            for (Node node : snapshot.values()) {
                if (covers(snapshot, scopeId, node.id())) {
                    result.add(node.id());
                }
            }
        }
        return result;
    }

    /**
     * One organisation of the tree.
     *
     * @param id       organisation id
     * @param type     organisation type
     * @param parentId parent id, null for the root
     * @param depth    depth from the root
     * @param active   whether the organisation is active
     */
    public record Node(long id, OrganizationType type, Long parentId, int depth, boolean active) {
    }
}
