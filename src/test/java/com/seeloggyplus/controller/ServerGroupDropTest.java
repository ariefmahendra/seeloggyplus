package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServerModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the drag-and-drop targeting rules of the File Management location
 * tree: dropping a server onto a group folder, onto another server, onto the
 * root area (ungrouped) or onto Local Drive.
 */
class ServerGroupDropTest {

    private SSHServerModel server(String name, String group) {
        SSHServerModel s = new SSHServerModel();
        s.setName(name);
        s.setHost("host");
        s.setPort(22);
        s.setUsername("user");
        s.setGroupName(group);
        return s;
    }

    @Test
    @DisplayName("dropping onto a group folder assigns that group")
    void droppingOnGroupAssignsThatGroup() {
        var target = UnifiedFileManagerDialogController.LocationItem.group("Production");
        assertEquals("Production", UnifiedFileManagerDialogController.groupForDropTarget(target));
    }

    @Test
    @DisplayName("dropping onto Local Drive moves the server OUT of its folder")
    void droppingOnLocalClearsGroup() {
        var target = UnifiedFileManagerDialogController.LocationItem.local();
        assertNull(UnifiedFileManagerDialogController.groupForDropTarget(target));
    }

    @Test
    @DisplayName("dropping onto the tree root moves the server OUT of its folder")
    void droppingOnRootClearsGroup() {
        var target = UnifiedFileManagerDialogController.LocationItem.root();
        assertNull(UnifiedFileManagerDialogController.groupForDropTarget(target));
    }

    @Test
    @DisplayName("dropping onto a grouped server joins that server's group")
    void droppingOnServerJoinsItsGroup() {
        var target = UnifiedFileManagerDialogController.LocationItem.of(server("web-1", "Staging"));
        assertEquals("Staging", UnifiedFileManagerDialogController.groupForDropTarget(target));
    }

    @Test
    @DisplayName("dropping onto an ungrouped server stays ungrouped")
    void droppingOnUngroupedServerStaysUngrouped() {
        var target = UnifiedFileManagerDialogController.LocationItem.of(server("web-1", null));
        assertNull(UnifiedFileManagerDialogController.groupForDropTarget(target));
    }

    @Test
    @DisplayName("node metadata distinguishes local, group and server nodes")
    void nodeMetadata() {
        var local = UnifiedFileManagerDialogController.LocationItem.local();
        var group = UnifiedFileManagerDialogController.LocationItem.group("Production");
        var srv = UnifiedFileManagerDialogController.LocationItem.of(server("web-1", "Production"));

        assertTrue(local.isLocal());
        assertFalse(local.isServer());
        assertEquals("Local Drive", local.getLabel());

        assertTrue(group.isGroup());
        assertEquals("Production", group.getGroupName());
        assertEquals("Production", group.getLabel());

        assertTrue(srv.isServer());
        assertEquals("web-1", srv.getServer().getName());
        assertEquals("Production / web-1", srv.getTooltip());
    }
}
