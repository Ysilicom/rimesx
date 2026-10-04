package org.scholay.rimes.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

/** Real attached Button/Looper contract. Call on the instrumentation thread, not on main. */
final class NativeTouchContract {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private KeyboardRoot root;
    private KeyButton first,second;
    private int checks,firstClicks,secondClicks;
    private long down;

    private NativeTouchContract(Instrumentation instrumentation,Activity activity) {
        this.instrumentation=instrumentation; this.activity=activity;
    }
    static int run(Instrumentation instrumentation) {
        if(Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("NativeTouchContract must run off main");
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(SetupActivity.class.getName(),null,false);
        Activity activity=null;
        // Some devices deny activity starts from the instrumentation app UID. Shell launch
        // matches the existing test-host harness and monitor waiting has a finite deadline.
        String component=instrumentation.getTargetContext().getPackageName()+"/"+SetupActivity.class.getName();
        try(android.os.ParcelFileDescriptor launch=instrumentation.getUiAutomation().executeShellCommand(
                "am start -W -f 0x10008000 -n "+component)) {
            activity=instrumentation.waitForMonitorWithTimeout(monitor,15000);
            if(activity==null) throw new AssertionError("Native touch fixture did not start within 15 seconds");
            NativeTouchContract test=new NativeTouchContract(instrumentation,activity);
            test.run(); return test.checks;
        } catch(java.io.IOException error) {
            throw new AssertionError("Native touch fixture shell launch failed",error);
        }
        finally {
            instrumentation.removeMonitor(monitor);
            if(activity!=null) {
                Activity fixture=activity;
                instrumentation.runOnMainSync(fixture::finish);
                instrumentation.waitForIdleSync();
            }
        }
    }
    private void onMain(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch(Throwable failure) { error.set(failure); } });
        if(error.get()!=null) throw new AssertionError("Native touch main-thread phase failed",error.get());
    }
    private void idle() { instrumentation.waitForIdleSync(); }
    private void check(boolean condition,String label) {
        checks++; if(!condition) throw new AssertionError("Native touch: "+label);
    }
    private void newPress(KeyButton key) {
        // Distinct real uptime tokens avoid accidentally reusing a retired millisecond.
        SystemClock.sleep(2);
        onMain(() -> { down=SystemClock.uptimeMillis(); emit(MotionEvent.ACTION_DOWN,new KeyButton[]{key}); });
    }
    private void release(KeyButton key) { emit(MotionEvent.ACTION_UP,new KeyButton[]{key}); }
    private void emit(int action,KeyButton[] keys) {
        int[] ids=new int[keys.length]; for(int i=0;i<ids.length;i++) ids[i]=i;
        emit(action,keys,ids);
    }
    private void emit(int action,KeyButton[] keys,int[] ids) {
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[keys.length];
        MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[keys.length];
        for(int i=0;i<keys.length;i++) {
            properties[i]=new MotionEvent.PointerProperties(); properties[i].id=ids[i];
            properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i]=new MotionEvent.PointerCoords(); coordinates[i].pressure=1; coordinates[i].size=1;
            coordinates[i].x=keys[i].getLeft()+keys[i].getWidth()/2f;
            coordinates[i].y=keys[i].getTop()+keys[i].getHeight()/2f;
        }
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,keys.length,properties,coordinates,
                0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try { root.dispatchTouchEvent(event); }
        finally { event.recycle(); }
    }
    private void run() {
        onMain(() -> {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            root=new KeyboardRoot(activity); root.setOrientation(LinearLayout.HORIZONTAL); root.setMotionEventSplittingEnabled(true);
            first=new KeyButton(activity); first.setText("a"); first.setFocusable(false);
            second=new KeyButton(activity); second.setText("b"); second.setFocusable(false);
            first.setOnClickListener(view -> firstClicks++); second.setOnClickListener(view -> secondClicks++);
            int width=Math.round(100*activity.getResources().getDisplayMetrics().density);
            int height=Math.round(44*activity.getResources().getDisplayMetrics().density);
            root.addView(first,new LinearLayout.LayoutParams(width,height));
            root.addView(second,new LinearLayout.LayoutParams(width,height));
            activity.setContentView(root);
        }); idle();
        // An idle queue can precede the next frame. Wait outside main for the actual window layout.
        boolean[] laidOut={false};
        for(int attempt=0;attempt<80 && !laidOut[0];attempt++) {
            onMain(() -> laidOut[0]=first.isAttachedToWindow() && second.isAttachedToWindow() && first.getWidth()>0 && first.getHeight()>0);
            if(!laidOut[0]) SystemClock.sleep(25);
        }
        onMain(() -> check(laidOut[0],"fixture attached and laid out in a real window"));

        newPress(first);
        onMain(() -> {
            check(first.isPressed(),"native DOWN presses first key");
            first.cancelPendingInputEvents();
            check(!first.isPressed(),"button cancellation clears held feedback");
            release(first);
        }); idle();
        onMain(() -> check(firstClicks==0,"same-stream UP after button cancellation cannot click"));

        newPress(first);
        onMain(() -> { release(first); check(firstClicks==0,"native click is posted until the main queue drains"); }); idle();
        onMain(() -> check(firstClicks==1,"fresh native DOWN/UP clicks exactly once"));
        onMain(() -> release(first)); idle();
        onMain(() -> check(firstClicks==1,"duplicate release cannot click again"));

        newPress(first);
        onMain(() -> {
            release(first); check(firstClicks==1,"second click pending in actual main queue");
            root.cancelPendingInputEvents();
        }); idle();
        onMain(() -> check(firstClicks==1,"hierarchy cancellation removes posted native click"));

        newPress(first);
        onMain(() -> {
            check(first.isPressed(),"whole-stream fixture has old first finger");
            root.cancelPendingInputEvents();
            check(!first.isPressed(),"whole hierarchy releases old native touch target");
            emit(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new KeyButton[]{first,second});
            check(!second.isPressed(),"retired sibling POINTER_DOWN never becomes a new child DOWN");
            emit(MotionEvent.ACTION_MOVE,new KeyButton[]{first,second});
            check(!first.isPressed() && !second.isPressed(),"retired MOVE cannot revive either key");
            emit(MotionEvent.ACTION_POINTER_UP,new KeyButton[]{first,second});
            emit(MotionEvent.ACTION_UP,new KeyButton[]{second},new int[]{1});
        }); idle();
        onMain(() -> check(firstClicks==1 && secondClicks==0,"all retired stream releases stay inert"));

        newPress(second); onMain(() -> release(second)); idle();
        onMain(() -> check(secondClicks==1,"fresh untouched sibling press works after retirement"));
        onMain(() -> {
            root.cancelPendingInputEvents();
            check(first.performClick() && firstClicks==2,"direct accessibility-style performClick remains available");
            check(first.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null),"native accessibility ACTION_CLICK is accepted");
        }); idle();
        onMain(() -> check(firstClicks==3,"accessibility ACTION_CLICK invokes listener exactly once"));
    }
}
