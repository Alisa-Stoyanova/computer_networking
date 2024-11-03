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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
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

    public void startServer() {
        ServerSocket welcomeSocket; // TCP-Server-Socketklasse
        Socket connectionSocket; // TCP-Standard-Socketklasse

        int nextThreadNumber = 0;

        try {
            /* Server-Socket erzeugen */
            System.err.println("Creating new TCP Server Socket Port " + serverPort);
            welcomeSocket = new ServerSocket(serverPort);

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
            System.err.println(e.toString());
        }
    }

    public static void main(String[] args) {
        /* Erzeuge Server und starte ihn */
        TCPServer myServer = new TCPServer(60000, 2);
        myServer.startServer();
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
    private String requestLine;
    private String getMethod = "GET";
    String[] statusCodes = {"400 Bad Request", "401 Unauthorized", "404 Not Found", "406 Not Acceptable"};

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
            requestLine = inFromClient.readLine();
            if (!requestLine.substring(0, 3).equals(getMethod)) {
                respond(0);
            }

            //collect all headers
            ArrayList<String> headerLines = new ArrayList<>();
            while (true) {
                String line = inFromClient.readLine();
                if (line.length() != 0) {
                    headerLines.add(line);
                } else {
                    break;
                }
            }
            // System.err.println(headerLines);
            for (String headerLine : headerLines) {
                String[] splitLine = headerLine.split(": ");
                if(!(splitLine[0].equalsIgnoreCase("User-Agent") && splitLine[1].equals("Firefox"))) { // HTTP header names are case-insensitive
                    respond(3);
                }
            }

            /* Socket-Streams schliessen --> Verbindungsabbau */
            socket.close();
        } catch (
                IOException e) {
            System.err.println("Connection aborted by client!");
        } finally {
            //System.err.println("TCP Worker Thread " + name + " stopped!");
            /* Platz fuer neuen Thread freigeben */
            server.workerThreadsSem.release();
        }
    }

    private void respond(int statusCodeIdx) throws IOException {
        outToClient.writeChars("HTTP/1.0 " + statusCodes[statusCodeIdx] + CRLF + CRLF); // end requestLine + end header
    }
}
