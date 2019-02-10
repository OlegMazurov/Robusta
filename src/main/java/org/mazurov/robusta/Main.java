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

public class Main {
    public static void main(String[] args)
    {
        int width = 0;
        int height = 0;
        int time = 10000;
        int parallelism = Runtime.getRuntime().availableProcessors();
        boolean vis = true;
        RLE rle = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-w")) {
                width = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("-h")) {
                height = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("-p")) {
                parallelism = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("-t")) {
                time = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("-novis")) {
                vis = false;
            }
            else {
                rle = RLE.fromFile(args[i]);
                if (rle == null) {
                    return;
                }
            }
        }

        if (rle == null) {
            rle = RLE.getAcorn();
        }

        Life lf = Life.fromRLE(rle, width, height, time, parallelism, vis);
        long start = System.currentTimeMillis();
        lf.execute();
        long end = System.currentTimeMillis();

        String[] state = lf.getResult();
        for (String str : state) {
            System.out.println(str);
        }
        System.out.println("Score: " + (1000l * time * lf.Width * lf.Height / (end-start)) + " ops/sec");
    }
}
