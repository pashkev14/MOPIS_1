package ru.etu.mopis.fsm;

/**
 * Экземпляр процесса — единственный носитель изменяемого состояния процесса.
 */
public final class ProcessInstance {

    private final String objectId;
    private final String classId;
    private final String processId;
    private String stateId;

    public ProcessInstance(String objectId, String classId, String processId, String stateId) {
        this.objectId = objectId;
        this.classId = classId;
        this.processId = processId;
        this.stateId = stateId;
    }

    public String objectId() {
        return objectId;
    }

    public String classId() {
        return classId;
    }

    public String processId() {
        return processId;
    }

    public String stateId() {
        return stateId;
    }

    /** Единственная мутация состояния процесса. */
    public void moveTo(String stateId) {
        this.stateId = stateId;
    }

    @Override
    public String toString() {
        return "ProcessInstance[" + objectId + "@" + processId + ":" + stateId + "]";
    }
}
