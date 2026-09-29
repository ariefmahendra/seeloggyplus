package com.seeloggyplus.service;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the server-list organisation features: favorite flag, explicit
 * ordering and grouping.
 */
class ServerOrganizationTest {

    private ServerManagementService service;
    private final List<SSHServerModel> created = new ArrayList<>();
    private final List<String> createdGroups = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ServerManagementServiceImpl();
    }

    @AfterEach
    void tearDown() {
        for (String group : createdGroups) {
            try {
                service.deleteGroup(group);
            } catch (Exception ignored) {
            }
        }
        for (SSHServerModel server : created) {
            try {
                service.deleteServer(server.getId());
            } catch (Exception ignored) {
            }
        }
    }

    private void group(String name) {
        service.createGroup(name);
        if (!createdGroups.contains(name)) {
            createdGroups.add(name);
        }
    }

    private SSHServerModel create(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SSHServerModel server = new SSHServerModel();
        server.setName("Org " + label + " " + suffix);
        server.setHost("10.0.0.1");
        server.setPort(22);
        server.setUsername("user");
        service.saveServer(server);
        created.add(server);
        return server;
    }

    @Test
    @DisplayName("favorite flag round-trips through the database")
    void favoriteRoundTrips() {
        SSHServerModel server = create("Fav");

        assertFalse(service.getServerById(server.getId()).isFavorite());

        server.setFavorite(true);
        service.saveServer(server);

        assertTrue(service.getServerById(server.getId()).isFavorite());
    }

    @Test
    @DisplayName("group name round-trips through the database")
    void groupNameRoundTrips() {
        SSHServerModel server = create("Group");

        server.setGroupName("Production");
        service.saveServer(server);

        assertEquals("Production", service.getServerById(server.getId()).getGroupName());

        server.setGroupName(null);
        service.saveServer(server);
        assertNull(service.getServerById(server.getId()).getGroupName());
    }

    @Test
    @DisplayName("groups persist even when they have no members (empty folder survives restart)")
    void emptyGroupsPersist() {
        group("Empty Group");

        assertTrue(service.getGroupNames().contains("Empty Group"),
                "a newly created group must be visible before any server joins it");

        service.deleteGroup("Empty Group");
        assertFalse(service.getGroupNames().contains("Empty Group"));
    }

    @Test
    @DisplayName("createGroup trims names, is idempotent and rejects blank names")
    void createGroupValidation() {
        group("  Spaced  ");

        assertEquals(1, service.getGroupNames().stream().filter("Spaced"::equals).count());
        service.createGroup("Spaced");
        assertEquals(1, service.getGroupNames().stream().filter("Spaced"::equals).count());
        assertThrows(IllegalArgumentException.class, () -> service.createGroup("   "));
    }

    @Test
    @DisplayName("renameGroup moves members and rejects an existing name")
    void renameGroupUpdatesMembers() {
        SSHServerModel server = create("Rename");
        group("Before");
        server.setGroupName("Before");
        service.saveServer(server);

        service.renameGroup("Before", "After");
        createdGroups.add("After");

        assertEquals("After", service.getServerById(server.getId()).getGroupName());
        assertTrue(service.getGroupNames().contains("After"));
        assertFalse(service.getGroupNames().contains("Before"));

        group("Other");
        assertThrows(IllegalArgumentException.class, () -> service.renameGroup("After", "Other"));
        assertEquals("After", service.getServerById(server.getId()).getGroupName(),
                "a failed rename must not move members");
    }

    @Test
    @DisplayName("deleteGroup ungroups its members but keeps the servers")
    void deleteGroupKeepsServers() {
        SSHServerModel server = create("Doomed");
        group("Doomed");
        server.setGroupName("Doomed");
        service.saveServer(server);

        service.deleteGroup("Doomed");

        assertNull(service.getServerById(server.getId()).getGroupName());
        assertNotNull(service.getServerById(server.getId()), "server must survive group deletion");
        assertFalse(service.getGroupNames().contains("Doomed"));
    }

    @Test
    @DisplayName("nested groups create their ancestors and rename/delete the whole subtree")
    void nestedGroupsCreateAncestorsAndRenameSubtree() {
        group("Parent");
        service.createGroup("Parent/Child");
        createdGroups.add("Parent/Child");

        assertTrue(service.getGroupNames().contains("Parent"));
        assertTrue(service.getGroupNames().contains("Parent/Child"));

        SSHServerModel server = create("Nested");
        server.setGroupName("Parent/Child");
        service.saveServer(server);

        service.renameGroup("Parent", "Renamed");
        createdGroups.add("Renamed");
        createdGroups.add("Renamed/Child");

        assertTrue(service.getGroupNames().contains("Renamed"));
        assertTrue(service.getGroupNames().contains("Renamed/Child"));
        assertFalse(service.getGroupNames().contains("Parent/Child"));
        assertEquals("Renamed/Child", service.getServerById(server.getId()).getGroupName());

        service.deleteGroup("Renamed");
        assertFalse(service.getGroupNames().contains("Renamed"));
        assertFalse(service.getGroupNames().contains("Renamed/Child"));
        assertNull(service.getServerById(server.getId()).getGroupName(),
                "servers inside a deleted subtree must survive ungrouped");
    }

    @Test
    @DisplayName("a nested path auto-creates missing ancestors and rejects blank segments")
    void nestedPathValidation() {
        service.createGroup(" A / B / C ");
        createdGroups.add("A");
        createdGroups.add("A/B");
        createdGroups.add("A/B/C");

        assertTrue(service.getGroupNames().containsAll(List.of("A", "A/B", "A/B/C")),
                "creating a/b/c must also create a and a/b");
        assertThrows(IllegalArgumentException.class, () -> service.createGroup("Bad//Path"));
        assertThrows(IllegalArgumentException.class, () -> service.createGroup("  "));
    }

    @Test
    @DisplayName("a group cannot be renamed into itself")
    void renameIntoSelfRejected() {
        group("Self");
        assertThrows(IllegalArgumentException.class, () -> service.renameGroup("Self", "Self/Sub"));
        assertTrue(service.getGroupNames().contains("Self"));
    }

    @Test
    @DisplayName("getGroupNames keeps creation order")
    void groupOrderIsStable() {
        group("Zulu");
        group("Alpha");

        int zulu = service.getGroupNames().indexOf("Zulu");
        int alpha = service.getGroupNames().indexOf("Alpha");
        assertTrue(zulu >= 0 && alpha >= 0);
        assertTrue(zulu < alpha, "groups should keep insertion order, not alphabetical order");
    }

    @Test
    @DisplayName("reorderServers persists the given order")
    void reorderPersists() {
        SSHServerModel a = create("A");
        SSHServerModel b = create("B");
        SSHServerModel c = create("C");

        service.reorderServers(List.of(a.getId(), b.getId(), c.getId()));

        List<String> ordered = orderedIds(a, b, c);
        assertEquals(List.of(a.getId(), b.getId(), c.getId()), ordered,
                "servers must come back in the persisted order");
    }

    @Test
    @DisplayName("a newly added server is placed at the top of the list")
    void newServerGoesToTop() {
        SSHServerModel a = create("First");
        SSHServerModel b = create("Second");

        List<String> ordered = orderedIds(a, b);
        assertEquals(b.getId(), ordered.get(0), "the newest server should be first");
    }

    @Test
    void failedReorderRollsBackAndPartialOrderKeepsOtherServers() {
        SSHServerModel a = create("A"), b = create("B"), c = create("C");
        service.reorderServers(List.of(a.getId(), b.getId(), c.getId()));
        assertThrows(RuntimeException.class, () -> service.reorderServers(List.of(c.getId(), "missing-id")));
        assertEquals(List.of(a.getId(), b.getId(), c.getId()), orderedIds(a, b, c));
        service.reorderServers(List.of(c.getId()));
        assertEquals(List.of(c.getId(), a.getId(), b.getId()), orderedIds(a, b, c));
        assertThrows(IllegalArgumentException.class, () -> service.reorderServers(List.of(a.getId(), a.getId())));
    }

    private List<String> orderedIds(SSHServerModel... servers) {
        List<String> expected = new ArrayList<>();
        for (SSHServerModel s : servers) {
            expected.add(s.getId());
        }
        List<String> ordered = new ArrayList<>();
        for (SSHServerModel s : service.getAllServers()) {
            if (expected.contains(s.getId())) {
                ordered.add(s.getId());
            }
        }
        return ordered;
    }
}
