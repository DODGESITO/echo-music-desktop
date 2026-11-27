package echo.music.desktop.recommend

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Music Flow: endless "swipe to discover" feed, ported from the phone app (musicflow/*).
// Radios of the songs you play and like, filtered and ranked so the same artist does not repeat.

enum class FlowSource { NOW_PLAYING, HISTORY, LIKED, QUICK_PICKS, CATALOG, OFFLINE }

data class FlowItem(val song: SongItem, val source: FlowSource) {
    val songId: String get() = song.id
    val primaryArtistKey: String? get() = song.artists.firstOrNull()?.let { it.id ?: it.name.lowercase() }
    val trackKey: String get() = trackKeyOf(song.title, song.artists.joinToString(", ") { it.name })
}

/** Same key as the phone app: catches the same song uploaded twice. */
fun trackKeyOf(title: String, artist: String): String = "${title.trim()}|${artist.trim()}".lowercase()

enum class FlowError { OFFLINE, EMPTY, FAILED }

data class FlowState(
    val items: List<FlowItem> = emptyList(),
    val selectedIndex: Int = 0,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: FlowError? = null,
    val hasMore: Boolean = true,
    val isOffline: Boolean = false
) {
    val selectedSongId: String? get() = items.getOrNull(selectedIndex)?.songId
}

data class FlowRankingConfig(
    val artistSpacing: Int = 2,
    val likedArtistBoost: Float = 1.0f,
    val lessLikeThisPenalty: Float = 2.5f,
    val positionDecay: Float = 0.02f,
    val sourceWeights: Map<FlowSource, Float> = mapOf(
        FlowSource.HISTORY to 0.6f,
        FlowSource.LIKED to 0.8f,
        FlowSource.QUICK_PICKS to 0.5f,
        FlowSource.CATALOG to 0.2f,
        FlowSource.OFFLINE to 0.4f
    )
)

data class FlowFilter(
    val seenSongIds: Set<String> = emptySet(),
    val seenTrackKeys: Set<String> = emptySet(),
    val excludedTrackKeys: Set<String> = emptySet(),
    val hiddenSongIds: Set<String> = emptySet(),
    val recentSongIds: Set<String> = emptySet()
)

data class FlowSignals(val filter: FlowFilter, val likedArtists: Set<String>)

fun filterFlowCandidates(candidates: List<FlowItem>, filter: FlowFilter): List<FlowItem> {
    val batchIds = mutableSetOf<String>()
    val batchKeys = mutableSetOf<String>()
    return candidates.filter { item ->
        val key = item.trackKey
        item.songId.isNotBlank() &&
            item.songId !in filter.seenSongIds &&
            key !in filter.seenTrackKeys &&
            key !in filter.excludedTrackKeys &&
            item.songId !in filter.hiddenSongIds &&
            item.songId !in filter.recentSongIds &&
            batchIds.add(item.songId) &&
            batchKeys.add(key)
    }
}

/**
 * Scores by source, liked-artist affinity and "less like this", then reorders greedily so the same
 * main artist never appears within [FlowRankingConfig.artistSpacing] songs of itself (also across
 * the end of what is already in the feed).
 */
fun rankFlowCandidates(
    candidates: List<FlowItem>,
    feedTailArtists: List<String?> = emptyList(),
    likedArtists: Set<String> = emptySet(),
    lessLikeArtists: Set<String> = emptySet(),
    config: FlowRankingConfig = FlowRankingConfig()
): List<FlowItem> {
    val scored = candidates
        .mapIndexed { index, item ->
            val artist = item.primaryArtistKey
            var score = config.sourceWeights[item.source] ?: 0f
            score -= index * config.positionDecay
            if (artist != null && artist in likedArtists) score += config.likedArtistBoost
            if (artist != null && artist in lessLikeArtists) score -= config.lessLikeThisPenalty
            item to score
        }
        .sortedByDescending { it.second }
        .map { it.first }
        .toMutableList()

    val recent = ArrayDeque(feedTailArtists.takeLast(config.artistSpacing))
    val result = ArrayList<FlowItem>(scored.size)
    while (scored.isNotEmpty()) {
        val pick = scored.indexOfFirst { it.primaryArtistKey == null || it.primaryArtistKey !in recent }.takeIf { it >= 0 } ?: 0
        val picked = scored.removeAt(pick)
        result.add(picked)
        recent.addLast(picked.primaryArtistKey)
        while (recent.size > config.artistSpacing) recent.removeFirst()
    }
    return result
}

internal fun <T> interleave(sources: List<List<T>>): List<T> = buildList {
    for (index in 0 until (sources.maxOfOrNull { it.size } ?: 0)) {
        sources.forEach { source -> source.getOrNull(index)?.let { add(it) } }
    }
}

class FlowOfflineException(cause: Throwable?) : Exception(cause)

interface FlowCursor {
    val hasMore: Boolean
    suspend fun next(): List<FlowItem>
}

interface FlowFeedSource {
    suspend fun signals(): FlowSignals
    fun open(nowPlaying: SongItem?): FlowCursor
    suspend fun offlineCandidates(): List<FlowItem>
}

/** What Music Flow reads from the app (kept as plain functions so the feed can be tested). */
class FlowInputs(
    val history: () -> List<SongItem>,
    val liked: () -> List<SongItem>,
    val quickPicks: () -> List<SongItem>,
    val downloaded: () -> List<SongItem>,
    val hideRecent: () -> Boolean,
    /** Explicit, videos, blocked artists and "No me gusta" according to the user's settings. */
    val contentFilter: (List<SongItem>) -> List<SongItem>,
    /** Songs marked "No recomendar nunca más" (track keys). */
    val neverRecommend: () -> Set<String>
)

/** Radios (YouTube "RDAMVM" mixes) of the songs you play and like, page by page. */
class YouTubeFlowSource(private val inputs: FlowInputs) : FlowFeedSource {
    override suspend fun signals(): FlowSignals = FlowSignals(
        filter = FlowFilter(
            excludedTrackKeys = inputs.neverRecommend(),
            recentSongIds = if (inputs.hideRecent()) inputs.history().take(RECENT_EXCLUSION_LIMIT).map { it.id }.toSet() else emptySet()
        ),
        likedArtists = inputs.liked().flatMap { s -> s.artists.mapNotNull { it.id ?: it.name.lowercase() } }.toSet()
    )

    override fun open(nowPlaying: SongItem?): FlowCursor = RadioCursor(nowPlaying)

    override suspend fun offlineCandidates(): List<FlowItem> =
        inputs.contentFilter(inputs.downloaded().shuffled()).map { FlowItem(it, FlowSource.OFFLINE) }

    private data class Seed(val song: SongItem, val source: FlowSource)

    private inner class RadioCursor(private val nowPlaying: SongItem?) : FlowCursor {
        private var seeds: ArrayDeque<Seed>? = null
        private var tailEndpoint: WatchEndpoint? = null
        private var tailContinuation: String? = null
        private var tailSource = FlowSource.CATALOG
        private var exhausted = false

        override val hasMore: Boolean get() = !exhausted

        override suspend fun next(): List<FlowItem> = withContext(Dispatchers.IO) {
            val queue = seeds ?: loadSeeds().also { seeds = it }
            val batch = mutableListOf<List<FlowItem>>()
            var networkFailure: Throwable? = null

            repeat(SEEDS_PER_BATCH) {
                val seed = queue.removeFirstOrNull() ?: return@repeat
                runCatching { radio(WatchEndpoint(videoId = seed.song.id, playlistId = "RDAMVM${seed.song.id}"), null) }
                    .onSuccess { (songs, endpoint, continuation) ->
                        batch += songs.map { FlowItem(it, seed.source) }
                        tailEndpoint = endpoint
                        tailContinuation = continuation
                        tailSource = seed.source
                    }
                    .onFailure {
                        if (it is CancellationException) throw it
                        networkFailure = it
                    }
            }

            if (batch.isEmpty()) {
                val endpoint = tailEndpoint
                val continuation = tailContinuation
                if (endpoint != null && continuation != null) {
                    runCatching { radio(endpoint, continuation) }
                        .onSuccess { (songs, nextEndpoint, nextContinuation) ->
                            batch += songs.map { FlowItem(it, tailSource) }
                            tailEndpoint = nextEndpoint
                            tailContinuation = nextContinuation
                        }
                        .onFailure {
                            if (it is CancellationException) throw it
                            networkFailure = it
                            tailContinuation = null
                        }
                }
            }

            if (batch.isEmpty() && networkFailure != null) throw FlowOfflineException(networkFailure)
            if (queue.isEmpty() && tailContinuation == null) exhausted = true
            interleave(batch)
        }

        private suspend fun radio(endpoint: WatchEndpoint, continuation: String?): Triple<List<SongItem>, WatchEndpoint, String?> {
            val result = YouTube.next(endpoint, continuation).getOrThrow()
            return Triple(inputs.contentFilter(result.items), result.endpoint, result.continuation)
        }

        private suspend fun loadSeeds(): ArrayDeque<Seed> {
            val recent = inputs.history().take(SEED_LIMIT).map { Seed(it, FlowSource.HISTORY) }
            val liked = inputs.liked().take(LIKED_POOL).shuffled().take(SEED_LIMIT).map { Seed(it, FlowSource.LIKED) }
            val quick = inputs.quickPicks().take(SEED_LIMIT).map { Seed(it, FlowSource.QUICK_PICKS) }
            val personal = listOfNotNull(nowPlaying?.let { Seed(it, FlowSource.NOW_PLAYING) }) +
                interleave(listOf(recent, liked, quick))
            val seeds = personal.ifEmpty { catalogSeeds() }
            return ArrayDeque(seeds.distinctBy { it.song.id })
        }

        private suspend fun catalogSeeds(): List<Seed> = runCatching { YouTube.home().getOrThrow() }
            .getOrNull()
            ?.sections
            ?.flatMap { section -> section.items.filterIsInstance<SongItem>() }
            ?.let(inputs.contentFilter)
            ?.take(SEED_LIMIT)
            ?.map { Seed(it, FlowSource.CATALOG) }
            .orEmpty()
    }

    private companion object {
        const val SEEDS_PER_BATCH = 2
        const val SEED_LIMIT = 6
        const val LIKED_POOL = 40
        const val RECENT_EXCLUSION_LIMIT = 30
    }
}

/**
 * The feed itself. Pages only ever get appended, so going back always shows the same songs.
 */
class FlowSession(private val source: FlowFeedSource) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state.asStateFlow()

    private var cursor: FlowCursor? = null
    private var signals = FlowSignals(FlowFilter(), emptySet())
    private val seenSongIds = mutableSetOf<String>()
    private val seenTrackKeys = mutableSetOf<String>()
    private val hiddenSongIds = mutableSetOf<String>()
    private val lessLikeArtists = mutableSetOf<String>()

    var rankingConfig = FlowRankingConfig()

    val isStarted: Boolean get() = _state.value.items.isNotEmpty()

    suspend fun start(nowPlaying: FlowItem?) {
        if (isStarted) return
        reset(nowPlaying)
    }

    suspend fun reset(nowPlaying: FlowItem?) = mutex.withLock {
        seenSongIds.clear()
        seenTrackKeys.clear()
        val seedItems = listOfNotNull(nowPlaying)
        seedItems.forEach(::markSeen)
        _state.value = FlowState(items = seedItems, isInitialLoading = true)

        signals = try {
            source.signals()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            FlowSignals(FlowFilter(), emptySet())
        }
        cursor = source.open(nowPlaying?.song)

        var fetches = 0
        var error: FlowError? = null
        while (_state.value.items.size < INITIAL_TARGET && cursor?.hasMore == true && fetches < MAX_FETCHES_PER_LOAD) {
            fetches++
            val outcome = fetchAndAppendLocked()
            if (outcome is Outcome.Failed) {
                error = outcome.error
                break
            }
        }
        if (error == FlowError.OFFLINE && _state.value.items.size <= seedItems.size) appendOfflineLocked()

        _state.update { current ->
            current.copy(
                isInitialLoading = false,
                hasMore = cursor?.hasMore == true && !current.isOffline,
                error = when {
                    current.items.isNotEmpty() -> null
                    error != null -> error
                    else -> FlowError.EMPTY
                }
            )
        }
    }

    /** One bounded round of new songs; returns exactly the ones added. */
    suspend fun loadMore(): List<FlowItem> = mutex.withLock {
        val current = _state.value
        if (!current.hasMore || current.isInitialLoading || current.isLoadingMore || current.items.size >= MAX_FEED_SIZE) {
            return@withLock emptyList()
        }
        _state.update { it.copy(isLoadingMore = true) }
        val before = _state.value.items.size
        var fetches = 0
        var failure: FlowError? = null
        while (_state.value.items.size == before && cursor?.hasMore == true && fetches < MAX_FETCHES_PER_LOAD) {
            fetches++
            val outcome = fetchAndAppendLocked()
            if (outcome is Outcome.Failed) {
                failure = outcome.error
                break
            }
        }
        val items = _state.value.items
        _state.update {
            it.copy(
                isLoadingMore = false,
                hasMore = cursor?.hasMore == true && items.size < MAX_FEED_SIZE,
                error = if (items.size == before) failure else null
            )
        }
        items.drop(before)
    }

    fun select(index: Int) = _state.update { current ->
        if (current.items.isEmpty()) current else current.copy(selectedIndex = index.coerceIn(0, current.items.lastIndex))
    }

    fun indexOf(songId: String?): Int = if (songId == null) -1 else _state.value.items.indexOfFirst { it.songId == songId }

    /** "No me interesa": never again in this Flow. */
    fun hide(songId: String) {
        hiddenSongIds.add(songId)
    }

    /** "Menos como esto": that artist goes to the back from now on. */
    fun lessLikeThis(item: FlowItem) {
        item.primaryArtistKey?.let(lessLikeArtists::add)
    }

    private fun markSeen(item: FlowItem) {
        seenSongIds.add(item.songId)
        seenTrackKeys.add(item.trackKey)
    }

    private suspend fun fetchAndAppendLocked(): Outcome {
        val active = cursor ?: return Outcome.Failed(FlowError.FAILED)
        val raw = try {
            active.next()
        } catch (e: FlowOfflineException) {
            return Outcome.Failed(FlowError.OFFLINE)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Outcome.Failed(FlowError.FAILED)
        }
        appendLocked(raw)
        return Outcome.Appended
    }

    private fun appendLocked(raw: List<FlowItem>) {
        val filter = signals.filter.copy(
            seenSongIds = seenSongIds,
            seenTrackKeys = seenTrackKeys,
            hiddenSongIds = signals.filter.hiddenSongIds + hiddenSongIds
        )
        val current = _state.value.items
        val room = MAX_FEED_SIZE - current.size
        if (room <= 0) return
        val ranked = rankFlowCandidates(
            candidates = filterFlowCandidates(raw, filter),
            feedTailArtists = current.takeLast(rankingConfig.artistSpacing).map { it.primaryArtistKey },
            likedArtists = signals.likedArtists,
            lessLikeArtists = lessLikeArtists,
            config = rankingConfig
        ).take(room)
        ranked.forEach(::markSeen)
        if (ranked.isNotEmpty()) _state.update { it.copy(items = it.items + ranked, error = null) }
    }

    private suspend fun appendOfflineLocked() {
        val offline = try {
            source.offlineCandidates()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        if (offline.isEmpty()) return
        val before = _state.value.items.size
        appendLocked(offline)
        if (_state.value.items.size > before) _state.update { it.copy(isOffline = true) }
    }

    private sealed interface Outcome {
        data object Appended : Outcome
        data class Failed(val error: FlowError) : Outcome
    }

    companion object {
        const val INITIAL_TARGET = 12
        const val PREFETCH_THRESHOLD = 4
        const val MAX_FETCHES_PER_LOAD = 3
        const val MAX_FEED_SIZE = 300
    }
}
