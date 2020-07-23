package edu.upenn.cis455.hw1;
import java.io.*;
import java.net.*;

class HttpServer {
    public static void main(String args[]) throws IOException {
        
        // Handling input cases
        if (args.length == 0) {
            System.out.println("Jacob Quon jquon");
            return;
        } else if (args.length != 2) {
            System.out.println("INPUT ERROR: Number of arguments must be 0 or 2");
            return;
        } 
        
        int numConnections = 1000;
        BlockingQueue q = new BlockingQueue(numConnections);
        
        // Opening up a connection with specified port
        ServerSocket s = new ServerSocket(Integer.parseInt(args[0]));
        System.out.println("Listening for connection on port "+ args[0] +"....");
        
        // Initializing all the threads and putting them to work
        int numThreads = 100;
        Worker[] threads = new Worker[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Worker(q, args[1], threads, s);
            threads[i].start();
        }
        
        
        // Listens on the port waiting for user requests
        while (true) {
            // wait for incoming connection
            try {
                q.enq(s.accept());
            } catch (InterruptedException e) {
                System.err.println(e);
                e.printStackTrace();
            } catch (SocketException e) {
                System.out.println("Server has shut down");
                return;
            }
        }
    }
}
