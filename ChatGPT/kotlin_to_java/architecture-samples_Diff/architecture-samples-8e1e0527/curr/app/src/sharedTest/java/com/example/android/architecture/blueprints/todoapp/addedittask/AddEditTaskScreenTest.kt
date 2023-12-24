
package com.example.android.architecture.blueprints.todoapp.addedittask;

import androidx.activity.ComponentActivity;
import androidx.compose.material.Surface;
import androidx.compose.ui.test.SemanticsNodeInteraction;
import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.hasSetTextAction;
import androidx.compose.ui.test.hasText;
import androidx.compose.ui.test.junit4.createAndroidComposeRule;
import androidx.compose.ui.test.onNodeWithContentDescription;
import androidx.compose.ui.test.onNodeWithText;
import androidx.compose.ui.test.performClick;
import androidx.compose.ui.test.performTextClearance;
import androidx.compose.ui.test.performTextInput;
import androidx.navigation.Navigation;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.example.android.architecture.blueprints.todoapp.util.getTasksBlocking;
import com.google.accompanist.appcompattheme.AppCompatTheme;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.TextLayoutMode;

@RunWith(AndroidJUnit4.class)
@MediumTest
@LooperMode(LooperMode.Mode.PAUSED)
@TextLayoutMode(TextLayoutMode.Mode.REALISTIC)
@ExperimentalCoroutinesApi
public class AddEditTaskScreenTest {

    @get:Rule
    public createAndroidComposeRule<ComponentActivity> composeTestRule = createAndroidComposeRule<>(ComponentActivity.class);
    private ComponentActivity activity() {
        return composeTestRule.getActivity();
    }

    private FakeRepository repository;

    @Before
    public void setup() {
        repository = new FakeRepository();

        composeTestRule.setContent(activity -> {
            AppCompatTheme.INSTANCE.applyTheme();
            Surface.INSTANCE.applySurface();
            AddEditTaskScreenKt.AddEditTaskScreen(
                new AddEditTaskViewModel(repository),
                null,
                () -> {}
            );
        });
    }

    @Test
    public void emptyTask_isNotSaved() {
        findTextField(R.string.title_hint).performTextClearance();
        findTextField(R.string.description_hint).performTextClearance();
        composeTestRule.onNodeWithContentDescription(activity().getString(R.string.cd_save_task))
            .performClick();

        composeTestRule
            .onNodeWithText(activity().getString(R.string.empty_task_message))
            .assertIsDisplayed();
    }

    @Test
    public void validTask_isSaved() {
        findTextField(R.string.title_hint).performTextInput("title");
        findTextField(R.string.description_hint).performTextInput("description");
        composeTestRule.onNodeWithContentDescription(activity().getString(R.string.cd_save_task))
            .performClick();

        Result.Success tasks = (Result.Success) repository.getTasksBlocking(true);
        Assert.assertEquals(tasks.getData().size(), 1);
        Assert.assertEquals(tasks.getData().get(0).getTitle(), "title");
        Assert.assertEquals(tasks.getData().get(0).getDescription(), "description");
    }

    private SemanticsNodeInteraction findTextField(int text) {
        return composeTestRule.onNode(
            hasSetTextAction().and(hasText(activity().getString(text)))
        );
    }
}
