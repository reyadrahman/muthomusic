package com.mutho.music.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import com.mutho.music.R;
import com.mutho.music.utils.ResourceUtils;

public class CircleView extends View {

    private Paint paint;

    private Drawable tickDrawable;

    public CircleView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(false);

        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        if (isInEditMode()) {
            paint.setColor(Color.RED);
        }

        tickDrawable = ContextCompat.getDrawable(context, R.drawable.ic_check_24dp);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawCircle(getWidth() / 2, getHeight() / 2, getWidth() / 2, paint);

        if (isActivated()) {
            int padding = ResourceUtils.toPixels(4);
            tickDrawable.setBounds(padding, padding, getWidth() - padding, getHeight() - padding);
            tickDrawable.draw(canvas);
        }
    }
}