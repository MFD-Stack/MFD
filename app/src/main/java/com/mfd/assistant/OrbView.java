package com.mfd.assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * MFD - Mehmet Fatih DURSUN
 * Animated ORB view v4 — smooth state transitions
 */
public class OrbView extends View {

    public enum State { LISTENING, SPEAKING, THINKING, ERROR, IDLE }

    private State currentState = State.IDLE;
    private Paint paint;
    private float pulseScale = 1f;
    private float glowAlpha  = 0.5f;
    private ValueAnimator pulseAnim;

    // Colors per state
    private static final int[] COLORS_LISTENING = {0xFF00C8A0, 0xFF009975, 0xFF003322};
    private static final int[] COLORS_SPEAKING  = {0xFF4488FF, 0xFF2255CC, 0xFF001133};
    private static final int[] COLORS_THINKING  = {0xFFFFCC00, 0xFFCC9900, 0xFF332200};
    private static final int[] COLORS_ERROR     = {0xFFFF3344, 0xFFCC1122, 0xFF330011};
    private static final int[] COLORS_IDLE      = {0xFF334455, 0xFF223344, 0xFF111122};

    public OrbView(Context c) { super(c); init(); }
    public OrbView(Context c, AttributeSet a) { super(c, a); init(); }
    public OrbView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setState(State state) {
        if (currentState == state) return;
        currentState = state;
        stopAnim();
        startAnim();
        invalidate();
    }

    private void stopAnim() {
        if (pulseAnim != null) {
            pulseAnim.cancel();
            pulseAnim = null;
        }
        pulseScale = 1f;
        glowAlpha  = 0.5f;
    }

    private void startAnim() {
        switch (currentState) {
            case LISTENING:
                pulse(0.92f, 1.0f, 1200, true);
                break;
            case SPEAKING:
                pulse(0.88f, 1.05f, 400, true);
                break;
            case THINKING:
                pulse(0.95f, 1.05f, 700, true);
                break;
            case ERROR:
                pulse(0.85f, 1.0f, 250, true);
                break;
            default:
                pulseScale = 0.9f;
                glowAlpha  = 0.3f;
                invalidate();
                break;
        }
    }

    private void pulse(float from, float to, int duration, boolean repeat) {
        pulseAnim = ValueAnimator.ofFloat(from, to);
        pulseAnim.setDuration(duration);
        pulseAnim.setInterpolator(new DecelerateInterpolator());
        if (repeat) {
            pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        }
        pulseAnim.addUpdateListener(a -> {
            pulseScale = (float) a.getAnimatedValue();
            glowAlpha  = 0.3f + (pulseScale - from) / (to - from) * 0.5f;
            invalidate();
        });
        pulseAnim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = getWidth()  / 2f;
        float cy = getHeight() / 2f;
        float r  = Math.min(cx, cy) * pulseScale;

        int[] colors = getColors();

        // Outer glow
        paint.setAlpha((int)(glowAlpha * 255));
        RadialGradient glow = new RadialGradient(cx, cy, r * 1.4f,
                new int[]{colors[0] & 0x55FFFFFF, Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(glow);
        c.drawCircle(cx, cy, r * 1.4f, paint);

        // Main orb
        paint.setAlpha(255);
        RadialGradient orb = new RadialGradient(cx - r*0.25f, cy - r*0.25f, r,
                colors, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(orb);
        c.drawCircle(cx, cy, r, paint);

        // Inner highlight
        paint.setAlpha(80);
        RadialGradient hl = new RadialGradient(cx - r*0.3f, cy - r*0.3f, r*0.5f,
                new int[]{Color.WHITE, Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(hl);
        c.drawCircle(cx - r*0.1f, cy - r*0.1f, r*0.5f, paint);
    }

    private int[] getColors() {
        switch (currentState) {
            case LISTENING: return COLORS_LISTENING;
            case SPEAKING:  return COLORS_SPEAKING;
            case THINKING:  return COLORS_THINKING;
            case ERROR:     return COLORS_ERROR;
            default:        return COLORS_IDLE;
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnim();
    }
}
