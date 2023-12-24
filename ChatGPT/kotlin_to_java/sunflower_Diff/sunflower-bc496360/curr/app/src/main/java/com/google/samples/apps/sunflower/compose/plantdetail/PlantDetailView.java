
package com.google.samples.apps.sunflower.compose.plantdetail;

import android.text.method.LinkMovementMethod;
import androidx.annotation.VisibleForTesting;
import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.animateFloat;
import androidx.compose.animation.core.spring;
import androidx.compose.animation.core.updateTransition;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.ScrollState;
import androidx.compose.foundation.background;
import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.foundation.layout.Box;
import androidx.compose.foundation.layout.Column;
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
import androidx.compose.material.Text;
import androidx.compose.material.TopAppBar;
import androidx.compose.material.icons.Icons;
import androidx.compose.material.icons.filled.Add;
import androidx.compose.material.icons.filled.ArrowBack;
import androidx.compose.material.icons.filled.Share;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.CompositionLocalProvider;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.livedata.observeAsState;
import androidx.compose.runtime.mutableStateOf;
import androidx.compose.runtime.remember;
import androidx.compose.runtime.setValue;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.alpha;
import androidx.compose.ui.geometry.Offset;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection;
import androidx.compose.ui.input.nestedscroll.NestedScrollSource;
import androidx.compose.ui.input.nestedscroll.nestedScroll;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.layout.onGloballyPositioned;
import androidx.compose.ui.layout.positionInWindow;
import androidx.compose.ui.platform.LocalContext;
import androidx.compose.ui.platform.LocalDensity;
import androidx.compose.ui.res.stringResource;
import androidx.compose.ui.semantics.contentDescription;
import androidx.compose.ui.semantics.semantics;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.unit.Dp;
import androidx.compose.ui.viewinterop.AndroidViewBinding;
import androidx.constraintlayout.compose.ConstraintLayout;
import androidx.core.text.HtmlCompat;
import coil.compose.AsyncImagePainter;
import coil.compose.rememberAsyncImagePainter;
import coil.request.ImageRequest;
import com.google.android.material.composethemeadapter.MdcTheme;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.compose.Dimens;
import com.google.samples.apps.sunflower.compose.utils.TextSnackbarContainer;
import com.google.samples.apps.sunflower.compose.utils.getQuantityString;
import com.google.samples.apps.sunflower.compose.visible;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.databinding.ItemPlantDescriptionBinding;
import com.google.samples.apps.sunflower.viewmodels.PlantDetailViewModel;

public class PlantDetailsCallbacks {
    private final Runnable onFabClick;
    private final Runnable onBackClick;
    private final Runnable onShareClick;

    public PlantDetailsCallbacks(Runnable onFabClick, Runnable onBackClick, Runnable onShareClick) {
        this.onFabClick = onFabClick;
        this.onBackClick = onBackClick;
        this.onShareClick = onShareClick;
    }

    public Runnable getOnFabClick() {
        return onFabClick;
    }

    public Runnable getOnBackClick() {
        return onBackClick;
    }

    public Runnable getOnShareClick() {
        return onShareClick;
    }
}

@Composable
public void PlantDetailsScreen(PlantDetailViewModel plantDetailsViewModel, Runnable onBackClick, Runnable onShareClick) {
    Plant plant = plantDetailsViewModel.getPlant().observeAsState().getValue();
    Boolean isPlanted = plantDetailsViewModel.getIsPlanted().observeAsState().getValue();
   