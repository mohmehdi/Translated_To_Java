
package com.example.android.architecture.blueprints.todoapp;

import androidx.lifecycle.Observer;

public class Event<T> {

    private final T content;
    private boolean hasBeenHandled = false;

    public Event(T content) {
        this.content = content;
    }

    public boolean hasBeenHandled() {
        return hasBeenHandled;
    }

    public T getContentIfNotHandled() {
        if (hasBeenHandled) {
            return null;
        } else {
            hasBeenHandled = true;
            return content;
        }
    }

    public T peekContent() {
        return content;
    }
}

class EventObserver<T> implements Observer<Event<T>> {

    private final OnEventUnhandledContent<T> onEventUnhandledContent;

    public EventObserver(OnEventUnhandledContent<T> onEventUnhandledContent) {
        this.onEventUnhandledContent = onEventUnhandledContent;
    }

    @Override
    public void onChanged(Event<T> event) {
        T value = event.getContentIfNotHandled();
        if (value != null) {
            onEventUnhandledContent.onEventUnhandledContent(value);
        }
    }

    public interface OnEventUnhandledContent<T> {
        void onEventUnhandledContent(T value);
    }
}
