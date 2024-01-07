package com.google.samples.apps.sunflower.compose.plantdetail;

import android.graphics.drawable.Drawable;
import android.text.HtmlCompat;
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
import androidx.compose.foundation.layout.Row;
import androidx.compose.foundation.layout.FillMaxSize;
import androidx.compose.foundation.layout.FillMaxWidth;
import androidx.compose.foundation.layout.Height;
import androidx.compose.foundation.layout.Offset;
import androidx.compose.foundation.layout.PaddingValues;
import androidx.compose.foundation.layout.SizeIn;
import androidx.compose.foundation.layout.StatusBarsPadding;
import androidx.compose.foundation.layout.SystemBarsPadding;
import androidx.compose.foundation.layout.WrapContentSize;
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
import androidx.compose.ui.GraphicsLayer;
import androidx.compose.ui.InputDestination;
import androidx.compose.ui.Layout;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.Alpha;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.graphics.DefaultAlpha;
import androidx.compose.ui.graphics.ImageBitmap;
import androidx.compose.ui.graphics.asImageBitmap;
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection;
import androidx.compose.ui.input.nestedscroll.NestedScrollSource;
import androidx.compose.ui.input.pointer.PointerEventPass;
import androidx.compose.ui.input.pointer.PointerInputChange;
import androidx.compose.ui.input.pointer.consumeAllChanges;
import androidx.compose.ui.layout.MeasureResult;
import androidx.compose.ui.layout.MeasureScope;
import androidx.compose.ui.layout.Placeable;
import androidx.compose.ui.layout.SubcomposeLayout;
import androidx.compose.ui.layout.layout;
import androidx.compose.ui.modifier.ModifierLocalConsumer;
import androidx.compose.ui.platform.AmbientDensity;
import androidx.compose.ui.platform.LocalDensity;
import androidx.compose.ui.platform.LocalLayoutDirection;
import androidx.compose.ui.platform.LocalView;
import androidx.compose.ui.res.painterResource;
import androidx.compose.ui.semantics.SemanticsActions;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;
import androidx.compose.ui.text.AnnotatedString;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.unit.Dp;
import androidx.compose.ui.unit.dp;
import androidx.compose.ui.util.fastFor;
import androidx.compose.ui.viewinterop.AndroidView;
import androidx.constraintlayout.compose.ConstraintLayout;
import androidx.constraintlayout.compose.ConstraintSet;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.compose.Dimens;
import com.google.samples.apps.sunflower.compose.SunflowerImage;
import com.google.samples.apps.sunflower.compose.TextSnackbarContainer;
import com.google.samples.apps.sunflower.compose.utils.Plant;
import com.google.samples.apps.sunflower.compose.utils.TextSnackbarContainerKt;
import com.google.samples.apps.sunflower.databinding.ItemPlantDescriptionBinding;
import com.google.samples.apps.sunflower.viewmodels.PlantDetailViewModel;
import java.util.Objects;

public class PlantDetailsScreenKt {

    PlantDetailsScreen(
        PlantDetailViewModel plantDetailsViewModel,
        @NonNull ComponentActivity activity,
        @NonNull Runnable onBackClick,
        @NonNull Runnable onShareClick,
        @NonNull Runnable onGalleryClick
    ) {
        Plant plant = plantDetailsViewModel.getPlant().getValue();
        Boolean isPlanted = plantDetailsViewModel.getIsPlanted().getValue();
        Boolean showSnackbar = plantDetailsViewModel.getShowSnackbar().getValue();
        if (plant != null && isPlanted != null && showSnackbar != null) {
            TextSnackbarContainerKt.TextSnackbarContainer(
                activity,
                R.string.added_plant_to_garden,
                showSnackbar,
                () - > plantDetailsViewModel.dismissSnackbar()
            ) {
                PlantDetails(
                    plant,
                    isPlanted,
                    plantDetailsViewModel.hasValidUnsplashKey(),
                    new PlantDetailsCallbacks(
                        () - > onFabClick.run(),
                        () - > onBackClick.run(),
                        () - > onShareClick.run(),
                        () - > onGalleryClick.run()
                    ),
                    Modifier.fillMaxSize()
                );
            }
        }
    }

    public static class PlantDetailsCallbacks {
        public final Runnable onFabClick;
        public final Runnable onBackClick;
        public final Runnable onShareClick;
        public final Runnable onGalleryClick;

        public PlantDetailsCallbacks(
            @NonNull Runnable onFabClick,
            @NonNull Runnable onBackClick,
            @NonNull Runnable onShareClick,
            @NonNull Runnable onGalleryClick
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
        @NonNull PlantDetailsCallbacks callbacks,
        Modifier modifier
    ) {
        ScrollState scrollState = rememberScrollState();
        PlantDetailsScroller plantScroller = remember {
            new PlantDetailsScroller(scrollState, -Float.MAX\ _VALUE)
        };
        PlantDetailsScroller.ToolbarTransitionState toolbarTransitionState =
            plantScroller.getToolbarTransitionState();
        ToolbarState toolbarState = plantScroller.getToolbarState(Objects.requireNonNull(LocalDensity.current));

        Float toolbarAlpha = animateFloat(
            toolbarTransitionState,
            Spring(stiffness = StiffnessLow),
            ToolbarState.HIDDEN,
            1 f,
            0 f
        );
        Float contentAlpha = animateFloat(
            toolbarTransitionState,
            Spring(stiffness = StiffnessLow),
            ToolbarState.HIDDEN,
            0 f,
            1 f
        );

        Float toolbarHeightPx =
            Dimens.PlantDetailAppBarHeight.toPx().floatValue();
        MutableState < Float > toolbarOffsetHeightPx = remember {
            mutableStateOf(0 f)
        };
        NestedScrollConnection nestedScrollConnection = new NestedScrollConnection() {
            @Override
            public Offset onPreScroll(
                Offset available,
                NestedScrollSource source
            ) {
                Float delta = available.y;
                Float newOffset = toolbarOffsetHeightPx.getValue() + delta;
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
        );
        PlantDetailsContent(
            scrollState,
            toolbarState,
            plant,
            isPlanted,
            hasValidUnsplashKey,
            new Dp(toolbarHeightPx + toolbarOffsetHeightPx.getValue()),
            callbacks::onFabClick,
            callbacks::onGalleryClick,
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

    @Composable
    private static void PlantDetailsContent(
        ScrollState scrollState,
        ToolbarState toolbarState,
        Plant plant,
        Boolean isPlanted,
        Boolean hasValidUnsplashKey,
        Dp imageHeight,
        Runnable onFabClick,
        Runnable onGalleryClick,
        @NonNull Supplier < Float > contentAlpha
    ) {
        Column(Modifier.verticalScroll(scrollState)) {
            ConstraintLayout(
                Modifier.fillMaxWidth()
            ) {
                val image = createRef();
                val fab = createRef();
                val info = createRef();

                PlantImage(
                    plant.getImageUrl(),
                    imageHeight,
                    Modifier
                    .constrainAs(image) {
                        top.linkTo(parent.top);
                    }
                    .alpha(contentAlpha.get()),
                    null
                );

                if (!isPlanted) {
                    Float fabEndMargin = Dimens.PaddingSmall;
                    PlantFab(
                        onFabClick,
                        Modifier
                        .constrainAs(fab) {
                            centerAround(image.bottom);
                            absoluteRight.linkTo(
                                parent.absoluteRight,
                                margin = fabEndMargin
                            );
                        }
                        .alpha(contentAlpha.get())
                    );
                }

                PlantInformation(
                    plant.getName(),
                    plant.getWateringInterval(),
                    plant.getDescription(),
                    hasValidUnsplashKey,
                    toolbarState,
                    onGalleryClick,
                    Modifier
                    .constrainAs(info) {
                        top.linkTo(image.bottom);
                    }
                );
            }
        }
    }

    @Composable
    private static void PlantImage(
        String imageUrl,
        Dp imageHeight,
        Modifier modifier,
        @Nullable Color placeholderColor
    ) {
        Boolean isLoading = true;
        Box(
            modifier
            .fillMaxWidth()
            .height(imageHeight)
        );
        AndroidView({
            View view = new AppCompatImageView(
                Objects.requireNonNull(LocalContext.current)
            );
            Glide.with(view)
            .load(imageUrl)
            .listener(new RequestListener < Drawable > () {
                @Override
                public boolean onLoadFailed(
                    @Nullable GlideException e,
                    Object model,
                    Target < Drawable > target,
                    boolean isFirstResource
                ) {
                    isLoading = false;
                    return false;
                }

                @Override
                public boolean onResourceReady(
                    Drawable resource,
                    Object model,
                    Target < Drawable > target,
                    DataSource dataSource,
                    boolean isFirstResource
                ) {
                    isLoading = false;
                    return false;
                }
            })
            .into(view);
            return view;
        }) {
            view - >
                view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setLayoutParams(
                new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH\ _PARENT,
                    ViewGroup.LayoutParams.MATCH\ _PARENT
                )
            );
        }
    }

    @Composable
    private static void PlantFab(
        Runnable onFabClick,
        Modifier modifier
    ) {
        FloatingActionButton(
            onFabClick,
            Shapes.small,
            null,
            modifier
            .semantics {
                contentDescription = stringResource(R.string.add\ _plant);
            }
        ); {
            Icon(
                Icons.Filled.Add,
                null
            );
        }
    }

    @Composable
    private static void PlantToolbar(
        ToolbarState toolbarState,
        String plantName,
        @NonNull PlantDetailsCallbacks callbacks,
        @NonNull Supplier < Float > toolbarAlpha,
        @NonNull Supplier < Float > contentAlpha
    ) {
        if (toolbarState.isShown) {
            PlantDetailsToolbar(
                plantName,
                callbacks.onBackClick,
                callbacks.onShareClick,
                Modifier.alpha(toolbarAlpha.get())
            );
        } else {
            PlantHeaderActions(
                callbacks.onBackClick,
                callbacks.onShareClick,
                Modifier.alpha(contentAlpha.get())
            );
        }
    }

    @Composable
    private static void PlantDetailsToolbar(
        String plantName,
        @NonNull Runnable onBackClick,
        @NonNull Runnable onShareClick,
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
                ); {
                    Icon(
                        Icons.Filled.ArrowBack,
                        null,
                        Modifier.semantics {
                            contentDescription = stringResource(id = R.string.a11y\ _back);
                        }
                    );
                }
                Text(
                    plantName,
                    style = MaterialTheme.typography.h6,
                    Modifier
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
                        contentDescription = shareContentDescription;
                    }
                ); {
                    Icon(
                        Icons.Filled.Share,
                        null
                    );
                }
            }
        }
    }

    @Composable
    private static void PlantHeaderActions(
        @NonNull Runnable onBackClick,
        @NonNull Runnable onShareClick,
        Modifier modifier
    ) {
        Row(
            modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(top = Dimens.ToolbarIconPadding),
            Arrangement.SpaceBetween
        ); {
            val iconModifier = Modifier
                .sizeIn(
                    maxWidth = Dimens.ToolbarIconSize,
                    maxHeight = Dimens.ToolbarIconSize
                )
                .background(
                    MaterialTheme.colors.surface,
                    CircleShape
                );

            IconButton(
                onBackClick,
                Modifier
                .padding(start = Dimens.ToolbarIconPadding)
                .then(iconModifier)
            ); {
                Icon(
                    Icons.Filled.ArrowBack,
                    null
                );
            }
        }
        val shareContentDescription =
            stringResource(R.string.menu\ _item\ _share\ _plant);
        IconButton(
            onShareClick,
            Modifier
            .padding(end = Dimens.ToolbarIconPadding)
            .then(iconModifier)
            .semantics {
                contentDescription = shareContentDescription;
            }
        ); {
            Icon(
                Icons.Filled.Share,
                null
            );
        }
    }

    @Composable
    private static void PlantInformation(
        String name,
        int wateringInterval,
        String description,
        Boolean hasValidUnsplashKey,
        ToolbarState toolbarState,
        @NonNull Runnable onGalleryClick,
        Modifier modifier
    ) {
        Column(modifier) {
            Text(
                name,
                style = MaterialTheme.typography.h5,
                Modifier
                .padding(
                    start = Dimens.PaddingSmall,
                    end = Dimens.PaddingSmall,
                    bottom = Dimens.PaddingNormal
                )
                .align(Alignment.CenterHorizontally)
                .onGloballyPositioned {}
                .visible {
                    toolbarState == ToolbarState.HIDDEN
                }
            );
            Box(
                Modifier
                .align(Alignment.CenterHorizontally)
                .padding(
                    start = Dimens.PaddingSmall,
                    end = Dimens.PaddingSmall,
                    bottom = Dimens.PaddingNormal
                )
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Watering Needs:",
                        color = MaterialTheme.colors.primaryVariant,
                        fontWeight = FontWeight.Bold,
                        Modifier
                        .padding(horizontal = Dimens.PaddingSmall)
                        .align(Alignment.CenterHorizontally)
                    );
                    CompositionLocalProvider(
                        LocalContentAlpha provides ContentAlpha.medium
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.watering\ _needs\ _suffix,
                                wateringInterval,
                                wateringInterval
                            ),
                            Modifier
                            .align(Alignment.CenterHorizontally)
                        );
                    }
                }
                if (hasValidUnsplashKey) {
                    Image(
                        painter = painterResource(id = R.drawable.ic\ _photo\ _library),
                        contentDescription = "Gallery Icon",
                        Modifier
                        .clickable {
                            onGalleryClick.run();
                        }
                        .align(Alignment.CenterEnd)
                    );
                }
            }
            PlantDescription(description);
        }

        @Composable
        private static void PlantDescription(String description) {
            AndroidView({
                TextView textView = new TextView(Objects.requireNonNull(LocalContext.current));
                textView.setText(HtmlCompat.fromHtml(description, HtmlCompat.FROM\ _HTML\ _MODE\ _COMPACT));
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                return textView;
            }) {
                view - >
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
                        ),
                        Modifier.fillMaxSize()
                    );
                }
            }
        }
    }
}