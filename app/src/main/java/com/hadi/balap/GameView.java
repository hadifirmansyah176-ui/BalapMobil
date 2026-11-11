package com.hadi.balap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final int[] COLORS = {0xFFE53935, 0xFFFDD835, 0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1};

    private Thread thread;
    private volatile boolean running;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Random rnd = new Random();
    private final SharedPreferences prefs;

    private int w, h;
    private float roadL, roadW, laneW, carW, carH, playerY;
    private float playerX, roadOffset, speed, spawnTimer, score;
    private int targetLane = 1, best;
    private boolean over;
    private final List<float[]> enemies = new ArrayList<>(); // {lane, y, colorIndex}

    public GameView(Context ctx) {
        super(ctx);
        getHolder().addCallback(this);
        prefs = ctx.getSharedPreferences("balap", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
    }

    private void layout() {
        roadL = w * 0.1f;
        roadW = w * 0.8f;
        laneW = roadW / 3f;
        carW = laneW * 0.6f;
        carH = carW * 1.8f;
        playerY = h - carH * 1.6f;
        reset();
    }

    private void reset() {
        enemies.clear();
        targetLane = 1;
        playerX = laneCenter(1);
        speed = h * 0.5f;
        spawnTimer = 0;
        score = 0;
        over = false;
    }

    private float laneCenter(int lane) {
        return roadL + laneW * lane + laneW / 2f;
    }

    // ---------- loop ----------
    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1e9f, 0.05f);
            last = now;
            update(dt);
            Canvas c = null;
            try {
                c = getHolder().lockCanvas();
                if (c != null) render(c);
            } finally {
                if (c != null) getHolder().unlockCanvasAndPost(c);
            }
            try { Thread.sleep(8); } catch (InterruptedException ignored) { }
        }
    }

    private void update(float dt) {
        if (over || w == 0) return;
        speed = Math.min(speed + h * 0.02f * dt, h * 1.3f);
        roadOffset = (roadOffset + speed * dt) % (h / 6f);
        score += dt * speed / h * 10f;

        // gerak halus ke jalur tujuan
        float tx = laneCenter(targetLane);
        float step = w * 2.5f * dt;
        if (Math.abs(tx - playerX) <= step) playerX = tx;
        else playerX += Math.signum(tx - playerX) * step;

        // munculkan mobil musuh
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            enemies.add(new float[]{rnd.nextInt(3), -carH, rnd.nextInt(COLORS.length)});
            spawnTimer = Math.max(0.45f, 1.1f - speed / h * 0.5f);
        }

        for (int i = enemies.size() - 1; i >= 0; i--) {
            float[] e = enemies.get(i);
            e[1] += speed * 0.8f * dt;
            if (e[1] > h + carH) { enemies.remove(i); continue; }
            float ex = laneCenter((int) e[0]);
            float pad = carW * 0.1f;
            boolean hitX = Math.abs(ex - playerX) < carW - pad;
            boolean hitY = e[1] + carH - pad > playerY && e[1] + pad < playerY + carH;
            if (hitX && hitY) { gameOver(); break; }
        }
    }

    private void gameOver() {
        over = true;
        if ((int) score > best) {
            best = (int) score;
            prefs.edit().putInt("best", best).apply();
        }
    }

    // ---------- gambar ----------
    private void render(Canvas c) {
        c.drawColor(0xFF2E7D32);                       // rumput
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF424242);                        // jalan
        c.drawRect(roadL, 0, roadL + roadW, h, p);
        p.setColor(0xFFFFFFFF);                        // garis tepi
        c.drawRect(roadL - 6, 0, roadL, h, p);
        c.drawRect(roadL + roadW, 0, roadL + roadW + 6, h, p);

        float seg = h / 6f;                            // garis putus-putus
        for (int i = 1; i <= 2; i++) {
            float x = roadL + laneW * i;
            for (float y = -seg + roadOffset; y < h; y += seg) {
                c.drawRect(x - 4, y, x + 4, y + seg / 2f, p);
            }
        }

        for (float[] e : enemies) drawCar(c, laneCenter((int) e[0]), e[1], COLORS[(int) e[2]]);
        drawCar(c, playerX, playerY, 0xFF1E88E5);

        p.setColor(0xFFFFFFFF);
        p.setTextSize(w * 0.06f);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("Skor: " + (int) score, 24, w * 0.09f, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Terbaik: " + best, w - 24, w * 0.09f, p);

        if (over) {
            p.setColor(0xAA000000);
            c.drawRect(0, 0, w, h, p);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(w * 0.12f);
            c.drawText("GAME OVER", w / 2f, h * 0.42f, p);
            p.setTextSize(w * 0.06f);
            c.drawText("Skor: " + (int) score, w / 2f, h * 0.49f, p);
            c.drawText("Ketuk layar untuk main lagi", w / 2f, h * 0.56f, p);
        }
    }

    private void drawCar(Canvas c, float cx, float top, int color) {
        p.setColor(0xFF111111);                        // roda
        float ww = carW * 0.18f, wh = carH * 0.22f;
        float[] ys = {top + carH * 0.10f, top + carH * 0.68f};
        for (float y : ys) {
            c.drawRect(cx - carW / 2f - ww / 2f, y, cx - carW / 2f + ww / 2f, y + wh, p);
            c.drawRect(cx + carW / 2f - ww / 2f, y, cx + carW / 2f + ww / 2f, y + wh, p);
        }
        p.setColor(color);                             // badan
        r.set(cx - carW / 2f, top, cx + carW / 2f, top + carH);
        c.drawRoundRect(r, carW * 0.2f, carW * 0.2f, p);
        p.setColor(0xCC90CAF9);                        // kaca
        c.drawRect(cx - carW * 0.3f, top + carH * 0.25f, cx + carW * 0.3f, top + carH * 0.45f, p);
    }

    // ---------- kontrol ----------
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (over) reset();
            else if (ev.getX() < w / 2f) targetLane = Math.max(0, targetLane - 1);
            else targetLane = Math.min(2, targetLane + 1);
        }
        return true;
    }

    // ---------- siklus hidup ----------
    @Override public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        boolean first = (w == 0);
        w = width; h = height;
        if (first) layout();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try { thread.join(); } catch (InterruptedException ignored) { }
    }
}
