
package com.example.android.architecture.blueprints.todoapp.tasks;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.databinding.TaskItemBinding;

public class TasksAdapter extends ListAdapter<Task, TasksAdapter.ViewHolder> {

    private TasksViewModel viewModel;

    public TasksAdapter(TasksViewModel viewModel) {
        super(new TaskDiffCallback());
        this.viewModel = viewModel;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Task item = getItem(position);
        holder.bind(viewModel, item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return ViewHolder.from(parent);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TaskItemBinding binding;

        private ViewHolder(TaskItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(TasksViewModel viewModel, Task item) {
            binding.setViewmodel(viewModel);
            binding.setTask(item);
            binding.executePendingBindings();
        }

        public static ViewHolder from(ViewGroup parent) {
            LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
            TaskItemBinding binding = TaskItemBinding.inflate(layoutInflater, parent, false);
            return new ViewHolder(binding);
        }
    }

    public static class TaskDiffCallback extends DiffUtil.ItemCallback<Task> {

        @Override
        public boolean areItemsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
            return oldItem.equals(newItem);
        }
    }
}
