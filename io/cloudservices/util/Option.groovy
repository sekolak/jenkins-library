package io.cloudservices.util

class Option<T> {
    private final T value

    private Option(T value) {
        this.value = value
    }

    static <T> Option<T> of(T value) {
        return new Option<>(value)
    }

    T get() {
        return value
    }

    boolean isPresent() {
        return value != null
    }

    static <T> Option<T> Some(T value) {
        return of(value)
    }
}
