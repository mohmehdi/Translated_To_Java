
package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.databinding.BindingAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.android.architecture.blueprints.todoapp.data.Task;

public class BindingUtils {

    @BindingAdapter("app:items")
    public static void setItems(RecyclerView listView, List<Task> items) {
        if (items != null) {
            ((TasksAdapter) listView.getAdapter()).submitList(items);
        }
    }
}
