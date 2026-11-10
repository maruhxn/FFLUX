package com.maruhxn.fflux.comment

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 다시보기 댓글: 작성 조건(ENDED + READY 녹화), 작성자만 수정·삭제, 삭제된 댓글의 가림, 목록 커서. */
class CommentTest : BroadcastFlowTest() {

    // 작성

    @Test
    fun `로그인 회원이 다시보기에 쓴 댓글은 작성자 닉네임과 함께 목록에 보인다`() {
        val id = replay()

        assertEquals(HttpStatus.CREATED, write(id, other, "  좋은 방송  ").statusCode)

        val page = comments(id, null).body!!
        assertEquals(1, (page["count"] as Number).toInt())
        val comment = items(page).single()
        assertEquals("좋은 방송", comment["content"])
        assertEquals("테스트시청자", comment["authorNickname"])
        assertEquals(false, comment["edited"])
        assertEquals(false, comment["deleted"])
    }

    @Test
    fun `비로그인은 목록을 볼 수 있고 작성은 401`() {
        val id = replay()

        assertEquals(HttpStatus.OK, comments(id, null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, write(id, null, "댓글").statusCode)
    }

    @Test
    fun `스트리머도 자기 다시보기에 댓글을 쓸 수 있다`() {
        assertEquals(HttpStatus.CREATED, write(replay(), streamer, "봐 주셔서 고마워요").statusCode)
    }

    @Test
    fun `빈 본문과 500자를 넘는 본문은 content 칸의 400`() {
        val id = replay()

        listOf("   ", "가".repeat(501)).forEach { content ->
            val response = write(id, other, content)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(setOf("content"), (response.body!!["errors"] as Map<*, *>).keys)
        }
    }

    @Test
    fun `다시보기가 아닌 방송에 쓰면 409`() {
        val ready = register(streamer)
        val endedWithoutRecording = goLive(register(streamer)).also { unavailable("live/$it") }
        val processing = endRecorded(register(streamer)).also { processing(it) }
        val failed = endRecorded(register(streamer)).also { failed(it) }
        // 스트리머는 한 번에 한 방송만 송출하므로 LIVE 방송은 마지막에 만든다.
        val live = goLive(register(streamer))

        listOf(ready, live, endedWithoutRecording, processing, failed).forEach {
            assertEquals(HttpStatus.CONFLICT, write(it, other, "댓글").statusCode, "방송 $it")
        }
    }

    @Test
    fun `없거나 삭제된 방송에 쓰면 404`() {
        val deleted = replay().also { assertEquals(HttpStatus.NO_CONTENT, delete(it, streamer).statusCode) }

        assertEquals(HttpStatus.NOT_FOUND, write(deleted, other, "댓글").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, write(9999, other, "댓글").statusCode)
    }

    // 수정·삭제

    @Test
    fun `작성자가 고치면 본문이 바뀌고 수정됨이 표시된다`() {
        val id = replay()
        val commentId = writeId(id, other, "처음")

        assertEquals(HttpStatus.NO_CONTENT, edit(commentId, other, "고친 내용").statusCode)

        val comment = items(comments(id, null).body!!).single()
        assertEquals("고친 내용", comment["content"])
        assertEquals(true, comment["edited"])
    }

    @Test
    fun `댓글 수정과 삭제는 비로그인 401, 다른 회원과 스트리머 403`() {
        val commentId = writeId(replay(), other, "시청자 댓글")

        assertEquals(HttpStatus.UNAUTHORIZED, edit(commentId, null, "x").statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, deleteComment(commentId, null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, edit(commentId, streamer, "x").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, deleteComment(commentId, streamer).statusCode)
    }

    @Test
    fun `남의 댓글은 작성자도 스트리머도 아닌 회원이 고치거나 지우면 403`() {
        val commentId = writeId(replay(), streamer, "스트리머 댓글")

        assertEquals(HttpStatus.FORBIDDEN, edit(commentId, other, "x").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, deleteComment(commentId, other).statusCode)
    }

    @Test
    fun `지운 댓글은 자리만 남고 본문과 작성자를 가리며 댓글 수에서 빠진다`() {
        val id = replay()
        writeId(id, streamer, "남는 댓글")
        val commentId = writeId(id, other, "지울 댓글")

        assertEquals(HttpStatus.NO_CONTENT, deleteComment(commentId, other).statusCode)

        val page = comments(id, null).body!!
        assertEquals(1, (page["count"] as Number).toInt())
        val deleted = items(page).first()
        assertEquals((commentId as Number).toLong(), (deleted["id"] as Number).toLong())
        assertEquals(true, deleted["deleted"])
        assertNull(deleted["content"])
        assertNull(deleted["authorNickname"])
        assertNull(deleted["authorId"])
    }

    @Test
    fun `지운 댓글의 수정과 재삭제는 작성자면 409, 다른 회원이면 403`() {
        val commentId = writeId(replay(), other, "지울 댓글")
        deleteComment(commentId, other)

        assertEquals(HttpStatus.CONFLICT, edit(commentId, other, "x").statusCode)
        assertEquals(HttpStatus.CONFLICT, deleteComment(commentId, other).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, edit(commentId, streamer, "x").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, deleteComment(commentId, streamer).statusCode)
    }

    @Test
    fun `수정 본문이 규칙에 맞지 않으면 작성자 확인보다 먼저 400`() {
        val commentId = writeId(replay(), other, "댓글")

        assertEquals(HttpStatus.BAD_REQUEST, edit(commentId, streamer, " ").statusCode)
    }

    @Test
    fun `없는 댓글의 수정과 삭제는 404`() {
        assertEquals(HttpStatus.NOT_FOUND, edit(9999, other, "x").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, deleteComment(9999, other).statusCode)
    }

    // 목록

    @Test
    fun `목록은 최신순 20개씩이고 nextCursor로 이어 읽는다`() {
        val id = replay()
        val ids = (1..21).map { writeId(id, other, "댓글 $it") }

        val first = comments(id, null).body!!
        assertEquals(ids.reversed().take(20), items(first).map { it["id"] })
        assertEquals(21, (first["count"] as Number).toInt())

        val second = comments(id, (first["nextCursor"] as Number).toLong()).body!!
        assertEquals(listOf(ids.first()), items(second).map { it["id"] })
        assertNull(second["nextCursor"])
    }

    @Test
    fun `방송을 삭제하면 댓글이 목록에서 사라지고 고칠 수 없다`() {
        val id = replay()
        val commentId = writeId(id, other, "댓글")

        assertEquals(HttpStatus.NO_CONTENT, delete(id, streamer).statusCode)

        val page = comments(id, null).body!!
        assertEquals(emptyList<Any>(), page["items"])
        assertEquals(0, (page["count"] as Number).toInt())
        assertEquals(HttpStatus.NOT_FOUND, edit(commentId, other, "x").statusCode)
    }

    // 답글

    @Test
    fun `답글은 원댓글 목록에서 빠지고 원댓글의 답글 수와 댓글 수에 들어간다`() {
        val id = replay()
        val parentId = writeId(id, streamer, "원댓글")

        assertEquals(HttpStatus.CREATED, reply(parentId, other, "  답글  ").statusCode)

        val page = comments(id, null).body!!
        assertEquals(2, (page["count"] as Number).toInt())
        val parent = items(page).single()
        assertEquals(parentId, parent["id"])
        assertEquals(1, (parent["replyCount"] as Number).toInt())
        val answer = items(replies(parentId, null).body!!).single()
        assertEquals("답글", answer["content"])
        assertEquals("테스트시청자", answer["authorNickname"])
    }

    @Test
    fun `답글은 비로그인 401, 회원과 스트리머는 쓸 수 있고 본문 규칙은 댓글과 같다`() {
        val parentId = writeId(replay(), other, "원댓글")

        assertEquals(HttpStatus.UNAUTHORIZED, reply(parentId, null, "답글").statusCode)
        assertEquals(HttpStatus.CREATED, reply(parentId, streamer, "고마워요").statusCode)
        assertEquals(HttpStatus.CREATED, reply(parentId, other, "저도요").statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, reply(parentId, other, " ").statusCode)
    }

    @Test
    fun `답글에 답글을 달면 409`() {
        val replyId = replyId(writeId(replay(), other, "원댓글"), streamer, "답글")

        assertEquals(HttpStatus.CONFLICT, reply(replyId, other, "답글의 답글").statusCode)
    }

    @Test
    fun `없거나 방송이 삭제된 원댓글에 답글을 달면 404`() {
        val id = replay()
        val parentId = writeId(id, other, "원댓글")
        assertEquals(HttpStatus.NO_CONTENT, delete(id, streamer).statusCode)

        assertEquals(HttpStatus.NOT_FOUND, reply(parentId, other, "답글").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, reply(9999, other, "답글").statusCode)
    }

    @Test
    fun `답글 목록은 비로그인도 보고 오래된순 20개씩 nextCursor로 이어 읽는다`() {
        val parentId = writeId(replay(), other, "원댓글")
        val ids = (1..21).map { replyId(parentId, streamer, "답글 $it") }

        val first = replies(parentId, null).body!!
        assertEquals(ids.take(20), items(first).map { it["id"] })

        val second = replies(parentId, (first["nextCursor"] as Number).toLong()).body!!
        assertEquals(listOf(ids.last()), items(second).map { it["id"] })
        assertNull(second["nextCursor"])
    }

    @Test
    fun `답글 수정과 삭제는 작성자만 하고 비로그인 401, 스트리머와 다른 회원 403`() {
        val parentId = writeId(replay(), streamer, "원댓글")
        val viewerReply = replyId(parentId, other, "시청자 답글")
        val streamerReply = replyId(parentId, streamer, "스트리머 답글")

        assertEquals(HttpStatus.UNAUTHORIZED, edit(viewerReply, null, "x").statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, deleteComment(viewerReply, null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, edit(viewerReply, streamer, "x").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, deleteComment(viewerReply, streamer).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, edit(streamerReply, other, "x").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, deleteComment(streamerReply, other).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, edit(viewerReply, other, "고친 답글").statusCode)
        assertEquals(HttpStatus.NO_CONTENT, deleteComment(streamerReply, streamer).statusCode)
    }

    @Test
    fun `지운 답글은 자리만 남고 답글 수와 댓글 수에서 빠지며 다시 고치거나 지우면 작성자 409, 다른 회원 403`() {
        val id = replay()
        val parentId = writeId(id, streamer, "원댓글")
        val kept = replyId(parentId, other, "남는 답글")
        val removed = replyId(parentId, other, "지울 답글")
        edit(kept, other, "고친 답글")

        assertEquals(HttpStatus.NO_CONTENT, deleteComment(removed, other).statusCode)

        val page = comments(id, null).body!!
        assertEquals(2, (page["count"] as Number).toInt())
        assertEquals(1, (items(page).single()["replyCount"] as Number).toInt())
        val (first, second) = items(replies(parentId, null).body!!)
        assertEquals(true, first["edited"])
        assertEquals(true, second["deleted"])
        assertNull(second["content"])
        assertNull(second["authorNickname"])
        assertEquals(HttpStatus.CONFLICT, edit(removed, other, "x").statusCode)
        assertEquals(HttpStatus.CONFLICT, deleteComment(removed, other).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, edit(removed, streamer, "x").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, deleteComment(removed, streamer).statusCode)
    }

    @Test
    fun `원댓글을 지워도 답글은 남고 지운 원댓글에 새 답글을 달면 409`() {
        val parentId = writeId(replay(), streamer, "원댓글")
        replyId(parentId, other, "먼저 단 답글")

        assertEquals(HttpStatus.NO_CONTENT, deleteComment(parentId, streamer).statusCode)

        assertEquals(listOf("먼저 단 답글"), items(replies(parentId, null).body!!).map { it["content"] })
        assertEquals(HttpStatus.CONFLICT, reply(parentId, other, "늦은 답글").statusCode)
    }

    @Test
    fun `방송을 삭제하면 답글도 사라지고 고칠 수 없다`() {
        val id = replay()
        val parentId = writeId(id, streamer, "원댓글")
        val replyId = replyId(parentId, other, "답글")

        assertEquals(HttpStatus.NO_CONTENT, delete(id, streamer).statusCode)

        assertEquals(emptyList<Any>(), replies(parentId, null).body!!["items"])
        assertEquals(HttpStatus.NOT_FOUND, edit(replyId, other, "x").statusCode)
    }

    // 헬퍼

    /** 녹화를 켜고 송출한 뒤 끝나 녹화가 READY가 된 방송(다시보기). */
    private fun replay(): Long = endRecorded(register(streamer)).also { ready(it) }

    private fun endRecorded(id: Long): Long {
        val token = tokenOf(issue(id, streamer, record = true))
        assertEquals(HttpStatus.OK, auth("publish", "rec/$id", token).statusCode)
        available("rec/$id")
        unavailable("rec/$id")
        return id
    }

    private fun processing(id: Long) = notify("processing", id)

    private fun failed(id: Long) = notify("failed", id)

    private fun ready(id: Long) = notify("ready", id, filePath = "rec/$id/$SEGMENT")

    private fun notify(event: String, id: Long, filePath: String? = null) {
        val response = http.post().uri("/internal/mediamtx/recordings/{event}", event)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                buildMap {
                    put("path", "rec/$id")
                    put("segmentPath", "/recordings/rec/$id/$SEGMENT")
                    filePath?.let { put("filePath", it) }
                },
            )
            .retrieve().toBodilessEntity()
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Suppress("UNCHECKED_CAST")
    private fun write(broadcastId: Long, cookie: String?, content: String): ResponseEntity<Map<String, Any?>> =
        http.post().uri("/api/broadcasts/{id}/comments", broadcastId).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("content" to content))
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    /** 댓글을 쓰고 ID를 돌려준다. 목록 응답의 `id`와 비교할 수 있게 JSON 숫자 그대로 둔다. */
    private fun writeId(broadcastId: Long, cookie: String, content: String): Any {
        val response = write(broadcastId, cookie, content)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!["id"]!!
    }

    private fun edit(commentId: Any, cookie: String?, content: String): ResponseEntity<Void> =
        http.patch().uri("/api/comments/{id}", commentId).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("content" to content))
            .retrieve().toBodilessEntity()

    private fun deleteComment(commentId: Any, cookie: String?): ResponseEntity<Void> =
        http.delete().uri("/api/comments/{id}", commentId).withCookie(cookie).retrieve().toBodilessEntity()

    @Suppress("UNCHECKED_CAST")
    private fun comments(broadcastId: Long, cursor: Long?): ResponseEntity<Map<String, Any?>> =
        http.get().uri { it.path("/api/broadcasts/{id}/comments").queryParamIfPresent("cursor", java.util.Optional.ofNullable(cursor)).build(broadcastId) }
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun reply(parentId: Any, cookie: String?, content: String): ResponseEntity<Map<String, Any?>> =
        http.post().uri("/api/comments/{id}/replies", parentId).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("content" to content))
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    private fun replyId(parentId: Any, cookie: String, content: String): Any {
        val response = reply(parentId, cookie, content)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!["id"]!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun replies(parentId: Any, cursor: Long?): ResponseEntity<Map<String, Any?>> =
        http.get().uri { it.path("/api/comments/{id}/replies").queryParamIfPresent("cursor", java.util.Optional.ofNullable(cursor)).build(parentId) }
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun items(page: Map<String, Any?>) = page["items"] as List<Map<String, Any?>>

    companion object {
        private const val SEGMENT = "2026-11-10_21-00-00-000000.mp4"
    }
}
