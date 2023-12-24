
package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.clickable;
import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.foundation.layout.Column;
import androidx.compose.foundation.layout.Row;
import androidx.compose.foundation.layout.fillMaxSize;
import androidx.compose.foundation.layout.fillMaxWidth;
import androidx.compose.foundation.layout.padding;
import androidx.compose.foundation.layout.size;
import androidx.compose.foundation.lazy.LazyColumn;
import androidx.compose.foundation.lazy.items;
import androidx.compose.material.Checkbox;
import androidx.compose.material.FloatingActionButton;
import androidx.compose.material.Icon;
import androidx.compose.material.MaterialTheme;
import androidx.compose.material.Scaffold;
import androidx.compose.material.Surface;
import androidx.compose.material.Text;
import androidx.compose.material.icons.Icons;
import androidx.compose.material.icons.filled.Add;
import androidx.compose.material.rememberScaffoldState;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.livedata.observeAsState;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.res.dimensionResource;
import androidx.compose.ui.res.painterResource;
import androidx.compose.ui.res.stringResource;
import androidx.compose.ui.text.style.TextDecoration;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.unit.dp;
import androidx.lifecycle.viewmodel.compose.viewModel;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.util.LoadingContent;
import com.example.android.architecture.blueprints.todoapp.util.getViewModelFactory;
import com.google.accompanist.appcompattheme.AppCompatTheme;

@Composable
public class TasksScreen(
    onAddTask: () -> Unit,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = viewModel(factory = getViewModelFactory())
) {
    val scaffoldState = rememberScaffoldState();
    Scaffold(
        scaffoldState = scaffoldState,
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Filled.Add, stringResource(id = R.string.add_task));
            }
        }
    ) { paddingValues ->
        val loading by viewModel.dataLoading.observeAsState(initial = false);
        val items by viewModel.items.observeAsState(initial = emptyList());
        val filteringLabel by viewModel.currentFilteringLabel.observeAsState(R.string.label_all);
        val noTasksLabel by viewModel.noTasksLabel.observeAsState(initial = R.string.no_tasks_all);
        val noTasksIconRes by viewModel.noTaskIconRes.observeAsState(R.drawable.logo_no_fill);

        TasksContent(
            loading = loading,
            tasks = items,
            currentFilteringLabel = filteringLabel,
            noTasksLabel = noTasksLabel,
            noTasksIconRes = noTasksIconRes,
            onRefresh = viewModel::refresh,
            onTaskClick = onTaskClick,
            onTaskCheckedChange = viewModel::completeTask,
            modifier = Modifier.padding(paddingValues)
        );
    }
}

@Composable
private void TasksContent(
    boolean loading,
    List<Task> tasks,
    @StringRes int currentFilteringLabel,
    @StringRes int noTasksLabel,
    @DrawableRes int noTasksIconRes,
    Runnable onRefresh,
    (Task) -> Unit onTaskClick,
    (Task, boolean) -> Unit onTaskCheckedChange,
    Modifier modifier = Modifier
) {
    LoadingContent(
        loading = loading,
        empty = tasks.isEmpty(),
        emptyContent = { TasksEmptyContent(noTasksLabel, noTasksIconRes, modifier); },
        onRefresh = onRefresh
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(currentFilteringLabel),
                modifier = Modifier.padding(
                    horizontal = dimensionResource(id = R.dimen.list_item_padding),
                    vertical = dimensionResource(id = R.dimen.activity_vertical_margin)
                ),
                style = MaterialTheme.typography.h6
            );
            LazyColumn {
                items(tasks) { task ->
                    TaskItem(
                        task = task,
                        onTaskClick = onTaskClick,
                        onCheckedChange = { onTaskCheckedChange(task, it); }
                    );
                }
            }
        }
    }
}

@Composable
private void TaskItem(
    Task task,
    (boolean) -> Unit onCheckedChange,
    (Task) -> Unit onTaskClick
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(id = R.dimen.activity_horizontal_margin),
                vertical = dimensionResource(id = R.dimen.list_item_padding),
            )
            .clickable { onTaskClick(task); }
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = onCheckedChange
        );
        Text(
            text = task.titleForList,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(
                start = dimensionResource(id = R.dimen.activity_horizontal_margin)
            ),
            textDecoration = if (task.isCompleted) {
                TextDecoration.LineThrough
            } else {
                null
            }
        );
    }
}

@Composable
private void TasksEmptyContent(
    @StringRes int noTasksLabel,
    @DrawableRes int noTasksIconRes,
    Modifier modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = noTasksIconRes),
            contentDescription = stringResource(R.string.no_tasks_image_content_description),
            modifier = Modifier.size(96.dp)
        );
        Text(stringResource(id = noTasksLabel));
    }
}

@Preview
@Composable
private void TasksContentPreview() {
    AppCompatTheme {
        Surface {
            TasksContent(
                loading = false,
                tasks = listOf(
                    Task("Title 1", "Description 1"),
                    Task("Title 2", "Description 2", true),
                    Task("Title 3", "Description 3", true),
                    Task("Title 4", "Description 4"),
                    Task("Title 5", "Description 5", true)
                ),
                currentFilteringLabel = R.string.label_all,
                noTasksLabel = R.string.no_tasks_all,
                noTasksIconRes = R.drawable.logo_no_fill,
                onRefresh = { },
                onTaskClick = { },
                onTaskCheckedChange = { _, _ -> },
            );
        }
    }
}

@Preview
@Composable
private void TasksContentEmptyPreview() {
    AppCompatTheme {
        Surface {
            TasksContent(
                loading = false,
                tasks = emptyList(),
                currentFilteringLabel = R.string.label_all,
                noTasksLabel = R.string.no_tasks_all,
                noTasksIconRes = R.drawable.logo_no_fill,
                onRefresh = { },
                onTaskClick = { },
                onTaskCheckedChange = { _, _ -> },
            );
        }
    }
}

@Preview
@Composable
private void TasksEmptyContentPreview() {
    AppCompatTheme {
        Surface {
            TasksEmptyContent(
                noTasksLabel = R.string.no_tasks_all,
                noTasksIconRes = R.drawable.logo_no_fill
            );
        }
    }
}

@Preview
@Composable
private void TaskItemPreview() {
    AppCompatTheme {
        Surface {
            TaskItem(
                task = Task("Title", "Description"),
                onTaskClick = { },
                onCheckedChange = { }
            );
        }
    }
}

@Preview
@Composable
private void TaskItemCompletedPreview() {
    AppCompatTheme {
        Surface {
            TaskItem(
                task = Task("Title", "Description", true),
                onTaskClick = { },
                onCheckedChange = { }
            );
        }
    }
}
