package com.jotty.android.ui.notes;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.util.concurrent.atomic.AtomicBoolean;

/** Notifies Kotlin when the user edits the WYSIWYG body. */
final class WysiwygEditorBridge {
    interface Listener {
        void onContentChanged(String html);

        void onFormatStateChanged(String json);
    }

    private final AtomicBoolean acceptChanges = new AtomicBoolean(false);
    private final AtomicBoolean userEdited = new AtomicBoolean(false);
    private final Listener listener;

    WysiwygEditorBridge(Listener listener) {
        this.listener = listener;
    }

    void attachTo(WebView webView) {
        beginLoad();
        webView.addJavascriptInterface(this, "AndroidBridge");
    }

    void beginLoad() {
        acceptChanges.set(false);
        userEdited.set(false);
    }

    void endLoad() {
        acceptChanges.set(true);
    }

    @JavascriptInterface
    public void onContentChanged(String html) {
        if (!acceptChanges.get()) {
            return;
        }
        if (!userEdited.get() && (html == null || html.isBlank())) {
            return;
        }
        userEdited.set(true);
        listener.onContentChanged(html);
    }

    @JavascriptInterface
    public void onFormatStateChanged(String json) {
        if (!acceptChanges.get()) {
            return;
        }
        if (json != null) {
            listener.onFormatStateChanged(json);
        }
    }
}
