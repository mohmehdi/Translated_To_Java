
package com.google.samples.apps.sunflower.compose.plantdetail;

import android.graphics.drawable.Drawable;
import android.text.method.LinkMovementMethod;
import androidx.annotation.VisibleForTesting;
import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.animateFloat;
import androidx.compose.animation.core.spring;
import androidx.compose.animation.core.updateTransition;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.ScrollState;
import androidx.compose.foundation.background;
import androidx.compose.foundation.clickable;
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
import androidx.compose.ui.ExperimentalComposeUiApi;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.alpha;
import androidx.compose.ui.geometry.Offset;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection;
import androidx.compose.ui.input.nestedscroll.NestedScrollSource;
import androidx.compose.ui.input.nestedscroll.nestedScroll;
import androidx.compose