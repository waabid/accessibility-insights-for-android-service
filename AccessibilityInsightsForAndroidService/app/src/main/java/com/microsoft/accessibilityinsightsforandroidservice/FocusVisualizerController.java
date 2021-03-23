package com.microsoft.accessibilityinsightsforandroidservice;

import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class FocusVisualizerController {
    private FocusVisualizer focusVisualizer;
    private FocusVisualizationStateManager focusVisualizationStateManager;

    public FocusVisualizerController(FocusVisualizer focusVisualizer, FocusVisualizationStateManager focusVisualizationStateManager) {
        this.focusVisualizer = focusVisualizer;
        this.focusVisualizationStateManager = focusVisualizationStateManager;
        this.focusVisualizationStateManager.subscribe(this::onFocusVisualizationStateChange);
    }

    public void onFocusEvent(AccessibilityEvent event) {
        if (focusVisualizationStateManager.getState() == false) {
            return;
        }

        focusVisualizer.HandleAccessibilityFocusEvent(event);
    }

    public void onRedrawEvent(AccessibilityEvent event) {

        if (focusVisualizationStateManager.getState() == false) {
            return;
        }

        focusVisualizer.HandleAccessibilityRedrawEvent(event);
    }

    public void onOrientationChange(){
        focusVisualizer.resetVisualizations();
    }

    private void onFocusVisualizationStateChange(boolean newState) {
        if (newState) {
            return;
        }
        else {
            new Handler(Looper.getMainLooper()).post(() -> focusVisualizer.resetVisualizations());
        }
    }
}
