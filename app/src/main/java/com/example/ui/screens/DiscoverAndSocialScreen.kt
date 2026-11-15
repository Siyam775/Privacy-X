package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.BlockEdge
import com.example.data.model.Conversation
import com.example.data.model.FollowEdge
import com.example.data.model.PostComment
import com.example.data.model.SocialPost
import com.example.data.model.UserProfile
import com.example.data.model.UsernameReservation
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPillSearchBar
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalPrivacyXTokens
import kotlinx.coroutines.launch

@Composable
fun DiscoverAndSocialScreen(
    currentUser: UserProfile,
    directoryUsers: List<UsernameReservation>,
    publicConversations: List<Conversation>,
    posts: List<SocialPost>,
    postComments: List<PostComment>,
    following: List<FollowEdge>,
    blockedUsers: List<BlockEdge>,
    isUploadingPost: Boolean,
    onSearchUsers: (String) -> Unit,
    onLoadUserProfile: suspend (String) -> UserProfile?,
    onStartDirectChat: (UsernameReservation) -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit,
    onJoinOrOpenPublicSpace: (Conversation) -> Unit,
    onToggleFollow: (String) -> Unit,
    onToggleBlock: (String) -> Unit,
    onSubmitReport: (targetType: String, targetId: String, reason: String, details: String) -> Unit,
    onCreatePost: (caption: String, mediaUri: Uri?, collectionTag: String, onDone: () -> Unit) -> Unit,
    onTogglePostLike: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onAddComment: (postId: String, text: String) -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    val scope = rememberCoroutineScope()
    var activeSegment by remember { mutableStateOf("feed") } // feed, people, spaces
    var searchQuery by remember { mutableStateOf("") }
    var selectedCollection by remember { mutableStateOf("All") }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<SocialPost?>(null) }
    var inspectedUserPopup by remember { mutableStateOf<Pair<UsernameReservation, UserProfile?>?>(null) }

    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.blockedId }.toSet() }
    val followingIds = remember(following) { following.map { it.followedId }.toSet() }

    val collections = remember(posts) {
        listOf("All") + posts.map { it.collectionTag.ifBlank { "General" } }.distinct()
    }
    val visiblePosts = remember(posts, selectedCollection, blockedIds) {
        posts.filter { p ->
            p.authorId !in blockedIds &&
                (selectedCollection == "All" || p.collectionTag.equals(selectedCollection, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Discover", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Social feed, people directory & public channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreatePostDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                    modifier = Modifier.testTag("create_post_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New Post", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Post")
                }
            }
        }

        item {
            GlassPillSearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    onSearchUsers(it)
                },
                placeholder = "Search @username, posts, or communities...",
                testTag = "discover_search_input"
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("feed" to "Social Feed", "people" to "People", "spaces" to "Public Spaces").forEach { (id, label) ->
                    val selected = activeSegment == id
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) tokens.accentPrimary else tokens.surfaceGlassElevated,
                        border = BorderStroke(1.dp, if (selected) Color.Transparent else tokens.surfaceBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { activeSegment = id }
                            .testTag("discover_tab_$id")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        if (activeSegment == "feed") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    collections.forEach { tag ->
                        val active = tag == selectedCollection
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (active) tokens.accentPrimary.copy(alpha = 0.16f) else tokens.surfaceGlass,
                            border = BorderStroke(0.8.dp, if (active) tokens.accentPrimary else tokens.surfaceBorder),
                            modifier = Modifier.clickable { selectedCollection = tag }
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) tokens.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (visiblePosts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Explore,
                        title = "Feed is quiet",
                        subtitle = "Share a thoughtful update or photo to start the community feed."
                    )
                }
            } else {
                items(visiblePosts, key = { it.id }) { post ->
                    val likes = post.reactions["❤️"].orEmpty()
                    val liked = currentUser.userId in likes
                    GlassCard(modifier = Modifier.fillMaxWidth().testTag("post_card_${post.id}")) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = post.authorName, photoUrl = post.authorAvatarUrl, size = 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(post.authorName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "#${post.collectionTag}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.accentPrimary
                                    )
                                }
                                if (post.authorId != currentUser.userId) {
                                    TextButton(onClick = { onToggleFollow(post.authorId) }) {
                                        Text(if (post.authorId in followingIds) "Following" else "Follow")
                                    }
                                }
                            }
                            if (post.caption.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(post.caption, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (post.mediaUrl.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                AsyncImage(
                                    model = post.mediaUrl,
                                    contentDescription = "Post image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { onTogglePostLike(post.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Like Post",
                                        tint = if (liked) tokens.accentPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("${likes.size}", style = MaterialTheme.typography.labelMedium)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable {
                                            onOpenComments(post.id)
                                            activeCommentsPost = post
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Comment,
                                        contentDescription = "Comments",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("${post.commentCount}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeSegment == "people") {
            val filteredPeople = directoryUsers.filter {
                searchQuery.isBlank() ||
                    it.username.contains(searchQuery.removePrefix("@"), ignoreCase = true) ||
                    it.displayName.contains(searchQuery, ignoreCase = true)
            }
            if (filteredPeople.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.PersonAdd,
                        title = "No matching users",
                        subtitle = "Search any registered @username to connect, follow, or message."
                    )
                }
            } else {
                items(filteredPeople, key = { it.username }) { person ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                val full = onLoadUserProfile(person.userId)
                                inspectedUserPopup = person to full
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = person.displayName, photoUrl = person.photoUrl, size = 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${person.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onToggleFollow(person.userId) }) {
                                Icon(
                                    imageVector = if (person.userId in followingIds) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
                                    contentDescription = "Follow Toggle",
                                    tint = tokens.accentPrimary
                                )
                            }
                            IconButton(onClick = { onStartDirectChat(person) }) {
                                Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "Message", tint = tokens.accentPrimary)
                            }
                        }
                    }
                }
            }
        } else {
            val filteredSpaces = publicConversations.filter {
                searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
            }
            if (filteredSpaces.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Explore,
                        title = "No public spaces yet",
                        subtitle = "Create a public channel or community from Chats or Spaces."
                    )
                }
            } else {
                items(filteredSpaces, key = { it.id }) { space ->
                    val joined = currentUser.userId in space.memberUids
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onJoinOrOpenPublicSpace(space) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = space.title, photoUrl = space.photoUrl, size = 48.dp, gradientSeed = space.id.hashCode())
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(space.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${space.type.uppercase()} • ${space.memberUids.size} members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (space.description.isNotBlank()) {
                                    Text(
                                        text = space.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (joined) tokens.surfaceGlass else tokens.accentPrimary
                            ) {
                                Text(
                                    text = if (joined) "Open" else "Join",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (joined) MaterialTheme.colorScheme.onSurface else Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePostDialog) {
        CreatePostDialog(
            isUploading = isUploadingPost,
            onDismiss = { showCreatePostDialog = false },
            onPublish = { caption, uri, tag ->
                onCreatePost(caption, uri, tag) { showCreatePostDialog = false }
            }
        )
    }

    activeCommentsPost?.let { post ->
        PostCommentsDialog(
            post = post,
            comments = postComments,
            onDismiss = { activeCommentsPost = null },
            onAddComment = { text -> onAddComment(post.id, text) }
        )
    }

    inspectedUserPopup?.let { (res, profile) ->
        SocialProfilePopupDialog(
            reservation = res,
            profile = profile,
            isFollowing = res.userId in followingIds,
            isBlocked = res.userId in blockedIds,
            onDismiss = { inspectedUserPopup = null },
            onMessage = {
                inspectedUserPopup = null
                onStartDirectChat(res)
            },
            onVoiceCall = {
                inspectedUserPopup = null
                onStartCall(res.userId, res.displayName, false)
            },
            onVideoCall = {
                inspectedUserPopup = null
                onStartCall(res.userId, res.displayName, true)
            },
            onToggleFollow = { onToggleFollow(res.userId) },
            onToggleBlock = { onToggleBlock(res.userId) },
            onReport = { reason ->
                onSubmitReport("user", res.userId, reason, "Reported from profile popup")
                inspectedUserPopup = null
            }
        )
    }
}

@Composable
private fun CreatePostDialog(
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onPublish: (caption: String, mediaUri: Uri?, collectionTag: String) -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    var caption by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("General") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        mediaUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share to Social Feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(1500) },
                    label = { Text("What's on your mind?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it.take(40) },
                    label = { Text("Collection / Topic Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = "Pick Photo")
                    Spacer(Modifier.width(8.dp))
                    Text(if (mediaUri == null) "Attach Photo (Photo Picker)" else "Photo Selected ✓")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(caption, mediaUri, tag) },
                enabled = !isUploading && (caption.isNotBlank() || mediaUri != null),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
            ) {
                if (isUploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Publish")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PostCommentsDialog(
    post: SocialPost,
    comments: List<PostComment>,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments • ${post.authorName}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(comments, key = { it.id }) { c ->
                        Column {
                            Text(c.authorName, style = MaterialTheme.typography.labelLarge)
                            Text(c.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Write a comment...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (draft.isNotBlank()) {
                            onAddComment(draft)
                            draft = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send Comment")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun SocialProfilePopupDialog(
    reservation: UsernameReservation,
    profile: UserProfile?,
    isFollowing: Boolean,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
    onReport: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val tokens = LocalPrivacyXTokens.current
    var reportText by remember { mutableStateOf("") }
    var showReportBox by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(elevated = true, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Profile", style = MaterialTheme.typography.labelLarge, color = tokens.accentPrimary)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Lavender Profile Header Card matching Screenshot 3 & 5
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(tokens.radialFabGradient))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        UserAvatar(
                            name = reservation.displayName,
                            photoUrl = reservation.photoUrl,
                            size = 76.dp,
                            isOnline = profile?.isOnline == true
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = reservation.displayName,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "@${reservation.username} • ${if (profile?.isOnline == true) "online" else (profile?.statusText ?: "PrivacyX")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
                if (!profile?.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(profile!!.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onMessage,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                        modifier = Modifier.weight(1f)
                    ) { Text("Message") }
                    OutlinedButton(onClick = onVoiceCall) {
                        Icon(Icons.Rounded.Call, contentDescription = "Voice Call", modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(onClick = onVideoCall) {
                        Icon(Icons.Rounded.Videocam, contentDescription = "Video Call", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onToggleFollow) {
                        Text(if (isFollowing) "Unfollow" else "Follow")
                    }
                    TextButton(onClick = onToggleBlock) {
                        Icon(Icons.Rounded.Block, contentDescription = "Block", modifier = Modifier.size(16.dp), tint = tokens.dangerRed)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isBlocked) "Unblock" else "Block", color = tokens.dangerRed)
                    }
                    TextButton(onClick = { showReportBox = !showReportBox }) {
                        Icon(Icons.Rounded.Flag, contentDescription = "Report", modifier = Modifier.size(16.dp), tint = tokens.dangerRed)
                        Spacer(Modifier.width(4.dp))
                        Text("Report", color = tokens.dangerRed)
                    }
                }
                if (showReportBox) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    OutlinedTextField(
                        value = reportText,
                        onValueChange = { reportText = it },
                        label = { Text("Reason for report") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { if (reportText.isNotBlank()) onReport(reportText) },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.dangerRed),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Submit Report") }
                }
            }
        }
    }
}
