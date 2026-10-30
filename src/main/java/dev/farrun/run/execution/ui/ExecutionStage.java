package dev.farrun.run.execution.ui;

/** Ordered real milestones of one FarRun execution. */
public enum ExecutionStage {
    BUILD("Build"), SSH("SSH"), RUNTIME("Runtime"), UPLOAD("Upload"), JVM("JVM"), RUNNING("Running");

    private final String title;
    ExecutionStage(String title) { this.title = title; }
    public String title() { return title; }
}
