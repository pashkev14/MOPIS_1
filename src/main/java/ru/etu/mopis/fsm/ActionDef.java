package ru.etu.mopis.fsm;

/**
 * Действие шаблона процесса. Название действия — данные конфигурации, не Java-класс.
 */
public final class ActionDef {

    private final String id;
    private final String title;

    public ActionDef(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    @Override
    public String toString() {
        return "Action[" + id + "]";
    }
}
