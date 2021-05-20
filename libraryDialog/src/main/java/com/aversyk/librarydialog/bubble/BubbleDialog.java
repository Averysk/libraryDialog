package com.aversyk.librarydialog.bubble;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.aversyk.librarydialog.DialogUtil;
import com.aversyk.librarydialog.R;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * 气泡Dialog
 * Created by Averysk
 */
public class BubbleDialog extends Dialog {
    /**
     * 气泡位置
     */
    public enum Position {
        /**
         * 左边
         */
        LEFT,
        /**
         * 上边
         */
        TOP,
        /**
         * 右边
         */
        RIGHT,
        /**
         * 下边
         */
        BOTTOM
    }

    private BubbleLayout mBubbleLayout;
    private int mWidth, mHeight, mMargin;
    private View mAddView;//需要添加的view
    private Rect mClickedRect;
    private int mOffsetX, mOffsetY;//x和y方向的偏移
    private int mStatusBarHeight;
    private int mRelativeOffset;//相对与被点击view的偏移
    private boolean mSoftShowUp;//当软件盘弹出时Dialog上移
    private Position mPosition = Position.TOP;//气泡位置，默认上位
    private Position[] mPositions = new Position[4];
    private Auto mAuto;//记录自动确定位置的方案
    private boolean isThroughEvent = false;//是否穿透Dialog事件交互
    private boolean mCancelable;//是否能够取消
    private int[] clickedViewLocation = new int[2];
    private Activity mActivity;
    private ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener;
    private int mLookPosition = -1;
    private int mLookWidth = -1;
    private int mLookLength = -1;
    private int mBubbleColor = Color.WHITE;
    private int mBubbleRadius = -1;
    private int mBubblePadding = 1;
    private int mShadowColor = Color.GRAY;
    private int mShadowRadius = -1;
    private int mShadowX = -1;
    private int mShadowY = -1;

    public BubbleDialog(Context context) {
        super(context, R.style.bubble_dialog);
        mActivity = (Activity) context;
        initWindow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isThroughEvent && keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            dismiss();
            mActivity.onBackPressed();
            mActivity = null;
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBubbleLayout();
        setWindowMode();
        onAutoPosition();
        setLook();
        measureBubbleLayout();
        onDialogPosition();
        addListener();
        setCustomConfig();
    }

    @Override
    public void dismiss() {
        if (mSoftShowUp) {
            DialogUtil.hideKeyboard(BubbleDialog.this);
        }
        if (mBubbleLayout != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mBubbleLayout.getViewTreeObserver().removeOnGlobalLayoutListener(mOnGlobalLayoutListener);
        }
        super.dismiss();
    }

    private void initWindow() {
        Window window = getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        int screenW = DialogUtil.getScreenSize(getContext())[0];
        getWindow().getDecorView().setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isThroughEvent) {
                    float x = params.x < 0 ? 0 : params.x;//如果小于0则等于0
                    x = x + v.getWidth() > screenW ? screenW - v.getWidth() : x;
                    x += event.getX();
                    float y = params.y + event.getY();
                    event.setLocation(x, y);
                    mActivity.getWindow().getDecorView().dispatchTouchEvent(event);
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    private void initBubbleLayout() {
        if (mBubbleLayout == null) {
            mBubbleLayout = new BubbleLayout(getContext());
        }
        if (mAddView != null) {
            mBubbleLayout.addView(mAddView);
        }
        setContentView(mBubbleLayout);
    }

    private void setWindowMode() {
        final Window window = getWindow();
        if (window == null) return;
        if (mSoftShowUp) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        window.setLayout(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 处理自动位置(确定Dialog气泡方位与位置)
     */
    private void onAutoPosition() {
        if (mClickedRect == null || (mAuto == null && !havePositions())) return;

        final int[] spaces = new int[4];//被点击View左上右下分别的距离边缘的间隔距离
        spaces[0] = clickedViewLocation[0];//左距离
        spaces[1] = clickedViewLocation[1];//上距离
        spaces[2] = DialogUtil.getScreenSize(getContext())[0] - clickedViewLocation[0] - mClickedRect.width();//右距离
        spaces[3] = DialogUtil.getScreenSize(getContext())[1] - clickedViewLocation[1] - mClickedRect.height();//下距离

        if (havePositions()) { // 设置了优先级的情况
            mAddView.measure(0, 0);
            for (Position p : mPositions) {
                if (p == null) return;
                switch (p) {
                    case LEFT:
                        if (spaces[0] > mAddView.getMeasuredWidth()) {
                            mPosition = Position.LEFT;
                            return;
                        }
                        break;
                    case TOP:
                        if (spaces[1] > mAddView.getMeasuredHeight()) {
                            mPosition = Position.TOP;
                            return;
                        }
                        break;
                    case RIGHT:
                        if (spaces[2] > mAddView.getMeasuredWidth()) {
                            mPosition = Position.RIGHT;
                            return;
                        }
                        break;
                    case BOTTOM:
                        if (spaces[3] > mAddView.getMeasuredHeight()) {
                            mPosition = Position.BOTTOM;
                            return;
                        }
                        break;
                }
            }
            mPosition = mPositions[0]; // 如果都不能在有限的空间中显示完，那么默认第一优先级的位置
            return;
        }
        if (mAuto != null) {
            switch (mAuto) {
                case AROUND:
                    break;
                case UP_AND_DOWN:
                    mPosition = spaces[1] > spaces[3] ? Position.TOP : Position.BOTTOM;
                    return;
                case LEFT_AND_RIGHT:
                    mPosition = spaces[0] > spaces[2] ? Position.LEFT : Position.RIGHT;
                    return;
                default:
            }
        }

        // 自动位置
        int max = 0;
        for (int value : spaces) {
            if (value > max) max = value;
        }

        if (max == spaces[0]) {
            mPosition = Position.LEFT;
        } else if (max == spaces[1]) {
            mPosition = Position.TOP;
        } else if (max == spaces[2]) {
            mPosition = Position.RIGHT;
        } else if (max == spaces[3]) {
            mPosition = Position.BOTTOM;
        }
    }

    private boolean havePositions() {
        int num = 0;
        for (Position p : mPositions) {
            if (p != null) {
                num++;
            }
        }
        return num > 0;
    }

    private void setLook() {
        switch (mPosition) {
            case LEFT:
                mBubbleLayout.setLook(BubbleLayout.Look.RIGHT);
                break;
            case TOP:
                mBubbleLayout.setLook(BubbleLayout.Look.BOTTOM);
                break;
            case RIGHT:
                mBubbleLayout.setLook(BubbleLayout.Look.LEFT);
                break;
            case BOTTOM:
                mBubbleLayout.setLook(BubbleLayout.Look.TOP);
                break;
        }
        mBubbleLayout.initPadding();
    }

    private void measureBubbleLayout() {
        mBubbleLayout.measure(0, 0);
    }

    private void addListener() {
        mOnGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            int lastWidth, lastHeight;

            @Override
            public void onGlobalLayout() {
                if (lastWidth == mBubbleLayout.getMeasuredWidth() && lastHeight == mBubbleLayout.getMeasuredHeight())
                    return;
                onDialogPosition();
                lastWidth = mBubbleLayout.getMeasuredWidth();
                lastHeight = mBubbleLayout.getMeasuredHeight();
            }
        };
        mBubbleLayout.getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
        mBubbleLayout.setOnClickEdgeListener(new BubbleLayout.OnClickEdgeListener() {
            @Override
            public void edge() {
                if (BubbleDialog.this.mCancelable) {
                    dismiss();
                }
            }
        });
    }

    /**
     * 处理箭头位置
     */
    private void onDialogPosition() {
        if (mClickedRect == null) {
            return;
        }

        Window window = getWindow();
        if (window == null) return;
        window.setGravity(Gravity.LEFT | Gravity.TOP);
        WindowManager.LayoutParams params = window.getAttributes();
        FrameLayout.LayoutParams bubbleParams = null;

        if (mWidth != 0) {
            params.width = mWidth;
        }

        if (mHeight != 0) {
            params.height = mHeight;
        }

        if (mMargin != 0) {
            bubbleParams = (FrameLayout.LayoutParams) mBubbleLayout.getLayoutParams();
            if (mPosition == Position.TOP || mPosition == Position.BOTTOM) {
                bubbleParams.leftMargin = mMargin;
                bubbleParams.rightMargin = mMargin;
            } else {
                bubbleParams.topMargin = mMargin;
                bubbleParams.bottomMargin = mMargin;
            }
            mBubbleLayout.setLayoutParams(bubbleParams);
        }

        mStatusBarHeight = DialogUtil.getStatusHeight(getContext());

        switch (mPosition) {
            case TOP:
            case BOTTOM:
                params.x = clickedViewLocation[0] + mClickedRect.width() / 2 - mBubbleLayout.getMeasuredWidth() / 2 + mOffsetX;
                if (mMargin != 0 && mWidth == MATCH_PARENT) {
                    mBubbleLayout.setLookPosition(clickedViewLocation[0] - mMargin + mClickedRect.width() / 2 - mBubbleLayout.getLookWidth() / 2);
                } else if (params.x <= 0) {
                    mBubbleLayout.setLookPosition(clickedViewLocation[0] + mClickedRect.width() / 2 - mBubbleLayout.getLookWidth() / 2);
                } else if (params.x + mBubbleLayout.getMeasuredWidth() > DialogUtil.getScreenSize(getContext())[0]) {
                    mBubbleLayout.setLookPosition(clickedViewLocation[0] - (DialogUtil.getScreenSize(getContext())[0] - mBubbleLayout.getMeasuredWidth()) + mClickedRect.width() / 2 - mBubbleLayout.getLookWidth() / 2);
                } else {
                    mBubbleLayout.setLookPosition(clickedViewLocation[0] - params.x + mClickedRect.width() / 2 - mBubbleLayout.getLookWidth() / 2);
                }
                if (mPosition == Position.BOTTOM) {
                    if (mRelativeOffset != 0) mOffsetY = mRelativeOffset;
                    params.y = clickedViewLocation[1] + mClickedRect.height() + mOffsetY - mStatusBarHeight;

                } else {
                    if (mRelativeOffset != 0) mOffsetY = -mRelativeOffset;
                    params.y = clickedViewLocation[1] - mBubbleLayout.getMeasuredHeight() + mOffsetY - mStatusBarHeight;
                }
                break;
            case LEFT:
            case RIGHT:
                params.y = clickedViewLocation[1] + mOffsetY + mClickedRect.height() / 2 - mBubbleLayout.getMeasuredHeight() / 2 - mStatusBarHeight;
                if (mMargin != 0 && mHeight == MATCH_PARENT) {
                    mBubbleLayout.setLookPosition(clickedViewLocation[1] - mMargin + mClickedRect.height() / 2 - mBubbleLayout.getLookWidth() / 2 - mStatusBarHeight);
                } else if (params.y <= 0) {
                    mBubbleLayout.setLookPosition(clickedViewLocation[1] + mClickedRect.height() / 2 - mBubbleLayout.getLookWidth() / 2 - mStatusBarHeight);
                } else if (params.y + mBubbleLayout.getMeasuredHeight() > DialogUtil.getScreenSize(getContext())[1]) {
                    mBubbleLayout.setLookPosition(clickedViewLocation[1] - (DialogUtil.getScreenSize(getContext())[1] - mBubbleLayout.getMeasuredHeight()) + mClickedRect.height() / 2 - mBubbleLayout.getLookWidth() / 2);
                } else {
                    mBubbleLayout.setLookPosition(clickedViewLocation[1] - params.y + mClickedRect.height() / 2 - mBubbleLayout.getLookWidth() / 2 - mStatusBarHeight);
                }
                if (mPosition == Position.RIGHT) {
                    if (mRelativeOffset != 0) mOffsetX = mRelativeOffset;
                    params.x = clickedViewLocation[0] + mClickedRect.width() + mOffsetX;
                } else {
                    if (mRelativeOffset != 0) mOffsetX = -mRelativeOffset;
                    params.x = clickedViewLocation[0] - mBubbleLayout.getMeasuredWidth() + mOffsetX;
                }
                break;
        }

        mBubbleLayout.invalidate();
        window.setAttributes(params);
    }

    public boolean onTouchEvent(MotionEvent event) {
        Window window = getWindow();

        if (window == null) return false;
        final View decorView = window.getDecorView();
        if (this.mCancelable && isShowing() && shouldCloseOnTouch(event, decorView)) {
            cancel();
            return true;
        }
        return false;
    }

    public boolean shouldCloseOnTouch(MotionEvent event, View decorView) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        return (x <= 0) || (y <= 0)
                || (x > (decorView.getWidth()))
                || (y > (decorView.getHeight()));
    }

    public void setCancelable(boolean flag) {
        super.setCancelable(flag);
        mCancelable = flag;
    }


    /**
     * @param width  设置气泡的宽
     * @param height 设置气泡的高
     * @param margin 设置距离屏幕边缘的间距,只有当设置 width 或 height 为 MATCH_PARENT 才有效
     */
    public BubbleDialog setLayout(int width, int height, int margin) {
        mWidth = width;
        mHeight = height;
        mMargin = margin;
        return this;
    }

    /**
     * 计算时是否包含状态栏(如果有状态栏目，而没有设置为true将会出现上下的偏差)
     */
    @Deprecated
    public BubbleDialog calBar(boolean cal) {
//        this.mCalBar = cal;
        return this;
    }

    /**
     * 设置被点击的view来设置弹出dialog的位置
     */
    public BubbleDialog setClickedView(View view) {
        this.mClickedRect = new Rect(0, 0, view.getWidth(), view.getHeight());
        view.getLocationOnScreen(clickedViewLocation);
        handleGlobalLayoutListener();
        return this;
    }

    /**
     * 设置被点击的位置来弹出dialog的位置
     */
    public BubbleDialog setClickedPosition(int x, int y) {
        this.mClickedRect = new Rect(0, 0, 1, 1);
        clickedViewLocation[0] = x;
        clickedViewLocation[1] = y;
        handleGlobalLayoutListener();
        return this;
    }

    private void handleGlobalLayoutListener() {
        onAutoPosition();
        if (mOnGlobalLayoutListener != null) {
            setLook();
            onDialogPosition();
        }
    }

    /**
     * 当软件键盘弹出时，dialog根据条件上移
     */
    public BubbleDialog softShowUp() {
        this.mSoftShowUp = true;
        return this;
    }

    /**
     * 设置dialog内容view
     * 请使用{@link #setBubbleContentView(View)}
     */
    @Deprecated
    public BubbleDialog addContentView(View view) {
        this.mAddView = view;
        return this;
    }

    /**
     * 设置dialog内容view
     */
    public BubbleDialog setBubbleContentView(View view) {
        this.mAddView = view;
        return this;
    }

    /**
     * 设置气泡位置，排列最前的优先级越高<hr/>
     * <li>注意1：调用该方法后{@link #autoPosition(Auto)}将失效</li>
     * <li>注意2：如果设置的位置数组中没有满足可在空间中显示完的条件，那么默认第一优先级位置</li>
     *
     * @param positions 设置气泡可能出现的位置 <br/>
     *                  设置显示全部并设置优先级（下 > 左 > 上 > 右）： setPosition(Position.BOTTOM, Position.LEFT, Position.TOP, Position.RIGHT); <br/>
     *                  显示左下，优先级 （下， 左）： setPosition(Position.BOTTOM, Position.LEFT);  <br/>
     *                  显示在上面：setPosition(Position.TOP);
     */
    public BubbleDialog setPosition(Position... positions) {
        if (positions.length == 1 && positions[0] != null) {
            this.mPosition = positions[0];
            return this;
        }
        this.mPositions = positions;
        return this;
    }

    /**
     * 设置是否自动设置Dialog的位置
     *
     * @param auto 自动设置位置的方案
     * @see Auto#AROUND 位置可在相对于被点击控件的四周
     * @see Auto#LEFT_AND_RIGHT 位置只可在相对于被点击控件左右
     * @see Auto#UP_AND_DOWN 位置只可在相对于被点击控件的上下
     */
    public BubbleDialog autoPosition(Auto auto) {
        this.mAuto = auto;
        return this;
    }

    /**
     * 设置是否穿透Dialog手势交互
     *
     * @param cancelable 点击空白是否能取消Dialog，只有当"isThroughEvent = false"时才有效
     */
    public BubbleDialog setThroughEvent(boolean isThroughEvent, boolean cancelable) {
        this.isThroughEvent = isThroughEvent;
        if (isThroughEvent) {
            setCancelable(false);
        } else {
            setCancelable(cancelable);
        }
        return this;
    }

    /**
     * 设置x方向偏移量
     */
    public BubbleDialog setOffsetX(int offsetX) {
        this.mOffsetX = DialogUtil.dip2px(getContext(), offsetX);
        return this;
    }

    /**
     * 设置y方向偏移量
     */
    public BubbleDialog setOffsetY(int offsetY) {
        this.mOffsetY = DialogUtil.dip2px(getContext(), offsetY);
        return this;
    }

    /**
     * 设置dialog相对与被点击View的偏移
     */
    public BubbleDialog setRelativeOffset(int relativeOffset) {
        this.mRelativeOffset = DialogUtil.dip2px(getContext(), relativeOffset);
        return this;
    }

    /**
     * 自定义气泡布局
     */
    public BubbleDialog setBubbleLayout(BubbleLayout bl) {
        this.mBubbleLayout = bl;
        return this;
    }

    /**
     * 背景全透明
     */
    public BubbleDialog setTransParentBackground() {
        Window window = getWindow();
        if (window == null) return this;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        return this;
    }


    /**
     * 设置自定义配置
     */
    private void setCustomConfig(){
        setLookPosition();
        setLookWidth();
        setLookLength();
        setBubbleColor();
        setBubbleRadius();
        setBubblePadding();
        setShadowColor();
        setShadowRadius();
        setShadowX();
        setShadowY();
    }

    /**
     * 设置箭头位置
     */
    public BubbleDialog setLookPosition(int mLookPosition) {
        this.mLookPosition = mLookPosition;
        return this;
    }

    /**
     * 设置箭头位置
     */
    private void setLookPosition() {
        if (mLookPosition >= 0){
            mBubbleLayout.setLookPosition(DialogUtil.dip2px(getContext(), mLookPosition));
        }
    }

    /**
     * 设置箭头宽度
     */
    public BubbleDialog setLookWidth(int mLookWidth) {
        this.mLookWidth = mLookWidth;
        return this;
    }

    /**
     * 设置箭头宽度
     */
    private void setLookWidth() {
        if (mLookWidth >= 0){
            mBubbleLayout.setLookWidth(DialogUtil.dip2px(getContext(), mLookWidth));
        }
    }

    /**
     * 设置箭头长度
     */
    public BubbleDialog setLookLength(int mLookLength) {
        this.mLookLength = mLookLength;
        return this;
    }

    /**
     * 设置箭头长度
     */
    private void setLookLength() {
        if (mLookLength >= 0){
            mBubbleLayout.setLookLength(DialogUtil.dip2px(getContext(), mLookLength));
        }
    }

    /**
     * 设置气泡背景颜色
     */
    public BubbleDialog setBubbleColor(int mBubbleColor) {
        this.mBubbleColor = mBubbleColor;
        return this;
    }

    /**
     * 设置气泡背景颜色
     */
    private void setBubbleColor() {
        mBubbleLayout.setBubbleColor(mBubbleColor);
    }

    /**
     * 设置气泡圆弧
     */
    public BubbleDialog setBubbleRadius(int mBubbleRadius) {
        this.mBubbleRadius = mBubbleRadius;
        return this;
    }

    /**
     * 设置气泡圆弧
     */
    private void setBubbleRadius() {
        if (mBubbleRadius >= 0){
            mBubbleLayout.setBubbleRadius(DialogUtil.dip2px(getContext(), mBubbleRadius));
        }
    }

    /**
     * 设置气泡内填兖边距
     */
    public BubbleDialog setBubblePadding(int mBubblePadding) {
        this.mBubblePadding = mBubblePadding;
        return this;
    }

    /**
     * 设置气泡圆弧
     */
    private void setBubblePadding() {
        if (mBubblePadding >= 0){
            mBubbleLayout.setBubblePadding(DialogUtil.dip2px(getContext(), mBubblePadding));
        }
    }

    /**
     * 设置阴影颜色
     */
    public BubbleDialog setShadowColor(int mShadowColor) {
        this.mShadowColor = mShadowColor;
        return this;
    }

    /**
     * 设置阴影颜色
     */
    private void setShadowColor() {
        mBubbleLayout.setShadowColor(mShadowColor);
    }

    /**
     * 设置阴影圆弧
     */
    public BubbleDialog setShadowRadius(int mShadowRadius) {
        this.mShadowRadius = mShadowRadius;
        return this;
    }

    /**
     * 设置阴影圆弧
     */
    private void setShadowRadius() {
        if (mShadowRadius >= 0){
            mBubbleLayout.setShadowRadius(DialogUtil.dip2px(getContext(), mShadowRadius));
        }
    }

    /**
     * 设置阴影x轴
     */
    public BubbleDialog setShadowX(int mShadowX) {
        this.mShadowX = mShadowX;
        return this;
    }

    /**
     * 设置阴影x轴
     */
    private void setShadowX() {
        if (mShadowX >= 0){
            mBubbleLayout.setShadowY(DialogUtil.dip2px(getContext(), mShadowX));
        }
    }

    /**
     * 设置阴影y轴
     */
    public BubbleDialog setShadowY(int mShadowY) {
        this.mShadowY = mShadowY;
        return this;
    }

    /**
     * 设置阴影y轴
     */
    private void setShadowY() {
        if (mShadowY >= 0){
            mBubbleLayout.setShadowY(DialogUtil.dip2px(getContext(), mShadowY));
        }
    }

    // 呼吸灯
    private AlphaAnimation animationFadeIn = null;
    private AlphaAnimation animationFadeOut = null;
    private static int BREATH_INTERVAL_TIME = 1500; //设置呼吸灯时间间隔

    public void startAlphaAnimation(){
        animationFadeIn = new AlphaAnimation(0.2f, 1.0f);
        animationFadeIn.setDuration(BREATH_INTERVAL_TIME);
        animationFadeOut = new AlphaAnimation(1.0f, 0.2f);
        animationFadeOut.setDuration(BREATH_INTERVAL_TIME);
        animationFadeIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mBubbleLayout.startAnimation(animationFadeOut);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        animationFadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mBubbleLayout.startAnimation(animationFadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mBubbleLayout.startAnimation(animationFadeOut);
    }

    public void cancelAlphaAnimation(){
        animationFadeIn.cancel();
        animationFadeIn.setAnimationListener(null);
        animationFadeIn = null;
        animationFadeOut.cancel();
        animationFadeOut.setAnimationListener(null);
        animationFadeOut = null;
        mBubbleLayout.clearAnimation();
    }

}
