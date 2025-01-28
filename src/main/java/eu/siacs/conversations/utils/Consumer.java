package eu.siacs.conversations.utils;

// Based on java.util.function.Consumer to avoid Android 24 dependency
public interface Consumer<T> {
    void accept(T t);
}
