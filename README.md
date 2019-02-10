# Surviving loss of threads in a parallel program

Robusta is a research project that:
* investigates an approach to thread-loss tolerant parallel computation
* provides a proof-of-concept implementation 
* presents an asynchronous parallel wait-free data-synchronized implementation of [Conway's Game of Life](https://en.wikipedia.org/wiki/Conway%27s_Game_of_Life), or a generic cellular automaton.

## Introduction
\<to be provided>

## Data synchronization
\<to be provided>

## Chaos Monkey
To test thread-loss tolerance, a "chaos monkey" is released immediately after worker thread creation in [Life.execute()](src/main/java/org/mazurov/robusta/Life.java).
The main thread picks a random thread from the current pool and stops it. 
Although Java has deprecated Thread.stop() exactly because it causes hard to diagnose and recover from troubles, I find it not brutal enough to simulate truly asynchronous and instantaneous loss of threads like hardware malfunction.
It's good enough for proof-of-concept demonstration, nevertheless: occasionally you may see Java exceptions caused by _java.lang.ThreadDeath_ - those are evidence the threads do not die gracefully.  

If all worker threads are killed, the computation ceases. To prevent that, workers constantly watch over each other and create new workers when a loss is detected. 
Of course, all that has to be done in a thread-loss tolerant fashion. All workers are organized in a directed ring so that each worker is responsible for monitoring and recovering only its next neighbor. No synchronization is needed in that scheme (but _volatile_ is enforced by using _AtomicReferenceArray_ for the pool).   
In general, detection of a non-functioning thread should be more elaborate. For demonstration purposes, in the provided implementation, worker removal is almost instantly communicated to the pool allowing for quick detection and recovery. 

## Forwardly Answered Questions
_The program hangs. Is it a bug?_  
Not necessarily. The chaos monkey may win and kill all worker threads on a slower system. The goal of this exercise is to show how to beat it.  

_What does "asynchronous parallel wait-free data-synchronized" mean?_  
Data-synchronized is explained above. For the rest, refer to my [Koyaanisqatsi](https://github.com/OlegMazurov/Koyaanisqatsi) project.

_Will Robusta survive in space (any real-life radiation-hard environment)?_  
No. It's a Java program. The JVM and the OS it runs on are not robust.

_Robusta shows how to survive a CPU glitch. Can it also survive a memory glitch?_  
No. If a state bit is flipped it will be accepted as valid by any worker processing it. To deal with memory glitches we need memory redundancy. See my other [Koyaanisqatsi](https://github.com/OlegMazurov/Koyaanisqatsi) project.   

## How to build, test, and run

The project uses Java 8 and Maven (3.3.9), though it doesn't really have any dependencies.

To run visualization, do one of the following:
```shell
    mvn exec:java           # Acorn pattern
    mvn exec:java@counter   # DecimalCounter pattern
```
To create a jar file:
```shell
    man package
```
To run from jar:
```shell
    java -jar target/Robusta-1.0.0.jar [-w width] [-h height] [-t generations] [-p threads] [-novis] [<file>.rle]
```

## How to build, test, and run without Maven

To build
```shell
    mkdir -p target/classes
    javac -sourcepath src/main/java -d target/classes src/main/java/org/mazurov/robusta/*.java
```
To run visualization
```shell
    java -cp target/classes org.mazurov.robusta.Main -t 10000 -p 8
    java -cp target/classes org.mazurov.robusta.Main -t 10000 -p 8 -w 860 -h 1400 src/main/resources/DecimalCounter.rle
```

## License

Robusta is licensed under the Apache License, Version 2.0.

For additional information, see the [LICENSE](LICENSE) file.

