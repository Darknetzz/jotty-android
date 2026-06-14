package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.TaskItemResponse
import com.jotty.android.data.local.FakeJottyApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class KanbanItemFieldsProbeTest {
    @After
    fun tearDown() {
        KanbanItemFieldsProbe.resetForTests()
    }

    @Test
    fun probe_successMarksSupported() =
        runBlocking {
            val api =
                object : FakeJottyApi() {
                    override suspend fun getTaskItem(
                        taskId: String,
                        itemIndex: String,
                    ): TaskItemResponse = TaskItemResponse(ChecklistItem(index = 0, text = "Task"))
                }
            assertTrue(
                KanbanItemFieldsProbe.probeKanbanItemFields(api, "task-1", "0", "instance-a"),
            )
            assertTrue(KanbanItemFieldsProbe.isKanbanItemRichFieldsSupported("instance-a"))
        }

    @Test
    fun probe_404MarksUnsupported() =
        runBlocking {
            val api =
                object : FakeJottyApi() {
                    override suspend fun getTaskItem(
                        taskId: String,
                        itemIndex: String,
                    ): TaskItemResponse = throw httpError(404)
                }
            assertFalse(
                KanbanItemFieldsProbe.probeKanbanItemFields(api, "task-1", "0", "instance-b"),
            )
            assertFalse(KanbanItemFieldsProbe.isKanbanItemRichFieldsSupported("instance-b"))
        }

    @Test
    fun markSupportedFromItems_detectsRichData() {
        KanbanItemFieldsProbe.markSupportedFromItems(
            "instance-c",
            listOf(ChecklistItem(index = 0, text = "Task", priority = "high")),
        )
        assertTrue(KanbanItemFieldsProbe.isKanbanItemRichFieldsSupported("instance-c"))
    }

    private fun httpError(code: Int): HttpException =
        HttpException(
            Response.error<TaskItemResponse>(
                code,
                "{}".toResponseBody(),
            ),
        )
}
