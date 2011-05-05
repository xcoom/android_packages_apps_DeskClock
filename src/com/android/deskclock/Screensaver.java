/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock;

import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.animation.TimeInterpolator;
import android.animation.ObjectAnimator;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.os.Handler;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.Runnable;
import android.util.Log;

public class Screensaver extends Activity {
    View mContainer;

    static final int CLOCK_COLOR = 0xFF66AAFF;

    static final long MOVE_DELAY = 60000; // DeskClock.SCREEN_SAVER_MOVE_DELAY;
    static final long SLIDE_TIME = 10000;
    static final long FADE_TIME = 1000;

    static final boolean SLIDE = false;

    private static TimeInterpolator mSlowStartWithBrakes = 
        new TimeInterpolator() {
            public float getInterpolation(float x) {
                return (float)(Math.cos((Math.pow(x,3) + 1) * Math.PI) / 2.0f) + 0.5f;
            }
        };

    private Handler mHandler = new Handler();

    private Runnable mMoveSaverRunnable = new Runnable() {
        @Override
        public void run() {
            long delay = MOVE_DELAY;

            View parent = (View)mContainer.getParent();
            //Log.d("DeskClock/Screensaver", String.format("parent=(%d x %d)", 
//                        parent.getWidth(), parent.getHeight()));
            final float xrange = parent.getWidth() - mContainer.getWidth();
            final float yrange = parent.getHeight() - mContainer.getHeight();

            if (xrange == 0) {
                delay = 500; // back in a split second
            } else {
                final int nextx = (int) (Math.random() * xrange);
                final int nexty = (int) (Math.random() * yrange);

                if (mContainer.getAlpha() == 0f) {
                    // jump right there
                    mContainer.setX(nextx);
                    mContainer.setY(nexty);
                    ObjectAnimator.ofFloat(mContainer, "alpha", 0f, 1f)
                        .setDuration(FADE_TIME)
                        .start();
                } else {
                    AnimatorSet s = new AnimatorSet();
                    Animator xMove   = ObjectAnimator.ofFloat(mContainer,
                                         "x", mContainer.getX(), nextx);
                    Animator yMove   = ObjectAnimator.ofFloat(mContainer,
                                         "y", mContainer.getY(), nexty);

                    Animator xShrink = ObjectAnimator.ofFloat(mContainer, "scaleX", 1f, 0.75f);
                    Animator xGrow   = ObjectAnimator.ofFloat(mContainer, "scaleX", 0.75f, 1f);

                    Animator yShrink = ObjectAnimator.ofFloat(mContainer, "scaleY", 1f, 0.75f);
                    Animator yGrow   = ObjectAnimator.ofFloat(mContainer, "scaleY", 0.75f, 1f);
                    AnimatorSet shrink = new AnimatorSet(); shrink.play(xShrink).with(yShrink);
                    AnimatorSet grow = new AnimatorSet(); grow.play(xGrow).with(yGrow);

                    Animator fadeout = ObjectAnimator.ofFloat(mContainer, "alpha", 1f, 0f);
                    Animator fadein = ObjectAnimator.ofFloat(mContainer, "alpha", 0f, 1f);


                    if (SLIDE) {
                        s.play(xMove).with(yMove);
                        s.setDuration(SLIDE_TIME);

                        s.play(shrink.setDuration(SLIDE_TIME/2));
                        s.play(grow.setDuration(SLIDE_TIME/2)).after(shrink);
                        s.setInterpolator(mSlowStartWithBrakes);
                    } else {
                        AccelerateInterpolator accel = new AccelerateInterpolator();
                        DecelerateInterpolator decel = new DecelerateInterpolator();

                        shrink.setDuration(FADE_TIME).setInterpolator(accel);
                        fadeout.setDuration(FADE_TIME).setInterpolator(accel);
                        grow.setDuration(FADE_TIME).setInterpolator(decel);
                        fadein.setDuration(FADE_TIME).setInterpolator(decel);
                        s.play(shrink);
                        s.play(fadeout);
                        s.play(xMove.setDuration(0)).after(FADE_TIME);
                        s.play(yMove.setDuration(0)).after(FADE_TIME);
                        s.play(fadein).after(FADE_TIME);
                        s.play(grow).after(FADE_TIME);
                    }
                    s.start();
                }

                long now = System.currentTimeMillis();
                long adjust = (now % 60000);
                delay = delay
                        + (MOVE_DELAY - adjust) // minute aligned
                        - (SLIDE ? 0 : FADE_TIME) // start moving before the fade
                        ;
                //Log.d("DeskClock/Screensaver", "will move again in " + delay + " now=" + now + " adjusted by " + adjust);
            }

            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, delay);
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        setContentView(R.layout.desk_clock_saver);
        mContainer = findViewById(R.id.saver_view);
        mContainer.setAlpha(0);
        mContainer.setSystemUiVisibility(View.STATUS_BAR_HIDDEN);
        mContainer.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        AndroidClockTextView timeDisplay = (AndroidClockTextView) findViewById(R.id.timeDisplay);
        if (timeDisplay != null) {
            timeDisplay.setTextColor(CLOCK_COLOR);
            AndroidClockTextView amPm = (AndroidClockTextView)findViewById(R.id.am_pm); 
            if (amPm != null) amPm.setTextColor(CLOCK_COLOR);
        }

        getWindow().addFlags(
                  WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                );
    }

    @Override
    public void onResume() {
        super.onResume();

        mMoveSaverRunnable.run();
    }

    @Override
    public void onPause() {
        super.onPause();

        mHandler.removeCallbacks(mMoveSaverRunnable);
    }

    public void onUserInteraction() {
        finish();
    }
}
