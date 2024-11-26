package com.example.javaonlineproject;

import java.io.*;
import java.net.*;

public class Sender{
    private PrintWriter output;
    public void setOutput(Socket socket){
        try {
            output = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void sendMessage(String message) {
        output.println(message);
    }
    public void closeOutput(){
        if (output != null) output.close();
    }
}