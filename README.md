# Surviving loss of threads in a parallel program

Robusta is a research project that:
* investigates an approach to thread-loss tolerant parallel computation
* provides a proof-of-concept implementation 
* presents an asynchronous parallel wait-free data-synchronized implementation of [Conway's Game of Life](https://en.wikipedia.org/wiki/Conway%27s_Game_of_Life), or a generic cellular automaton.

## Introduction
What would it take for a program running a pool of threads working on a common goal to tolerate loss of one or several threads?
We take the reliability of our modern multi-CPU systems for granted. Even if our hardware may malfunction, f.e. due to overheating,
it usually affects the system at so low a level that even the OS can't cope with that and panics, hopefully, saving what it can.
We never worry about dealing with that kind of problems at application level. But _if_ we were? Or should I have said _when_?

To achieve their common goal deterministically our threads need to synchronize. If synchronization is used to exclusively get hold of some resource
we have little hope of ensuring the consistency of that resource's state and releasing it properly if the thread owning it accidentally dies.
That rules out _locks_, _semaphores_ and the entire paradigm of _task parallelism_ (at least in conventional scheduler based implementation). _Barriers_ are used differently but they won't tolerate loss of a thread either.
Stepping down to _lock-free_ synchronization we still have to avoid any way of acquiring exclusive access to any part of the shared state. 
Memory is to be shared to the extreme now: if a thread wants to update a shared location but dies, another thread should be able to come and finish the job.
On the other hand, threads, the real workers, shall have no possessions.     

## Test case
The Game of Life is my [_Drosophila melanogaster_](https://en.wikipedia.org/wiki/Drosophila_melanogaster#History_of_use_in_genetic_analysis) of parallel computing. The reasons are simple: it is a universal computing model, ubiquitous in Nature (anything described by partial differential equations), highly concurrent, with fine granularity, and lots of dependencies.
Many parallel frameworks fail to bring out the most out of Life due to various deficiencies: they may not scale (anything that has to use barriers between generations or just can't express the inherent concurrency) or
be too heavy for the finest granularity (cells modeled by processes, threads, fibers, actors, anything with queues, etc.). Task parallelism is usually adequate when not inhibited by framework (static, syntactic tasks in Cilk, OpenMP, f.e.).
Here, I go one step further and push concurrency granularity down to data. Threads are still used, of course, but they are totally detached from cells and concurrent tasks as there is no task scheduler. 

## Data synchronization
What do I mean exactly when saying I'm pushing concurrency granularity down to data?
Each cell is represented by a current "task", which is  a tuple combining  cell generation _ts_ (30 bits) and two cell states (1 bit each) at _ts-1_ and _ts_.
Two states are used the same way two arrays are used for consecutive generations in the straightforward implementation of Life: once we compute a new state of a cell at time _ts_ we can't yet discard its previous state at _ts - 1_,
which is needed to update cell's neighbors to generation _ts_. We won't need, however, to synchronize all cells to update to generation _ts_ before any can be updated to _ts + 1_.
If you stop computation at an arbitrary moment you'll likely find cells from multiple generations, 
though any two space-neighbors (row, column difference at most 1) will always be time-neighbors (generation difference at most 1) as well. This is essential to bringing out all concurrency there is in Life.        

Cell's "task" is ready to be executed when all cell's neighbors (or rather their "tasks") have data necessary for update, i.e. they are at the same or next generation.
If a worker finds a neighbor from an earlier generation it just moves to that neighbor and tries to update it first. Is it possible for a worker to see a neighbor from a distant future?
Sure. That means that somebody else has worked the cell and its neighbors and the current worker's state is stale (the thread might have been preempted and kept off-CPU for some time). That's no problem. We just redeploy the slacker to another location in hope we are avoiding further conflicts.     

Once we have computed a new state we update the cell (its "task") not just atomically but making sure there has been no interference from other workers,
i.e. using _compareAndSet_. If it fails then somebody got there first so, again, we just redeploy our worker to another location to avoid further conflicts.  

It should be clear now that workers are totally dispensable in this computation: any "task" has only one possible
next value and it doesn't matter who makes the update as long as somebody does it eventually and the algorithm is clever enough to ensure that.  
If you worry about counter overflow in "task" generation, change _Integer_ to _Long_ and stop worrying. In fact, the algorithm should work correctly after counter overflow. 
The size of the counter space provides correctness guarantee with very high probability against a stale worker that computed a value and kept it to itself until the rest progressed through the entire counter space and produced the same value for the cell to be updated but in a different context so the stale update is wrong.
Changing _Integer_ to _Byte_ would reveal that vulnerability.

At this point I can't help but notice that the actual implementation is much more concise than the description of all the mechanisms above.  

## Chaos Monkey
To test thread-loss tolerance, a "chaos monkey" is released immediately after worker thread creation in [Life.execute()](src/main/java/org/mazurov/robusta/Life.java).
The main thread picks a random thread from the current pool and stops it. 
Although Java has deprecated Thread.stop() exactly because it causes hard to diagnose and recover from troubles, I find it not brutal enough to simulate truly asynchronous and instantaneous loss of threads like hardware malfunction.
It's good enough for proof-of-concept demonstration, nevertheless. Occasionally you may see Java exceptions caused by _java.lang.ThreadDeath_. Those are evidence the threads do not die gracefully - a desired goal rather than a sign of a problem.  

If all worker threads are killed, the computation ceases. To prevent that, workers constantly watch over each other and create new workers when a loss is detected. 
Of course, all that has to be done in a thread-loss tolerant fashion. All workers are organized in a directed ring so that each worker is responsible for monitoring and recovering only its next neighbor. No synchronization is needed in that scheme (but _volatile_ is enforced by using _AtomicReferenceArray_ for the pool).   
In general, detection of a non-functioning thread should be more elaborate. For demonstration purposes, in the provided implementation, worker removal is almost instantly communicated to the pool allowing for quick detection and recovery. 

## Forwardly Answered Questions
_The program hangs. Is it a bug?_  
Not necessarily. Stopped and unlinked threads need to be garbage collected and destroyed, which may freeze the program for some time. Just be patient.
On the other hand, the chaos monkey may win and kill all worker threads on a slower system. The goal of this exercise is to show that it can be beaten.  

_Why are the cells colored?_  
They are colored to show which thread updated them last. No clear patterns in coloring are due to lack of any synchronization between threads in picking up cells for update (yet the result is always deterministic).

_Why does DecimalCounter end up in ruins?_  
The design works in infinite space. The provided implementation works in bounded space wrapped around a torus. When the first digit hits the factory the whole thing breaks down. The result is always deterministic, though. 

_What does "asynchronous parallel wait-free data-synchronized" mean?_  
Data-synchronized is explained above. For the rest, refer to my [Koyaanisqatsi](https://github.com/OlegMazurov/Koyaanisqatsi) project.

_Will Robusta survive in space (any real-life radiation-hard environment)?_  
No. It's a Java program. The JVM and the OS it runs on are not robust.

_Robusta shows how to survive a CPU glitch. Can it also survive a memory glitch?_  
No. If a state bit is flipped it will be accepted as valid by any worker processing it. To deal with memory glitches we need memory redundancy. See my [Koyaanisqatsi](https://github.com/OlegMazurov/Koyaanisqatsi) project.   

_The concept of ownership is very useful in reasoning about concurrent program correctness. Are you suggesting it's an anti-pattern for parallel programs?_  
That it violates robustness is rather the point of breakage but before that it affects scalability: any delay in execution of a thread holding a resource creates a bottleneck for all dependent computations.
That becomes obvious with a task-based implementation of Life when the number of threads is increased beyond the number of physical cores: performance reaches its peak and then drops with more threads adding even more to the drop.     

_Does this implementation scale well then?_  
With Chaos Monkey commented out, it scales pretty well. Performance doesn't drop and remains more or less stable when the number of threads is several times the number of cores.   


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
    java -jar target/Robusta-1.0.0.jar [-w width] [-h height] [-t generations] [-p threads] [-novis] [-o output file] [<file>.rle]
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

