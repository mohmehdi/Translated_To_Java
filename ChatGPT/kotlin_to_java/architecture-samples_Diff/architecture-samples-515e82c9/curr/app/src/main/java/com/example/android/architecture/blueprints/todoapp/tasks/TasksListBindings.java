package com.example.android.architecture.blueprints.todoapp.tasks;

import android.graphics.Paint;
import android.widget.TextView;
import androidx.databinding.BindingAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.android.architecture.blueprints.todoapp.data.Task;

public class TasksListBindings {
    
    @BindingAdapter("app:items")
    public static void setItems(RecyclerView listView, List<Task> items) {
        if (items != null) {
            ((TasksAdapter) listView.getAdapter()).submitList(items);
        }
    }
    
    @BindingAdapter("app:completedTask")
    public static void setStyle(TextView textView, boolean enabled) {
        if (enabled) {
            textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            textView.setPaintFlags(textView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }
}