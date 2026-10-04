package org.scholay.rimes.android;

import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import org.scholay.rimes.core.ChordGesture;
import org.scholay.rimes.core.ChordLayout;

/** In-process MotionEvent contract, separate from physical-phone event injection. Run on main. */
final class ChordSurfaceContract {
    private final List<String> outputs=new ArrayList<>();
    private final ChordSurface surface;
    private final Context context;
    private int checks,previews;
    private boolean split;
    private ChordSurfaceContract(Context context) {
        this.context=context;
        surface=new ChordSurface(context,new ChordSurface.Handler() {
            public void onChord(String code) { outputs.add("chord:"+code); }
            public void onKey(String text) { outputs.add("key:"+text); }
            public void onPreview(ChordGesture.Preview preview) { previews++; }
            public void onControl(ChordLayout.Action action) { outputs.add("control:"+action); }
            public String label(ChordLayout.Action action) { return action==ChordLayout.Action.DELETE?"⌫":"☺"; }
            public String description(ChordLayout.Action action) { return action.name(); }
        });
        layout();
    }
    static int run(Context context) { ChordSurfaceContract test=new ChordSurfaceContract(context); test.run(); return test.checks; }
    private void layout() {
        int width=Math.round(400*context.getResources().getDisplayMetrics().density);
        surface.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));
        surface.layout(0,0,width,surface.getMeasuredHeight());
    }
    private void check(boolean condition,String label) { checks++; if(!condition) throw new AssertionError("ChordSurface: "+label); }
    private void emit(long down,int action,int[] ids,String[] keys) {
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];
        float density=context.getResources().getDisplayMetrics().density;
        List<ChordLayout.Key> frames=ChordLayout.keys(400,split);
        for(int i=0;i<ids.length;i++) {
            props[i]=new MotionEvent.PointerProperties(); props[i].id=ids[i]; props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords(); coords[i].pressure=1; coords[i].size=1;
            ChordLayout.Key frame=null;
            for(ChordLayout.Key key:frames) if(key.text.equals(keys[i]) || key.action.name().equals(keys[i])) { frame=key; break; }
            if(frame==null) { coords[i].x=-10; coords[i].y=-10; }
            else { coords[i].x=(frame.x+frame.width/2)*density; coords[i].y=(frame.y+frame.height/2)*density; }
        }
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,ids.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try { surface.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    private void chord(long down) {
        emit(down,MotionEvent.ACTION_DOWN,new int[]{0},new String[]{"d"});
        emit(down,MotionEvent.ACTION_MOVE,new int[]{0},new String[]{"v"});
        emit(down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1},new String[]{"v","i"});
        int before=outputs.size(); check(surface.isChordActive(),"two thumbs active");
        emit(down,MotionEvent.ACTION_POINTER_UP,new int[]{0,1},new String[]{"v","i"});
        check(outputs.size()==before,"first release never submits");
        emit(down,MotionEvent.ACTION_UP,new int[]{1},new String[]{"i"});
        check(outputs.size()==before+1 && outputs.get(before).equals("chord:ni"),"final release resolves ni exactly once");
        emit(down,MotionEvent.ACTION_UP,new int[]{1},new String[]{"i"});
        check(outputs.size()==before+1,"duplicate release ignored");
    }
    private void run() {
        chord(SystemClock.uptimeMillis());
        int before=outputs.size(); long old=SystemClock.uptimeMillis();
        emit(old,MotionEvent.ACTION_DOWN,new int[]{0},new String[]{"d"});
        emit(old,MotionEvent.ACTION_MOVE,new int[]{0},new String[]{"v"});
        check(surface.isChordActive(),"old target finger held");
        surface.cancel(); // Same method the IME invokes on target loss/hide.
        check(!surface.isChordActive(),"target cancellation retires contact");
        emit(old,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1},new String[]{"v","i"});
        check(!surface.isChordActive(),"new pointer in retired stream stays inert");
        emit(old,MotionEvent.ACTION_POINTER_UP,new int[]{0,1},new String[]{"v","i"});
        emit(old,MotionEvent.ACTION_UP,new int[]{1},new String[]{"i"});
        check(outputs.size()==before,"retired stream cannot revive or submit");
        chord(SystemClock.uptimeMillis()); // A fresh DOWN resumes normally.

        before=outputs.size(); old=SystemClock.uptimeMillis();
        emit(old,MotionEvent.ACTION_DOWN,new int[]{0},new String[]{"d"});
        emit(old,MotionEvent.ACTION_MOVE,new int[]{0},new String[]{"v"});
        split=true; surface.render(true,true,false,KeyboardTheme.ALL[0]); layout();
        emit(old,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1},new String[]{"v","i"});
        emit(old,MotionEvent.ACTION_POINTER_UP,new int[]{0,1},new String[]{"v","i"});
        emit(old,MotionEvent.ACTION_UP,new int[]{1},new String[]{"i"});
        check(outputs.size()==before && !surface.isChordActive(),"layout change retires whole old stream");
        chord(SystemClock.uptimeMillis());

        before=outputs.size(); long invalid=SystemClock.uptimeMillis();
        emit(invalid,MotionEvent.ACTION_DOWN,new int[]{0},new String[]{"d"});
        emit(invalid,MotionEvent.ACTION_MOVE,new int[]{0},new String[]{"v"});
        emit(invalid,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1},new String[]{"v","i"});
        emit(invalid,MotionEvent.ACTION_POINTER_DOWN|(2<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1,2},new String[]{"v","i","EMOJI"});
        emit(invalid,MotionEvent.ACTION_POINTER_UP,new int[]{0,1,2},new String[]{"v","i","EMOJI"});
        emit(invalid,MotionEvent.ACTION_POINTER_UP,new int[]{1,2},new String[]{"i","EMOJI"});
        check(surface.isChordActive(),"invalid extra finger remains quarantined until release");
        emit(invalid,MotionEvent.ACTION_UP,new int[]{2},new String[]{"EMOJI"});
        check(outputs.size()==before && !surface.isChordActive(),"utility extra finger cancels entire chord");

        surface.render(true,false,true,KeyboardTheme.ALL[0]);
        long english=SystemClock.uptimeMillis(); emit(english,MotionEvent.ACTION_DOWN,new int[]{0},new String[]{"d"});
        check(surface.isChordActive(),"English held finger blocks Buffer controls");
        int callbacks=previews;
        for(int i=0;i<100;i++) emit(english,MotionEvent.ACTION_MOVE,new int[]{0},new String[]{"d"});
        check(previews==callbacks,"stationary move does not refresh IME readout");
        before=outputs.size(); emit(english,MotionEvent.ACTION_UP,new int[]{0},new String[]{"d"});
        check(outputs.size()==before+1 && outputs.get(before).equals("key:D") && !surface.isChordActive(),"English release types once");
        emit(english,MotionEvent.ACTION_UP,new int[]{0},new String[]{"d"});
        check(outputs.size()==before+1,"English late release ignored");
        surface.cancel();
    }
}
