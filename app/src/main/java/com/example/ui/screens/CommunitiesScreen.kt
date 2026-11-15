package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.data.model.UsernameReservation
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalPrivacyXTokens

@Composable
fun CommunitiesScreen(
    currentUser: UserProfile,
    myConversations: List<Conversation>,
    publicConversations: List<Conversation>,
    directoryUsers: List<UsernameReservation>,
    onOpenConversation: (Conversation) -> Unit,
    onJoinConversation: (String) -> Unit,
    onCreateSpace: (type: String, title: String, description: String, isPublic: Boolean, members: List<String>) -> Unit,
    onAddMember: (conversationId: String, uid: String) -> Unit,
    onRemoveMember: (conversationId: String, uid: String) -> Unit,
    onPromoteMember: (conversationId: String, uid: String) -> Unit,
    onDemoteMember: (conversationId: String, uid: String) -> Unit = { _, _ -> }
) {
    val tokens = LocalPrivacyXTokens.current
    var showCreateType by remember { mutableStateOf<String?>(null) }
    var manageTarget by remember { mutableStateOf<Conversation?>(null) }
    var inviteCodeInput by remember { mutableStateOf("") }

    val communities = remember(myConversations) { myConversations.filter { it.type == "community" } }
    val groups = remember(myConversations) { myConversations.filter { it.type == "group" } }
    val channels = remember(myConversations) { myConversations.filter { it.type == "channel" } }

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
                    Text("Spaces", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Groups, Communities & Broadcast Channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreateType = "community" },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                    modifier = Modifier.testTag("spaces_create_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create Space", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Create")
                }
            }
        }

        // Join via Invite Code Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it.uppercase().take(16) },
                        label = { Text("Join with Invite Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val match = publicConversations.firstOrNull {
                                it.inviteCode.equals(inviteCodeInput.trim(), ignoreCase = true)
                            }
                            if (match != null) {
                                onJoinConversation(match.id)
                                inviteCodeInput = ""
                            }
                        },
                        enabled = inviteCodeInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
                    ) { Text("Join") }
                }
            }
        }

        // Quick Creation Pills
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCreateType = "group" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Groups, contentDescription = "New Group", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Group", maxLines = 1)
                }
                OutlinedButton(onClick = { showCreateType = "community" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Hub, contentDescription = "New Community", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Community", maxLines = 1)
                }
                OutlinedButton(onClick = { showCreateType = "channel" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Campaign, contentDescription = "New Channel", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Channel", maxLines = 1)
                }
            }
        }

        if (communities.isNotEmpty()) {
            item { Text("COMMUNITIES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(communities, key = { it.id }) { item ->
                SpaceRowCard(
                    conversation = item,
                    isAdmin = currentUser.userId in item.adminUids,
                    onOpen = { onOpenConversation(item) },
                    onManage = { manageTarget = item }
                )
            }
        }

        if (groups.isNotEmpty()) {
            item { Text("GROUPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(groups, key = { it.id }) { item ->
                SpaceRowCard(
                    conversation = item,
                    isAdmin = currentUser.userId in item.adminUids,
                    onOpen = { onOpenConversation(item) },
                    onManage = { manageTarget = item }
                )
            }
        }

        if (channels.isNotEmpty()) {
            item { Text("CHANNELS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(channels, key = { it.id }) { item ->
                SpaceRowCard(
                    conversation = item,
                    isAdmin = currentUser.userId in item.adminUids,
                    onOpen = { onOpenConversation(item) },
                    onManage = { manageTarget = item }
                )
            }
        }

        if (communities.isEmpty() && groups.isEmpty() && channels.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Hub,
                    title = "No Groups or Communities yet",
                    subtitle = "Create a group with discussion topics, a community hub, or a broadcast channel."
                )
            }
        }
    }

    showCreateType?.let { type ->
        CreateSpaceDialog(
            initialType = type,
            users = directoryUsers,
            onDismiss = { showCreateType = null },
            onCreate = { t, title, desc, isPub, members ->
                showCreateType = null
                onCreateSpace(t, title, desc, isPub, members)
            }
        )
    }

    manageTarget?.let { space ->
        ManageSpaceMembersDialog(
            space = space,
            directoryUsers = directoryUsers,
            onAddMember = { uid -> onAddMember(space.id, uid) },
            onRemoveMember = { uid -> onRemoveMember(space.id, uid) },
            onPromoteMember = { uid -> onPromoteMember(space.id, uid) },
            onDemoteMember = { uid -> onDemoteMember(space.id, uid) },
            onDismiss = { manageTarget = null }
        )
    }
}

@Composable
private fun SpaceRowCard(
    conversation: Conversation,
    isAdmin: Boolean,
    onOpen: () -> Unit,
    onManage: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(name = conversation.title, photoUrl = conversation.photoUrl, size = 48.dp, gradientSeed = conversation.id.hashCode())
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${conversation.memberUids.size} members • Code: ${conversation.inviteCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isAdmin) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tokens.accentPrimary.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, tokens.accentPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable(onClick = onManage)
                ) {
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.accentPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageSpaceMembersDialog(
    space: Conversation,
    directoryUsers: List<UsernameReservation>,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onPromoteMember: (String) -> Unit,
    onDemoteMember: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage • ${space.title}") },
        text = {
            LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Invite Code: ${space.inviteCode}", style = MaterialTheme.typography.labelMedium, color = tokens.accentPrimary) }
                items(directoryUsers, key = { "m_${it.userId}" }) { user ->
                    val isMember = user.userId in space.memberUids
                    val isAdmin = user.userId in space.adminUids
                    val isOwner = user.userId == space.ownerId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                            val roleLabel = when {
                                isOwner -> " • Owner"
                                isAdmin -> " • Admin"
                                isMember -> " • Member"
                                else -> ""
                            }
                            Text("@${user.username}$roleLabel", style = MaterialTheme.typography.bodySmall)
                        }
                        if (isMember && !isAdmin) {
                            IconButton(onClick = { onPromoteMember(user.userId) }) {
                                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = "Promote Admin", tint = tokens.accentPrimary)
                            }
                        } else if (isMember && isAdmin && !isOwner) {
                            TextButton(onClick = { onDemoteMember(user.userId) }) {
                                Text("Demote", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (isMember && !isOwner) {
                            IconButton(onClick = { onRemoveMember(user.userId) }) {
                                Icon(Icons.Rounded.PersonRemove, contentDescription = "Remove Member", tint = tokens.dangerRed)
                            }
                        } else if (!isMember) {
                            IconButton(onClick = { onAddMember(user.userId) }) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Member", tint = tokens.onlineGreen)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
