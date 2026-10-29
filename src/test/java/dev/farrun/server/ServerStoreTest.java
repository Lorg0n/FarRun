package dev.farrun.server;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerStoreTest {

    @Test
    void restoresNonSensitiveServerSettingsWithoutSecrets() {
        ServerStore store = new ServerStore();
        Server server = new Server(
                "8ed7d5ee-ec25-48b1-928d-1a084c1a5631",
                "Build host",
                "build.example.test",
                2222,
                "deploy",
                AuthenticationMethod.PRIVATE_KEY,
                "/home/user/.ssh/id_ed25519",
                "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestOnly"
        );
        store.replace(List.of(server));

        Element serialized = XmlSerializer.serialize(store.getState());
        ServerStore.State restoredState = XmlSerializer.deserialize(serialized, ServerStore.State.class);
        ServerStore restored = new ServerStore();
        restored.loadState(restoredState);

        assertEquals(List.of(server), restored.servers());
        String xml = serialized.toString();
        assertFalse(xml.contains("correct-horse-battery-staple"));
        assertFalse(xml.contains("private-key-content"));
    }

    @Test
    void replacesAndDeletesServerStateWithoutRetainingOldEntries() {
        ServerStore store = new ServerStore();
        Server first = Server.newServer();
        Server second = Server.newServer();

        store.replace(List.of(first, second));
        store.replace(List.of(second));

        assertEquals(List.of(second), store.servers());
        assertNull(store.find(first.id()));
    }
}
