package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Conversation
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.UserProfile
import com.example.data.model.UsernameReservation
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPillSearchBar
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalPrivacyXTokens
import java.text.SimpleDateFormat
import java.util.Locale

enum class ChatFilterTab(val label: String) {
    CHATS("Chats"),
    GROUPS("Groups"),
    CHANNELS("Channels"),
    ARCHIVED("Archived"),
    UNREAD("Unread")
}

@Composable
fun ChatsDashboardScreen(
    currentUser: UserProfile,
    isSuperAdmin: Boolean,
    conversations: List<Conversation>,
    directoryUsers: List<UsernameReservation>,
    announcements: List<PlatformAnnouncement>,
    unreadNotificationsCount: Int,
    onSearchQuery: (String) -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onStartDirectChat: (UsernameReservation) -> Unit,
    onCreateSpace: (type: String, title: String, description: String, isPublic: Boolean, members: List<String>) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
    showNewChatDialogExternal: Boolean,
    showCreateGroupDialogExternal: Boolean,
    showCreateCommunityDialogExternal: Boolean,
    onDismissExternalDialogs: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ChatFilterTab.CHATS) }
    var showCreateDialogType by remember { mutableStateOf<String?>(null) }
    var showNewDmDialog by remember { mutableStateOf(false) }

    val activeDialogType = when {
        showCreateGroupDialogExternal -> "group"
        showCreateCommunityDialogExternal -> "community"
        else -> showCreateDialogType
    }
    val activeNewDmDialog = showNewDmDialog || showNewChatDialogExternal

    val filteredConversations = remember(conversations, searchQuery, selectedFilter) {
        conversations.filter { conv ->
            val matchesQuery = searchQuery.isBlank() ||
                conv.title.contains(searchQuery, ignoreCase = true) ||
                conv.lastMessageText.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                ChatFilterTab.CHATS -> true
                ChatFilterTab.GROUPS -> conv.type == "group" || conv.type == "community"
                ChatFilterTab.CHANNELS -> conv.type == "channel"
                ChatFilterTab.ARCHIVED -> false
                ChatFilterTab.UNREAD -> conv.lastMessageSenderId.isNotBlank() &&
                    conv.lastMessageSenderId != currentUser.userId
            }
            matchesQuery && matchesFilter
        }
    }

    val groupAndCommunityList = filteredConversations.filter { it.type != "direct" }
    val directList = filteredConversations.filter { it.type == "direct" }
    val matchingUsers = remember(directoryUsers, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else directoryUsers.filter {
            it.username.contains(searchQuery.removePrefix("@"), ignoreCase = true) ||
                it.displayName.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Floating Profile Header Card (matching UI Reference 1)
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                elevated = true,
                onClick = onOpenProfile
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        name = currentUser.displayName,
                        photoUrl = currentUser.photoUrl,
                        size = 46.dp,
                        isOnline = true,
                        isCircular = false
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@${currentUser.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSuperAdmin) {
                        IconButton(
                            onClick = onOpenAdminDashboard,
                            modifier = Modifier.testTag("header_admin_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = "Super Admin Dashboard",
                                tint = tokens.accentPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenAiAssistant,
                        modifier = Modifier.testTag("header_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Assistant",
                            tint = tokens.accentPrimary
                        )
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("header_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge(containerColor = tokens.accentPink) {
                                        Text(unreadNotificationsCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.surfaceGlass,
                        border = BorderStroke(1.dp, tokens.surfaceBorder),
                        modifier = Modifier.clickable(onClick = onSignOut)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp).size(18.dp)
                        )
                    }
                }
            }
        }

        // Super Admin Announcement Banner (if published)
        if (announcements.isNotEmpty()) {
            val latest = announcements.first()
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = "Announcement",
                            tint = if (latest.priority == "urgent") tokens.dangerRed else tokens.accentPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = latest.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = latest.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Pill Search Bar
        item {
            GlassPillSearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    onSearchQuery(it)
                },
                placeholder = "Search users by @username or chats...",
                testTag = "chats_search_input"
            )
        }

        // Pill Filter Row (matching Screenshot 1)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChatFilterTab.entries.forEach { tab ->
                    val selected = tab == selectedFilter
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) tokens.accentPrimary else tokens.surfaceGlassElevated,
                        border = BorderStroke(1.dp, if (selected) Color.Transparent else tokens.surfaceBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { selectedFilter = tab }
                            .testTag("filter_pill_${tab.name.lowercase()}")
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        // Live username search quick results
        if (matchingUsers.isNotEmpty()) {
            item {
                Text(
                    text = "PEOPLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
            items(matchingUsers, key = { "u_${it.username}" }) { user ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onStartDirectChat(user) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(name = user.displayName, photoUrl = user.photoUrl, size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tokens.accentPrimary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "Message",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.accentPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // GROUPS & COMMUNITY Section
        if (groupAndCommunityList.isNotEmpty()) {
            item {
                Text(
                    text = "GROUPS & COMMUNITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }
            items(groupAndCommunityList, key = { it.id }) { conv ->
                ConversationListRow(
                    conversation = conv,
                    onClick = { onOpenConversation(conv) }
                )
            }
        }

        // DIRECT CHATS Section
        if (directList.isNotEmpty()) {
            item {
                Text(
                    text = "DIRECT CHATS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }
            items(directList, key = { it.id }) { conv ->
                ConversationListRow(
                    conversation = conv,
                    onClick = { onOpenConversation(conv) }
                )
            }
        }

        if (filteredConversations.isEmpty() && matchingUsers.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    title = "No conversations yet",
                    subtitle = "Tap + below or search by @username to start an encrypted chat, group, or channel."
                )
            }
        }

        // DISCOVER Quick Action Card (matching Screenshot 1)
        item {
            Text(
                text = "DISCOVER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("create_group_discover_card"),
                onClick = { showCreateDialogType = "group" }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(tokens.accentPrimary.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create Group", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Create a group or channel",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "You become the admin with topics & slow mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // New Direct Chat Picker Dialog
    if (activeNewDmDialog) {
        NewDirectChatDialog(
            users = directoryUsers,
            onDismiss = {
                showNewDmDialog = false
                onDismissExternalDialogs()
            },
            onSelectUser = { user ->
                showNewDmDialog = false
                onDismissExternalDialogs()
                onStartDirectChat(user)
            }
        )
    }

    // Create Group / Community / Channel Dialog
    if (activeDialogType != null) {
        CreateSpaceDialog(
            initialType = activeDialogType,
            users = directoryUsers,
            onDismiss = {
                showCreateDialogType = null
                onDismissExternalDialogs()
            },
            onCreate = { type, title, desc, isPublic, memberUids ->
                showCreateDialogType = null
                onDismissExternalDialogs()
                onCreateSpace(type, title, desc, isPublic, memberUids)
            }
        )
    }
}

@Composable
private fun ConversationListRow(
    conversation: Conversation,
    onClick: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    val timeStr = remember(conversation.lastMessageAt) {
        val date = conversation.lastMessageAt?.toDate()
        if (date != null) SimpleDateFormat("h:mm a", Locale.getDefault()).format(date) else ""
    }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversation_card_${conversation.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = conversation.title,
                photoUrl = conversation.photoUrl,
                size = 50.dp,
                gradientSeed = conversation.id.hashCode()
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.lastMessageText.ifBlank {
                            conversation.type.replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (conversation.pinnedMessageId.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            tint = tokens.accentPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewDirectChatDialog(
    users: List<UsernameReservation>,
    onDismiss: () -> Unit,
    onSelectUser: (UsernameReservation) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(users, query) {
        users.filter {
            query.isBlank() ||
                it.username.contains(query.removePrefix("@"), ignoreCase = true) ||
                it.displayName.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start New Chat") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search @username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    Text(
                        "No other users found yet. Invite friends to sign in on PrivacyX!",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(filtered, key = { it.username }) { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectUser(u) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = u.displayName, photoUrl = u.photoUrl, size = 38.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(u.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text("@${u.username}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun CreateSpaceDialog(
    initialType: String,
    users: List<UsernameReservation>,
    onDismiss: () -> Unit,
    onCreate: (type: String, title: String, description: String, isPublic: Boolean, members: List<String>) -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    var type by remember(initialType) { mutableStateOf(initialType) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(initialType == "channel" || initialType == "community") }
    val selectedUids = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create ${type.replaceFirstChar { it.uppercase() }}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("group", "community", "channel").forEach { candidate ->
                        val active = type == candidate
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (active) tokens.accentPrimary else tokens.surfaceGlass,
                            modifier = Modifier.clickable { type = candidate }
                        ) {
                            Text(
                                text = candidate.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_space_title_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(300) },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Publicly Discoverable", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                if (users.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Add Members:", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.height(110.dp)) {
                        items(users, key = { it.username }) { u ->
                            val checked = u.userId in selectedUids
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedUids.remove(u.userId)
                                        else selectedUids.add(u.userId)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (it) selectedUids.add(u.userId) else selectedUids.remove(u.userId)
                                    }
                                )
                                Text("${u.displayName} (@${u.username})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(type, title, description, isPublic, selectedUids.toList()) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                modifier = Modifier.testTag("create_space_confirm_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
