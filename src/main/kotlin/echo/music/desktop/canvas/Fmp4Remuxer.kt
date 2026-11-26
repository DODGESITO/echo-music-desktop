package echo.music.desktop.canvas

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Converts a fragmented MP4 (moov + moof/mdat fragments, as used by Apple Music's HLS canvas
 * videos) into a regular MP4 (single moov with a full sample table + one mdat), which JavaFX
 * can play. Copies the samples as they are (no re-encoding). Single-track (video-only) files.
 */
object Fmp4Remuxer {

    private class Box(val type: String, var payload: ByteArray = ByteArray(0), val children: MutableList<Box>? = null) {
        fun child(t: String) = children?.firstOrNull { it.type == t }
    }

    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl", "dinf", "mvex", "edts", "moof", "traf")

    private data class Sample(val offset: Long, val size: Int, val duration: Long, val sync: Boolean, val cto: Int)

    fun remux(input: ByteArray): ByteArray {
        val top = parse(input, 0, input.size, keepOffsets = true)
        val ftyp = top.firstOrNull { it.box.type == "ftyp" }?.box ?: Box("ftyp", ByteArray(0))
        val moov = top.firstOrNull { it.box.type == "moov" }?.box ?: error("not an MP4 (no moov)")
        val trak = moov.child("trak") ?: error("no track")
        val trex = moov.child("mvex")?.child("trex")?.payload?.let(ByteBuffer::wrap)
        val trexDuration = trex?.let { it.getInt(12).toLong() and 0xFFFFFFFFL } ?: 0L
        val trexSize = trex?.getInt(16) ?: 0
        val trexFlags = trex?.getInt(20) ?: 0

        // --- collect samples from every moof/traf ---
        val samples = mutableListOf<Sample>()
        for (entry in top) {
            if (entry.box.type != "moof") continue
            val moofStart = entry.start
            for (traf in entry.box.children.orEmpty().filter { it.type == "traf" }) {
                val tfhd = ByteBuffer.wrap(traf.child("tfhd")?.payload ?: continue)
                val tfFlags = tfhd.getInt(0) and 0xFFFFFF
                var p = 8 // version/flags + track_ID
                var base = moofStart
                if (tfFlags and 0x1 != 0) { base = tfhd.getLong(p); p += 8 }
                if (tfFlags and 0x2 != 0) p += 4
                var defDuration = trexDuration
                var defSize = trexSize
                var defFlags = trexFlags
                if (tfFlags and 0x8 != 0) { defDuration = tfhd.getInt(p).toLong() and 0xFFFFFFFFL; p += 4 }
                if (tfFlags and 0x10 != 0) { defSize = tfhd.getInt(p); p += 4 }
                if (tfFlags and 0x20 != 0) { defFlags = tfhd.getInt(p); p += 4 }

                // A traf can hold several trun boxes (Apple's canvas files use two per fragment).
                for (trunBox in traf.children.orEmpty().filter { it.type == "trun" }) {
                val trun = ByteBuffer.wrap(trunBox.payload)
                val version = trun.get(0).toInt()
                val trFlags = trun.getInt(0) and 0xFFFFFF
                val count = trun.getInt(4)
                var q = 8
                var dataOffset = 0
                if (trFlags and 0x1 != 0) { dataOffset = trun.getInt(q); q += 4 }
                var firstFlags: Int? = null
                if (trFlags and 0x4 != 0) { firstFlags = trun.getInt(q); q += 4 }
                var cursor = base + dataOffset
                for (i in 0 until count) {
                    val dur = if (trFlags and 0x100 != 0) (trun.getInt(q).toLong() and 0xFFFFFFFFL).also { q += 4 } else defDuration
                    val size = if (trFlags and 0x200 != 0) trun.getInt(q).also { q += 4 } else defSize
                    val flags = if (trFlags and 0x400 != 0) trun.getInt(q).also { q += 4 } else if (i == 0 && firstFlags != null) firstFlags else defFlags
                    val cto = if (trFlags and 0x800 != 0) trun.getInt(q).also { q += 4 } else 0
                    // sample_is_non_sync_sample bit = 0x10000
                    val sync = flags and 0x10000 == 0
                    samples += Sample(cursor, size, dur, sync, cto)
                    cursor += size
                }
                }
            }
        }
        require(samples.isNotEmpty()) { "no fragments" }

        // --- new sample data ---
        val mdatData = ByteArrayOutputStream(samples.sumOf { it.size })
        for (s in samples) mdatData.write(input, s.offset.toInt(), s.size)

        // --- rebuild sample table ---
        val mdia = trak.child("mdia") ?: error("no mdia")
        val mdhd = mdia.child("mdhd") ?: error("no mdhd")
        val minf = mdia.child("minf") ?: error("no minf")
        val stbl = minf.child("stbl") ?: error("no stbl")
        val stsd = stbl.child("stsd") ?: error("no stsd")
        val newStbl = mutableListOf(stsd, stts(samples), stss(samples), stsc(samples.size), stsz(samples), stco(0))
        if (samples.any { it.cto != 0 }) newStbl.add(2, ctts(samples))
        stbl.children!!.clear()
        stbl.children.addAll(newStbl)

        // --- durations ---
        val mediaDuration = samples.sumOf { it.duration }
        val mediaTimescale = timescale(mdhd.payload)
        setDuration(mdhd, mediaDuration)
        val mvhd = moov.child("mvhd") ?: error("no mvhd")
        val movieDuration = mediaDuration * timescale(mvhd.payload) / maxOf(1L, mediaTimescale)
        setDuration(mvhd, movieDuration)
        trak.child("tkhd")?.let { setTkhdDuration(it, movieDuration) }
        moov.children!!.removeAll { it.type == "mvex" }
        trak.children!!.removeAll { it.type == "edts" }

        // --- write: ftyp + moov (with chunk offset) + mdat ---
        val ftypBytes = serialize(ftyp)
        // stco offset depends on moov size; stco has a fixed size so serialize once to measure.
        val moovProbe = serialize(moov)
        val dataStart = ftypBytes.size + moovProbe.size + 8L
        stbl.children.replaceAll { if (it.type == "stco") stco(dataStart) else it }
        val moovBytes = serialize(moov)
        check(moovBytes.size == moovProbe.size)

        val out = ByteArrayOutputStream(ftypBytes.size + moovBytes.size + 8 + mdatData.size())
        out.write(ftypBytes)
        out.write(moovBytes)
        out.write(header(8 + mdatData.size(), "mdat"))
        mdatData.writeTo(out)
        return out.toByteArray()
    }

    // ---------------- box parsing / writing ----------------

    private class Entry(val box: Box, val start: Long)

    private fun parse(data: ByteArray, from: Int, to: Int, keepOffsets: Boolean = false): List<Entry> {
        val list = mutableListOf<Entry>()
        var pos = from
        while (pos + 8 <= to) {
            val buf = ByteBuffer.wrap(data, pos, to - pos)
            var size = buf.getInt().toLong() and 0xFFFFFFFFL
            val type = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) { size = ByteBuffer.wrap(data, pos + 8, 8).getLong(); header = 16 }
            if (size == 0L) size = (to - pos).toLong()
            if (size < header || pos + size > to) break
            val bodyStart = pos + header
            val bodyEnd = (pos + size).toInt()
            val box = if (type in CONTAINERS) {
                Box(type, children = parse(data, bodyStart, bodyEnd).map { it.box }.toMutableList())
            } else if (type == "mdat" && keepOffsets) {
                Box(type) // sample data is read straight from the input by offset
            } else {
                Box(type, data.copyOfRange(bodyStart, bodyEnd))
            }
            list += Entry(box, pos.toLong())
            pos = bodyEnd
        }
        return list
    }

    private fun serialize(box: Box): ByteArray {
        val body = if (box.children != null) {
            val out = ByteArrayOutputStream()
            box.children.forEach { out.write(serialize(it)) }
            out.toByteArray()
        } else box.payload
        return header(8 + body.size, box.type) + body
    }

    private fun header(size: Int, type: String) =
        ByteBuffer.allocate(8).putInt(size).put(type.toByteArray(Charsets.ISO_8859_1)).array()

    private fun full(type: String, version: Int, flags: Int, body: ByteBuffer.() -> Unit, capacity: Int): Box {
        val buf = ByteBuffer.allocate(capacity)
        buf.putInt((version shl 24) or flags)
        buf.body()
        return Box(type, buf.array().copyOf(buf.position()))
    }

    private fun stts(samples: List<Sample>): Box {
        val runs = mutableListOf<Pair<Int, Long>>()
        for (s in samples) {
            if (runs.isNotEmpty() && runs.last().second == s.duration) runs[runs.lastIndex] = runs.last().first + 1 to s.duration
            else runs += 1 to s.duration
        }
        return full("stts", 0, 0, { putInt(runs.size); runs.forEach { putInt(it.first); putInt(it.second.toInt()) } }, 8 + runs.size * 8)
    }

    /**
     * Composition offsets. Fragments may use signed offsets (trun v1); JavaFX's demuxer only
     * accepts the classic unsigned form (ctts v0), so everything is shifted to be >= 0 — this
     * delays presentation by at most a frame, invisible in a looping canvas.
     */
    private fun ctts(samples: List<Sample>): Box {
        val shift = -minOf(0, samples.minOf { it.cto })
        val runs = mutableListOf<Pair<Int, Int>>()
        for (s in samples) {
            val cto = s.cto + shift
            if (runs.isNotEmpty() && runs.last().second == cto) runs[runs.lastIndex] = runs.last().first + 1 to cto
            else runs += 1 to cto
        }
        return full("ctts", 0, 0, { putInt(runs.size); runs.forEach { putInt(it.first); putInt(it.second) } }, 8 + runs.size * 8)
    }

    private fun stss(samples: List<Sample>): Box {
        val sync = samples.indices.filter { samples[it].sync }.map { it + 1 }.ifEmpty { listOf(1) }
        return full("stss", 0, 0, { putInt(sync.size); sync.forEach { putInt(it) } }, 8 + sync.size * 4)
    }

    /** All samples in one chunk. */
    private fun stsc(count: Int) = full("stsc", 0, 0, { putInt(1); putInt(1); putInt(count); putInt(1) }, 20)

    private fun stsz(samples: List<Sample>) =
        full("stsz", 0, 0, { putInt(0); putInt(samples.size); samples.forEach { putInt(it.size) } }, 12 + samples.size * 4)

    private fun stco(offset: Long) = full("stco", 0, 0, { putInt(1); putInt(offset.toInt()) }, 12)

    /** mdhd/mvhd: version 0 = 32-bit times, version 1 = 64-bit. */
    private fun timescale(payload: ByteArray): Long {
        val b = ByteBuffer.wrap(payload)
        return if (payload[0].toInt() == 1) b.getInt(20).toLong() else b.getInt(12).toLong() and 0xFFFFFFFFL
    }

    private fun setDuration(box: Box, duration: Long) {
        val b = ByteBuffer.wrap(box.payload)
        if (box.payload[0].toInt() == 1) b.putLong(24, duration) else b.putInt(16, duration.toInt())
    }

    private fun setTkhdDuration(box: Box, duration: Long) {
        val b = ByteBuffer.wrap(box.payload)
        if (box.payload[0].toInt() == 1) b.putLong(28, duration) else b.putInt(20, duration.toInt())
    }
}
