package dev.farrun.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Application-level storage for non-secret SSH server data. */
@Service(Service.Level.APP)
@State(name = "FarRunServers", storages = @Storage("FarRunServers.xml"))
public final class ServerStore implements PersistentStateComponent<ServerStore.State> {

    private State state = new State();

    public static @NotNull ServerStore getInstance() {
        return ApplicationManager.getApplication().getService(ServerStore.class);
    }

    public synchronized @NotNull List<Server> servers() {
        return state.servers.stream()
                .map(ServerState::toServer)
                .sorted(Comparator.comparing(Server::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized void replace(@NotNull List<Server> servers) {
        State replacement = new State();
        replacement.servers = servers.stream().map(ServerState::from).toList();
        state = replacement;
    }

    public synchronized void trustHostKey(@NotNull String serverId, @NotNull String encodedHostKey) {
        state.servers.stream()
                .filter(server -> serverId.equals(server.id))
                .findFirst()
                .ifPresent(server -> server.trustedHostKey = encodedHostKey);
    }

    public synchronized @Nullable Server find(@NotNull String id) {
        return state.servers.stream()
                .filter(server -> id.equals(server.id))
                .findFirst()
                .map(ServerState::toServer)
                .orElse(null);
    }

    @Override
    public synchronized @NotNull State getState() {
        return state.copy();
    }

    @Override
    public synchronized void loadState(@NotNull State state) {
        this.state = state.copy();
    }

    public static final class State {
        public List<ServerState> servers = new ArrayList<>();

        public @NotNull State copy() {
            State copy = new State();
            copy.servers = servers.stream().map(ServerState::copy).toList();
            return copy;
        }
    }

    public static final class ServerState {
        public String id = "";
        public String displayName = "";
        public String host = "";
        public int port = 22;
        public String username = "";
        public String authenticationMethod = AuthenticationMethod.PASSWORD.name();
        public String privateKeyPath;
        public String trustedHostKey;

        static @NotNull ServerState from(@NotNull Server server) {
            ServerState state = new ServerState();
            state.id = server.id();
            state.displayName = server.displayName();
            state.host = server.host();
            state.port = server.port();
            state.username = server.username();
            state.authenticationMethod = server.authenticationMethod().name();
            state.privateKeyPath = server.privateKeyPath();
            state.trustedHostKey = server.trustedHostKey();
            return state;
        }

        @NotNull Server toServer() {
            AuthenticationMethod method;
            try {
                method = AuthenticationMethod.valueOf(authenticationMethod);
            } catch (IllegalArgumentException exception) {
                method = AuthenticationMethod.PASSWORD;
            }
            return new Server(id, displayName, host, port, username, method, privateKeyPath, trustedHostKey);
        }

        @NotNull ServerState copy() {
            ServerState copy = new ServerState();
            copy.id = id;
            copy.displayName = displayName;
            copy.host = host;
            copy.port = port;
            copy.username = username;
            copy.authenticationMethod = authenticationMethod;
            copy.privateKeyPath = privateKeyPath;
            copy.trustedHostKey = trustedHostKey;
            return copy;
        }
    }
}
