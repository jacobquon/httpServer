package edu.upenn.cis455.hw1;

import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

public class BlockingQueue {

    // List of sockets and max # threads
    private List<Socket> q = new LinkedList<Socket>();
    private int max;
    
    // Constructor takes max number of threads
    public BlockingQueue(int max) {
        this.max = max;
    }
    
    
    public synchronized void enq(Socket connection) throws InterruptedException {
        // If we are at the max number of connections wait before adding
        while(this.q.size() == this.max) {
            wait();
        }
        if (this.q.size() == 0) {
            notifyAll();
        }
        this.q.add(connection); 
    }
    
    public synchronized Socket deq() throws InterruptedException {
        // if we are at 0 connections wait before dequeuing
        while(this.q.size() == 0) {
            wait();
        }
        if (this.q.size() == this.max) {
            notifyAll();
        }
        return this.q.remove(0);
    }
    
}
