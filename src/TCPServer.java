/*
 * TCPServer.java
 *
 * Version 3.1
 * Autor: M. Huebner HAW Hamburg (nach Kurose/Ross)
 * Zweck: TCP-Server Beispielcode:
 *        Bei Dienstanfrage einen Arbeitsthread erzeugen, der eine Anfrage bearbeitet:
 *        einen String empfangen, in Grossbuchstaben konvertieren und zuruecksenden
 *        Maximale Anzahl Worker-Threads begrenzt durch Semaphore
 *
 */

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

// TCP-Server, der Verbindungsanfragen entgegennimmt
public class TCPServer {

    /* Semaphore begrenzt die Anzahl parallel laufender Worker-Threads  */
    public Semaphore workerThreadsSem;

    /* Portnummer */
    public final int serverPort;

    /* Anzeige, ob der Server-Dienst weiterhin benoetigt wird */
    public boolean serviceRequested = true;

    /* Konstruktor mit Parametern: Server-Port, Maximale Anzahl paralleler Worker-Threads */
    public TCPServer(int serverPort, int maxThreads) {
        this.serverPort = serverPort;
        this.workerThreadsSem = new Semaphore(maxThreads);
    }

    public static void main(String[] args) {
        /* Erzeuge Server und starte ihn */
        TCPServer myServer = new TCPServer(60000, 2);
        myServer.startServer();
    }

    public void startServer() {
        ServerSocket welcomeSocket; // TCP-Server-Socketklasse
        Socket connectionSocket; // TCP-Standard-Socketklasse

        int nextThreadNumber = 0;

        try {
            /* Server-Socket erzeugen */
            System.err.println("Creating new TCP Server Socket Port " + serverPort);
            welcomeSocket = new ServerSocket(serverPort);

/*            Path htuserPath = Paths.get("Testweb/.htuser");
            if(Files.exists(htuserPath)) {
                File htuser = new File(htuserPath.toUri());
                InputStream htuserStream = new BufferedReader(new FileInputStream(htuser));
                System.out.println(htuserStream.readLine);
            }*/

            while (serviceRequested) {
                workerThreadsSem.acquire();  // Blockieren, wenn max. Anzahl Worker-Threads erreicht

                System.err.println("TCP Server is waiting for connection - listening TCP port " + serverPort);
                /*
                 * Blockiert auf Verbindungsanfrage warten --> nach Verbindungsaufbau
                 * Standard-Socket erzeugen und an connectionSocket zuweisen
                 */
                connectionSocket = welcomeSocket.accept();

                /* Neuen Arbeits-Thread erzeugen und die Nummer, den Socket sowie das Serverobjekt uebergeben */
                (new TCPWorkerThread(nextThreadNumber++, connectionSocket, this)).start();
            }
        } catch (Exception e) {
            System.err.println(e);
        }
    }
}

// ----------------------------------------------------------------------------

// Arbeitsthread, der eine existierende Socket-Verbindung zur Bearbeitung erhaelt
class TCPWorkerThread extends Thread {

    public final String CHARSET = "IBM-850"; // "UTF-8"

    /* Protokoll-Codierung des Zeilenendes: CRLF */
    private final String CRLF = "\r\n"; // Carriage Return + Line Feed

    private int name;
    private Socket socket;
    private TCPServer server;
    private BufferedReader inFromClient;
    private DataOutputStream outToClient;
    private static String[] statusCodes = {"400 Bad Request",
                            "401 Unauthorized",
                            "404 Not Found",
                            "406 Not Acceptable",
                            "505 HTTP Version Not Supported",
                            "200 OK"};

    public TCPWorkerThread(int num, Socket sock, TCPServer server) {
        /* Konstruktor */
        this.name = num;
        this.socket = sock;
        this.server = server;
    }

    public void run() {
        /*System.err.println("TCP Worker Thread " + name + " is running until QUIT is received!");
        System.err.println("           Source Port: " + socket.getLocalPort() + " - Destination Port: " + socket.getPort());*/

        try {
            /* Socket-Basisstreams durch spezielle Streams filtern */
            inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream(), CHARSET));
            outToClient = new DataOutputStream(socket.getOutputStream());
            String requestLine = inFromClient.readLine();
            String[] requestLineParsed = requestLine.split(" "); // {method, URL, HTTP-Version}
            if (requestLineParsed.length != 3) {
                respond(3);
                return;
            }
            if (!requestLineParsed[0].equals("GET")) {
                respond(0);
                return;
            } // else -> it is a GET-request

            // System.out.println(Arrays.toString(requestLineParsed));

            if (!requestLineParsed[2].equals("HTTP/1.0") && !requestLineParsed[2].equals("HTTP/1.1")) {
                respond(4); // other HTTP-Version
                return;
            }

            //collect all headers
            ArrayList<String> headerLines = new ArrayList<>();
            while (true) {
                String line = inFromClient.readLine();
                if (line.length() != 0) {
                    headerLines.add(line);
                } else { // CRLFlength == 0
                    break;
                }
            }

            System.out.println("Header-fields from the client:");
            for (String headerLine : headerLines) {
                System.out.println(headerLine);
            }

            for (String headerLine : headerLines) {
                String[] splitLine = headerLine.split(": ");

                // System.out.println(Arrays.toString(splitLine));

                if (splitLine[0].equalsIgnoreCase("User-Agent")) { // HTTP header names are case-insensitive
                    if (!splitLine[1].contains("Firefox")) {
                        respond(3);
                        return;
                    }
                }
            }

            /* String userDirectory = System.getProperty("user.dir");
            System.err.println("Current directory: " + userDirectory);*/

            String url = requestLineParsed[1].substring(1); // remove "/"

            Path filePath = Paths.get(url);
            if (!Files.exists(filePath)) {
                respond(2);
                return;
            }

            outToClient.writeBytes("HTTP/1.0 " + statusCodes[5] + CRLF); // send status line

            // send entity-body
            File requestedResource = new File(url);

            String mimeType = URLConnection.guessContentTypeFromName(requestedResource.getName());

            outToClient.writeBytes("Content-Length: " + requestedResource.length() + CRLF);
            outToClient.writeBytes("Content-Type: " + mimeType + CRLF + CRLF);

            System.out.println("Response headers:");
            System.out.println("Content-Length: " + requestedResource.length());
            System.out.println("Content-Type: " + mimeType);

            InputStream fromFile = new FileInputStream(requestedResource);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fromFile.read(buffer)) > 0) { // copy the requested file
                outToClient.write(buffer, 0, len);
            }

            // socket.close(); // later, because of returns
        } catch (IOException e) {
            System.err.println(e);
            System.err.println("Connection aborted by client!");
        } finally {
            try {
                socket.close(); // Socket-Streams schliessen --> Verbindungsabbau
            } catch (IOException ignored) { }
            //System.err.println("TCP Worker Thread " + name + " stopped!");
            /* Platz fuer neuen Thread freigeben */
            server.workerThreadsSem.release();
        }
    }

    private void respond(int statusCodeIdx) throws IOException {
        outToClient.writeBytes("HTTP/1.0 " + statusCodes[statusCodeIdx] + CRLF + CRLF); // end requestLine + end header
        outToClient.writeBytes(statusCodes[statusCodeIdx] + CRLF);
    }
}

/*GET /Testweb/index.html HTTP/1.0
User-Agent: Firefox*/
