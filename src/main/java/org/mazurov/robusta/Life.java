/*
 * Copyright 2019 Oleg Mazurov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mazurov.robusta;

import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerArray;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class Life {

    protected static final int STATE0 = 0;
    protected static final int STATE1 = 1;
    protected static final int T0 = 0;
    private static final int[] COLORS = {
            0xff8000, 0xffffff, 0xff0000, 0x00ff00,
            0x0000ff, 0xffff00, 0xff00ff, 0x00ffff,
    };

    protected final int Width;
    protected final int Height;
    protected final int maxTime;
    protected final int nThreads;

    private final boolean vis;
    private int[] imgData;

    private AtomicIntegerArray cells;

    /**
     * Cell neighbors wrapped around a torus:
     *       -------------
     *    +1 | 6 | 5 | 4 |
     *       -------------
     *     r | 7 |   | 3 |
     *       -------------
     *    -1 | 0 | 1 | 2 |
     *       -------------
     *        -1   c  +1
     */
    private int getNeighbor(int idx, int i)
    {
        int r = idx / Width;
        int c = idx % Width;
        switch (i) {
            case 0:
                if (--c < 0) c += Width;
            case 1:
                if (--r < 0) r += Height;
                break;
            case 2:
                if (--r < 0) r += Height;
            case 3:
                if (++c == Width) c = 0;
                break;
            case 4:
                if (++c == Width) c = 0;
            case 5:
                if (++r == Height) r = 0;
                break;
            case 6:
                if (++r == Height) r = 0;
            case 7:
                if (--c < 0) c += Width;
                break;
        }
        return r * Width + c;
    }

    public void run(int id) {

        int nextId = id + 1 == threads.length() ? 0 : id + 1;

        // Start apart
        int leap = cells.length() / nThreads;
        int cur = leap * id;

        mainLoop:
        for (;;) {

            // Am I my brother's keeper?
            if (threads.get(nextId) == null) {
                Thread brother = new Thread(() -> run(nextId));
                brother.start();
                threads.set(nextId, brother);
            }

            int val = cells.get(cur);
            int ts = val >>> 2;

            // Are we done?
            if (ts == maxTime) {
                for (int n = 0; n < cells.length(); ++n) {
                    if (++cur == cells.length()) cur = 0;
                    val = cells.get(cur);
                    if (val >> 2 != maxTime) continue mainLoop;
                }
                done = true;
                return;
            }

            // Count alive neighbors
            int sum = 0;
            for (int n = 0; n < 8; ++n) {
                int idxn = getNeighbor(cur, n);
                int valn = cells.get(idxn);
                int tsn = valn >>> 2;
                if (tsn == ts) sum += valn & 0x1;
                else if (tsn == ts + 1) sum += (valn >>> 1) & 0x1;
                else {
                    if (tsn < ts) cur = idxn;
                    else {
                        cur += leap;
                        if (cur >= cells.length()) cur -= cells.length();
                    }
                    continue mainLoop;
                }
            }

            // Apply the rule of Life
            int nextState = sum < 2 ? STATE0 : sum == 2 ? (val & 0x1) : sum == 3 ? STATE1 : STATE0;
            int nextVal = ((ts + 1) << 2) | ((val & 0x1) << 1) | nextState;
            if (!cells.compareAndSet(cur, val, nextVal)) {
                // We are out of sync, start over
                cur += leap;
                if (cur >= cells.length()) cur -= cells.length();
                continue mainLoop;
            }

            // Color live cells according to the current thread id
            setColor(cur, nextState == STATE0 ? 0 : id + 1);
            // Color all cells according to the current thread id
            //setColor(cur, id + 1);
            // Color all cells according to the current generation
            //setColor(cur, ts + 1);

            if (++cur == cells.length()) cur = 0;
        }
    }

    private AtomicReferenceArray<Thread> threads;
    private volatile boolean done;

    public void execute() {
        done = false;
        threads = new AtomicReferenceArray<Thread>(nThreads);
        for (int i = 0; i < threads.length(); ++i) {
            final int id = i;
            Thread thread = new Thread(() -> run(id));
            threads.set(i, thread);
            thread.start();
        }

        // Release the chaos monkey
        Random rnd = new Random(0);
        int killed = 0;
        while (!done) {
            // Give them a chance (you can experiment with not sleeping at all but slower systems may not cope well).
            try {
                Thread.sleep(1);
            } catch (InterruptedException ie) {
            }

            int tidx = rnd.nextInt(threads.length());
            Thread victim = threads.get(tidx);
            if (victim != null) {
                threads.set(tidx, null);
                victim.stop();
                ++killed;
            }
        }
        System.out.println("Threads killed: " + killed);

        // Collect survivors
        try {
            for (int i = 0; i < threads.length(); ++i) {
                Thread survivor = threads.get(i);
                if (survivor != null) {
                    survivor.join();
                }
            }
        }
        catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    protected int getState(int row, int col) {
        return cells.get(row * Width + col) & 0x1;
    }

    protected void setColor(int idx, int color) {
        if (vis) {
            imgData[idx] = color == 0 ? 0 : COLORS[color % COLORS.length];
        }
    }

    protected Life(int w, int h, int t, int p, boolean v) {
        Width = w;
        Height = h;
        maxTime = T0 + t;
        nThreads = p;
        vis = v;

        // Initialize visualization
        if (vis) {
            BufferedImage img = new BufferedImage(Width, Height, BufferedImage.TYPE_INT_RGB);
            imgData = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();

            JFrame frame = new JFrame() {
                public void paint(Graphics g) {
                    g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                }
            };
            frame.setSize(Width, Height);
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
            frame.setVisible(true);

            Timer timer = new Timer(40, (e) -> frame.repaint());
            timer.start();
        }
    }

    private Life(int w, int h, int t, int p, boolean v, int[] st) {
        this(w, h, t, p, v);

        // Initialize cells
        cells = new AtomicIntegerArray(Width * Height);
        for (int i = 0; i < cells.length(); ++i) {
            cells.set(i, st[i] == 0 ? STATE0 : STATE1);
        }
    }

    public static Life fromRLE(RLE rle, int width, int height, int time, int par, boolean vis)
    {
        // Re-center
        width = Math.max(width, rle.getW());
        height = Math.max(height, rle.getH());
        int[] state = new int[width * height];
        int x0 = (width - rle.getW()) / 2;
        int y0 = (height - rle.getH()) / 2;
        for (int x = 0; x < rle.getW(); ++x) {
            for (int y = 0; y < rle.getH(); ++y) {
                state[(y + y0) * width + x + x0] = rle.getState(x, y);
            }
        }

        Life res = new Life(width, height, time, par, vis, state);
        return res;
    }

    public static Life fromRLE(RLE rle, int time, int par, boolean vis)
    {
        return fromRLE(rle, rle.getW(), rle.getH(), time, par, vis);
    }

    public String[] getResult() {
        String[] result = new String[Height];
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < Height; ++r) {
            sb.setLength(0);
            for (int c = 0; c < Width; ++c) {
                sb.append(getState(r, c));
            }
            result[r] = sb.toString();
        }
        return result;
    }
}
