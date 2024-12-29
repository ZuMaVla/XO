package com.example.cross_zero;

import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.View;
import android.widget.Button;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.os.Handler;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    ConstraintLayout rootLayout;
    String serverIP;
    String mySign;
    Boolean joinRequested = false;
    String temporaryID;
    Button[][] cells = new Button[3][3];

    int[][] board = new int[3][3];

    private MqttClient mqttClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        rootLayout = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Populate the array with Button references
        cells[0][0] = findViewById(R.id.button00);
        cells[1][0] = findViewById(R.id.button10);
        cells[2][0] = findViewById(R.id.button20);
        cells[0][1] = findViewById(R.id.button01);
        cells[1][1] = findViewById(R.id.button11);
        cells[2][1] = findViewById(R.id.button21);
        cells[0][2] = findViewById(R.id.button02);
        cells[1][2] = findViewById(R.id.button12);
        cells[2][2] = findViewById(R.id.button22);
        controlButtons(false);
        showButtons(false);
        // Start the background task for resolving the IP address from hostname
        new Thread(() -> {
            serverIP = findServerIP();      // runs the subroutine to resolve IP address

            // After the background task completes, update the UI on the main thread
            runOnUiThread(() -> {
                if (!serverIP.isEmpty()) {  // If IP address is found, enable the button and show a toast
                    Button startButton = findViewById(R.id.buttonStart);
                    startButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Game server found; IP: " + serverIP, Toast.LENGTH_LONG).show();
                } else {                    // If IP address resolution failed, show a failure message
                    Toast.makeText(MainActivity.this, "Failed to find the game server", Toast.LENGTH_SHORT).show();
                }
            });
            if (!serverIP.isEmpty()) {connectToMqttBroker();}
        }).start();
    }

    @Override
    protected void onDestroy() {
        disconnectFromMqttBroker();
        super.onDestroy();
    }

    private String findServerIP() {

        String output;
        try {
            InetAddress address;
            address = InetAddress.getByName(getString(R.string.xo_server));
            output = address.getHostAddress();
            Log.d("HostnameResolution", "IP Address: " + serverIP);
        } catch (UnknownHostException e) {
            Log.e("HostnameResolution", "Failed to find game server", e);
            output = "";
        }
        return output;
    }

    private void showYesNoResign() {        // Create an AlertDialog for resigning

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Set the title and message for the dialog to be displayed
        builder.setTitle("Resignation confirmation:")
            .setMessage("Do you want to resign?")

            .setPositiveButton("Resign", (dialog, which) -> {
                Toast.makeText(MainActivity.this, "You resigned", Toast.LENGTH_SHORT).show();
                sendMessage("XO/server", mySign + " resign");
            })
            .setNegativeButton("Continue playing", (dialog, which) -> Toast.makeText(MainActivity.this, "You chose to continue playing", Toast.LENGTH_SHORT).show());

        // Create and show the dialog
        AlertDialog resignDlg = builder.create();
        resignDlg.show();
    }

    public void controlButtons(Boolean state) {
        for (Button[] buttonz : cells) {
            for (Button button : buttonz) {
                button.setEnabled(state);  // Enable or disable the button
            }
        }
    }

    public void showButtons(Boolean state) {
        Button adHocButton = findViewById(R.id.buttonInfo);
        adHocButton.setVisibility(state ? View.VISIBLE : View.INVISIBLE);

        for (Button[] buttonz : cells) {
            for (Button button : buttonz) {
                button.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private void subscribeToTopic(String topic) {
        new Thread(() -> {              // Initialising Mosquitto in a separate thread to avoid lags
            try {
                // Subscribe to a topic and handle incoming messages
                mqttClient.subscribe(topic, 2, (_topic, message) -> {
                    String receivedMessage = new String(message.getPayload());
                    Log.d("MQTT", "Message received: " + receivedMessage);
                    runOnUiThread(() -> interpretMessage(_topic, receivedMessage));
                });
                Log.d("MQTT", "Listening to game server..." + serverIP);
            } catch (MqttException e) {
                e.printStackTrace();
                Log.e("MQTT", "Error while subscribing");
            };
            runOnUiThread(() -> {
                Toast.makeText(this, "Subscribed to: " + topic, Toast.LENGTH_SHORT).show();
                if (topic.equals("XO")) {
                    Button startButton = findViewById(R.id.buttonStart);
                    startButton.setText(R.string.start);
                }
            });
        }).start();
    }

    private void connectToMqttBroker() {
        new Thread(() -> {              // Initialising Mosquitto in a separate thread to avoid lags
            try {
                if (mqttClient == null) {
                    mqttClient = new MqttClient("tcp://" + serverIP + ":1883", MqttClient.generateClientId(), null);
                }
                if (!mqttClient.isConnected()) {
                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setCleanSession(true);
                    mqttClient.connect(options);
                    Log.d("MQTT", "(Re)connected to broker: tcp://" + serverIP + ":1883");
                } else {
                    Log.d("MQTT", "Already connected to the broker");
                }
                runOnUiThread(() -> Toast.makeText(this, "Connected to: " + serverIP, Toast.LENGTH_SHORT).show());
            } catch (MqttException e) {
                e.printStackTrace();
                Log.e("MQTT", "Error while connecting or subscribing");
            };
        }).start();
    }

    public void disconnectFromMqttBroker() {
        new Thread(() -> {
            if (mqttClient != null && mqttClient.isConnected()) {
                try {
                    // Disconnect from the broker
                    mqttClient.disconnect();
                    Log.d("MQTT", "Disconnected from the broker");

                    // Close the client
                    mqttClient.close();
                    Log.d("MQTT", "MQTT client closed");

                } catch (MqttException e) {
                    Log.e("MQTT", "Error while disconnecting: " + e.getMessage());
                }
            } else {
                Log.d("MQTT", "MQTT client is already disconnected or not initialized");
            }
            runOnUiThread(() -> {
                Button button = findViewById(R.id.buttonStart);
                button.setText(R.string.connect);
            });
        }).start();
    }

    // MQTT message interpretation
    private void interpretMessage(String topic, String message) {
        // messages before game started
        // temporary ID is used to identify invitation to join game (that it belongs to the requester)
        if (topic.equals("XO") && joinRequested && temporaryID.equals(message.substring(2))) {
            String sign = String.valueOf(message.charAt(0));
            int turn = Integer.parseInt(String.valueOf(message.charAt(1)));
            initGame(sign, turn);
        }
        // messages after game started
        else {
            Button button = findViewById(R.id.buttonInfo);
            if (message.startsWith("turn")) {
                int i = Character.getNumericValue(message.charAt(4));
                int j = Character.getNumericValue(message.charAt(5));
                int[] opponentTurn = new int[2];
                opponentTurn[0] = i;
                opponentTurn[1] = j;
                if (mySign.equals("X")) {
                    board[i][j] = -1;
                } else if (mySign.equals("O")){
                    board[i][j] = 1;
                }
                redrawCells(cells, board, opponentTurn);
                button.performClick();
            } else if (message.endsWith("found")) {
                button.setText(R.string.your_opponent_s_turn); // Update the button's text
                button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pale_red));
            } else if (message.startsWith("victory") ||
                       message.startsWith("_defeat") ||
                       message.startsWith("___draw")) {
                unsubscribeFromTopic("player" + mySign);
                if (message.length() == 7) {
                    endGame(message.substring(0, 7), false, null, null, null);
                } else if (message.length() >= 10) {
                    int[] cell1 = new int[2];
                    cell1[0] = Character.getNumericValue(message.charAt(7));
                    cell1[1] = Character.getNumericValue(message.charAt(8));
                    int[] cell2 = new int[2];
                    cell2[0] = Character.getNumericValue(message.charAt(9));
                    cell2[1] = Character.getNumericValue(message.charAt(10));
                    int[] cell3 = new int[2];
                    cell3[0] = Character.getNumericValue(message.charAt(11));
                    cell3[1] = Character.getNumericValue(message.charAt(12));
                    endGame(message.substring(0, 7), true, cell1, cell2, cell3);
                }
            } else {
                System.out.println("No match");
            }
        }
    }

    public void initGame(String assignedSign, int isYourTurn) {
        mySign = assignedSign;

        board[0][0] = 0;
        board[1][0] = 0;
        board[2][0] = 0;
        board[0][1] = 0;
        board[1][1] = 0;
        board[2][1] = 0;
        board[0][2] = 0;
        board[1][2] = 0;
        board[2][2] = 0;

        redrawCells(cells, board, null);

        showButtons(true);

        if (isYourTurn == 1) {
            Button myButton = findViewById(R.id.buttonInfo);
            myButton.performClick();
        }

        Button myButton2 = findViewById(R.id.buttonStart);
        myButton2.setText(R.string.resign);
        myButton2.setOnLongClickListener(this::resignGame);   // adding long tap functionality

        unsubscribeFromTopic("XO");

        String topic = "player" + mySign;

        subscribeToTopic(topic);
    }

    private void unsubscribeFromTopic(String topicToUnsubscribe) {
        try {
            // Ensure the MQTT client is connected before attempting to unsubscribe
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.unsubscribe(topicToUnsubscribe);
                System.out.println("Successfully unsubscribed from topic: " + topicToUnsubscribe);
            } else {
                System.out.println("MQTT client is not connected");
            }
        } catch (MqttException e) {
            e.printStackTrace();
            System.err.println("Error while unsubscribing from topic");
        }
    }

    public void startGame(View view) {
        Button button = (Button) view;
        if (button.getText().equals(getResources().getString(R.string.connect))) {
            button.setText(R.string.connecting);
            subscribeToTopic("XO");
        };
        if (button.getText().equals(getResources().getString(R.string.start))) {
            temporaryID = UUID.randomUUID().toString();
            button.setText(R.string.starting);
            sendMessage("XO/server", "ready" + temporaryID);
            joinRequested = true;
        }
    } // used as onClick subroutine for start button

    private String extractOutcome(String outcome) {
        switch (outcome) {
            case "victory":
                return "You won!";
            case "_defeat":
                return "You lost.";
            case "___draw":
                return "It is a draw.";
            default:
                return "Outcome unknown.";
        }
    }

    public void endGame(String outcome, boolean _flag, int[] cell1, int[] cell2, int[] cell3) {
        if (_flag) {                // case of victory or defeat except in case of resign
            rootLayout.setEnabled(false);
            new Handler().postDelayed(() -> {
                rootLayout.setEnabled(true);
                // Display outcome message
                Toast.makeText(MainActivity.this, extractOutcome(outcome), Toast.LENGTH_SHORT).show();

                // Reset the game state
                mySign = null;
                joinRequested = false;
                Button button = findViewById(R.id.buttonStart);
                button.setText(R.string.start);
                Button button2 = findViewById(R.id.buttonInfo);
                button2.setText(R.string.seeking_opponent);
                button2.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.light_grey));
                showButtons(false);
                controlButtons(false);
                subscribeToTopic("XO");

            }, 3100);

            int defaultColor = ContextCompat.getColor(this, R.color.light_grey);
            final int outcomeColor;
            if (outcome.equals("victory")) {
                outcomeColor = ContextCompat.getColor(this, R.color.olive_green);
            } else {
                outcomeColor = ContextCompat.getColor(this, R.color.red);
            }

            // End game "animation"
            for (int i = 0; i < 3; i++) {
                final int delay1 = 50 + 1000 * i;
                final int delay2 = 550 + 1000 * i;

                // Set the outcome color to the cells with a delay
                new Handler().postDelayed(() -> {
                    cells[cell1[0]][cell1[1]].setBackgroundTintList(ColorStateList.valueOf(outcomeColor));
                    cells[cell2[0]][cell2[1]].setBackgroundTintList(ColorStateList.valueOf(outcomeColor));
                    cells[cell3[0]][cell3[1]].setBackgroundTintList(ColorStateList.valueOf(outcomeColor));
                }, delay1);

                // Reset the default color with a delay
                new Handler().postDelayed(() -> {
                    cells[cell1[0]][cell1[1]].setBackgroundTintList(ColorStateList.valueOf(defaultColor));
                    cells[cell2[0]][cell2[1]].setBackgroundTintList(ColorStateList.valueOf(defaultColor));
                    cells[cell3[0]][cell3[1]].setBackgroundTintList(ColorStateList.valueOf(defaultColor));
                }, delay2);
            }
        } else {        // victory or defeat due to a resignation
            if (outcome.equals("victory")) {
                Toast.makeText(MainActivity.this, "Your opponent has resigned", Toast.LENGTH_SHORT).show();
            }
            // Display outcome message
            Toast.makeText(MainActivity.this, extractOutcome(outcome), Toast.LENGTH_SHORT).show();
            // Reset the game state
            mySign = null;
            joinRequested = false;
            Button button = findViewById(R.id.buttonStart);
            button.setText(R.string.start);
            Button button2 = findViewById(R.id.buttonInfo);
            button2.setText(R.string.seeking_opponent);
            button2.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.light_grey));
            showButtons(false);
            controlButtons(false);
            subscribeToTopic("XO");
        }
    }

    public boolean resignGame(View view) {             // Returns boolean to handle long tap event
        showYesNoResign();
        return true; // Allow further processing
    }

    public void opponentTurnReceived(View view) {
        Button button = (Button) view;
        button.setText(R.string.your_turn);
        button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pale_green));
        controlButtons(true);  // Enables board for next turn
    }

    public void redrawCells(Button[][] _buttons, int[][] _board, int[] lastTurn) {
        for (int i = 0; i < _buttons.length; i++) {
            for (int j = 0; j < _buttons[i].length; j++) {
                int cell = _board[i][j];
                _buttons[i][j].setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.light_grey));
                // Check if the cell is null
                if (cell == 0) {
                    _buttons[i][j].setText("?");  // Display question mark if cell is null
                }
                else if (cell == 1) {
                    _buttons[i][j].setText("X");
                }
                else if (cell == -1){
                    _buttons[i][j].setText("O");
                }
            }
        }
        if (lastTurn != null) {
            _buttons[lastTurn[0]][lastTurn[1]].setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pale_red));
        }
    }

    private void sendMessage(String topic, String data) {
        MqttMessage message = new MqttMessage(data.getBytes());
        try {
            mqttClient.publish(topic, message); // Publish the message
            Log.d("MQTT", "Message published: " + data);
        } catch (MqttException e) {
            Log.e("MQTT", "Failed to publish message", e); // Log the error
        }
    }

    public void onCellClick(View view) {   // onClick event subroutine for board cell buttons

        Button button = (Button) view;
        String buttonText = button.getText().toString(); // Get the button's text

        // Check if the cell is empty
        if (buttonText.equals("?")) {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pale_green));

            // Set the cell's text to "X"
            button.setText(mySign);
            controlButtons(false);
            Button button2 = findViewById(R.id.buttonInfo); // Find the button by its ID
            button2.setText(R.string.your_opponent_s_turn); // Update the button's text
            button2.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pale_red));
            String buttonIdName = getResources().getResourceEntryName(view.getId());
            String topic = getString(R.string.serverXO); // Get the topic name from resources
            String data = mySign + " " + buttonIdName.substring(6);
            sendMessage(topic, data);
            int i = Character.getNumericValue(buttonIdName.charAt(6));      // x coord
            int j = Character.getNumericValue(buttonIdName.charAt(7));      // y coord
            if (mySign.equals("X")) {
                board[i][j] = 1;
            }
            else if (mySign.equals("O")){
                board[i][j] = -1;
            }
        }
        else {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.black));
        }
    }
}