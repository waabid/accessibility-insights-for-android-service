package com.microsoft.accessibilityinsightsforandroidservice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.deque.axe.android.Axe;

import java.util.Objects;

public class ElementHighlight extends View {
    private static final String TAG = "ElementHighlight";
    private Paint paint = new Paint();
    private AccessibilityNodeInfo eventSource;
    private int yOffset;

    public ElementHighlight(Context context, AccessibilityEvent event, int yOffset) {
        super(context);
        this.eventSource = event.getSource();
        this.yOffset = yOffset;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean refreshWorked = eventSource.refresh();
        if (refreshWorked != true) {
            return;
        }

        Rect rect = new Rect();
        eventSource.getBoundsInScreen(rect);
        rect.offset(0, yOffset);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(5);
        Log.v(TAG, "" + canvas.getHeight() + "," + canvas.getWidth());
        canvas.drawRect(rect, paint);
    }

    public AccessibilityNodeInfo getEventSource() {
        return eventSource;
    }
}
