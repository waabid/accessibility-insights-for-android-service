package com.microsoft.accessibilityinsightsforandroidservice;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.accessibility.AccessibilityEvent;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Looper.class, FocusVisualizerController.class})
public class FocusVisualizerControllerTest {

    private final static ScheduledExecutorService mainThread = Executors.newSingleThreadScheduledExecutor();
    @Mock FocusVisualizer focusVisualizerMock;
    @Mock FocusVisualizationStateManager focusVisualizationStateManagerMock;
    @Mock AccessibilityEvent accessibilityEventMock;
    @Mock Looper looperMock;
    @Mock Handler handlerMock;

    FocusVisualizerController testSubject;

    @Before
    public void prepare() {
        testSubject = new FocusVisualizerController(focusVisualizerMock, focusVisualizationStateManagerMock);
    }

    @Test
    public void exists() {
        Assert.assertNotNull(testSubject);
    }

    @Test
    public void onFocusEventDoesNotCallVisualizerIfStateIsFalse(){
        when(focusVisualizationStateManagerMock.getState()).thenReturn(false);
        testSubject.onFocusEvent(accessibilityEventMock);
        verify(focusVisualizerMock, times(0)).HandleAccessibilityFocusEvent(any(AccessibilityEvent.class));
    }

    @Test
    public void onFocusEventCallsVisualizerIfStateIsTrue(){
        when(focusVisualizationStateManagerMock.getState()).thenReturn(true);
        testSubject.onFocusEvent(accessibilityEventMock);
        verify(focusVisualizerMock, times(1)).HandleAccessibilityFocusEvent(any(AccessibilityEvent.class));
    }

    @Test
    public void onRedrawEventDoesNotCallVisualizerIfStateIsFalse(){
        when(focusVisualizationStateManagerMock.getState()).thenReturn(false);
        testSubject.onRedrawEvent(accessibilityEventMock);
        verify(focusVisualizerMock, times(0)).HandleAccessibilityRedrawEvent(any(AccessibilityEvent.class));
    }

    @Test
    public void onRedrawEventCallsVisualizerIfStateIsTrue(){
        when(focusVisualizationStateManagerMock.getState()).thenReturn(true);
        testSubject.onRedrawEvent(accessibilityEventMock);
        verify(focusVisualizerMock, times(1)).HandleAccessibilityRedrawEvent(any(AccessibilityEvent.class));
    }

    @Test
    public void onOrientationChangeCallsResetVisualizations(){
        testSubject.onOrientationChange();
        verify(focusVisualizerMock, times(1)).resetVisualizations();
    }


    @Test
    public void onFocusVisualizationStateChangeDoesNotResetVisualizationsIfStateIsTrue() throws Exception {
        PowerMockito.mockStatic(Looper.class);
        when(Looper.getMainLooper()).thenReturn(looperMock);
        whenNew(Handler.class).withArguments(looperMock).thenReturn(handlerMock);

        Answer<Boolean> handlerPostAnswer = new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Runnable runnable = invocation.getArgument(0, Runnable.class);
                Long delay = 0L;
                if (invocation.getArguments().length > 1) {
                    delay = invocation.getArgument(1, Long.class);
                }
                if (runnable != null) {
                    mainThread.schedule(runnable, delay, TimeUnit.MILLISECONDS);
                }
                return true;
            }
        };

        doAnswer(handlerPostAnswer).when(handlerMock).post(any(Runnable.class));

        Whitebox.invokeMethod(testSubject, "onFocusVisualizationStateChange", false);
        verify(focusVisualizerMock, times(1)).resetVisualizations();
    }


}
