package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.ui.checklists.ChecklistFlatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistItemSearchTest {
  private fun flat(text: String, completed: Boolean, path: String = "0") =
      ChecklistFlatItem(
          item = ChecklistItem(index = 0, text = text, completed = completed),
          depth = 0,
          apiPath = path,
      )

  @Test
  fun findExactMatch_isCaseInsensitiveAndTrims() {
    val items = listOf(flat("  Milk  ", completed = true, path = "1"))
    assertEquals("1", findExactChecklistItemMatch(items, "milk")?.apiPath)
    assertNull(findExactChecklistItemMatch(items, "eggs"))
  }

  @Test
  fun filterByQuery_prioritizesExactThenCompleted() {
    val items =
        listOf(
            flat("Bread", completed = false, path = "0"),
            flat("Milk", completed = true, path = "1"),
            flat("Almond milk", completed = false, path = "2"),
        )
    val matches = filterChecklistItemsByQuery(items, "milk")
    assertEquals(listOf("1", "2"), matches.map { it.apiPath })
  }

  @Test
  fun resolveAddItemAction_unchecksCompletedDuplicate() {
    val items = listOf(flat("Milk", completed = true, path = "3"))
    val (action, match) = resolveChecklistAddItemAction(items, "Milk")
    assertEquals(ChecklistAddItemAction.UncheckExisting, action)
    assertEquals("3", match?.apiPath)
  }

  @Test
  fun resolveAddItemAction_blocksIncompleteDuplicate() {
    val items = listOf(flat("Milk", completed = false, path = "0"))
    val (action, _) = resolveChecklistAddItemAction(items, "milk")
    assertEquals(ChecklistAddItemAction.AlreadyExists, action)
  }

  @Test
  fun resolveAddItemAction_addsWhenNoMatch() {
    val items = listOf(flat("Milk", completed = true, path = "0"))
    val (action, match) = resolveChecklistAddItemAction(items, "Eggs")
    assertEquals(ChecklistAddItemAction.AddNew, action)
    assertNull(match)
  }
}
