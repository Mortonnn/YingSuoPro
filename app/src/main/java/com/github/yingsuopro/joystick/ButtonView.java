package com.github.yingsuopro.joystick;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.github.yingsuopro.R;
import com.github.yingsuopro.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 按键式方向摇杆。
 *
 * 交互约定：点任意方向键 = 朝该方向持续移动；点中间绿色按钮 = 停止 / 继续上一次方向。
 * 这样比原来“先点锁图标切换自动/手动，再点方向键”的两段式操作更直观。
 */
public class ButtonView extends LinearLayout {
    private ButtonViewClickListener mListener;
    private ImageButton btnCenter;
    private final List<ImageButton> mDirectionButtons = new ArrayList<>();
    private final List<Double> mDirectionAngles = new ArrayList<>();
    private boolean isMoving = false;
    private double lastAngle = 90;

    public ButtonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public ButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ButtonView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.joystick_button, this);
        initButtonView();
    }

    private void initButtonView() {
        btnCenter = findViewById(R.id.btn_center);
        btnCenter.setImageResource(R.drawable.ic_joystick_play);
        btnCenter.setColorFilter(ThemeHelper.colorOnPrimary(mContext()));
        btnCenter.setOnClickListener(view -> {
            if (isMoving) {
                stopMove();
            } else {
                startMove(lastAngle, findButton(lastAngle));
            }
        });

        addDirectionButton(R.id.btn_north, 90);
        addDirectionButton(R.id.btn_south, 270);
        addDirectionButton(R.id.btn_west, 180);
        addDirectionButton(R.id.btn_east, 0);
        addDirectionButton(R.id.btn_north_east, 45);
        addDirectionButton(R.id.btn_south_east, 315);
        addDirectionButton(R.id.btn_north_west, 135);
        addDirectionButton(R.id.btn_south_west, 225);
    }

    private Context mContext() {
        return getContext();
    }

    private void addDirectionButton(int resId, final double angle) {
        ImageButton button = findViewById(resId);
        if (button == null) {
            return;
        }
        button.setColorFilter(ThemeHelper.colorOnSurfaceVariant(mContext()));
        button.setOnClickListener(view -> startMove(angle, button));
        mDirectionButtons.add(button);
        mDirectionAngles.add(angle);
    }

    private ImageButton findButton(double angle) {
        int index = mDirectionAngles.indexOf(angle);
        return index >= 0 ? mDirectionButtons.get(index) : null;
    }

    private void startMove(double angle, ImageButton selectedButton) {
        lastAngle = angle;
        isMoving = true;

        for (ImageButton button : mDirectionButtons) {
            button.setColorFilter(ThemeHelper.colorOnSurfaceVariant(mContext()));
        }
        if (selectedButton != null) {
            selectedButton.setColorFilter(ThemeHelper.colorPrimary(mContext()));
        }
        btnCenter.setImageResource(R.drawable.ic_joystick_pause);

        if (mListener != null) {
            mListener.clickAngleInfo(true, angle, 1);
        }
    }

    private void stopMove() {
        isMoving = false;
        for (ImageButton button : mDirectionButtons) {
            button.setColorFilter(ThemeHelper.colorOnSurfaceVariant(mContext()));
        }
        btnCenter.setImageResource(R.drawable.ic_joystick_play);

        if (mListener != null) {
            mListener.clickAngleInfo(false, 0, 0);
        }
    }

    public void setListener(ButtonViewClickListener mListener) {
        this.mListener = mListener;
    }

    public interface ButtonViewClickListener {
        /**
         * 点击的角度信息
         */
        void clickAngleInfo(boolean auto, double angle, double r);
    }
}

