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
          function getFormatState() {
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
              link: document.queryCommandState('createLink')
            });
          }
          var formatStateTimer = null;
          function notifyFormatState() {
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
          function notifyChange() {
            if (suppressNotify) return;
            if (window.AndroidBridge && AndroidBridge.onContentChanged) {
              AndroidBridge.onContentChanged(getContent());
            }
          }
          document.getElementById('editor').addEventListener('input', notifyChange);
          var editor = document.getElementById('editor');
          editor.addEventListener('keyup', scheduleFormatStateNotify);
          editor.addEventListener('mouseup', scheduleFormatStateNotify);
          editor.addEventListener('touchend', scheduleFormatStateNotify);
          document.addEventListener('selectionchange', scheduleFormatStateNotify);
          document.addEventListener('DOMContentLoaded', function() {
            setEditorTheme($backgroundColor, $textColor, $borderColor);
            setContent(INITIAL_CONTENT);
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
