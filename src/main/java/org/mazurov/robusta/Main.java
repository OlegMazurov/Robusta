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

import java.io.*;

public class Main {

    static void usage() {
        System.out.println("Usage: java -jar Robusta.jar [-w width] [-h hight] [-p parallelism] [-t generations] [-novis] [-o output file] [input in RLE format]");
        System.exit(1);
    }

    public static void main(String[] args)
    {
        int width = 0;
        int height = 0;
        int time = 10000;
        int parallelism = Runtime.getRuntime().availableProcessors();
        boolean vis = true;
        String output = null;
        RLE rle = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-w":
                    if (++i == args.length) usage();
                    width = Integer.parseInt(args[i]);
                    break;
                case "-h":
                    if (++i == args.length) usage();
                    height = Integer.parseInt(args[i]);
                    break;
                case "-o":
                    if (++i == args.length) usage();
                    output = args[i];
                    break;
                case "-p":
                    if (++i == args.length) usage();
                    parallelism = Integer.parseInt(args[i]);
                    break;
                case "-t":
                    if (++i == args.length) usage();
                    time = Integer.parseInt(args[i]);
                    break;
                case "-novis":
                    vis = false;
                    break;
                default:
                    rle = RLE.fromFile(args[i]);
                    if (rle == null) usage();
                    break;
            }
        }

        if (rle == null) {
            rle = RLE.getAcorn();
        }

        Life lf = Life.fromRLE(rle, width, height, time, parallelism, vis);
        long start = System.currentTimeMillis();
        lf.execute();
        long end = System.currentTimeMillis();

        if (output != null) {
            try {
                FileWriter writer = new FileWriter(new File(output));
                String[] state = lf.getResult();
                for (String str : state) {
                    writer.write(str);
                    writer.write(System.lineSeparator());
                }
                writer.close();
            }
            catch (IOException ex) {
                System.err.println("ERROR writing to output file " + output);
                ex.printStackTrace(System.err);
            }
        }
        System.out.println("Score: " + (1000l * time * lf.Width * lf.Height / (end - start)) + " ops/sec");
    }
}
