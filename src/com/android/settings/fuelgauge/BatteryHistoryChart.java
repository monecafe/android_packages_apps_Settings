/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import com.android.settings.R;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.BatteryStats;
import android.os.SystemClock;
import android.os.BatteryStats.HistoryItem;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;

public class BatteryHistoryChart extends View {
    static final int SANS = 1;
    static final int SERIF = 2;
    static final int MONOSPACE = 3;

    static final int BATTERY_WARN = 29;
    static final int BATTERY_CRITICAL = 14;
    
    final Paint mBatteryBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mBatteryGoodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mBatteryWarnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mBatteryCriticalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mChargingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mScreenOnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    
    final Path mBatLevelPath = new Path();
    final Path mBatGoodPath = new Path();
    final Path mBatWarnPath = new Path();
    final Path mBatCriticalPath = new Path();
    final Path mChargingPath = new Path();
    final Path mScreenOnPath = new Path();
    
    int mFontSize;
    
    BatteryStats mStats;
    long mStatsPeriod;
    String mDurationString;
    
    int mChargingOffset;
    int mScreenOnOffset;
    int mLevelOffset;
    
    int mTextAscent;
    int mTextDescent;
    int mDurationStringWidth;
    
    int mNumHist;
    BatteryStats.HistoryItem mHistFirst;
    long mHistStart;
    long mHistEnd;
    int mBatLow;
    int mBatHigh;
    
    public BatteryHistoryChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        mBatteryBackgroundPaint.setARGB(255, 128, 128, 128);
        mBatteryBackgroundPaint.setStyle(Paint.Style.FILL);
        mBatteryGoodPaint.setARGB(128, 0, 255, 0);
        int lineWidth = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                2, getResources().getDisplayMetrics());
        if (lineWidth <= 0) lineWidth = 1;
        mBatteryGoodPaint.setStrokeWidth(lineWidth);
        mBatteryGoodPaint.setStyle(Paint.Style.STROKE);
        mBatteryWarnPaint.setARGB(128, 255, 255, 0);
        mBatteryWarnPaint.setStrokeWidth(lineWidth);
        mBatteryWarnPaint.setStyle(Paint.Style.STROKE);
        mBatteryCriticalPaint.setARGB(192, 255, 0, 0);
        mBatteryCriticalPaint.setStrokeWidth(lineWidth);
        mBatteryCriticalPaint.setStyle(Paint.Style.STROKE);
        mChargingPaint.setARGB(255, 0, 128, 0);
        mChargingPaint.setStrokeWidth(lineWidth);
        mChargingPaint.setStyle(Paint.Style.STROKE);
        mScreenOnPaint.setARGB(255, 0, 0, 255);
        mScreenOnPaint.setStrokeWidth(lineWidth);
        mScreenOnPaint.setStyle(Paint.Style.STROKE);
        
        mScreenOnOffset = lineWidth;
        mChargingOffset = lineWidth*2;
        mLevelOffset = lineWidth*3;
        
        mTextPaint.density = getResources().getDisplayMetrics().density;
        mTextPaint.setCompatibilityScaling(
                getResources().getCompatibilityInfo().applicationScale);
        
        TypedArray a =
            context.obtainStyledAttributes(
                attrs, R.styleable.BatteryHistoryChart, 0, 0);
        
        ColorStateList textColor = null;
        int textSize = 15;
        int typefaceIndex = -1;
        int styleIndex = -1;
        
        TypedArray appearance = null;
        int ap = a.getResourceId(R.styleable.BatteryHistoryChart_android_textAppearance, -1);
        if (ap != -1) {
            appearance = context.obtainStyledAttributes(ap,
                                com.android.internal.R.styleable.
                                TextAppearance);
        }
        if (appearance != null) {
            int n = appearance.getIndexCount();
            for (int i = 0; i < n; i++) {
                int attr = appearance.getIndex(i);

                switch (attr) {
                case com.android.internal.R.styleable.TextAppearance_textColor:
                    textColor = appearance.getColorStateList(attr);
                    break;

                case com.android.internal.R.styleable.TextAppearance_textSize:
                    textSize = appearance.getDimensionPixelSize(attr, textSize);
                    break;

                case com.android.internal.R.styleable.TextAppearance_typeface:
                    typefaceIndex = appearance.getInt(attr, -1);
                    break;

                case com.android.internal.R.styleable.TextAppearance_textStyle:
                    styleIndex = appearance.getInt(attr, -1);
                    break;
                }
            }

            appearance.recycle();
        }
        
        int shadowcolor = 0;
        float dx=0, dy=0, r=0;
        
        int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
                case R.styleable.BatteryHistoryChart_android_shadowColor:
                    shadowcolor = a.getInt(attr, 0);
                    break;

                case R.styleable.BatteryHistoryChart_android_shadowDx:
                    dx = a.getFloat(attr, 0);
                    break;

                case R.styleable.BatteryHistoryChart_android_shadowDy:
                    dy = a.getFloat(attr, 0);
                    break;

                case R.styleable.BatteryHistoryChart_android_shadowRadius:
                    r = a.getFloat(attr, 0);
                    break;

                case R.styleable.BatteryHistoryChart_android_textColor:
                    textColor = a.getColorStateList(attr);
                    break;

                case R.styleable.BatteryHistoryChart_android_textSize:
                    textSize = a.getDimensionPixelSize(attr, textSize);
                    break;

                case R.styleable.BatteryHistoryChart_android_typeface:
                    typefaceIndex = a.getInt(attr, typefaceIndex);
                    break;

                case R.styleable.BatteryHistoryChart_android_textStyle:
                    styleIndex = a.getInt(attr, styleIndex);
                    break;
            }
        }
        
        mTextPaint.setColor(textColor.getDefaultColor());
        mTextPaint.setTextSize(textSize);
        
        Typeface tf = null;
        switch (typefaceIndex) {
            case SANS:
                tf = Typeface.SANS_SERIF;
                break;

            case SERIF:
                tf = Typeface.SERIF;
                break;

            case MONOSPACE:
                tf = Typeface.MONOSPACE;
                break;
        }
        
        setTypeface(tf, styleIndex);
        
        if (shadowcolor != 0) {
            mTextPaint.setShadowLayer(r, dx, dy, shadowcolor);
        }
    }
    
    public void setTypeface(Typeface tf, int style) {
        if (style > 0) {
            if (tf == null) {
                tf = Typeface.defaultFromStyle(style);
            } else {
                tf = Typeface.create(tf, style);
            }

            mTextPaint.setTypeface(tf);
            // now compute what (if any) algorithmic styling is needed
            int typefaceStyle = tf != null ? tf.getStyle() : 0;
            int need = style & ~typefaceStyle;
            mTextPaint.setFakeBoldText((need & Typeface.BOLD) != 0);
            mTextPaint.setTextSkewX((need & Typeface.ITALIC) != 0 ? -0.25f : 0);
        } else {
            mTextPaint.setFakeBoldText(false);
            mTextPaint.setTextSkewX(0);
            mTextPaint.setTypeface(tf);
        }
    }
    
    void setStats(BatteryStats stats) {
        mStats = stats;
        
        long uSecTime = mStats.computeBatteryRealtime(SystemClock.elapsedRealtime() * 1000,
                BatteryStats.STATS_SINCE_CHARGED);
        mStatsPeriod = uSecTime;
        String durationString = Utils.formatElapsedTime(getContext(), mStatsPeriod / 1000);
        mDurationString = getContext().getString(R.string.battery_stats_on_battery,
                durationString);
        
        BatteryStats.HistoryItem rec = stats.getHistory();
        mHistFirst = null;
        int pos = 0;
        int lastInteresting = 0;
        byte lastLevel = -1;
        mBatLow = 0;
        mBatHigh = 100;
        while (rec != null) {
            pos++;
            if (rec.cmd == HistoryItem.CMD_UPDATE) {
                if (mHistFirst == null) {
                    mHistFirst = rec;
                    mHistStart = rec.time;
                }
                if (rec.batteryLevel != lastLevel || pos == 1) {
                    lastLevel = rec.batteryLevel;
                    lastInteresting = pos;
                    mHistEnd = rec.time;
                }
            }
            rec = rec.next;
        }
        mNumHist = lastInteresting;
        
        if (mHistEnd <= mHistStart) mHistEnd = mHistStart+1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mDurationStringWidth = (int)mTextPaint.measureText(mDurationString);
        mTextAscent = (int)mTextPaint.ascent();
        mTextDescent = (int)mTextPaint.descent();
    }

    void finishPaths(int w, int h, int levelh, int startX, int y, Path curLevelPath,
            int lastBatX, boolean lastCharging, boolean lastScreenOn, Path lastPath) {
        if (curLevelPath != null) {
            if (lastBatX >= 0) {
                if (lastPath != null) {
                    lastPath.lineTo(w, y);
                }
                curLevelPath.lineTo(w, y);
            }
            curLevelPath.lineTo(w, levelh);
            curLevelPath.lineTo(startX, levelh);
            curLevelPath.close();
        }
        
        if (lastCharging) {
            mChargingPath.lineTo(w, h-mChargingOffset);
        }
        if (lastScreenOn) {
            mScreenOnPath.lineTo(w, h-mScreenOnOffset);
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        mBatLevelPath.reset();
        mBatGoodPath.reset();
        mBatWarnPath.reset();
        mBatCriticalPath.reset();
        mScreenOnPath.reset();
        mChargingPath.reset();
        
        final long timeStart = mHistStart;
        final long timeChange = mHistEnd-mHistStart;
        
        final int batLow = mBatLow;
        final int batChange = mBatHigh-mBatLow;
        
        final int levelh = h - mLevelOffset;
        
        BatteryStats.HistoryItem rec = mHistFirst;
        int x = 0, y = 0, startX = 0, lastX = -1, lastY = -1, lastBatX = -1;
        int i = 0;
        Path curLevelPath = null;
        Path lastLinePath = null;
        boolean lastCharging = false, lastScreenOn = false;
        final int N = mNumHist;
        while (rec != null && i < N) {
            if (rec.cmd == BatteryStats.HistoryItem.CMD_UPDATE) {
                x = (int)(((rec.time-timeStart)*w)/timeChange);
                y = levelh - ((rec.batteryLevel-batLow)*(levelh-1))/batChange;
                
                if (lastX != x) {
                    // We have moved by at least a pixel.
                    if (lastY == y) {
                        // Battery level is still the same; don't plot,
                        // but remember it.
                        lastBatX = x;
                    } else {
                        Path path;
                        byte value = rec.batteryLevel;
                        if (value <= BATTERY_CRITICAL) path = mBatCriticalPath;
                        else if (value <= BATTERY_WARN) path = mBatWarnPath;
                        else path = mBatGoodPath;
                        
                        if (path != lastLinePath) {
                            if (lastLinePath != null) {
                                lastLinePath.lineTo(x, y);
                            }
                            path.moveTo(x, y);
                            lastLinePath = path;
                        } else {
                            path.lineTo(x, y);
                        }
                        
                        if (curLevelPath == null) {
                            curLevelPath = mBatLevelPath;
                            curLevelPath.moveTo(x, y);
                            startX = x;
                        } else {
                            curLevelPath.lineTo(x, y);
                        }
                        lastX = x;
                        lastY = y;
                        lastBatX = -1;
                        
                        final boolean charging =
                            (rec.states&HistoryItem.STATE_BATTERY_PLUGGED_FLAG) != 0;
                        if (charging != lastCharging) {
                            if (charging) {
                                mChargingPath.moveTo(x, h-mChargingOffset);
                            } else {
                                mChargingPath.lineTo(x, h-mChargingOffset);
                            }
                            lastCharging = charging;
                        }
                        
                        final boolean screenOn =
                            (rec.states&HistoryItem.STATE_SCREEN_ON_FLAG) != 0;
                        if (screenOn != lastScreenOn) {
                            if (screenOn) {
                                mScreenOnPath.moveTo(x, h-mScreenOnOffset);
                            } else {
                                mScreenOnPath.lineTo(x, h-mScreenOnOffset);
                            }
                            lastScreenOn = screenOn;
                        }
                    }
                }
                
            } else if (curLevelPath != null) {
                finishPaths(x+1, h, levelh, startX, lastY, curLevelPath, lastBatX,
                        lastCharging, lastScreenOn, lastLinePath);
                lastX = lastY = lastBatX = -1;
                curLevelPath = null;
                lastLinePath = null;
                lastCharging = lastScreenOn = false;
            }
            
            rec = rec.next;
            i++;
        }
        
        finishPaths(w, h, levelh, startX, lastY, curLevelPath, lastBatX,
                lastCharging, lastScreenOn, lastLinePath);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        final int width = getWidth();
        final int height = getHeight();
        
        canvas.drawPath(mBatLevelPath, mBatteryBackgroundPaint);
        canvas.drawText(mDurationString, (width/2) - (mDurationStringWidth/2),
                (height/2) - ((mTextDescent-mTextAscent)/2) - mTextAscent, mTextPaint);
        if (!mBatGoodPath.isEmpty()) {
            canvas.drawPath(mBatGoodPath, mBatteryGoodPaint);
        }
        if (!mBatWarnPath.isEmpty()) {
            canvas.drawPath(mBatWarnPath, mBatteryWarnPaint);
        }
        if (!mBatCriticalPath.isEmpty()) {
            canvas.drawPath(mBatCriticalPath, mBatteryCriticalPaint);
        }
        if (!mChargingPath.isEmpty()) {
            canvas.drawPath(mChargingPath, mChargingPaint);
        }
        if (!mScreenOnPath.isEmpty()) {
            canvas.drawPath(mScreenOnPath, mScreenOnPaint);
        }
    }
}