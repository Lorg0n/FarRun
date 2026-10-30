package dev.farrun.run.execution.ui;

public enum ExecutionStatus {
    PREPARING("Preparing"), BUILDING("Building"), CONNECTING("Connecting"), INSTALLING_RUNTIME("Installing Runtime"),
    UPLOADING("Uploading"), STARTING("Starting"), RUNNING("Running"), COMPLETED("Completed"), FAILED("Failed"),
    STOPPED("Stopped"), CONNECTION_LOST("Connection Lost");

    private final String title;
    ExecutionStatus(String title) { this.title = title; }
    public String title() { return title; }
}
