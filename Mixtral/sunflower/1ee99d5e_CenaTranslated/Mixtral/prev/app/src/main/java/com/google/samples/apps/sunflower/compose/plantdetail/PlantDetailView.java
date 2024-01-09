package com.google.samples.apps.sunflower.compose.plantdetail;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.Spring.StiffnessLow;
import androidx.compose.animation.core.animateFloat;
import androidx.compose.animation.core.spring;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.ScrollState;
import androidx.compose.foundation.background;
import androidx.compose.foundation.clickable;
import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.foundation.layout.Box;
import androidx.compose.foundation.layout.Column;
import androidx.compose.foundation.layout.ConstraintLayout;
import androidx.compose.foundation.layout.Row;
import androidx.compose.foundation.layout.fillMaxSize;
import androidx.compose.foundation.layout.fillMaxWidth;
import androidx.compose.foundation.layout.height;
import androidx.compose.foundation.layout.padding;
import androidx.compose.foundation.layout.sizeIn;
import androidx.compose.foundation.layout.statusBarsPadding;
import androidx.compose.foundation.layout.systemBarsPadding;
import androidx.compose.foundation.layout.wrapContentSize;
import androidx.compose.foundation.rememberScrollState;
import androidx.compose.foundation.shape.CircleShape;
import androidx.compose.foundation.verticalScroll;
import androidx.compose.material.ContentAlpha;
import androidx.compose.material.FloatingActionButton;
import androidx.compose.material.Icon;
import androidx.compose.material.IconButton;
import androidx.compose.material.LocalContentAlpha;
import androidx.compose.material.MaterialTheme;
import androidx.compose.material.Surface;
import androidx.compose.material.TopAppBar;
import androidx.compose.material.icons.Icons;
import androidx.compose.material.icons.filled.Add;
import androidx.compose.material.icons.filled.ArrowBack;
import androidx.compose.material.icons.filled.Share;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.CompositionLocalProvider;
import androidx.compose.runtime.DisposableEffect;
import androidx.compose.runtime.MutableState;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.mutableStateOf;
import androidx.compose.runtime.remember;
import androidx.compose.runtime.setValue;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.ExperimentalComposeUiApi;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.alpha;
import androidx.compose.ui.geometry.Offset;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection;
import androidx.compose.ui.input.nestedscroll.NestedScrollSource;
import androidx.compose.ui.input.nestedscroll.NestedScrollState;
import androidx.compose.ui.input.nestedscroll.nestedScroll;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.layout.onGloballyPositioned;
import androidx.compose.ui.platform.LocalDensity;
import androidx.compose.ui.res.painterResource;
import androidx.compose.ui.res.stringResource;
import androidx.compose.ui.semantics.SemanticsProperties;
import androidx.compose.ui.semantics.contentDescription;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.unit.Dp;
import androidx.compose.ui.unit.dp;
import androidx.constraintlayout.compose.ConstraintLayout;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.target.ViewTarget;
import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.compose.Dimens;
import com.google.samples.apps.sunflower.compose.SunflowerImage;
import com.google.samples.apps.sunflower.compose.utils.TextSnackbarContainer;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.databinding.ItemPlantDescriptionBinding;
import com.google.samples.apps.sunflower.viewmodels.PlantDetailViewModel;
import java.util.concurrent.ExecutionException;

public class PlantDetailsScreen {
    @Composable
    public static void PlantDetailsScreen(
        PlantDetailViewModel plantDetailsViewModel,
        Runnable onBackClick,
        Runnable onShareClick,
        Runnable onGalleryClick
    ) {
        Plant plant = plantDetailsViewModel.getPlant().getValue();
        Boolean isPlanted = plantDetailsViewModel.getIsPlanted().getValue();
        Boolean showSnackbar = plantDetailsViewModel.getShowSnackbar().getValue();
        if (plant != null && isPlanted != null && showSnackbar != null) {
            TextSnackbarContainer(
                stringResource(R.string.added_plant_to_garden),
                showSnackbar,
                () - > plantDetailsViewModel.dismissSnackbar()
            ) {
                PlantDetails(
                    plant,
                    isPlanted,
                    plantDetailsViewModel.hasValidUnsplashKey(),
                    new PlantDetailsCallbacks(
                        onBackClick,
                        () - > plantDetailsViewModel.addPlantToGarden(),
                        onShareClick,
                        onGalleryClick
                    )
                );
            };
        }
    }
}

public class PlantDetailsCallbacks {
    public Runnable onFabClick;
    public Runnable onBackClick;
    public Runnable onShareClick;
    public Runnable onGalleryClick;

    public PlantDetailsCallbacks(
        Runnable onFabClick,
        Runnable onBackClick,
        Runnable onShareClick,
        Runnable onGalleryClick
    ) {
        this.onFabClick = onFabClick;
        this.onBackClick = onBackClick;
        this.onShareClick = onShareClick;
        this.onGalleryClick = onGalleryClick;
    }
}

@Composable
public static void PlantDetails(
    Plant plant,
    Boolean isPlanted,
    Boolean hasValidUnsplashKey,
    PlantDetailsCallbacks callbacks,
    Modifier modifier
) {
    var scrollState = rememberScrollState();
    PlantDetailsScroller plantScroller = new PlantDetailsScroller(scrollState, Float.MIN\ _VALUE);
    PlantDetailsScroller.ToolbarTransitionState toolbarTransitionState = plantScroller.getToolbarTransitionState();
    ToolbarState toolbarState = plantScroller.getToolbarState(LocalDensity.current);

    var toolbarAlpha = animateFloat(
        Spring(stiffness = StiffnessLow),
        0 f,
        toolbarTransitionState, {}
    );
    var contentAlpha = animateFloat(
        Spring(stiffness = StiffnessLow),
        0 f,
        toolbarTransitionState, {}
    );

    float toolbarHeightPx = Dimens.PlantDetailAppBarHeight.toPx();
    MutableState < Float > toolbarOffsetHeightPx = remember {
        mutableStateOf(0 f)
    };
    NestedScrollConnection nestedScrollConnection = new NestedScrollConnection() {
        @Override
        public Offset onPreScroll(
            Offset available,
            NestedScrollSource source
        ) {
            float delta = available.y;
            float newOffset = toolbarOffsetHeightPx.getValue() + delta;
            toolbarOffsetHeightPx.setValue(
                newOffset.coerceIn(-toolbarHeightPx, 0 f)
            );
            return Offset.Zero;
        }
    };

    Box(
        modifier
        .fillMaxSize()
        .systemBarsPadding()
        .nestedScroll(nestedScrollConnection)
    ) {
        PlantDetailsContent(
            scrollState,
            toolbarState,
            name - > {},
            plant,
            isPlanted,
            hasValidUnsplashKey,
            plantScroller.getNamePosition(),
            callbacks.onFabClick,
            callbacks.onGalleryClick,
            () - > contentAlpha.getValue()
        );
        PlantToolbar(
            toolbarState,
            plant.getName(),
            callbacks,
            () - > toolbarAlpha.getValue(),
            () - > contentAlpha.getValue()
        );
    }
}

@Composable
private static void PlantDetailsContent(
    ScrollState scrollState,
    ToolbarState toolbarState,
    Consumer < Float > onNamePosition,
    Plant plant,
    Boolean isPlanted,
    Boolean hasValidUnsplashKey,
    Float namePosition,
    Runnable onFabClick,
    Runnable onGalleryClick,
    Function0 < Float > contentAlpha
) {
    Column(Modifier.verticalScroll(scrollState)) {
        ConstraintLayout(Modifier.fillMaxWidth()) {
            val(image, fab, info) = createRefs();

            PlantImage(
                plant.getImageUrl(),
                imageHeight,
                modifier
                .constrainAs(image) {
                    top.linkTo(parent.top)
                }
                .alpha(contentAlpha.invoke()),
                null
            );

            if (!isPlanted) {
                PlantFab(
                    onFabClick,
                    modifier
                    .constrainAs(fab) {
                        centerAround(image.bottom)
                        absoluteRight.linkTo(
                            parent.absoluteRight,
                            margin = Dimens.PaddingSmall
                        )
                    }
                    .alpha(contentAlpha.invoke())
                );
            }

            PlantInformation(
                name,
                plant.getWateringInterval(),
                plant.getDescription(),
                hasValidUnsplashKey,
                onNamePosition,
                toolbarState,
                onGalleryClick,
                modifier
                .constrainAs(info) {
                    top.linkTo(image.bottom)
                }
            );
        }
    }
}

@OptIn(ExperimentalGlideComposeApi.class)
@Composable
private static void PlantImage(
    String imageUrl,
    Dp imageHeight,
    Modifier modifier,
    Color placeholderColor,
    RequestListener < Drawable > requestListener
) {
    var isLoading by remember {
        mutableStateOf(true)
    };
    Box(
        modifier
        .fillMaxWidth()
        .height(imageHeight)
    ) {
        if (isLoading) {
            Box(
                modifier
                .fillMaxSize()
                .background(placeholderColor)
            )
        }
        SunflowerImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
            .fillMaxSize(),
            contentScale = ContentScale.Crop,
            requestListener
        );
    }
}

@Composable
private static void PlantFab(
    Runnable onFabClick,
    Modifier modifier
) {
    String addPlantContentDescription = stringResource(R.string.add\ _plant);
    FloatingActionButton(
        onClick = onFabClick,
        shape = MaterialTheme.shapes.small,
        modifier
        .semantics {
            contentDescription = addPlantContentDescription
        }
    );
}

@Composable
private static void PlantToolbar(
    ToolbarState toolbarState,
    String plantName,
    PlantDetailsCallbacks callbacks,
    Function0 < Float > toolbarAlpha,
    Function0 < Float > contentAlpha
) {
    if (toolbarState.isShown) {
        PlantDetailsToolbar(
            plantName,
            callbacks.onBackClick,
            callbacks.onShareClick,
            modifier
            .alpha(toolbarAlpha.invoke())
        );
    } else {
        PlantHeaderActions(
            callbacks.onBackClick,
            callbacks.onShareClick,
            modifier
            .alpha(contentAlpha.invoke())
        );
    }
}

@Composable
private static void PlantDetailsToolbar(
    String plantName,
    Runnable onBackClick,
    Runnable onShareClick,
    Modifier modifier
) {
    Surface {
        TopAppBar(
            modifier = modifier.statusBarsPadding(),
            backgroundColor = MaterialTheme.colors.surface
        ) {
            IconButton(
                onBackClick,
                Modifier.align(Alignment.CenterVertically)
            );
            Text(
                text = plantName,
                style = MaterialTheme.typography.h6,
                modifier = Modifier
                .weight(1 f)
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
            );
            val shareContentDescription =
                stringResource(R.string.menu\ _item\ _share\ _plant);
            IconButton(
                onShareClick,
                Modifier
                .align(Alignment.CenterVertically)
                .semantics {
                    contentDescription = shareContentDescription
                }
            );
        }
    }
}

@Composable
private static void PlantHeaderActions(
    Runnable onBackClick,
    Runnable onShareClick,
    Modifier modifier
) {
    Row(
        modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(top = Dimens.ToolbarIconPadding),
        Arrangement.SpaceBetween
    ) {
        val iconModifier = Modifier
            .sizeIn(
                maxWidth = Dimens.ToolbarIconSize,
                maxHeight = Dimens.ToolbarIconSize
            )
            .background(
                Color.WHITE,
                CircleShape
            )
            .clickable(onClick = onBackClick);

        Icon(
            Icons.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = iconModifier
        );
        val shareContentDescription =
            stringResource(R.string.menu\ _item\ _share\ _plant);
        iconModifier = Modifier
            .sizeIn(
                maxWidth = Dimens.ToolbarIconSize,
                maxHeight = Dimens.ToolbarIconSize
            )
            .background(
                Color.WHITE,
                CircleShape
            )
            .clickable(onClick = onShareClick)
            .semantics {
                contentDescription = shareContentDescription
            };

        Icon(
            Icons.Filled.Share,
            contentDescription = "Share",
            modifier = iconModifier
        );
    }
}

@OptIn(ExperimentalComposeUiApi.class)
@Composable
private static void PlantInformation(
    String name,
    int wateringInterval,
    String description,
    Boolean hasValidUnsplashKey,
    Consumer < Float > onNamePosition,
    ToolbarState toolbarState,
    Runnable onGalleryClick,
    Modifier modifier
) {
    Column(modifier = modifier.padding(Dimens.PaddingLarge)) {
        Text(
            text = name,
            style = MaterialTheme.typography.h5,
            modifier = Modifier
            .padding(
                start = Dimens.PaddingSmall,
                end = Dimens.PaddingSmall,
                bottom = Dimens.PaddingNormal
            )
            .align(Alignment.CenterHorizontally)
            .onGloballyPositioned(position - > onNamePosition.accept(position.getPositionInWindow().getY()))
            .visible(toolbarState == ToolbarState.HIDDEN)
        );
        Box(
            modifier
            .align(Alignment.CenterHorizontally)
            .padding(
                start = Dimens.PaddingSmall,
                end = Dimens.PaddingSmall,
                bottom = Dimens.PaddingNormal
            )
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Watering needs: ",
                    color = MaterialTheme.colors.primaryVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                    .padding(horizontal = Dimens.PaddingSmall)
                    .align(Alignment.CenterHorizontally)
                );
                TextView textView = new TextView(LocalContext.current);
                textView.setText(
                    Html.fromHtml(
                        "<b>Water " + wateringInterval + " times a month</b>",
                        Html.FROM\ _HTML\ _MODE\ _COMPACT
                    )
                );
                textView.setTextColor(MaterialTheme.colors.onSurface.getAlpha(ContentAlpha.medium));
                ViewTarget target = Glide.with(LocalContext.current).load(R.drawable.ic\ _photo\ _library).into(textView);
                DisposableEffect(target) {
                    target.addListener(new RequestListener < Drawable > () {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target < Drawable > target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target < Drawable > target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    });
                    onDispose {
                        target.onStop();
                    }
                };
            }
        }
        PlantDescription(description);
    }
}

@Composable
private static void PlantDescription(String description) {
    AndroidViewBinding(ItemPlantDescriptionBinding::inflate) {
        TextView plantDescription = plantDescription;
        plantDescription.setText(
            Html.fromHtml(
                description,
                Html.FROM\ _HTML\ _MODE\ _COMPACT
            )
        );
        plantDescription.setMovementMethod(LinkMovementMethod.getInstance());
        plantDescription.setLinksClickable(true);
    }
}

@Preview
@Composable
private static void PlantDetailContentPreview() {
    MdcTheme {
        Surface {
            PlantDetails(
                new Plant("plantId", "Tomato", "HTML<br>description", 6),
                true,
                true,
                new PlantDetailsCallbacks(
                    () - > {},
                    () - > {},
                    () - > {},
                    () - > {}
                )
            );
        }
    }
}

class PlantDetailsScroller {
    @NonNull
    public ToolbarState toolbarState;
    @NonNull
    public final ToolbarTransitionState toolbarTransitionState;
    @NonNull
    public final MutableState < Float > namePosition;

    public PlantDetailsScroller(
        @NonNull ScrollState scrollState,
        float namePosition
    ) {
        this.toolbarState = new ToolbarState(scrollState);
        this.toolbarTransitionState = new ToolbarTransitionState();
        this.namePosition = mutableStateOf(namePosition);
    }

    public ToolbarState getToolbarState(
        @NonNull LocalDensity localDensity
    ) {
        return this.toolbarState;
    }

    public float getNamePosition() {
        return this.namePosition.getValue();
    }
}

class ToolbarState {
    public ScrollState scrollState;
    public boolean isShown;

    public ToolbarState(
        @NonNull ScrollState scrollState
    ) {
        this.scrollState = scrollState;
        this.isShown = true;
    }
}

class ToolbarTransitionState {
    public float current;
    public float target;

    public ToolbarTransitionState() {
        this.current = Float.MIN\ _VALUE;
        this.target = Float.MIN\ _VALUE;
    }
}