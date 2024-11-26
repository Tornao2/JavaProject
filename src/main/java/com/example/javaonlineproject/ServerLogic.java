package com.example.javaonlineproject;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ServerLogic extends Application {
    private Thread connectingThread;
    private final ArrayList <Thread> listenerThreads = new ArrayList<>();
    private ServerSocket serverSocket;
    private final LinkedHashMap<String, UserInfo> userMap = new LinkedHashMap <>();
    private final ArrayList <UserInfo> waitingToPlay = new ArrayList<>();
    private final HashMap<UserInfo, UserInfo> playersInProgress = new HashMap<>();
    private static final String FILEPATH = "LoginData.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Button createExitButton() {
        Button loginButton = new Button("Exit");
        loginButton.setFont(new Font(20));
        loginButton.setOnAction(_ -> stopAll());
        return loginButton;
    }
    private VBox createVBox() {
        VBox organizer = new VBox(12);
        organizer.setStyle("-fx-background-color: #1A1A1A;");
        organizer.setMinSize(300, 210);
        organizer.setPadding(new Insets(10, 8, 10, 8));
        organizer.setAlignment(Pos.CENTER);
        return organizer;
    }
    private void manageScene(VBox organizer, Stage primaryStage) {
        Scene scene = new Scene(organizer);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Server");
        primaryStage.show();
        organizer.requestFocus();
    }
    public void start(Stage primaryStage) {
        try {
            serverSocket = new ServerSocket(12345);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Button exitButton = createExitButton();
        VBox organizer = createVBox();
        organizer.getChildren().addAll(exitButton);
        manageScene(organizer, primaryStage);
        logic();
    }

    private void logic() {
        Runnable mainListener = () -> {
            int disconnectCheck = 0;
            long startTime = System.currentTimeMillis();
            UserInfo userServed = userMap.lastEntry().getValue();
            while (!Thread.currentThread().isInterrupted()) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime >= 5000) {
                    userServed.getUserOutput().sendMessage("PROBED");
                    disconnectCheck++;
                    startTime = System.currentTimeMillis();
                }
                if (disconnectCheck >= 4) {
                    stopThisUser(userServed);
                    return;
                }
                String move = userServed.getUserInput().receiveMessage();
                if (move == null) {
                    continue;
                }
                String[] moveSplit = move.split(",");
                switch (moveSplit[0]){
                    case "PROBED":
                        disconnectCheck = 0;
                        break;
                    case "GETENEMY":
                        String enemyList = makeEnemyList(userServed);
                        waitingToPlay.add(userServed);
                        userServed.getUserOutput().sendMessage(enemyList);
                        sendListToEveryoneBesides(userServed);
                        break;
                    case "REMOVE":
                        waitingToPlay.remove(userServed);
                        sendListToEveryoneBesides(userServed);
                        break;
                    case "SOCKETERROR":
                        stopThisUser(userServed);
                        return;
                    case "INVITE":
                        String enemyNick = moveSplit[1];
                        UserInfo searchedUser = userMap.get(enemyNick);
                        searchedUser.getUserOutput().sendMessage("INVITED," + userServed.getUsername());
                        break;
                    case "PLAY":
                        String firstNick = moveSplit[1];
                        String secondNick = userServed.getUsername();
                        UserInfo firstUser = userMap.get(firstNick);
                        firstUser.getUserOutput().sendMessage("MATCH");
                        UserInfo secondUser = userMap.get(secondNick);
                        secondUser.getUserOutput().sendMessage("MATCH");
                        waitingToPlay.remove(firstUser);
                        waitingToPlay.remove(secondUser);
                        sendListToEveryoneBesides(firstUser);
                        firstUser.getUserOutput().sendMessage("X,0");
                        secondUser.getUserOutput().sendMessage("0,X");
                        playersInProgress.put(firstUser, secondUser);
                        playersInProgress.put(secondUser, firstUser);
                        break;
                    case "WIN":
                        //+1 lose dla tego co przegrał + 1 win dla drugiego zapisać do pliku
                        playersInProgress.get(userServed).getUserOutput().sendMessage("LOST");
                        break;
                    case "DRAW":
                        //+1 draw dla obu zapisać w pliku
                        playersInProgress.get(userServed).getUserOutput().sendMessage("DRAW");
                        break;
                    case "MOVE":
                        String row = moveSplit[1];
                        String col = moveSplit[2];
                        playersInProgress.get(userServed).getUserOutput().sendMessage("MOVE," + row + "," + col);
                        break;
                    case "RESIGNED":
                        //+1 lose dla tego co zrezygnował + 1 win dla drugiego zapisać do pliku
                        playersInProgress.get(userServed).getUserOutput().sendMessage("ENEMYRESIGNED");
                        playersInProgress.remove(playersInProgress.get(userServed));
                        playersInProgress.remove(userServed);
                        break;
                    case "QUIT":
                        playersInProgress.get(userServed).getUserOutput().sendMessage("ENEMYQUIT");
                        playersInProgress.remove(playersInProgress.get(userServed));
                        playersInProgress.remove(userServed);
                        break;
                    case "REMATCH":
                        playersInProgress.get(userServed).getUserOutput().sendMessage("REMATCH");
                        break;
                    case "ACCEPT":
                        playersInProgress.get(userServed).getUserOutput().sendMessage("ACCEPT");
                        userServed.getUserOutput().sendMessage("ACCEPT");
                        userServed.getUserOutput().sendMessage("X,0");
                        playersInProgress.get(userServed).getUserOutput().sendMessage("0,X");
                        break;
                    case "NAME":
                        userServed.getUserOutput().sendMessage(playersInProgress.get((userServed)).getUsername());
                        break;
                    default:
                        break;
                }
            }
        };
        Runnable connectionListener = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                UserInfo temp = new UserInfo();
                Socket connection;
                try {
                    connection = serverSocket.accept();
                } catch (IOException _) {
                    return;
                }
                temp.setUserSocket(connection);
                temp.setUserInput(connection);
                temp.setUserOutput(connection);
                String loginAttempt;
                loginAttempt = temp.getUserInput().receiveMessage();
                String[]data = loginAttempt.split(",");
                //Tutaj można dodać sprawdzanie hasła i loginu z data[1] i data[2]
                if(isLoginValid(data[1], data[2])){
                    //Uzytkownik istnieje
                    temp.setUsername(data[1]);
                    temp.getUserOutput().sendMessage("ALLOWED0");
                    userMap.put(temp.getUsername(), temp);
                    Thread listener = new Thread(mainListener);
                    listener.setDaemon(true);
                    listenerThreads.add(listener);
                    listener.start();
                }
                else{
                    //Uzytkownik nie istnieje
                    registerNewUser(data[1], data[2]);
                    temp.setUsername(data[1]);
                    temp.getUserOutput().sendMessage("ALLOWED");
                    userMap.put(temp.getUsername(), temp);
                    Thread listener = new Thread(mainListener);
                    listener.setDaemon(true);
                    listenerThreads.add(listener);
                    listener.start();
                }/*
                temp.setUsername(data[1]);
                temp.getUserOutput().sendMessage("ALLOWED");
                userMap.put(temp.getUsername(), temp);
                Thread listener = new Thread(mainListener);
                listener.setDaemon(true);
                listenerThreads.add(listener);
                listener.start();*/
            }
        };
        connectingThread = new Thread(connectionListener);
        connectingThread.setDaemon(true);
        connectingThread.start();
    }

    private boolean isLoginValid(String username, String password) {
        try {
            List<LoginData> users = loadUsersFromFile();
            for (LoginData user : users) {
                if (user.getLogin().equals(username) && user.getPassword().equals(password)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("isLoginValid exception");
        }
        return false;
    }
    private List<LoginData> loadUsersFromFile() throws IOException {
        File file = new File(FILEPATH);
        if(!file.exists()){
            return new ArrayList<>();
        }
        return objectMapper.readValue(file, objectMapper.getTypeFactory().constructCollectionType(List.class, LoginData.class));
    }
    private void registerNewUser(String username, String password) {
        try {
            List<LoginData> users = loadUsersFromFile();
            users.add(new LoginData(username, password));
            saveUsersToFile(users);
        } catch (IOException e) {
            System.err.println("registerNewUser exception");
        }
    }
    private void saveUsersToFile(List<LoginData> users) throws IOException {
        objectMapper.writeValue(new File(FILEPATH), users);
    }

    private void sendListToEveryoneBesides(UserInfo userServed) {
        for (UserInfo users: waitingToPlay) {
            String enemyList = makeEnemyList(users);
            if (!users.getUsername().equals(userServed.getUsername())) {
                users.getUserOutput().sendMessage("REFRESH");
                users.getUserOutput().sendMessage(enemyList);
            }
        }
    }
    private String makeEnemyList(UserInfo userServed) {
        String temp = "ENEMIES";
        for (UserInfo users: waitingToPlay) {
            if (!users.getUsername().equals(userServed.getUsername())) {
                temp = temp.concat("," + users.getUsername());
            }
        }
        return temp;
    }
    private void stopAll() {
        connectingThread.interrupt();
        for (Thread thread: listenerThreads)
            thread.interrupt();
        for (UserInfo reader : userMap.values()) {
            reader.closeConnection();
        }
        userMap.clear();
        listenerThreads.clear();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("stopAll exception");
            }
        }
        System.exit(0);
    }
    private void stopThisUser(UserInfo userServed) {
        userServed.closeConnection();
        userMap.remove(userServed.getUsername());
    }
}
