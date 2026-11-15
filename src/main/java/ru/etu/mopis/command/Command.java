package ru.etu.mopis.command;

/**
 * Команда (паттерн Command): запрос на исполнение операции над процессом.
 */
public interface Command {

    CommandResult execute();
}
