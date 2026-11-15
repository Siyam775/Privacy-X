package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class VoiceRecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationMs: Long = 0L,
    val amplitudes: List<Float> = emptyList(),
    val outputFile: File? = null,
    val errorMessage: String? = null
)

data class VoicePlaybackState(
    val activeUrl: String? = null,
    val isPlaying: Boolean = false,
    val progressFraction: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val errorMessage: String? = null
)

class VoiceRecorderPlayer(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var player: MediaPlayer? = null
    private var playbackJob: Job? = null
    private var recordingStartWallMs: Long = 0L

    private val _recordingState = MutableStateFlow(VoiceRecordingState())
    val recordingState: StateFlow<VoiceRecordingState> = _recordingState.asStateFlow()

    private val _playbackState = MutableStateFlow(VoicePlaybackState())
    val playbackState: StateFlow<VoicePlaybackState> = _playbackState.asStateFlow()

    fun startRecording(): Result<Unit> {
        stopPlayback()
        stopAndCleanupRecorder()

        val profiles = listOf(
            RecorderProfile("m4a", MediaRecorder.OutputFormat.MPEG_4, MediaRecorder.AudioEncoder.AAC, 44100, 96000),
            RecorderProfile("m4a", MediaRecorder.OutputFormat.MPEG_4, MediaRecorder.AudioEncoder.AAC, 16000, 64000),
            RecorderProfile("3gp", MediaRecorder.OutputFormat.THREE_GPP, MediaRecorder.AudioEncoder.AMR_NB, 8000, 12200)
        )

        var lastError: Exception? = null
        for (profile in profiles) {
            val outFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.${profile.ext}")
            val mr = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
            } catch (e: Exception) {
                lastError = e
                continue
            }

            try {
                mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                mr.setOutputFormat(profile.outputFormat)
                mr.setAudioEncoder(profile.audioEncoder)
                if (profile.sampleRate > 0) mr.setAudioSamplingRate(profile.sampleRate)
                if (profile.bitRate > 0) mr.setAudioEncodingBitRate(profile.bitRate)
                mr.setOutputFile(outFile.absolutePath)
                mr.prepare()
                mr.start()

                recorder = mr
                recordingStartWallMs = System.currentTimeMillis()
                _recordingState.value = VoiceRecordingState(
                    isRecording = true,
                    isPaused = false,
                    durationMs = 0L,
                    amplitudes = emptyList(),
                    outputFile = outFile,
                    errorMessage = null
                )
                startAmplitudeMeter()
                return Result.success(Unit)
            } catch (e: Exception) {
                Log.w("VoiceRecorderPlayer", "Recorder profile ${profile.ext}/${profile.sampleRate} failed: ${e.message}", e)
                lastError = e
                runCatching { mr.reset() }
                runCatching { mr.release() }
                runCatching { outFile.delete() }
            }
        }

        val errMsg = lastError?.localizedMessage ?: "Microphone is unavailable or in use by another app."
        _recordingState.value = VoiceRecordingState(errorMessage = errMsg)
        return Result.failure(lastError ?: IllegalStateException(errMsg))
    }

    private data class RecorderProfile(
        val ext: String,
        val outputFormat: Int,
        val audioEncoder: Int,
        val sampleRate: Int,
        val bitRate: Int
    )

    fun togglePauseRecording() {
        val current = _recordingState.value
        val mr = recorder ?: return
        if (!current.isRecording) return
        try {
            if (current.isPaused) {
                mr.resume()
                _recordingState.value = current.copy(isPaused = false, errorMessage = null)
            } else {
                mr.pause()
                _recordingState.value = current.copy(isPaused = true, errorMessage = null)
            }
        } catch (e: Exception) {
            _recordingState.value = current.copy(errorMessage = e.localizedMessage ?: "Pause not supported on device")
        }
    }

    fun finishRecording(): Result<Pair<Uri, Long>> {
        val state = _recordingState.value
        val file = state.outputFile
        val elapsedMs = maxOf(
            state.durationMs,
            if (recordingStartWallMs > 0L) System.currentTimeMillis() - recordingStartWallMs else 0L
        )
        val stopOk = stopAndCleanupRecorder()
        recordingStartWallMs = 0L
        _recordingState.value = VoiceRecordingState()

        if (file == null || !file.exists() || file.length() <= 64L || elapsedMs < 350L) {
            runCatching { file?.delete() }
            val msg = if (!stopOk || elapsedMs < 350L) {
                "Recording too short. Hold or speak for at least 1 second."
            } else {
                "No audio captured from microphone."
            }
            return Result.failure(IllegalStateException(msg))
        }
        return Result.success(Uri.fromFile(file) to elapsedMs.coerceAtLeast(1000L))
    }

    fun cancelRecording() {
        val file = _recordingState.value.outputFile
        stopAndCleanupRecorder()
        recordingStartWallMs = 0L
        runCatching { file?.delete() }
        _recordingState.value = VoiceRecordingState()
    }

    private fun startAmplitudeMeter() {
        recordingJob?.cancel()
        recordingJob = scope.launch {
            while (isActive && _recordingState.value.isRecording) {
                delay(100L)
                val state = _recordingState.value
                if (!state.isPaused) {
                    val amp = try {
                        ((recorder?.maxAmplitude ?: 0) / 32767f).coerceIn(0.08f, 1f)
                    } catch (_: Exception) {
                        0.12f
                    }
                    _recordingState.value = state.copy(
                        durationMs = state.durationMs + 100L,
                        amplitudes = (state.amplitudes + amp).takeLast(36)
                    )
                }
            }
        }
    }

    private fun stopAndCleanupRecorder(): Boolean {
        recordingJob?.cancel()
        recordingJob = null
        var stoppedCleanly = true
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w("VoiceRecorderPlayer", "Recorder stop exception: ${e.message}")
            stoppedCleanly = false
        }
        try {
            recorder?.reset()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        return stoppedCleanly
    }

    fun togglePlay(urlOrPath: String) {
        if (urlOrPath.isBlank()) {
            _playbackState.value = _playbackState.value.copy(errorMessage = "Audio link is missing.")
            return
        }
        val current = _playbackState.value
        if (current.activeUrl == urlOrPath && player != null) {
            if (current.isPlaying) {
                runCatching { player?.pause() }
                _playbackState.value = current.copy(isPlaying = false, errorMessage = null)
            } else {
                runCatching { player?.start() }
                _playbackState.value = current.copy(isPlaying = true, errorMessage = null)
                startPlaybackTracker()
            }
            return
        }

        stopPlayback()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                if (urlOrPath.startsWith("content://") || urlOrPath.startsWith("file://")) {
                    setDataSource(context, Uri.parse(urlOrPath))
                } else {
                    setDataSource(urlOrPath)
                }
                setOnPreparedListener { ready ->
                    runCatching {
                        ready.playbackParams = PlaybackParams().setSpeed(_playbackState.value.speed)
                    }
                    ready.start()
                    _playbackState.value = _playbackState.value.copy(
                        activeUrl = urlOrPath,
                        isPlaying = true,
                        durationMs = ready.duration.toLong().coerceAtLeast(1L),
                        errorMessage = null
                    )
                    startPlaybackTracker()
                }
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        progressFraction = 0f,
                        currentPositionMs = 0L
                    )
                }
                setOnErrorListener { _, what, extra ->
                    Log.w("VoiceRecorderPlayer", "Playback error what=$what extra=$extra")
                    stopPlayback()
                    _playbackState.value = _playbackState.value.copy(
                        errorMessage = "Unable to play voice message."
                    )
                    true
                }
                prepareAsync()
            }
            player = mp
        } catch (e: Exception) {
            stopPlayback()
            _playbackState.value = _playbackState.value.copy(
                errorMessage = e.localizedMessage ?: "Playback failed"
            )
        }
    }

    fun cyclePlaybackSpeed() {
        val nextSpeed = when (_playbackState.value.speed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playbackState.value = _playbackState.value.copy(speed = nextSpeed)
        try {
            player?.let { mp ->
                if (mp.isPlaying) {
                    mp.playbackParams = PlaybackParams().setSpeed(nextSpeed)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun startPlaybackTracker() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                val mp = player ?: break
                val dur = runCatching { mp.duration.toLong() }.getOrDefault(1L).coerceAtLeast(1L)
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L).coerceIn(0L, dur)
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = pos,
                    durationMs = dur,
                    progressFraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                )
                delay(100L)
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        _playbackState.value = VoicePlaybackState(speed = _playbackState.value.speed)
    }

    fun release() {
        stopAndCleanupRecorder()
        stopPlayback()
    }
}
