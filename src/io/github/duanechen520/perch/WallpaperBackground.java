package io.github.duanechen520.perch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * The "moss terrace" backdrop: warm charcoal, one soft light over the clock, four
 * receding ridges and a fine grain that keeps the dark gradient from banding.
 * Everything is drawn in view-relative units, so one implementation covers 720p to 8K.
 */
public final class WallpaperBackground extends View {

    private static final int HILLS = 4;
    private static final int GRAIN_SIZE = 256;
    private static final int[] HILL_FILL_ALPHA = {0x1A, 0x14, 0x0F, 0x0A};
    private static final int[] HILL_EDGE_ALPHA = {0x2E, 0x28, 0x22, 0x1C};

    private static Bitmap sharedGrain;

    private final Paint basePaint = new Paint(Paint.DITHER_FLAG);
    private final Paint glowPaint = new Paint(Paint.DITHER_FLAG);
    private final Paint hillPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint vignettePaint = new Paint(Paint.DITHER_FLAG);
    private final Paint grainPaint = new Paint(Paint.DITHER_FLAG);
    private final Path[] hillPaths = new Path[HILLS];
    private final Path[] edgePaths = new Path[HILLS];
    private final Shader[] hillShaders = new Shader[HILLS];

    private float glowCx;
    private float glowCy;
    private float glowRx;
    private float glowRy;
    private int moss;

    public WallpaperBackground(Context context) {
        this(context, null);
    }

    public WallpaperBackground(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        moss = getResources().getColor(R.color.accent) & 0x00FFFFFF;
        grainPaint.setShader(new android.graphics.BitmapShader(
                grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        grainPaint.setFilterBitmap(false);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) {
            return;
        }
        buildBase(width, height);
        buildHills(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || basePaint.getShader() == null) {
            return;
        }
        canvas.drawRect(0f, 0f, width, height, basePaint);

        canvas.save();
        canvas.scale(1f, glowRy / glowRx, glowCx, glowCy);
        canvas.drawCircle(glowCx, glowCy, glowRx, glowPaint);
        canvas.restore();

        for (int i = 0; i < HILLS; i++) {
            hillPaint.setShader(hillShaders[i]);
            canvas.drawPath(hillPaths[i], hillPaint);
        }
        for (int i = 0; i < HILLS; i++) {
            edgePaint.setColor((HILL_EDGE_ALPHA[i] << 24) | moss);
            canvas.drawPath(edgePaths[i], edgePaint);
        }

        canvas.drawRect(0f, 0f, width, height, vignettePaint);
        canvas.drawRect(0f, 0f, width, height, grainPaint);
    }

    private void buildBase(int width, int height) {
        basePaint.setShader(new LinearGradient(
                0f, 0f, 0f, height,
                new int[]{
                        getResources().getColor(R.color.wall_top),
                        getResources().getColor(R.color.wall_mid),
                        getResources().getColor(R.color.wall_bottom),
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));

        glowCx = width * 0.5f;
        glowCy = height * 0.18f;
        glowRx = width * 0.62f;
        glowRy = height * 0.34f;
        glowPaint.setShader(new RadialGradient(
                glowCx, glowCy, glowRx,
                new int[]{(0x20 << 24) | moss, (0x0B << 24) | moss, (0x00 << 24) | moss},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));

        vignettePaint.setShader(new RadialGradient(
                width * 0.5f, height * 0.5f, Math.max(width, height) * 0.72f,
                new int[]{0x00000000, 0x00000000, 0x4A000000},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
    }

    private void buildHills(int width, int height) {
        hillPaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        edgePaint.setStrokeJoin(Paint.Join.ROUND);
        edgePaint.setStrokeWidth(Math.max(1.2f, height / 1080f * 1.6f));

        for (int i = 0; i < HILLS; i++) {
            float base = height * (0.50f + i * 0.075f);
            float amp = height * (0.045f + i * 0.012f);

            Path crest = new Path();
            crest.moveTo(0f, base);
            crest.cubicTo(width * 0.24f, base - amp,
                    width * 0.36f, base + amp * 0.6f,
                    width * 0.52f, base - amp * 0.2f);
            crest.cubicTo(width * 0.70f, base - amp * 1.1f,
                    width * 0.84f, base + amp * 0.7f,
                    width, base - amp * 0.35f);

            Path hill = new Path(crest);
            hill.lineTo(width, height);
            hill.lineTo(0f, height);
            hill.close();

            hillPaths[i] = hill;
            edgePaths[i] = crest;
            hillShaders[i] = new LinearGradient(0f, base - amp, 0f, height,
                    new int[]{(HILL_FILL_ALPHA[i] << 24) | moss, (0x00 << 24) | moss},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP);
        }
    }

    private static Bitmap grain() {
        if (sharedGrain != null) {
            return sharedGrain;
        }
        int[] pixels = new int[GRAIN_SIZE * GRAIN_SIZE];
        Random random = new Random(0x5EED);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (random.nextInt(11) << 24) | 0x00FFFFFF;
        }
        sharedGrain = Bitmap.createBitmap(GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888);
        sharedGrain.setPixels(pixels, 0, GRAIN_SIZE, 0, 0, GRAIN_SIZE, GRAIN_SIZE);
        sharedGrain.setDensity(Bitmap.DENSITY_NONE);
        return sharedGrain;
    }
}
