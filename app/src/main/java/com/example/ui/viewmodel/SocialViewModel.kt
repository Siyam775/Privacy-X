package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PostComment
import com.example.data.model.SavedMessage
import com.example.data.model.SocialPost
import com.example.data.model.Story
import com.example.data.model.StoryView
import com.example.data.model.UserProfile
import com.example.data.repository.SocialRepository
import com.example.media.SupabaseMediaService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class SocialViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val socialRepo = SocialRepository(context)
    private val mediaService = SupabaseMediaService(context)

    private val _activeStories = MutableStateFlow<List<Story>>(emptyList())
    val activeStories: StateFlow<List<Story>> = _activeStories.asStateFlow()

    private val _myStories = MutableStateFlow<List<Story>>(emptyList())
    val myStories: StateFlow<List<Story>> = _myStories.asStateFlow()

    private val _storyViewers = MutableStateFlow<List<StoryView>>(emptyList())
    val storyViewers: StateFlow<List<StoryView>> = _storyViewers.asStateFlow()

    private val _posts = MutableStateFlow<List<SocialPost>>(emptyList())
    val posts: StateFlow<List<SocialPost>> = _posts.asStateFlow()

    private val _postComments = MutableStateFlow<List<PostComment>>(emptyList())
    val postComments: StateFlow<List<PostComment>> = _postComments.asStateFlow()

    private val _savedMessages = MutableStateFlow<List<SavedMessage>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessage>> = _savedMessages.asStateFlow()

    private val _isUploadingStoryOrPost = MutableStateFlow(false)
    val isUploadingStoryOrPost: StateFlow<Boolean> = _isUploadingStoryOrPost.asStateFlow()

    private val _socialNotice = MutableStateFlow<String?>(null)
    val socialNotice: StateFlow<String?> = _socialNotice.asStateFlow()

    private var streamJob: Job? = null
    private var detailJob: Job? = null

    fun bindSocialStreams(uid: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            launch {
                socialRepo.observeActiveStories(uid).catch {}.collect { _activeStories.value = it }
            }
            launch {
                socialRepo.observeMyArchivedStories(uid).catch {}.collect { _myStories.value = it }
            }
            launch {
                socialRepo.observePosts().catch {}.collect { _posts.value = it }
            }
            launch {
                socialRepo.observeSavedMessages(uid).catch {}.collect { _savedMessages.value = it }
            }
        }
    }

    fun openStoryAndTrackView(story: Story, viewerName: String) {
        viewModelScope.launch {
            socialRepo.recordStoryView(story, viewerName)
        }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            socialRepo.observeStoryViewers(story.id).catch {}.collect { _storyViewers.value = it }
        }
    }

    fun reactToStory(story: Story, viewerName: String, emoji: String) {
        viewModelScope.launch {
            socialRepo.recordStoryView(story, viewerName, emoji)
            if (story.authorId != viewerName) {
                socialRepo.sendInAppNotification(
                    targetUserId = story.authorId,
                    type = "reaction",
                    title = "$viewerName reacted $emoji",
                    body = "Reacted to your story",
                    targetId = story.id
                )
            }
            _socialNotice.value = "Sent $emoji reaction"
        }
    }

    fun createTextOrMediaStory(
        profile: UserProfile,
        caption: String,
        mediaUri: Uri?,
        backgroundGradient: String,
        privacy: String,
        allowedUids: List<String>,
        hiddenUids: List<String>,
        highlightTitle: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isUploadingStoryOrPost.value = true
            var mediaUrl = ""
            var mediaType = "text"
            if (mediaUri != null) {
                val up = mediaService.uploadMedia(mediaUri, conversationId = "stories")
                up.onSuccess {
                    mediaUrl = it.secureUrl
                    mediaType = "image"
                }.onFailure { err ->
                    _isUploadingStoryOrPost.value = false
                    _socialNotice.value = err.localizedMessage ?: "Media upload failed"
                    return@launch
                }
            }
            val res = socialRepo.createStory(
                authorName = profile.displayName,
                authorAvatarUrl = profile.photoUrl,
                caption = caption,
                mediaType = mediaType,
                mediaUrl = mediaUrl,
                backgroundGradient = backgroundGradient,
                privacy = privacy,
                allowedUids = allowedUids,
                hiddenUids = hiddenUids,
                highlightTitle = highlightTitle
            )
            _isUploadingStoryOrPost.value = false
            if (res.isSuccess) {
                _socialNotice.value = "Story published!"
                onComplete()
            } else {
                _socialNotice.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to publish story"
            }
        }
    }

    fun archiveOrHighlightStory(storyId: String, archive: Boolean, highlightTitle: String) {
        viewModelScope.launch {
            val res = socialRepo.archiveOrHighlightStory(storyId, archive, highlightTitle)
            _socialNotice.value = if (res.isSuccess) "Story updated" else "Update failed"
        }
    }

    fun createPost(
        profile: UserProfile,
        caption: String,
        mediaUri: Uri?,
        collectionTag: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isUploadingStoryOrPost.value = true
            var mediaUrl = ""
            if (mediaUri != null) {
                val up = mediaService.uploadMedia(mediaUri, conversationId = "posts")
                up.onSuccess { mediaUrl = it.secureUrl }.onFailure { err ->
                    _isUploadingStoryOrPost.value = false
                    _socialNotice.value = err.localizedMessage ?: "Post image upload failed"
                    return@launch
                }
            }
            val res = socialRepo.createPost(
                authorName = profile.displayName,
                authorAvatarUrl = profile.photoUrl,
                caption = caption,
                mediaUrl = mediaUrl,
                collectionTag = collectionTag
            )
            _isUploadingStoryOrPost.value = false
            if (res.isSuccess) {
                _socialNotice.value = "Post shared to Discover Feed"
                onComplete()
            } else {
                _socialNotice.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to publish post"
            }
        }
    }

    fun togglePostLike(postId: String) {
        viewModelScope.launch { socialRepo.togglePostLike(postId) }
    }

    fun openPostComments(postId: String) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            socialRepo.observePostComments(postId).catch {}.collect { _postComments.value = it }
        }
    }

    fun addPostComment(postId: String, authorName: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            socialRepo.addPostComment(postId, authorName, text)
        }
    }

    fun saveQuickNote(noteText: String) {
        if (noteText.isBlank()) return
        viewModelScope.launch {
            socialRepo.saveMessageBookmark(content = noteText, sourceSenderName = "Personal Note")
        }
    }

    fun deleteSavedMessage(itemId: String) {
        viewModelScope.launch { socialRepo.deleteSavedMessage(itemId) }
    }

    fun clearNotice() {
        _socialNotice.value = null
    }
}
