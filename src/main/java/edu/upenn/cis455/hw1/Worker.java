package edu.upenn.cis455.hw1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class Worker extends Thread{
	
    private BlockingQueue q;
    private String rootDir;
    private Worker[] threads;
    private volatile boolean terminated;
    private String fullPath;
    private ServerSocket s;
    
    public Worker(BlockingQueue q, String rootDir, Worker[] threads, ServerSocket s) {
        this.q = q;
        this.rootDir = rootDir;
        this.threads = threads;
        this.terminated = false;
        this.fullPath = "";
        this.s = s;
    }
    
    public void terminate() {
        this.terminated = true;
    }
    
    public String getPath() {
        return fullPath;
    }
   
    
    public String httpDate() {
        Calendar currentTime = Calendar.getInstance();
        SimpleDateFormat httpFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.US);
        httpFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return httpFormat.format(currentTime.getTime());
    }
    
	public void run() {
	    while (!terminated) {
    		try {
    		    Socket connection = q.deq();
    		    
    		    
    		    // read connection
                BufferedReader clientIn = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = clientIn.readLine();   

                String headerOut = "";
                
                // parse the lines
                if (line == null) {
                    connection.close();
                    clientIn.close();
                    fullPath = "";
                    continue;
                }
                String[] initLineTokens = line.split("\\s");
                
                // Faulty input handling
                if (initLineTokens.length < 3) {
                    String errorMessage = "<!DOCTYPE html><html><body><h1>400 Bad Request</h1><p1>Request was missing either the method, path, or http version</p1></body></html>";
                    
                    headerOut += "HTTP/1.1 400 Bad Request\r\n";
                    headerOut += "Date: " + httpDate() + "\r\n";
                    headerOut += "Connection: close\r\n";
                    headerOut += "Content-Type: text/html\r\n";
                    headerOut += "Content-length: "+errorMessage.getBytes("UTF-8").length + "\r\n\r\n";
                    headerOut += errorMessage;
                    
                    connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                    connection.close();
                    clientIn.close();
                    fullPath = "";
                    continue;
                    
                }
                
                String method = initLineTokens[0];
                String path = initLineTokens[1];
                String httpVer = initLineTokens[2];
                fullPath = rootDir + path;
                File file = new File(fullPath);
                
                // Gets rid of redundant names
                fullPath = file.getCanonicalPath();
                
                // Unsupported Methods
                if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
                    String errorMessage = "<!DOCTYPE html><html><body><h1>501 Not Implemented</h1><p1>Requested Method not implemented by server</p1></body></html>";
                    
                    headerOut += httpVer + " 501 Not Implemented\r\n";
                    headerOut += "Date: " + httpDate() + "\r\n";
                    headerOut += "Connection: close\r\n";
                    headerOut += "Content-Type: text/html\r\n";
                    headerOut += "Content-length: "+errorMessage.getBytes("UTF-8").length + "\r\n\r\n";
                    headerOut += errorMessage;
                    
                    connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                    connection.close();
                    clientIn.close();
                    fullPath = "";
                    continue;   
                }
                
                
                // Security handling
                if ((rootDir.startsWith(fullPath) && !rootDir.equals(fullPath))) {
                    String errorMessage = "<!DOCTYPE html><html><body><h1>403 Forbidden</h1><p1>Cannot go above root directory</p1></body></html>";
                    
                    headerOut += httpVer + " 403 Forbidden\r\n";
                    headerOut += "Date: " + httpDate() + "\r\n";
                    headerOut += "Connection: close\r\n";
                    headerOut += "Content-Type: text/html\r\n";
                    headerOut += "Content-length: "+errorMessage.getBytes("UTF-8").length + "\r\n\r\n";
                    headerOut += errorMessage;
                    connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                    connection.close();
                    clientIn.close();
                    fullPath = "";
                    continue;
                }
                
                
                // Handling HTTP/1.1 specifics
                if (httpVer.equalsIgnoreCase("HTTP/1.1")) {
                    // Make a list of all the headers
                    List<String> clientHeaders = new ArrayList<>();
                    while((line = clientIn.readLine()) != null) {
                        if (line.isEmpty()) {
                            break;
                        }
                        clientHeaders.add(line);
                    }
                    
                    
                    // Checking for certain important headers
                    boolean host = false;
                    boolean modified = false;
                    int modifiedIndex = 0;
                    boolean unmodified = false;
                    int unmodifiedIndex = 0;
                    boolean expectContinue = false;
                    for (int i = 0; i < clientHeaders.size(); i++) {
                        if (clientHeaders.get(i).toUpperCase().startsWith("HOST:")) {
                            host = true;
                        } else if (clientHeaders.get(i).toUpperCase().startsWith("IF-MODIFIED-SINCE:")) {
                            modified = true;
                            modifiedIndex = i;
                        } else if (clientHeaders.get(i).toUpperCase().startsWith("IF-UNMODIFIED-SINCE:")) {
                            unmodified = true;
                            unmodifiedIndex = i;
                        } else if (clientHeaders.get(i).toUpperCase().startsWith("EXPECT:")) {
                            expectContinue = true;
                        }
                    }
                    
                    // Error if there is no host header
                    if (!host) {
                        String errorMessage = "<!DOCTYPE html><html><body><h1>400 Bad Request</h1><p1>Request must include a host header</p1></body></html>";
                        
                        headerOut += httpVer + " 400 Bad Request \r\n";
                        headerOut += "Date: " + httpDate() + "\r\n";
                        headerOut += "Connection: close\r\n";
                        headerOut += "Content-Type: text/html\r\n";
                        headerOut += "Content-length: "+errorMessage.getBytes("UTF-8").length + "\r\n\r\n";
                        headerOut += errorMessage;
                        
                        connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                        connection.close();
                        clientIn.close();
                        fullPath = "";
                        continue;
                    }
                    
                    // Handling modified and unmodified headers
                    Date parseDate = new Date();
                    // handling the response
                    if (modified) {
                                            
                        // Potential formats for the date
                        List<SimpleDateFormat> datePatterns = new ArrayList<>();
                        datePatterns.add(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z"));
                        datePatterns.add(new SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss z"));
                        datePatterns.add(new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy"));
                        
                        // Extracting the date from the given header
                        String dateAsString = clientHeaders.get(modifiedIndex);
                        dateAsString = dateAsString.substring(dateAsString.indexOf(":")+1);
                        dateAsString = dateAsString.trim();
    
                        // Parse the string into a Date
                        for(int j = 0; j < datePatterns.size(); j++) {
                            try {
                                parseDate = datePatterns.get(j).parse(dateAsString);
                            } catch (ParseException e) {}   
                        }
                        
                        // handling the response
                        if (parseDate.after(new Date(file.lastModified()))) {
                            String errorMessage = "<!DOCTYPE html><html><body><h1>304 Not Modified</h1><p1>File has not been modified since given date</p1></body></html>";
                            
                            headerOut += httpVer + " 304 Not Modified\r\n";
                            headerOut += "Date: " + httpDate() + "\r\n";
                            headerOut += "Connection: close\r\n";
                            headerOut += "Content-Type: text/html\r\n";
                            headerOut += "Content-length: "+errorMessage.getBytes("UTF-8").length + "\r\n\r\n";
                            headerOut += errorMessage;
                            
                            connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                            connection.close();
                            clientIn.close();
                            fullPath = "";
                            continue;
                        }
                    } else if (unmodified) {
                                            
                        // Potential formats for the date
                        List<SimpleDateFormat> datePatterns = new ArrayList<>();
                        datePatterns.add(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z"));
                        datePatterns.add(new SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss z"));
                        datePatterns.add(new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy"));
                        
                        // Extracting the date from the given header
                        String dateAsString = clientHeaders.get(unmodifiedIndex);
                        dateAsString = dateAsString.substring(dateAsString.indexOf(":")+1);
                        dateAsString = dateAsString.trim();
    
                        // Parse the string into a Date
                        for(int j = 0; j < datePatterns.size(); j++) {
                            try {
                                parseDate = datePatterns.get(j).parse(dateAsString);
                            } catch (ParseException e) {}   
                        }
                        
                       
                        // Handling the response
                       
                        if (parseDate.before(new Date(file.lastModified()))) {
                            String errorMessage = "<!DOCTYPE html><html><body><h1>412 Precondition Failed</h1><p1>File has been modified since given date</p1></body></html>";
                           
                            headerOut += httpVer + " 412 Precondition Failed\r\n";
                            headerOut += "Date: " + httpDate() + "\r\n";
                            headerOut += "Connection: close\r\n";
                            headerOut += "Content-Type: text/html\r\n";
                            headerOut += "Content-length: "+errorMessage.getBytes("UTF-8").length + "\r\n\r\n";
                            headerOut += errorMessage;
                            
                            connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                            connection.close();
                            clientIn.close();
                            fullPath = "";
                            continue;
                        }
                    }
                    
                    // Sending a 100 continue response
                    if (expectContinue) {           
                        headerOut += httpVer + " 100 Continue\r\n\r\n";
                        
                        connection.getOutputStream().write(headerOut.getBytes("UTF-8"));;
                    }
                    
                }
                
                
                // Handling filepath input
                String mimeType = "";
                if (path.equalsIgnoreCase("/shutdown")) {
                    headerOut += httpVer + " 200 Ok\r\n\r\n";
                    connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                    
                    s.close();
                    
                    // Interrupt each thread. Causes them to exit while loop
                    for (Worker thread: threads) {
                        if (this.equals(thread)) {
                            continue;
                        }
                        thread.interrupt();
                    }
                    
                    terminate();
                    connection.close();
                    clientIn.close();
                    fullPath = "";
                    continue;
                    
                } else if (path.equalsIgnoreCase("/control")) {
                    String html = "<!DOCTYPE html><html><body><h1>Jacob Quon | jquon</h1>";
                    html += "<a href=\"/shutdown\">/shutdown</a>";
                    html += "<ul style=\"list-style-type:none\">";
                    for (Worker thread: threads) {
                        html += "<li>" + thread.getName() + " ----- " + thread.getState() + "   " + thread.fullPath + "</li>";
                    }
                    
                    html += "</ul></body></html>";
                    
                    headerOut += "HTTP/1.1 200 Ok\r\n";
                    headerOut += "Date: " + httpDate() + "\r\n";
                    headerOut += "Connection: close\r\n";
                    headerOut += "Content-Length: " + html.getBytes("UTF-8").length + "\r\n";
                    headerOut += "Content-Type: text/html \r\n\r\n";
                    headerOut += html;
                    
                    connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                    connection.close();
                    clientIn.close();
                    fullPath = "";
                    continue;
                    
                } else if (!file.exists()) {
                    String errorMessage = "<!DOCTYPE html><html><body><h1>404 File Not Found</h1><p1>The given path does not exist</p1></body></html>";
                    
                    headerOut += httpVer + "404 File Not Found\r\n";
                    headerOut += "Date: " + httpDate() + "\r\n";
                    headerOut += "Connection: close\r\n";
                    headerOut += "Content-Type: text/html\r\n";
                    headerOut += "Content-length: "+errorMessage.getBytes("UTF-8").length + "\r\n\r\n";
                    headerOut += errorMessage;
                    
                    connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                    connection.close();
                    clientIn.close();
                    fullPath = "";
                    continue;
                    
                } else if (file.isDirectory()) {
                    // Construct an HTML doc with links to the parent directory and all the children
                    String[] directoryChildren = file.list();
                    String html = "<!DOCTYPE html><html><body><h1>Index of " + fullPath + "</h1><ul style=\"list-style-type:none\">";
                    if (path.endsWith("/")) {
                        path = path.substring(0, path.length() - 1);
                    }
                    if (file.getParent() != null) {
                        html += "<li><a href=\"" + path + "/../\">../</a></li>";
                    }
                    for (int i = 0; i < directoryChildren.length; i++) {
                        html += "<li><a href=\"" + path + "/" + directoryChildren[i] + "\">" + directoryChildren[i] + "</a></li>";
                    }
                    html += "</ul></body></html>";
                    
                    headerOut += httpVer + " 200 Ok\r\n";
                    headerOut += "Date: " + httpDate() + "\r\n";
                    headerOut += "Connection: close\r\n";
                    headerOut += "Content-Length: " + html.length() + "\r\n";
                    headerOut += "Content-Type: text/html\r\n\r\n";
                    headerOut += html;
                   
                    connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                    connection.close();
                    clientIn.close();
                    fullPath = "";
                    continue;
                    
                } else {
                    // Setting the MIME type
                    if (fullPath.endsWith(".jpg")) {
                        mimeType += "image/jpeg";
                    } else if (fullPath.endsWith(".png")) {
                        mimeType += "image/png";
                    } else if (fullPath.endsWith(".gif")) {
                        mimeType += "image/gif";
                    } else if (fullPath.endsWith(".txt")) {
                        mimeType += "text/plain";
                    } else if (fullPath.endsWith(".html") || path.endsWith(".htm")) {
                        mimeType += "text/html";
                    } else if (fullPath.endsWith(".mp4")) {
                        mimeType += "video/mp4";
                    } else {
                        String errorMessage = "<!DOCTYPE html><html><body><h1>501 Not Implemented</h1><p1>The file type submitted is unsupported by the server</p1></body></html>";
                        
                        headerOut += httpVer + " 501 Not Implemented\r\n";
                        headerOut += "Date: " + httpDate() + "\r\n";
                        headerOut += "Connection: close\r\n";
                        headerOut += "Content-Type: text/html\r\n";
                        headerOut += "Content-length: "+errorMessage.getBytes("UTF-8").length + "\r\n\r\n";
                        headerOut += errorMessage;
                        
                        connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                        connection.close();
                        clientIn.close();
                        fullPath = "";
                        continue;
                    }
                } 
                
                
                // Printing header
                headerOut += httpVer + " 200 Ok\r\n";
                headerOut += "Connection: close\r\n";
                headerOut += "Date: " + httpDate() + "\r\n";
                headerOut += "Content-Length: " + file.length() + "\r\n";
                headerOut += "Content-Type: " + mimeType + "\r\n\r\n";
                
                connection.getOutputStream().write(headerOut.getBytes("UTF-8"));
                
                // Only send data if the method is GET
                if (method.equalsIgnoreCase("GET")) {
                    byte[] data = new byte[1000];
                    FileInputStream fileIn = new FileInputStream(fullPath);
                    
                    // Read in the data and send it out
                    int numBytes = 0;
                    while((numBytes = fileIn.read(data)) != -1) {
                        // Allow interruption mid read/write
                        if (isInterrupted()) {
                            fileIn.close();
                            clientIn.close();
                            connection.close();
                            terminate();
                            break;
                        }
                        connection.getOutputStream().write(data,0,numBytes);
                    }
                    
                    if(isInterrupted()) {
                        continue;
                    }
                }
                
                clientIn.close();
                fullPath = "";
                connection.close();
    		} catch (IOException e) {

    		} catch (InterruptedException e) {
                terminate();
    		}
    	} 
	}
}
