package app.sotto.voice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;

/**
 * Reads the focused editable field and inserts dictated text.
 *
 * The service is intentionally narrow: it only looks at the focused input node,
 * never stores screen content, and ignores password fields.
 */
public final class SottoAccessibilityService extends AccessibilityService {

    private static volatile SottoAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String focusedPackage = "";
    private String lastBefore = "";
    private int lastSelStart;
    private int lastSelEnd;
    private boolean lastWasInsertion;

    public static SottoAccessibilityService get() {
        return instance;
    }

    public static boolean connected() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED
                    | AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) focusedPackage = pkg.toString();
        }
        // Only walk the tree on events that can change what "focused" means.
        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityNodeInfo node = findEditable();
            if (node != null) {
                CharSequence pkg = node.getPackageName();
                if (pkg != null) focusedPackage = pkg.toString();
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public String focusedPackage() {
        return focusedPackage == null ? "" : focusedPackage;
    }

    public boolean hasField() {
        return findEditable() != null;
    }

    private AccessibilityNodeInfo findEditable() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (isUsable(node)) return node;
        node = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (isUsable(node)) return node;
        return search(root, 0);
    }

    private AccessibilityNodeInfo search(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 12) return null;
        if (isUsable(node)) return node;
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = search(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean isUsable(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (!node.isEditable()) return false;
        if (node.isPassword()) return false;
        return node.isVisibleToUser();
    }

    private CharSequence nodeText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        CharSequence text = node.getText();
        if (text == null) {
            CharSequence hint = node.getHintText();
            return hint == null ? "" : hint;
        }
        return text;
    }

    /**
     * Insert text at the current selection.
     *
     * Strategy: paste first (keeps the app's own undo stack intact), then fall
     * back to ACTION_SET_TEXT for fields that reject paste.
     */
    public boolean insert(final String text) {
        if (text == null || text.isEmpty()) return false;
        AccessibilityNodeInfo node = findEditable();
        if (node == null) return false;

        final String before = nodeText(node).toString();
        final int selStart = node.getTextSelectionStart();
        final int selEnd = node.getTextSelectionEnd();
        snapshot(before, selStart, selEnd);

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

        boolean pasted = tryPaste(node, text);
        if (pasted) {
            lastWasInsertion = true;
            verifyInsertion(text, before, selStart, selEnd);
            return true;
        }
        boolean set = trySetText(node, insertAt(before, text, selStart, selEnd));
        lastWasInsertion = set;
        return set;
    }

    private void snapshot(String before, int start, int end) {
        lastBefore = before == null ? "" : before;
        lastSelStart = start;
        lastSelEnd = end;
    }

    /**
     * A few apps accept ACTION_PASTE and then ignore it. Re-read the field and
     * fall back to ACTION_SET_TEXT when nothing actually changed.
     */
    private void verifyInsertion(final String inserted, final String before,
                                 final int selStart, final int selEnd) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                AccessibilityNodeInfo node = findEditable();
                if (node == null) return;
                String now = nodeText(node).toString();
                if (now.equals(before)) {
                    boolean ok = trySetText(node, insertAt(before, inserted, selStart, selEnd));
                    if (ok) lastWasInsertion = true;
                }
            }
        }, 340);
    }

    /** Replace the whole field, keeping the previous value for undo. */
    public boolean replaceText(String value) {
        AccessibilityNodeInfo node = findEditable();
        if (node == null) return false;
        snapshot(nodeText(node).toString(), node.getTextSelectionStart(), node.getTextSelectionEnd());
        boolean ok = trySetText(node, value == null ? "" : value);
        lastWasInsertion = ok;
        return ok;
    }

    private boolean tryPaste(AccessibilityNodeInfo node, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;
            final ClipData previous = cm.getPrimaryClip();
            cm.setPrimaryClip(ClipData.newPlainText("Sotto", text));
            boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (previous != null) {
                            ClipboardManager c = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (c != null) c.setPrimaryClip(previous);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }, 1200);
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean trySetText(AccessibilityNodeInfo node, String value) {
        try {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Throwable t) {
            return false;
        }
    }

    private String insertAt(String before, String insert, int start, int end) {
        if (start < 0 || start > before.length()) start = before.length();
        if (end < start || end > before.length()) end = start;
        return before.substring(0, start) + insert + before.substring(end);
    }

    public String currentText() {
        AccessibilityNodeInfo node = findEditable();
        return nodeText(node).toString();
    }

    public boolean undoLast() {
        if (!lastWasInsertion) return false;
        AccessibilityNodeInfo node = findEditable();
        if (node == null) return false;
        boolean ok = trySetText(node, lastBefore);
        if (ok) lastWasInsertion = false;
        return ok;
    }

    public boolean clear() {
        return replaceText("");
    }

    public boolean selectAll() {
        AccessibilityNodeInfo node = findEditable();
        if (node == null) return false;
        String text = nodeText(node).toString();
        Bundle args = new Bundle();
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length());
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
    }

    public boolean copy() {
        AccessibilityNodeInfo node = findEditable();
        return node != null && node.performAction(AccessibilityNodeInfo.ACTION_COPY);
    }

    public boolean cut() {
        AccessibilityNodeInfo node = findEditable();
        return node != null && node.performAction(AccessibilityNodeInfo.ACTION_CUT);
    }

    public boolean paste() {
        AccessibilityNodeInfo node = findEditable();
        return node != null && node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    }

    /** Press the IME "send"/enter action. */
    public boolean send() {
        AccessibilityNodeInfo node = findEditable();
        if (node == null) return false;
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    /** Put text on the clipboard when no field is focused. */
    public void toClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Sotto", text));
        } catch (Throwable ignored) {
        }
    }
}
