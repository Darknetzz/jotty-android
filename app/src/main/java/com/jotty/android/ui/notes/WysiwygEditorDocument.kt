package com.jotty.android.ui.notes

/**
 * Builds a self-contained HTML document for the note WYSIWYG editor.
 * Content is embedded in the page (not fetched via [JavascriptInterface]) because
 * `getInitialHtml()` is unreliable when the page is loaded from `file://` assets.
 */
internal fun buildWysiwygEditorDocument(
    bodyHtml: String,
    backgroundColor: Int,
    textColor: Int,
    borderColor: Int,
): String {
    val initialContentJs = escapeForJsString(bodyHtml.ifBlank { "<p><br></p>" })
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
        <style>
          html, body {
            height: 100%;
            margin: 0;
            padding: 0;
            overflow: hidden;
          }
          #editor {
            height: 100%;
            min-height: 100%;
            margin: 0;
            padding: 12px 8px 16px;
            outline: none;
            line-height: 1.5;
            font-size: 16px;
            overflow-y: auto;
            -webkit-overflow-scrolling: touch;
            box-sizing: border-box;
            word-wrap: break-word;
          }
          table { border-collapse: collapse; width: 100%; margin: 8px 0; }
          td, th { border: 1px solid var(--border-color, #ccc); padding: 6px; vertical-align: top; }
          img { max-width: 100%; height: auto; }
          h1, h2, h3 { margin: 0.6em 0 0.3em; line-height: 1.25; }
          p { margin: 0.4em 0; }
          code { font-family: monospace; background: rgba(127,127,127,0.15); padding: 0.1em 0.25em; border-radius: 3px; }
          blockquote { margin: 0.4em 0; padding-left: 12px; border-left: 3px solid var(--border-color, #ccc); }
        </style>
        </head>
        <body>
        <div id="editor" contenteditable="true"></div>
        <script>
          var suppressNotify = false;
          var INITIAL_CONTENT = $initialContentJs;

          function argbToCss(argb) {
            var a = ((argb >>> 24) & 0xff) / 255;
            var r = (argb >>> 16) & 0xff;
            var g = (argb >>> 8) & 0xff;
            var b = argb & 0xff;
            if (a >= 0.999) {
              return 'rgb(' + r + ',' + g + ',' + b + ')';
            }
            return 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';
          }

          function setEditorTheme(bgArgb, fgArgb, borderArgb) {
            var bg = argbToCss(bgArgb);
            var fg = argbToCss(fgArgb);
            var border = argbToCss(borderArgb);
            document.body.style.background = bg;
            var editor = document.getElementById('editor');
            editor.style.color = fg;
            editor.style.caretColor = fg;
            document.documentElement.style.setProperty('--border-color', border);
          }

          function cmd(command, value) {
            document.execCommand(command, false, value || null);
            notifyChange();
            scheduleFormatStateNotify();
          }
          function insertLink() {
            if (window.AndroidBridge && AndroidBridge.onInsertLinkRequested) {
              AndroidBridge.onInsertLinkRequested();
            }
          }
          function insertLinkWithUrl(url) {
            if (url) { cmd('createLink', url); }
          }
          function insertImage() {
            if (window.AndroidBridge && AndroidBridge.onInsertImageRequested) {
              AndroidBridge.onInsertImageRequested();
            }
          }
          function insertImageWithUrl(url) {
            if (url) { cmd('insertImage', url); }
          }
          function insertCode() {
            var sel = window.getSelection();
            var text = sel && sel.rangeCount > 0 ? sel.toString() : '';
            if (text) {
              var escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
              cmd('insertHTML', '<code>' + escaped + '</code>');
            } else {
              cmd('insertHTML', '<code></code>');
            }
          }
          function insertTable(rows, cols) {
            rows = Math.max(1, rows || 2);
            cols = Math.max(1, cols || 2);
            var html = '<table><thead><tr>';
            for (var c = 0; c < cols; c++) {
              html += '<th>Header</th>';
            }
            html += '</tr></thead><tbody>';
            for (var r = 0; r < rows - 1; r++) {
              html += '<tr>';
              for (var c2 = 0; c2 < cols; c2++) {
                html += '<td>Cell</td>';
              }
              html += '</tr>';
            }
            html += '</tbody></table><p></p>';
            cmd('insertHTML', html);
          }
          function insertTaskList() {
            cmd('insertHTML', '<ul style="list-style-type:none;padding-left:0;"><li><input type="checkbox" disabled> Task</li></ul><p></p>');
          }
          function undoEdit() { document.execCommand('undo', false, null); notifyChange(); scheduleFormatStateNotify(); }
          function redoEdit() { document.execCommand('redo', false, null); notifyChange(); scheduleFormatStateNotify(); }
          function headingLevel() {
            var block = getParentBlock();
            if (!block) return 0;
            var tag = block.tagName;
            if (tag === 'H1') return 1;
            if (tag === 'H2') return 2;
            if (tag === 'H3') return 3;
            return 0;
          }
          function setContent(html) {
            suppressNotify = true;
            document.getElementById('editor').innerHTML = html || '';
            suppressNotify = false;
          }
          function getContent() {
            return document.getElementById('editor').innerHTML;
          }
          function getParentBlock() {
            var sel = window.getSelection();
            if (!sel || sel.rangeCount === 0) return null;
            var node = sel.anchorNode;
            if (node && node.nodeType === 3) node = node.parentNode;
            while (node && node.id !== 'editor') {
              var tag = node.tagName;
              if (tag && /^(P|DIV|H[1-6]|BLOCKQUOTE|LI|TD|TH)$/.test(tag)) return node;
              node = node.parentNode;
            }
            return null;
          }
          function isHeadingBlock() {
            var block = getParentBlock();
            if (!block) return false;
            var tag = block.tagName;
            return tag === 'H1' || tag === 'H2' || tag === 'H3';
          }
          function isBlockquote() {
            var block = getParentBlock();
            return !!(block && block.tagName === 'BLOCKQUOTE');
          }
          function isInCode() {
            var sel = window.getSelection();
            if (!sel || sel.rangeCount === 0) return false;
            var node = sel.anchorNode;
            while (node) {
              if (node.nodeType === 1 && node.tagName === 'CODE') return true;
              node = node.parentNode;
            }
            return false;
          }
          var lastKnownInTable = false;
          var lastTablePosition = null;

          function clearTableUiState() {
            lastKnownInTable = false;
            lastTablePosition = null;
          }

          function cacheTablePosition(ctx) {
            if (!ctx) return;
            lastKnownInTable = true;
            lastTablePosition = {
              table: ctx.table,
              rowIndex: ctx.rowIndex,
              cellIndex: ctx.cellIndex
            };
          }

          function resolveTableContextFromCache() {
            if (!lastTablePosition || !lastTablePosition.table) return null;
            var table = lastTablePosition.table;
            if (!table.parentNode) {
              clearTableUiState();
              return null;
            }
            var allRows = Array.prototype.slice.call(table.querySelectorAll('tr'));
            var row = allRows[lastTablePosition.rowIndex];
            if (!row) return null;
            var cells = Array.prototype.slice.call(row.children).filter(function(c) {
              return c.tagName === 'TD' || c.tagName === 'TH';
            });
            var cell = cells[lastTablePosition.cellIndex];
            if (!cell) return null;
            var colCount = 0;
            allRows.forEach(function(r) {
              var count = r.querySelectorAll('td, th').length;
              if (count > colCount) colCount = count;
            });
            return {
              cell: cell,
              row: row,
              table: table,
              cellIndex: lastTablePosition.cellIndex,
              rowIndex: lastTablePosition.rowIndex,
              rowCount: allRows.length,
              colCount: colCount
            };
          }

          function getTableCell() {
            var editorEl = document.getElementById('editor');
            var sel = window.getSelection();
            if (sel && sel.rangeCount > 0) {
              var node = sel.anchorNode;
              if (node && node.nodeType === 3) node = node.parentNode;
              while (node && node.id !== 'editor') {
                if (node.nodeType === 1 && (node.tagName === 'TD' || node.tagName === 'TH')) return node;
                node = node.parentNode;
              }
            }
            var active = document.activeElement;
            if (active && active !== editorEl) {
              var activeNode = active;
              while (activeNode && activeNode.id !== 'editor') {
                if (activeNode.nodeType === 1 && (activeNode.tagName === 'TD' || activeNode.tagName === 'TH')) return activeNode;
                activeNode = activeNode.parentNode;
              }
            }
            return null;
          }
          function getTableRowFromCell(cell) {
            if (!cell) return null;
            var node = cell;
            while (node && node.id !== 'editor') {
              if (node.nodeType === 1 && node.tagName === 'TR') return node;
              node = node.parentNode;
            }
            return null;
          }
          function getTableContext() {
            var cell = getTableCell();
            if (!cell) {
              if (lastKnownInTable) return resolveTableContextFromCache();
              if (editorHasTable()) {
                seedTableUiFromContent();
                return resolveTableContextFromCache();
              }
              return null;
            }
            var row = getTableRowFromCell(cell);
            if (!row) return null;
            var table = row;
            while (table && table.tagName !== 'TABLE') table = table.parentNode;
            if (!table || table.id === 'editor') return null;
            var cells = Array.prototype.slice.call(row.children).filter(function(c) {
              return c.tagName === 'TD' || c.tagName === 'TH';
            });
            var cellIndex = cells.indexOf(cell);
            var allRows = Array.prototype.slice.call(table.querySelectorAll('tr'));
            var rowIndex = allRows.indexOf(row);
            var colCount = 0;
            allRows.forEach(function(r) {
              var count = r.querySelectorAll('td, th').length;
              if (count > colCount) colCount = count;
            });
            var ctx = { cell: cell, row: row, table: table, cellIndex: cellIndex, rowIndex: rowIndex, rowCount: allRows.length, colCount: colCount };
            cacheTablePosition(ctx);
            return ctx;
          }
          function focusElement(node, atStart) {
            if (!node) return;
            if (node.focus) node.focus();
            var range = document.createRange();
            range.selectNodeContents(node);
            range.collapse(!!atStart);
            var sel = window.getSelection();
            if (sel) {
              sel.removeAllRanges();
              sel.addRange(range);
            }
          }
          function focusCell(cell) {
            focusElement(cell, false);
          }
          function createTableCell(tagName) {
            var cell = document.createElement(tagName || 'td');
            cell.innerHTML = '<br>';
            return cell;
          }
          function cloneTableRow(row) {
            var newRow = row.cloneNode(true);
            var cells = newRow.querySelectorAll('td, th');
            for (var i = 0; i < cells.length; i++) {
              cells[i].innerHTML = '<br>';
            }
            return newRow;
          }
          function addTableRow(below) {
            var ctx = getTableContext();
            if (!ctx) return;
            var newRow = cloneTableRow(ctx.row);
            if (below) {
              ctx.row.parentNode.insertBefore(newRow, ctx.row.nextSibling);
            } else {
              ctx.row.parentNode.insertBefore(newRow, ctx.row);
            }
            focusCell(newRow.children[Math.min(ctx.cellIndex, newRow.children.length - 1)]);
            notifyChange();
            scheduleFormatStateNotify();
          }
          function addTableColumn(after) {
            var ctx = getTableContext();
            if (!ctx) return;
            var allRows = ctx.table.querySelectorAll('tr');
            for (var i = 0; i < allRows.length; i++) {
              var row = allRows[i];
              var cells = Array.prototype.slice.call(row.children).filter(function(c) {
                return c.tagName === 'TD' || c.tagName === 'TH';
              });
              var tag = cells.length > 0 ? cells[0].tagName.toLowerCase() : 'td';
              var newCell = createTableCell(tag);
              var insertAt = after ? ctx.cellIndex + 1 : ctx.cellIndex;
              if (insertAt >= cells.length) {
                row.appendChild(newCell);
              } else {
                row.insertBefore(newCell, cells[insertAt]);
              }
            }
            var updatedRow = ctx.table.querySelectorAll('tr')[ctx.rowIndex];
            if (updatedRow) {
              var updatedCells = updatedRow.querySelectorAll('td, th');
              var focusIndex = after ? Math.min(ctx.cellIndex + 1, updatedCells.length - 1) : ctx.cellIndex;
              if (updatedCells.length > 0) focusCell(updatedCells[focusIndex]);
            }
            notifyChange();
            scheduleFormatStateNotify();
          }
          function deleteTableRow() {
            var ctx = getTableContext();
            if (!ctx || ctx.rowCount <= 1) return;
            var focusRow = ctx.row.nextElementSibling || ctx.row.previousElementSibling;
            ctx.row.parentNode.removeChild(ctx.row);
            if (focusRow) {
              var targetCell = focusRow.querySelector('td, th');
              if (targetCell) focusCell(targetCell);
            }
            notifyChange();
            scheduleFormatStateNotify();
          }
          function deleteTableColumn() {
            var ctx = getTableContext();
            if (!ctx || ctx.colCount <= 1) return;
            var allRows = ctx.table.querySelectorAll('tr');
            var focusIndex = Math.max(0, ctx.cellIndex > 0 ? ctx.cellIndex - 1 : 0);
            for (var i = 0; i < allRows.length; i++) {
              var cells = Array.prototype.slice.call(allRows[i].children).filter(function(c) {
                return c.tagName === 'TD' || c.tagName === 'TH';
              });
              if (cells.length > ctx.cellIndex) {
                cells[ctx.cellIndex].parentNode.removeChild(cells[ctx.cellIndex]);
              }
            }
            var focusRow = allRows[Math.min(ctx.rowIndex, allRows.length - 1)];
            if (focusRow) {
              var cellsAfter = focusRow.querySelectorAll('td, th');
              if (cellsAfter.length > 0) focusCell(cellsAfter[Math.min(focusIndex, cellsAfter.length - 1)]);
            }
            notifyChange();
            scheduleFormatStateNotify();
          }
          function exitTable() {
            var ctx = getTableContext();
            if (!ctx) return;
            var table = ctx.table;
            var next = table.nextSibling;
            var paragraph = null;
            if (next && next.nodeType === 1 && next.tagName === 'P') {
              paragraph = next;
            } else {
              paragraph = document.createElement('p');
              paragraph.innerHTML = '<br>';
              if (next) {
                table.parentNode.insertBefore(paragraph, next);
              } else {
                table.parentNode.appendChild(paragraph);
              }
            }
            clearTableUiState();
            focusElement(paragraph, true);
            notifyChange();
            scheduleFormatStateNotify();
          }
          function isInTable() {
            return !!getTableCell();
          }
          function editorHasTable() {
            var editorEl = document.getElementById('editor');
            return !!(editorEl && editorEl.querySelector('table'));
          }
          function isInTableForToolbar() {
            if (isInTable()) return true;
            if (lastKnownInTable) return true;
            return editorHasTable();
          }
          function getTableDimensions() {
            var ctx = getTableContext();
            if (ctx) return { rows: ctx.rowCount, cols: ctx.colCount };
            var editorEl = document.getElementById('editor');
            var table = editorEl ? editorEl.querySelector('table') : null;
            if (!table) return { rows: 0, cols: 0 };
            var allRows = table.querySelectorAll('tr');
            var colCount = 0;
            for (var i = 0; i < allRows.length; i++) {
              var count = allRows[i].querySelectorAll('td, th').length;
              if (count > colCount) colCount = count;
            }
            return { rows: allRows.length, cols: colCount };
          }
          function updateTableUiCacheFromSelection() {
            var liveCell = getTableCell();
            if (liveCell) {
              var row = getTableRowFromCell(liveCell);
              if (row) {
                var table = row;
                while (table && table.tagName !== 'TABLE') table = table.parentNode;
                if (table && table.id !== 'editor') {
                  var cells = Array.prototype.slice.call(row.children).filter(function(c) {
                    return c.tagName === 'TD' || c.tagName === 'TH';
                  });
                  var allRows = Array.prototype.slice.call(table.querySelectorAll('tr'));
                  cacheTablePosition({
                    table: table,
                    rowIndex: allRows.indexOf(row),
                    cellIndex: cells.indexOf(liveCell)
                  });
                }
              }
              return;
            }
            if (!lastKnownInTable) return;
            var sel = window.getSelection();
            if (!sel || sel.rangeCount === 0) return;
            var node = sel.anchorNode;
            if (node && node.nodeType === 3) node = node.parentNode;
            while (node) {
              if (node.id === 'editor') {
                clearTableUiState();
                return;
              }
              if (node.nodeType === 1 && node.tagName === 'TABLE') return;
              node = node.parentNode;
            }
          }
          function getAllTableCells() {
            var ctx = getTableContext();
            if (!ctx) return [];
            var cells = [];
            var allRows = ctx.table.querySelectorAll('tr');
            for (var i = 0; i < allRows.length; i++) {
              var rowCells = allRows[i].querySelectorAll('td, th');
              for (var j = 0; j < rowCells.length; j++) {
                cells.push(rowCells[j]);
              }
            }
            return cells;
          }
          function moveTableCellFocus(forward) {
            var ctx = getTableContext();
            if (!ctx) return false;
            var cells = getAllTableCells();
            var currentIndex = cells.indexOf(ctx.cell);
            if (currentIndex < 0) return false;
            if (forward) {
              if (currentIndex < cells.length - 1) {
                focusCell(cells[currentIndex + 1]);
                return true;
              }
              addTableRow(true);
              return true;
            }
            if (currentIndex > 0) {
              focusCell(cells[currentIndex - 1]);
              return true;
            }
            return false;
          }
          function isCaretAtEndOfCell(cell) {
            var sel = window.getSelection();
            if (!sel || sel.rangeCount === 0 || !cell) return false;
            var range = sel.getRangeAt(0);
            if (!cell.contains(range.endContainer)) return false;
            var testRange = document.createRange();
            testRange.selectNodeContents(cell);
            testRange.setStart(range.endContainer, range.endOffset);
            return testRange.toString().length === 0;
          }
          function isOnLastTableRow() {
            var ctx = getTableContext();
            return !!(ctx && ctx.rowIndex === ctx.rowCount - 1);
          }
          function handleTableKeydown(event) {
            if (!isInTable()) return;
            var key = event.key;
            if (key === 'Tab') {
              event.preventDefault();
              moveTableCellFocus(!event.shiftKey);
              return;
            }
            if (key === 'Enter' && !event.shiftKey) {
              var ctx = getTableContext();
              if (ctx && ctx.rowIndex === ctx.rowCount - 1 && isCaretAtEndOfCell(ctx.cell)) {
                event.preventDefault();
                exitTable();
              }
              return;
            }
            if (key === 'ArrowDown') {
              var ctxDown = getTableContext();
              if (ctxDown && ctxDown.rowIndex === ctxDown.rowCount - 1) {
                var table = ctxDown.table;
                var next = table.nextSibling;
                if (!next || (next.nodeType === 1 && next.tagName !== 'P')) {
                  event.preventDefault();
                  exitTable();
                }
              }
            }
          }
          function getFormatState() {
            var dims = getTableDimensions();
            return JSON.stringify({
              bold: document.queryCommandState('bold'),
              italic: document.queryCommandState('italic'),
              underline: document.queryCommandState('underline'),
              strikeThrough: document.queryCommandState('strikeThrough'),
              unorderedList: document.queryCommandState('insertUnorderedList'),
              orderedList: document.queryCommandState('insertOrderedList'),
              heading: isHeadingBlock(),
              heading1: headingLevel() === 1,
              heading2: headingLevel() === 2,
              heading3: headingLevel() === 3,
              blockquote: isBlockquote(),
              code: isInCode(),
              link: document.queryCommandState('createLink'),
              inTable: isInTableForToolbar(),
              tableRows: dims.rows,
              tableCols: dims.cols
            });
          }
          var formatStateTimer = null;
          function notifyFormatState() {
            updateTableUiCacheFromSelection();
            if (window.AndroidBridge && AndroidBridge.onFormatStateChanged) {
              AndroidBridge.onFormatStateChanged(getFormatState());
            }
          }
          function scheduleFormatStateNotify() {
            if (formatStateTimer) clearTimeout(formatStateTimer);
            formatStateTimer = setTimeout(function() {
              formatStateTimer = null;
              notifyFormatState();
            }, 50);
          }
          function scheduleFormatStateNotifySoon() {
            scheduleFormatStateNotify();
            setTimeout(scheduleFormatStateNotify, 120);
          }
          function notifyChange() {
            if (suppressNotify) return;
            if (window.AndroidBridge && AndroidBridge.onContentChanged) {
              AndroidBridge.onContentChanged(getContent());
            }
          }
          document.getElementById('editor').addEventListener('input', notifyChange);
          var editor = document.getElementById('editor');
          editor.addEventListener('keyup', scheduleFormatStateNotify);
          editor.addEventListener('mouseup', scheduleFormatStateNotifySoon);
          editor.addEventListener('touchend', scheduleFormatStateNotifySoon);
          editor.addEventListener('click', scheduleFormatStateNotifySoon);
          editor.addEventListener('focusin', scheduleFormatStateNotifySoon);
          editor.addEventListener('keydown', handleTableKeydown);
          document.addEventListener('selectionchange', scheduleFormatStateNotify);
          function seedTableUiFromContent() {
            var table = editor.querySelector('table');
            if (!table) return;
            var firstCell = table.querySelector('td, th');
            if (!firstCell) return;
            var row = getTableRowFromCell(firstCell);
            if (!row) return;
            var allRows = table.querySelectorAll('tr');
            var cells = Array.prototype.slice.call(row.children).filter(function(c) {
              return c.tagName === 'TD' || c.tagName === 'TH';
            });
            cacheTablePosition({
              table: table,
              rowIndex: Array.prototype.indexOf.call(allRows, row),
              cellIndex: cells.indexOf(firstCell)
            });
          }
          document.addEventListener('DOMContentLoaded', function() {
            setEditorTheme($backgroundColor, $textColor, $borderColor);
            setContent(INITIAL_CONTENT);
            seedTableUiFromContent();
            scheduleFormatStateNotify();
          });
        </script>
        </body>
        </html>
        """.trimIndent()
}

/** JSON-style string literal for embedding HTML in a `<script>` block. */
internal fun escapeForJsString(value: String): String {
    val safe = value.replace("</script>", "<\\/script>", ignoreCase = true)
    return buildString(safe.length + 2) {
        append('"')
        for (ch in safe) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
