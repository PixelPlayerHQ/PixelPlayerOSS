@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.lostf1sh.pixelplayeross.presentation.screens

import android.net.Uri
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.ImageCropView
import com.lostf1sh.pixelplayeross.data.model.PlaylistShapeType
import com.lostf1sh.pixelplayeross.data.model.SmartPlaylistRule
// import com.lostf1sh.pixelplayeross.presentation.screens.ShapeType // Removed local enum
import com.lostf1sh.pixelplayeross.presentation.components.SongPickerSelectionPane
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.lostf1sh.pixelplayeross.utils.shapes.RoundedStarShape
import com.lostf1sh.pixelplayeross.utils.resolvePlaylistCoverContentColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Matrix
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.TransformOrigin
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.SliderDefaults


import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.screens.TabAnimation

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun smartPlaylistRuleTitle(rule: SmartPlaylistRule): String =
    stringResource(
        when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> R.string.presentation_batch_f_smart_rule_top_played_title
            SmartPlaylistRule.RECENTLY_PLAYED -> R.string.presentation_batch_f_smart_rule_recently_played_title
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> R.string.presentation_batch_f_smart_rule_forgotten_favorites_title
            SmartPlaylistRule.NEW_GEMS -> R.string.presentation_batch_f_smart_rule_new_gems_title
        }
    )

@Composable
private fun smartPlaylistRuleSubtitle(rule: SmartPlaylistRule): String =
    stringResource(
        when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> R.string.presentation_batch_f_smart_rule_top_played_subtitle
            SmartPlaylistRule.RECENTLY_PLAYED -> R.string.presentation_batch_f_smart_rule_recently_played_subtitle
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> R.string.presentation_batch_f_smart_rule_forgotten_favorites_subtitle
            SmartPlaylistRule.NEW_GEMS -> R.string.presentation_batch_f_smart_rule_new_gems_subtitle
        }
    )

private enum class PlaylistCreationMode {
    MANUAL,
    SMART
}

@Composable
fun CreatePlaylistDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "create_playlist_dialog"
            ) {
                CreatePlaylistContent(
                    onDismiss = onDismiss,
                    onCreate = onCreate
                )
            }
        }
    }
}

// Enum removed, using com.lostf1sh.pixelplayeross.data.model.PlaylistShapeType

@Composable
fun EditPlaylistDialog(
    visible: Boolean,
    currentName: String,
    currentImageUri: String?,
    currentColor: Int?,
    currentIconName: String?,
    currentShapeType: PlaylistShapeType?,
    currentShapeDetail1: Float?,
    currentShapeDetail2: Float?,
    currentShapeDetail3: Float?,
    currentShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "edit_playlist_dialog"
            ) {
                 EditPlaylistContent(
                    initialName = currentName,
                    initialImageUri = currentImageUri,
                    initialColor = currentColor,
                    initialIconName = currentIconName,
                    initialShapeType = currentShapeType,
                    initialShapeDetail1 = currentShapeDetail1,
                    initialShapeDetail2 = currentShapeDetail2,
                    initialShapeDetail3 = currentShapeDetail3,
                    initialShapeDetail4 = currentShapeDetail4,
                    onDismiss = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CreatePlaylistContent(
    onDismiss: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?) -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Shared State
    var playlistName by remember { mutableStateOf("") }
    
    // Step 1: Info State
    var currentStep by remember { mutableStateOf(0) } // 0: Info, 1: Songs
    var selectedTab by remember { mutableStateOf(0) } // 0: Default, 1: Image, 2: Icon
    var creationMode by remember { mutableStateOf(PlaylistCreationMode.MANUAL) }
    var selectedSmartRule by remember { mutableStateOf(SmartPlaylistRule.TOP_PLAYED) }
    
    // Songs State
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }
    
    // Image State
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCropUi by remember { mutableStateOf(false) }
    // Lifted Bitmap State for Preview Consistency
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }

    // Crop State
    var cropScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Custom Color/Icon State
    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf<Int?>(defaultColor) }
    var selectedIconName by remember { mutableStateOf<String?>("MusicNote") }

    // Shape State
    var selectedShapeType by remember { mutableStateOf(PlaylistShapeType.Circle) }
    
    // SmoothRect Params
    var smoothRectCornerRadius by remember { androidx.compose.runtime.mutableFloatStateOf(20f) } // 0-50
    var smoothRectSmoothness by remember { androidx.compose.runtime.mutableFloatStateOf(60f) } // 0-100
    
    var starCurve by remember { androidx.compose.runtime.mutableDoubleStateOf(0.15) } // 0.0 - 0.5
    var starRotation by remember { androidx.compose.runtime.mutableFloatStateOf(0f) } // 0 - 360
    var starScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) } // 0.5 - 1.5
    var starSides by remember { androidx.compose.runtime.mutableIntStateOf(5) } // 3 - 20

    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = context.imageLoader
             val request = ImageRequest.Builder(context)
                 .data(selectedImageUri)
                 .allowHardware(false)
                 .build()
             
             val result = loader.execute(request)
             val drawable = result.drawable
             if (drawable is android.graphics.drawable.BitmapDrawable) {
                 imageBitmap = drawable.bitmap.asImageBitmap()
             }
         } else {
             imageBitmap = null
             cropScale = 1f
             cropOffset = androidx.compose.ui.geometry.Offset.Zero
         }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            cropScale = 1f
            cropOffset = androidx.compose.ui.geometry.Offset.Zero
            showCropUi = true
        }
    }
    
    // Back Handler for navigation flow
    BackHandler(enabled = showCropUi || (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
        when {
            showCropUi -> showCropUi = false
            currentStep == 1 -> currentStep = 0
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedContent(targetState = if (showCropUi) 2 else currentStep, label = "Title Animation") { displayStep ->
                        Text(
                            when (displayStep) {
                                2 -> stringResource(R.string.presentation_batch_f_edit_cover_title)
                                0 -> {
                                    if (creationMode == PlaylistCreationMode.SMART) {
                                        stringResource(R.string.presentation_batch_f_new_smart_playlist)
                                    } else {
                                        stringResource(R.string.presentation_batch_f_new_playlist)
                                    }
                                }
                                else -> stringResource(R.string.presentation_batch_f_add_songs)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 24.sp,
                                textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                            ),
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            when {
                                showCropUi -> showCropUi = false
                                currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL -> currentStep = 0
                                else -> onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            if (showCropUi || (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            } else {
                                Icons.Rounded.Close
                            },
                            contentDescription = stringResource(R.string.presentation_batch_f_cd_back_or_cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            if (!showCropUi && !(currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
                MediumExtendedFloatingActionButton(
                    text = {
                        Text(
                            if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) {
                                stringResource(R.string.presentation_batch_f_next)
                            } else {
                                stringResource(R.string.presentation_batch_f_create)
                            }
                        )
                    },
                    icon = { 
                        Icon(
                            if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) {
                                Icons.AutoMirrored.Rounded.ArrowForward
                            } else {
                                Icons.Rounded.Check
                            }, 
                            contentDescription = null
                        ) 
                    },
                    onClick = {
                        if (currentStep == 0) {
                            if (playlistName.isNotBlank()) {
                                if (creationMode == PlaylistCreationMode.MANUAL) {
                                    currentStep = 1
                                } else {
                                    val imageUriString = if(selectedTab == 1) selectedImageUri?.toString() else null
                                    val color = if(selectedTab == 2) selectedColor else null
                                    val icon = if(selectedTab == 2) selectedIconName else null

                                    val scale = if(selectedTab == 1) cropScale else 1f
                                    val panX = if(selectedTab == 1) cropOffset.x else 0f
                                    val panY = if(selectedTab == 1) cropOffset.y else 0f

                                    val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                                    val (d1, d2, d3, d4) = if (selectedTab == 2) {
                                        when (selectedShapeType) {
                                            PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                            PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                            else -> Quadruple(0f, 0f, 0f, 0f)
                                        }
                                    } else Quadruple(null, null, null, null)

                                    onCreate(
                                        playlistName,
                                        imageUriString,
                                        color,
                                        icon,
                                        emptyList(),
                                        scale,
                                        panX,
                                        panY,
                                        shapeTypeForSave,
                                        d1, d2, d3, d4,
                                        selectedSmartRule.storageKey
                                    )
                                }
                            }
                        } else {
                            val imageUriString = if(selectedTab == 1) selectedImageUri?.toString() else null
                            val color = if(selectedTab == 2) selectedColor else null
                            val icon = if(selectedTab == 2) selectedIconName else null
                            
                            val scale = if(selectedTab == 1) cropScale else 1f
                            val panX = if(selectedTab == 1) cropOffset.x else 0f
                            val panY = if(selectedTab == 1) cropOffset.y else 0f
                            
                            val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                            val (d1, d2, d3, d4) = if (selectedTab == 2) {
                                when (selectedShapeType) {
                                    PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                    PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                    else -> Quadruple(0f, 0f, 0f, 0f)
                                }
                            } else Quadruple(null, null, null, null)

                            onCreate(
                                playlistName, 
                                imageUriString, 
                                color, 
                                icon, 
                                selectedSongIds.filterValues { it }.keys.toList(),
                                scale,
                                panX,
                                panY,
                                shapeTypeForSave,
                                d1, d2, d3, d4,
                                null
                            )
                        }
                    },
                    expanded = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 8.dp, end = 8.dp)
                        .height(56.dp), // Standard height, feels substantial
                    containerColor = if (currentStep == 0 && playlistName.isBlank()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (currentStep == 0 && playlistName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
        bottomBar = {
            if (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val storageFilter by playerViewModel.playlistPickerStorageFilter.collectAsStateWithLifecycle()
                    val tabs = listOf(
                        StorageFilter.OFFLINE to R.string.library_storage_filter_offline,
                        StorageFilter.ONLINE to R.string.library_storage_filter_online
                    )
                    val selectedTabIndex = tabs.indexOfFirst { it.first == storageFilter }.coerceAtLeast(0)

                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(5.dp),
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {}
                    ) {
                        tabs.forEachIndexed { index, (filter, labelRes) ->
                            TabAnimation(
                                index = index,
                                title = stringResource(labelRes),
                                selectedIndex = selectedTabIndex,
                                onClick = { playerViewModel.setPlaylistPickerStorageFilter(filter) },
                                transformOrigin = if (index == 0) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (filter == StorageFilter.OFFLINE) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_phonef),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Cloud,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(labelRes),
                                        fontFamily = RoundedSans,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    FilledIconButton(
                        onClick = {
                            val imageUriString = if(selectedTab == 1) selectedImageUri?.toString() else null
                            val color = if(selectedTab == 2) selectedColor else null
                            val icon = if(selectedTab == 2) selectedIconName else null
                            
                            val scale = if(selectedTab == 1) cropScale else 1f
                            val panX = if(selectedTab == 1) cropOffset.x else 0f
                            val panY = if(selectedTab == 1) cropOffset.y else 0f
                            
                            val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                            val (d1, d2, d3, d4) = if (selectedTab == 2) {
                                when (selectedShapeType) {
                                    PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                    PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                    else -> Quadruple(0f, 0f, 0f, 0f)
                                }
                            } else Quadruple(null, null, null, null)

                            onCreate(
                                playlistName, 
                                imageUriString, 
                                color, 
                                icon, 
                                selectedSongIds.filterValues { it }.keys.toList(),
                                scale,
                                panX,
                                panY,
                                shapeTypeForSave,
                                d1, d2, d3, d4,
                                null
                            )
                        },
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.presentation_batch_f_create),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
            label = "Step Transition"
        ) { step ->
            if (step == 0) {
                 PlaylistFormContent(
                     playlistName = playlistName,
                     onNameChange = { playlistName = it },
                     selectedTab = selectedTab,
                     onTabChange = { selectedTab = it },
                     selectedImageUri = selectedImageUri,
                     showCropUi = showCropUi,
                     onShowCropUiChange = { showCropUi = it },
                     cropScale = cropScale,
                     onCropScaleChange = { cropScale = it },
                     cropOffset = cropOffset,
                     onCropOffsetChange = { cropOffset = it },
                     imageBitmap = imageBitmap,
                     imagePickerLauncher = imagePickerLauncher,
                     selectedColor = selectedColor,
                     onColorChange = { selectedColor = it },
                     selectedIconName = selectedIconName,
                     onIconChange = { selectedIconName = it },
                     selectedShapeType = selectedShapeType,
                     onShapeTypeChange = { selectedShapeType = it },
                     smoothRectCornerRadius = smoothRectCornerRadius,
                     onSmoothRectCornerRadiusChange = { smoothRectCornerRadius = it },
                     smoothRectSmoothness = smoothRectSmoothness,
                     onSmoothRectSmoothnessChange = { smoothRectSmoothness = it },
                     starSides = starSides,
                     onStarSidesChange = { starSides = it },
                     starCurve = starCurve,
                     onStarCurveChange = { starCurve = it },
                     starRotation = starRotation,
                     onStarRotationChange = { starRotation = it },
                     starScale = starScale,
                     onStarScaleChange = { starScale = it },
                     creationMode = creationMode,
                     onCreationModeChange = { creationMode = it },
                     selectedSmartRule = selectedSmartRule,
                     onSmartRuleChange = { selectedSmartRule = it },
                     onImageUriChange = { selectedImageUri = it }
                 )
            } else {
                SongPickerSelectionPane(
                    selectedSongIds = selectedSongIds,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                    playerViewModel = playerViewModel
                )
            }
        }
    }
}

@Composable
fun EditPlaylistContent(
    initialName: String,
    initialImageUri: String?,
    initialColor: Int?,
    initialIconName: String?,
    initialShapeType: PlaylistShapeType?,
    initialShapeDetail1: Float?,
    initialShapeDetail2: Float?,
    initialShapeDetail3: Float?,
    initialShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
    val context = LocalContext.current
    
    // Initial State Setup
    var playlistName by remember { mutableStateOf(initialName) }
    
    // Determine initial tab
    // 0=Default, 1=Image, 2=Icon
    // Logic: If imageUri present -> Image. If Color/Icon present -> Icon. Else Default.
    // NOTE: existing playlist usually has one of these.
    var selectedTab by remember { 
        mutableStateOf(
            when {
                // If it's a file path or content URI
                initialImageUri != null -> 1 
                // If it has specific color/icon (and not just defaults potentially, though defaults are allowed)
                // We check if image is null. If image is null, do we have custom icon?
                initialColor != null || initialIconName != null -> 2
                else -> 0
            }
        )
    }

    var selectedImageUri by remember { mutableStateOf<Uri?>(initialImageUri?.let { Uri.parse(it) }) }
    var showCropUi by remember { mutableStateOf(false) }
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }
    
    // Crop: We don't store crop params in DB currently for playlist updates properly unless we re-save image.
    // But assuming we start with scale 1f if editing.
    var cropScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf(initialColor ?: defaultColor) }
    var selectedIconName by remember { mutableStateOf(initialIconName ?: "MusicNote") }

    var selectedShapeType by remember { mutableStateOf(initialShapeType ?: PlaylistShapeType.Circle) }
    
    // Shape Params
    var smoothRectCornerRadius by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail1 ?: 20f) }
    var smoothRectSmoothness by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail2 ?: 60f) }
    
    var starCurve by remember { androidx.compose.runtime.mutableDoubleStateOf(initialShapeDetail1?.toDouble() ?: 0.15) }
    var starRotation by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail2 ?: 0f) }
    var starScale by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail3 ?: 1f) }
    var starSides by remember { androidx.compose.runtime.mutableIntStateOf(initialShapeDetail4?.toInt() ?: 5) }

    // Constants needed for Form
    val searchQuery = "" // Not used in Edit

    // Image Loader
    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = context.imageLoader
             val request = ImageRequest.Builder(context)
                 .data(selectedImageUri)
                 .allowHardware(false)
                 .build()
             val result = loader.execute(request)
             val drawable = result.drawable
             if (drawable is android.graphics.drawable.BitmapDrawable) {
                 imageBitmap = drawable.bitmap.asImageBitmap()
             }
         } else {
             imageBitmap = null
             cropScale = 1f
             cropOffset = androidx.compose.ui.geometry.Offset.Zero
         }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            cropScale = 1f
            cropOffset = androidx.compose.ui.geometry.Offset.Zero
            showCropUi = true
            selectedTab = 1 // Force switch to image tab
        }
    }

    BackHandler(enabled = showCropUi) {
        showCropUi = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.presentation_batch_f_edit_playlist),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 24.sp,
                            textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                        ),
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            if (showCropUi) showCropUi = false else onDismiss()
                        }
                    ) {
                        Icon(
                            if (showCropUi) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                            contentDescription = stringResource(if (showCropUi) R.string.presentation_batch_f_cd_back_or_cancel else R.string.presentation_batch_f_cd_close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            if (!showCropUi) {
                MediumExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.presentation_batch_f_save)) },
                    icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        val imageUriString = if(selectedTab == 1) selectedImageUri?.toString() else null
                        val color = if(selectedTab == 2) selectedColor else null
                        val icon = if(selectedTab == 2) selectedIconName else null
                        
                        val scale = if(selectedTab == 1) cropScale else 1f
                        val panX = if(selectedTab == 1) cropOffset.x else 0f
                        val panY = if(selectedTab == 1) cropOffset.y else 0f
                        
                        val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                        val (d1, d2, d3, d4) = if (selectedTab == 2) {
                            when (selectedShapeType) {
                                PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                else -> Quadruple(0f, 0f, 0f, 0f)
                            }
                        } else Quadruple(null, null, null, null)

                        onSave(
                            playlistName,
                            imageUriString,
                            color,
                            icon,
                            scale,
                            panX,
                            panY,
                            shapeTypeForSave,
                            d1, d2, d3, d4
                        )
                    },
                    expanded = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 8.dp, end = 8.dp)
                        .height(56.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
         PlaylistFormContent(
             modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
             playlistName = playlistName,
             onNameChange = { playlistName = it },
             selectedTab = selectedTab,
             onTabChange = { selectedTab = it },
             selectedImageUri = selectedImageUri,
             showCropUi = showCropUi,
             onShowCropUiChange = { showCropUi = it },
             cropScale = cropScale,
             onCropScaleChange = { cropScale = it },
             cropOffset = cropOffset,
             onCropOffsetChange = { cropOffset = it },
             imageBitmap = imageBitmap,
             imagePickerLauncher = imagePickerLauncher,
             selectedColor = selectedColor,
             onColorChange = { selectedColor = it },
             selectedIconName = selectedIconName,
             onIconChange = { selectedIconName = it },
             selectedShapeType = selectedShapeType,
             onShapeTypeChange = { selectedShapeType = it },
             smoothRectCornerRadius = smoothRectCornerRadius,
             onSmoothRectCornerRadiusChange = { smoothRectCornerRadius = it },
             smoothRectSmoothness = smoothRectSmoothness,
             onSmoothRectSmoothnessChange = { smoothRectSmoothness = it },
             starSides = starSides,
             onStarSidesChange = { starSides = it },
             starCurve = starCurve,
             onStarCurveChange = { starCurve = it },
             starRotation = starRotation,
             onStarRotationChange = { starRotation = it },
             starScale = starScale,
             onStarScaleChange = { starScale = it },
             showCreationModeSelector = false,
             creationMode = PlaylistCreationMode.MANUAL,
             onCreationModeChange = { },
             selectedSmartRule = SmartPlaylistRule.TOP_PLAYED,
             onSmartRuleChange = { },
             onImageUriChange = { selectedImageUri = it }
         )
    }
}


@Composable
private fun PlaylistFormContent(
    modifier: Modifier = Modifier,
    playlistName: String,
    onNameChange: (String) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    selectedImageUri: Uri?,
    showCropUi: Boolean,
    onShowCropUiChange: (Boolean) -> Unit,
    cropScale: Float,
    onCropScaleChange: (Float) -> Unit,
    cropOffset: androidx.compose.ui.geometry.Offset,
    onCropOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit,
    imageBitmap: ImageBitmap?,
    imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    selectedColor: Int?,
    onColorChange: (Int) -> Unit,
    selectedIconName: String?,
    onIconChange: (String) -> Unit,
    selectedShapeType: PlaylistShapeType,
    onShapeTypeChange: (PlaylistShapeType) -> Unit,
    smoothRectCornerRadius: Float,
    onSmoothRectCornerRadiusChange: (Float) -> Unit,
    smoothRectSmoothness: Float,
    onSmoothRectSmoothnessChange: (Float) -> Unit,
    starSides: Int,
    onStarSidesChange: (Int) -> Unit,
    starCurve: Double,
    onStarCurveChange: (Double) -> Unit,
    starRotation: Float,
    onStarRotationChange: (Float) -> Unit,
    starScale: Float,
    onStarScaleChange: (Float) -> Unit,
    showCreationModeSelector: Boolean = true,
    creationMode: PlaylistCreationMode,
    onCreationModeChange: (PlaylistCreationMode) -> Unit,
    selectedSmartRule: SmartPlaylistRule,
    onSmartRuleChange: (SmartPlaylistRule) -> Unit,
    onImageUriChange: (Uri?) -> Unit
) {
    if (showCropUi) {
         // Fullscreen Crop UI overrides normal content
         Box(
             modifier = modifier
                 .fillMaxSize()
                 .padding(16.dp)
                 .clip(RoundedCornerShape(24.dp))
                 .background(MaterialTheme.colorScheme.surface)
         ) {
             if (imageBitmap != null) {
                 Column(
                     modifier = Modifier.fillMaxSize(),
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     Spacer(Modifier.height(32.dp))
                     Text(
                         text = stringResource(R.string.presentation_batch_f_adjust_cover_title),
                         style = MaterialTheme.typography.headlineSmall,
                         textAlign = TextAlign.Center,
                         modifier = Modifier.padding(horizontal = 24.dp)
                     )
                     Spacer(Modifier.height(8.dp))
                     Text(
                         text = stringResource(R.string.presentation_batch_f_adjust_cover_hint),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         textAlign = TextAlign.Center,
                         modifier = Modifier.padding(horizontal = 32.dp)
                     )
                     Spacer(Modifier.weight(1f))
                     Box(
                         modifier = Modifier
                             .fillMaxWidth()
                             .aspectRatio(1f)
                             .padding(16.dp)
                             .clip(RoundedCornerShape(32.dp))
                     ) {
                         ImageCropView(
                             imageBitmap = imageBitmap,
                             modifier = Modifier.fillMaxSize(),
                             scale = cropScale,
                             pan = cropOffset,
                             enabled = true,
                             onCrop = { scale, pan -> 
                                 onCropScaleChange(scale)
                                 onCropOffsetChange(pan)
                             }
                         )
                     }
                     Spacer(Modifier.weight(1f))
                     Button(
                        onClick = { onShowCropUiChange(false) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(24.dp)
                            .height(56.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.presentation_batch_f_done))
                    }
                 }
             } else {
                 CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
             }
         }
         return
    }

    Column(modifier = modifier
        .fillMaxSize()
        .imePadding()) {
        
         // PREVIEW SECTION
        AnimatedContent(
             targetState = selectedTab,
             transitionSpec = {
                 fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
             },
             label = "preview_transition",
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(top = 8.dp, bottom = 4.dp)
        ) { targetTab ->
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                when (targetTab) {
                    0 -> { // Default
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                 Icon(
                                    Icons.Rounded.GridView,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.presentation_batch_f_auto_generated_collage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    1 -> { // Image
                         // Image Preview
                         if (imageBitmap != null) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                 Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                 ) {
                                     ImageCropView(
                                         imageBitmap = imageBitmap,
                                         modifier = Modifier.fillMaxSize(),
                                         scale = cropScale,
                                         pan = cropOffset,
                                         enabled = false,
                                         onCrop = { _, _ -> }
                                     )
                                 }
                                 Spacer(Modifier.height(12.dp))
                                 Row(
                                     horizontalArrangement = Arrangement.spacedBy(8.dp),
                                     modifier = Modifier.padding(horizontal = 16.dp)
                                 ) {
                                     FilledTonalButton(
                                         onClick = { imagePickerLauncher.launch("image/*") },
                                         contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                         modifier = Modifier.height(40.dp).weight(1f)
                                     ) {
                                         Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                         Spacer(Modifier.width(8.dp))
                                         Text(stringResource(R.string.presentation_batch_f_change), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                     }
                                     Button(
                                         onClick = { onImageUriChange(null) },
                                         contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                         modifier = Modifier.height(40.dp).weight(1f),
                                         colors = ButtonDefaults.buttonColors(
                                             containerColor = MaterialTheme.colorScheme.error,
                                             contentColor = MaterialTheme.colorScheme.onError
                                         )
                                     ) {
                                         Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                         Spacer(Modifier.width(8.dp))
                                         Text(stringResource(R.string.presentation_batch_f_remove), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                     }
                                 }
                             }
                         } else {
                             Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { imagePickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                             ) {
                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                     Icon(
                                         Icons.Rounded.AddPhotoAlternate,
                                         contentDescription = stringResource(R.string.presentation_batch_f_cd_add_photo),
                                         modifier = Modifier.size(56.dp),
                                         tint = MaterialTheme.colorScheme.primary
                                     )
                                     Spacer(Modifier.height(12.dp))
                                     Text(stringResource(R.string.presentation_batch_f_pick_image), style = MaterialTheme.typography.titleSmall)
                                 }
                             }
                         }
                    }
                    2 -> { // Icon / Custom Shape
                         AnimatedContent(
                             targetState = selectedShapeType,
                             transitionSpec = {
                                 fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f) togetherWith 
                                 fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f)
                             },
                             label = "shape_transition"
                         ) { currentShapeType ->
                             val currentShape: Shape = when(currentShapeType) {
                                 PlaylistShapeType.Circle -> CircleShape
                                 PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(
                                     cornerRadiusTL = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentTR = smoothRectSmoothness.toInt(),
                                     cornerRadiusTR = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentTL = smoothRectSmoothness.toInt(),
                                     cornerRadiusBR = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentBR = smoothRectSmoothness.toInt(),
                                     cornerRadiusBL = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentBL = smoothRectSmoothness.toInt(),
                                 )
                                 PlaylistShapeType.RotatedPill -> {
                                     androidx.compose.foundation.shape.GenericShape { size, _ ->
                                         val w = size.width
                                         val h = size.height
                                         val pillW = w * 0.75f
                                         val offset = (w - pillW) / 2
                                         addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                                     }
                                 }
                                 PlaylistShapeType.Star -> RoundedStarShape(
                                     sides = starSides,
                                     curve = starCurve,
                                     rotation = starRotation
                                 )
                             }
                             
                             val shapeMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                             val iconMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = -45f) else Modifier
                             val scaleMod = if(currentShapeType == PlaylistShapeType.Star) Modifier.graphicsLayer(scaleX = starScale, scaleY = starScale) else Modifier

                             Box(
                                 modifier = Modifier
                                     .size(180.dp)
                                     .then(scaleMod)
                                     .then(shapeMod)
                                     .clip(currentShape)
                                     .background(selectedColor?.let { Color(it) }
                                         ?: MaterialTheme.colorScheme.primaryContainer),
                                 contentAlignment = Alignment.Center
                             ) {
                                 if (selectedIconName != null) {
                                     val icon = getIconByName(selectedIconName) ?: Icons.Rounded.MusicNote
                                     Icon(
                                         imageVector = icon,
                                         contentDescription = null,
                                         modifier = Modifier
                                             .size(80.dp)
                                             .then(iconMod),
                                         tint = selectedColor?.let { getThemeContentColor(it, MaterialTheme.colorScheme) } 
                                               ?: MaterialTheme.colorScheme.onPrimaryContainer
                                     )
                                 }
                             }
                         }
                    }
                }
            }
        }

        // CONTROL SECTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            
            OutlinedTextField(
                value = playlistName,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.presentation_batch_f_playlist_name_label)) },
                placeholder = { Text(stringResource(R.string.presentation_batch_f_playlist_name_placeholder)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (showCreationModeSelector) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                ) {
                    SegmentedButton(
                        selected = creationMode == PlaylistCreationMode.MANUAL,
                        onClick = { onCreationModeChange(PlaylistCreationMode.MANUAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.presentation_batch_f_creation_mode_manual))
                    }
                    SegmentedButton(
                        selected = creationMode == PlaylistCreationMode.SMART,
                        onClick = { onCreationModeChange(PlaylistCreationMode.SMART) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.presentation_batch_f_creation_mode_smart))
                    }
                }
            }

            AnimatedVisibility(visible = creationMode == PlaylistCreationMode.SMART) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_f_smart_rule),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmartPlaylistRule.entries.forEach { rule ->
                            FilterChip(
                                selected = selectedSmartRule == rule,
                                onClick = { onSmartRuleChange(rule) },
                                label = { Text(smartPlaylistRuleTitle(rule)) },
                                shape = CircleShape
                            )
                        }
                    }

                    Text(
                        text = smartPlaylistRuleSubtitle(selectedSmartRule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val tabs = listOf(
                stringResource(R.string.presentation_batch_f_tab_default),
                stringResource(R.string.presentation_batch_f_tab_image),
                stringResource(R.string.presentation_batch_f_tab_icon)
            )
            ExpressiveButtonGroup(
                items = tabs,
                selectedIndex = selectedTab,
                onItemClick = onTabChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            )
        
            AnimatedVisibility(visible = selectedTab == 2) {
                 Column(
                     modifier = Modifier.padding(top = 14.dp),
                     verticalArrangement = Arrangement.spacedBy(16.dp)
                 ) {
                     // Colors
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.presentation_batch_f_background_color),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     
                     val colors = listOf( 
                         MaterialTheme.colorScheme.primary.toArgb(),
                         MaterialTheme.colorScheme.primaryContainer.toArgb(),
                         MaterialTheme.colorScheme.secondary.toArgb(),
                         MaterialTheme.colorScheme.secondaryContainer.toArgb(),
                         MaterialTheme.colorScheme.tertiary.toArgb(),
                         MaterialTheme.colorScheme.tertiaryContainer.toArgb(),
                         MaterialTheme.colorScheme.error.toArgb(),
                         MaterialTheme.colorScheme.errorContainer.toArgb(),
                         MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
                         MaterialTheme.colorScheme.inverseSurface.toArgb()
                     )
                     
                     FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                     ) {
                         colors.forEach { color ->
                             val isSelected = selectedColor == color
                             val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")
                             
                             Box(
                                 modifier = Modifier
                                     .size(52.dp)
                                    .clip(RoundedCornerShape(cornerRadius))
                                     .background(if (isSelected) Color(color) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color(color) else Color.Transparent,
                                        shape = RoundedCornerShape(cornerRadius)
                                    ),
                                 contentAlignment = Alignment.Center
                             ) {
                                  Box(
                                     modifier = Modifier
                                         .size(if (isSelected) 42.dp else 48.dp)
                                        .clip(RoundedCornerShape(if (isSelected) 8.dp else cornerRadius))
                                        .background(Color(color))
                                         .clickable { onColorChange(color) }
                                  )
                             }
                         }
                     }
                     
                     Spacer(Modifier.height(8.dp))

                     // Icons
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.presentation_batch_f_icon_symbol),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )

                     val icons = listOf(
                        "MusicNote", "Headphones", "Album", "Mic", "Speaker", "Favorite", "Piano", "Queue"
                     )
                     
                     FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                     ) {
                         icons.forEach { iconName ->
                             val isSelected = selectedIconName == iconName
                             val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                             Box(
                                 modifier = Modifier
                                     .size(52.dp)
                                     .clip(RoundedCornerShape(cornerRadius))
                                     .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                     .clickable { onIconChange(iconName) },
                                 contentAlignment = Alignment.Center
                             ) {
                                 Icon(
                                     imageVector = getIconByName(iconName) ?: Icons.Rounded.MusicNote,
                                     contentDescription = null,
                                     tint = if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                 )
                             }
                         }
                     }

                     Spacer(Modifier.height(16.dp))

                     // Shapes
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.presentation_batch_f_shape_style),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )

                     LazyRow(
                         modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                         horizontalArrangement = Arrangement.spacedBy(2.dp),
                         contentPadding = PaddingValues(horizontal = 16.dp)
                     ) {
                         item {
                             PlaylistShapeType.entries.forEach { shapeType ->
                                 val isSelected = selectedShapeType == shapeType
                                 val previewShape = when(shapeType) {
                                     PlaylistShapeType.Circle -> CircleShape
                                     PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(12.dp, 60, 12.dp, 60, 12.dp, 60, 12.dp, 60)
                                     PlaylistShapeType.RotatedPill -> androidx.compose.foundation.shape.GenericShape { size, _ ->
                                         val w = size.width
                                         val h = size.height
                                         val pillW = w * 0.75f
                                         val offset = (w - pillW) / 2
                                         addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                                     }
                                     PlaylistShapeType.Star -> RoundedStarShape(5, 0.15, 0f)
                                 }

                                 val rotationM = if(shapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                                 val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                                 Row(modifier = Modifier.padding(2.dp)) {
                                     Spacer(Modifier.width(2.dp))
                                     Column(
                                         horizontalAlignment = Alignment.CenterHorizontally,
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(cornerRadius))
                                             .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                             .clickable { onShapeTypeChange(shapeType) }
                                             .padding(12.dp)
                                     ) {
                                         Box(
                                             modifier = Modifier
                                                 .size(50.dp)
                                                 .then(rotationM)
                                                 .clip(previewShape)
                                                 .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                                             contentAlignment = Alignment.Center
                                         ) {}
                                     }
                                     Spacer(Modifier.width(2.dp))
                                 }
                             }
                         }
                     }
                     
                     // Params
                     AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.SmoothRect) {
                         Column(
                             modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                             verticalArrangement = Arrangement.spacedBy(16.dp)
                         ) {
                             Text(stringResource(R.string.presentation_batch_f_shape_parameters), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_corner_radius), smoothRectCornerRadius, 0f..50f, onSmoothRectCornerRadiusChange, { it.toInt().toString() })
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_smoothness), smoothRectSmoothness, 0f..100f, onSmoothRectSmoothnessChange, { "${it.toInt()}%" })
                         }
                     }
                     
                     AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.Star) {
                         Column(
                             modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                             verticalArrangement = Arrangement.spacedBy(16.dp)
                         ) {
                             Text(stringResource(R.string.presentation_batch_f_shape_parameters), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_sides), starSides.toFloat(), 3f..20f, { onStarSidesChange(it.toInt()) }, { it.toInt().toString() }, steps = 17)
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_curve), starCurve.toFloat(), 0f..0.5f, { onStarCurveChange(it.toDouble()) }, { String.format(Locale.US, "%.2f", it) })
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_rotation), starRotation, 0f..360f, onStarRotationChange, { "${it.toInt()}°" })
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_scale), starScale, 0.5f..1.5f, onStarScaleChange, { String.format(Locale.US, "%.1fx", it) })
                         }
                     }
                 }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}




fun getIconByName(name: String?): ImageVector? {
    return when (name) {
        "MusicNote" -> Icons.Rounded.MusicNote
        "Headphones" -> Icons.Rounded.Headphones
        "Album" -> Icons.Rounded.Album
        "Mic" -> Icons.Rounded.MicExternalOn 
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> null
    }
}

@Composable
fun ExpressiveButtonGroup(
    items: List<String>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Transparent, CircleShape), // Optional container background
            //.padding(1.dp), // Check if padding is needed
        horizontalArrangement = Arrangement.spacedBy(4.dp) // Gap between buttons
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val shape = if (isSelected) CircleShape else RoundedCornerShape(10.dp) // Pill vs RoundedRect
            val containerColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "ButtonColor"
            )
            val contentColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                label = "ContentColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp) // Taller button
                    .clip(shape)
                    .background(containerColor)
                    .clickable { onItemClick(index) },
                contentAlignment = Alignment.Center
            ) {
                 Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                     AnimatedVisibility(visible = isSelected) {
                         Icon(
                             Icons.Rounded.Check, 
                             contentDescription = null, 
                             modifier = Modifier.padding(end = 8.dp).size(18.dp),
                             tint = contentColor
                         )
                     }
                     Text(
                         text = title,
                         style = MaterialTheme.typography.labelLarge,
                         color = contentColor,
                         fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Medium
                     )
                 }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapeParameterCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueDisplay: (Float) -> String,
    steps: Int = 0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                     text = title,
                     style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                     text = valueDisplay(value),
                     style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.onSurface,
                     fontWeight = FontWeight.Bold
                )
            }
            
            // Custom Thick Slider
            ThickSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}

@Composable
fun ThickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
             thumbColor = MaterialTheme.colorScheme.primary,
             activeTrackColor = MaterialTheme.colorScheme.primary,
             inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        thumb = {
             Box(
                 modifier = Modifier
                    .size(width = 6.dp, height = 18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
             )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(8.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 4.dp,
                drawStopIndicator = null
            )
        }
    )
}


fun getThemeContentColor(colorArgb: Int, scheme: androidx.compose.material3.ColorScheme): Color {
    return resolvePlaylistCoverContentColor(colorArgb, scheme)
}

// End of file
