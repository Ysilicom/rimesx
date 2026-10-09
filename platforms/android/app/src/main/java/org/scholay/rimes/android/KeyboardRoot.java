package org.scholay.rimes.android;

import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/** Retires the raw keyboard touch stream before a ViewGroup can split it onto a new key. */
final class KeyboardRoot extends LinearLayout {
    private boolean activeStream;
    private long streamDownTime=-1,retiredDownTime=-1;
    /** Cursor pad. A relayout must not cancel the finger; only a real lift ends it. */
    private boolean retainStream;
    private Runnable padRelease;

    KeyboardRoot(Context context) { super(context); }

    void retainStream(boolean retain,Runnable onRelease) {
        retainStream=retain;
        padRelease=retain?onRelease:null;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_CANCEL && retainStream) return true;
        if(action==MotionEvent.ACTION_DOWN) {
            if(retainStream) {
                retainStream=false;
                Runnable release=padRelease;
                padRelease=null;
                if(release!=null) release.run();
            }
            if(event.getDownTime()==retiredDownTime) return true;
            if(activeStream) cancelPendingInputEvents();
            activeStream=true; streamDownTime=event.getDownTime();
        } else if(!activeStream || event.getDownTime()!=streamDownTime) return true;
        try { return super.dispatchTouchEvent(event); }
        finally {
            if(action==MotionEvent.ACTION_UP || (action==MotionEvent.ACTION_CANCEL && !retainStream)) {
                activeStream=false; streamDownTime=-1;
            }
        }
    }

    @Override public void onCancelPendingInputEvents() {
        if(retainStream) return;
        super.onCancelPendingInputEvents();
        long down=streamDownTime;
        if(activeStream) retiredDownTime=down;
        activeStream=false; streamDownTime=-1;
        // Also release native child touch targets. Clearing pressed flags alone would leave
        // ViewGroup's old target list able to translate a sibling POINTER_DOWN into DOWN.
        long now=SystemClock.uptimeMillis();
        MotionEvent cancel=MotionEvent.obtain(down>=0?down:now,now,MotionEvent.ACTION_CANCEL,0,0,0);
        cancel.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { super.dispatchTouchEvent(cancel); }
        finally { cancel.recycle(); }
    }
}
