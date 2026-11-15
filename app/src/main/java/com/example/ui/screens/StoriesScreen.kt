package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.Story
import com.example.data.model.StoryView
import com.example.data.model.UserProfile
import com.example.data.model.UsernameReservation
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalPrivacyXTokens
import kotlinx.coroutines.delay

@Composable
fun StoriesScreen(
    currentUser: UserProfile,
    activeStories: List<Story>,
    myStories: List<Story>,
    storyViewers: List<StoryView>,
    directoryUsers: List<UsernameReservation>,
    isUploading: Boolean,
    showCreatorExternal: Boolean,
    onDismissExternalCreator: () -> Unit,
    onOpenStory: (Story) -> Unit,
    onReactStory: (Story, String) -> Unit,
    onCreateStory: (
        caption: String,
        mediaUri: Uri?,
        gradient: String,
        privacy: String,
        allowedUids: List<String>,
        hiddenUids: List<String>,
        highlightTitle: String,
        onDone: () -> Unit
    ) -> Unit,
    onArchiveOrHighlight: (storyId: String, archive: Boolean, highlightTitle: String) -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var viewingStories by remember { mutableStateOf<List<Story>?>(null) }
    var viewingIndex by remember { mutableIntStateOf(0) }

    val creatorVisible = showCreateDialog || showCreatorExternal
    val highlights = remember(myStories) {
        myStories.filter { it.highlightTitle.isNotBlank() }.groupBy { it.highlightTitle }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Stories", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "24-hour ephemeral moments, Close Friends & highlights",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                    modifier = Modifier.testTag("create_story_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create Story", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Story")
                }
            }
        }

        // Story Strip Carousel
        item {
            Text("ACTIVE 24H STORIES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (activeStories.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.AutoStories,
                    title = "No active stories",
                    subtitle = "Tap Add Story to post a text or photo story with custom privacy."
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(activeStories, key = { it.id }) { story ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(78.dp)
                                .clickable {
                                    viewingStories = activeStories
                                    viewingIndex = activeStories.indexOf(story).coerceAtLeast(0)
                                    onOpenStory(story)
                                }
                                .testTag("story_ring_${story.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, Brush.linearGradient(tokens.radialFabGradient), CircleShape)
                                    .padding(4.dp)
                            ) {
                                UserAvatar(
                                    name = story.authorName,
                                    photoUrl = story.authorAvatarUrl.ifBlank { story.mediaUrl },
                                    size = 64.dp
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = story.authorName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Story Highlights
        if (highlights.isNotEmpty()) {
            item {
                Text("YOUR HIGHLIGHTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    highlights.forEach { (title, list) ->
                        GlassCard(
                            modifier = Modifier.clickable {
                                viewingStories = list
                                viewingIndex = 0
                                onOpenStory(list.first())
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Star, contentDescription = "Highlight", tint = tokens.accentPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("$title (${list.size})", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        // Story Archive
        if (myStories.isNotEmpty()) {
            item {
                Text("MY STORY ARCHIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(myStories, key = { "my_${it.id}" }) { story ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewingStories = listOf(story)
                        viewingIndex = 0
                        onOpenStory(story)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(storyGradientFor(story.backgroundGradient))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (story.mediaUrl.isNotBlank()) {
                                AsyncImage(
                                    model = story.mediaUrl,
                                    contentDescription = "Story thumb",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Rounded.AutoStories, contentDescription = "Story", tint = Color.White)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = story.caption.ifBlank { "Media Story" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "Privacy: ${story.privacy} • Views: ${story.viewCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            onArchiveOrHighlight(story.id, !story.isArchived, story.highlightTitle.ifBlank { "Memories" })
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Archive,
                                contentDescription = "Toggle Archive / Highlight",
                                tint = if (story.isArchived) tokens.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (creatorVisible) {
        CreateStoryDialog(
            directoryUsers = directoryUsers,
            isUploading = isUploading,
            onDismiss = {
                showCreateDialog = false
                onDismissExternalCreator()
            },
            onCreate = { caption, uri, grad, privacy, allowed, hidden, highlight ->
                onCreateStory(caption, uri, grad, privacy, allowed, hidden, highlight) {
                    showCreateDialog = false
                    onDismissExternalCreator()
                }
            }
        )
    }

    viewingStories?.let { queue ->
        if (queue.isNotEmpty()) {
            val safeIdx = viewingIndex.coerceIn(0, queue.lastIndex)
            val current = queue[safeIdx]
            FullScreenStoryViewerDialog(
                stories = queue,
                currentIndex = safeIdx,
                isOwner = current.authorId == currentUser.userId,
                viewers = storyViewers,
                onNext = {
                    if (safeIdx < queue.lastIndex) {
                        viewingIndex = safeIdx + 1
                        onOpenStory(queue[safeIdx + 1])
                    } else {
                        viewingStories = null
                    }
                },
                onPrev = {
                    if (safeIdx > 0) {
                        viewingIndex = safeIdx - 1
                        onOpenStory(queue[safeIdx - 1])
                    }
                },
                onReact = { emoji -> onReactStory(current, emoji) },
                onDismiss = { viewingStories = null }
            )
        }
    }
}

@Composable
private fun FullScreenStoryViewerDialog(
    stories: List<Story>,
    currentIndex: Int,
    isOwner: Boolean,
    viewers: List<StoryView>,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val story = stories[currentIndex]
    var progress by remember(story.id) { mutableStateOf(0f) }
    var showViewers by remember { mutableStateOf(false) }

    LaunchedEffect(story.id) {
        progress = 0f
        while (progress < 1f) {
            delay(100L)
            progress += 0.02f
        }
        onNext()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(storyGradientFor(story.backgroundGradient)))
        ) {
            if (story.mediaUrl.isNotBlank()) {
                AsyncImage(
                    model = story.mediaUrl,
                    contentDescription = "Story Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Left / Right tap zones
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize().clickable(onClick = onPrev))
                Box(modifier = Modifier.weight(2f).fillMaxSize().clickable(onClick = onNext))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    stories.forEachIndexed { idx, _ ->
                        val p = when {
                            idx < currentIndex -> 1f
                            idx == currentIndex -> progress.coerceIn(0f, 1f)
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { p },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).height(3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    UserAvatar(name = story.authorName, photoUrl = story.authorAvatarUrl, size = 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(story.authorName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("Privacy: ${story.privacy}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Story", tint = Color.White)
                    }
                }
            }

            if (story.caption.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = story.caption,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }

            // Bottom reactions or viewer list
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOwner) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable { showViewers = !showViewers }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Visibility, contentDescription = "Viewers", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${viewers.size} Viewers", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("❤️", "🔥", "😂", "👏").forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.42f),
                                modifier = Modifier.clickable { onReact(emoji) }
                            ) {
                                Text(emoji, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
            }

            if (showViewers) {
                AlertDialog(
                    onDismissRequest = { showViewers = false },
                    title = { Text("Story Viewers (${viewers.size})") },
                    text = {
                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            items(viewers, key = { it.viewerId }) { v ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(v.viewerName, style = MaterialTheme.typography.bodyMedium)
                                    if (v.reaction.isNotBlank()) Text(v.reaction)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showViewers = false }) { Text("Close") } }
                )
            }
        }
    }
}

@Composable
private fun CreateStoryDialog(
    directoryUsers: List<UsernameReservation>,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        caption: String,
        mediaUri: Uri?,
        gradient: String,
        privacy: String,
        allowedUids: List<String>,
        hiddenUids: List<String>,
        highlightTitle: String
    ) -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    var caption by remember { mutableStateOf("") }
    var gradient by remember { mutableStateOf("lavender_violet") }
    var privacy by remember { mutableStateOf("public") }
    var highlightTitle by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    val selectedAllowed = remember { mutableStateListOf<String>() }
    val selectedHidden = remember { mutableStateListOf<String>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        mediaUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create 24h Story") },
        text = {
            LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it.take(400) },
                        label = { Text("Story Text / Caption") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = "Pick Story Media")
                        Spacer(Modifier.width(8.dp))
                        Text(if (mediaUri == null) "Attach Photo/Video" else "Media Selected ✓")
                    }
                }
                item {
                    Text("Background Gradient", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf("lavender_violet", "ocean_iris", "sunset_rose", "midnight_noir").forEach { g ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(storyGradientFor(g)))
                                    .border(if (gradient == g) 2.5.dp else 1.dp, if (gradient == g) Color.Black else Color.White, CircleShape)
                                    .clickable { gradient = g }
                            )
                        }
                    }
                }
                item {
                    Text("Privacy Audience", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("public", "followers", "close_friends", "custom").forEach { p ->
                            val active = privacy == p
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (active) tokens.accentPrimary else tokens.surfaceGlass,
                                border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                                modifier = Modifier.clickable { privacy = p }
                            ) {
                                Text(
                                    text = p.replace('_', ' '),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
                if (privacy == "close_friends" || privacy == "custom") {
                    item { Text("Select Allowed Viewers:", style = MaterialTheme.typography.labelMedium) }
                    items(directoryUsers, key = { "allow_${it.username}" }) { u ->
                        val checked = u.userId in selectedAllowed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (checked) selectedAllowed.remove(u.userId) else selectedAllowed.add(u.userId)
                            }
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                if (it) selectedAllowed.add(u.userId) else selectedAllowed.remove(u.userId)
                            })
                            Text("@${u.username}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (directoryUsers.isNotEmpty()) {
                    item { Text("Hide Story From (optional):", style = MaterialTheme.typography.labelMedium) }
                    items(directoryUsers.take(8), key = { "hide_${it.username}" }) { u ->
                        val checked = u.userId in selectedHidden
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (checked) selectedHidden.remove(u.userId) else selectedHidden.add(u.userId)
                            }
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                if (it) selectedHidden.add(u.userId) else selectedHidden.remove(u.userId)
                            })
                            Text("Hide from @${u.username}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = highlightTitle,
                        onValueChange = { highlightTitle = it.take(40) },
                        label = { Text("Highlight Collection (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(caption, mediaUri, gradient, privacy, selectedAllowed.toList(), selectedHidden.toList(), highlightTitle)
                },
                enabled = !isUploading && (caption.isNotBlank() || mediaUri != null),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
            ) {
                if (isUploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Post Story")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun storyGradientFor(key: String): List<Color> = when (key) {
    "ocean_iris" -> listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))
    "sunset_rose" -> listOf(Color(0xFFEC4899), Color(0xFFF59E0B))
    "midnight_noir" -> listOf(Color(0xFF1E1B4B), Color(0xFF09090B))
    else -> listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
}
