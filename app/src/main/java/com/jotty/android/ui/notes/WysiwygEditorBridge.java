package com.jotty.android.ui.notes;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Notifies Kotlin when the user edits the WYSIWYG body. */
final class WysiwygEditorBridge {
    interface Listener {
        void onContentChanged(String html);

        void onFormatStateChanged(String json);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean acceptChanges = new AtomicBoolean(false);
    private final AtomicBoolean userEdited = new AtomicBoolean(false);
    private final Listener listener;
    private volatile Consumer<String> pendingSnapshot;

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

    boolean wasEditedByUser() {
        return userEdited.get();
    }

    /**
     * Requests editor HTML via [deliverContentSnapshot] (no JSON return from evaluateJavascript).
     * [callback] runs on the main thread.
     */
    void requestContentSnapshot(Consumer<String> callback) {
        pendingSnapshot = callback;
    }

    @JavascriptInterface
    public void deliverContentSnapshot(String html) {
        Consumer<String> callback = pendingSnapshot;
        pendingSnapshot = null;
        if (callback != null) {
            String safe = html != null ? html : "";
            mainHandler.post(() -> callback.accept(safe));
        }
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
