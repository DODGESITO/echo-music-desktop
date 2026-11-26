package echo.music.desktop.explore
import com.music.innertube.YouTube
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
class CommentsProbe {
    @Test fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            val r = YouTube.comments("kJQP7kiw5Fk")
            val (threads, next) = r.getOrNull() ?: (emptyList<com.music.innertube.models.comment.CommentThreadRenderer>() to null)
            println("PROBE comments ok=${r.isSuccess} err=${r.exceptionOrNull()?.message?.take(80)} n=${threads.size} next=${next != null}")
            threads.take(2).forEach { t -> val c = t.comment?.commentRenderer; println("PROBE  ${c?.authorText?.runs?.firstOrNull()?.text} | ${c?.publishedTimeText?.runs?.firstOrNull()?.text} | likes=${c?.voteCount?.runs?.firstOrNull()?.text} | ${c?.contentText?.runs?.joinToString("") { it.text }?.take(60)}") }
            if (next != null) println("PROBE page2 n=${YouTube.commentContinuation(next).getOrNull()?.first?.size}")
        }
    }
}
