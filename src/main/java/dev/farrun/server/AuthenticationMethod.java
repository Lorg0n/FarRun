package dev.farrun.server;

/** Supported SSH authentication mechanisms. */
public enum AuthenticationMethod {
    PASSWORD("Password"),
    PRIVATE_KEY("SSH private key");

    private final String presentableName;

    AuthenticationMethod(String presentableName) {
        this.presentableName = presentableName;
    }

    public String presentableName() {
        return presentableName;
    }

    @Override
    public String toString() {
        return presentableName;
    }
}
