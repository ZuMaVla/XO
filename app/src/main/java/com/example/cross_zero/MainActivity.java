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
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.os.Handler;
import java.net.InetAddress;


public class MainActivity extends AppCompatActivity {
    ConstraintLayout rootLayout;
    String mySign;
    Boolean Play;
    Button[][] buttons = new Button[3][3];

    String[][] board = new String[3][3];

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
        buttons[0][0] = findViewById(R.id.button00);
        buttons[1][0] = findViewById(R.id.button10);
        buttons[2][0] = findViewById(R.id.button20);
        buttons[0][1] = findViewById(R.id.button01);
        buttons[1][1] = findViewById(R.id.button11);
        buttons[2][1] = findViewById(R.id.button21);
        buttons[0][2] = findViewById(R.id.button02);
        buttons[1][2] = findViewById(R.id.button12);
        buttons[2][2] = findViewById(R.id.button22);
        controlButtons(false);
        showButtons(false);

    }


    private void showYesNoResign() {        // Create an AlertDialog for resigning

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Set the title and message for the dialog
        builder.setTitle("Resignation confirmation:")
            .setMessage("Do you want to resign?")

            .setPositiveButton("Resign", (dialog, which) -> {
                Toast.makeText(MainActivity.this, "You resigned", Toast.LENGTH_SHORT).show();
                disconnectFromMqttBroker();
                controlButtons(false);
                showButtons(false);
                Button button = findViewById(R.id.buttonStart);
                button.setText(R.string.start);
            })
            .setNegativeButton("Continue playing", (dialog, which) -> Toast.makeText(MainActivity.this, "You chose to continue playing", Toast.LENGTH_SHORT).show());

        // Create and show the dialog
        AlertDialog resignDlg = builder.create();
        resignDlg.show();
    }


    public void controlButtons(Boolean state) {
        for (Button[] buttonz : buttons) {
            for (Button button : buttonz) {
                button.setEnabled(state);  // Enable the button
            }
        }
    }

    public void showButtons(Boolean state) {
        Button adHocButton = findViewById(R.id.button);
        adHocButton.setVisibility(state ? View.VISIBLE : View.INVISIBLE);

        for (Button[] buttonz : buttons) {
            for (Button button : buttonz) {
                button.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private void connectToMqttBroker() {
        new Thread(() -> {              // Initialising Mosquito in a separate thread to avoid lags
            try {
                if (mqttClient == null) {
                    mqttClient = new MqttClient("tcp://192.168.0.55:1883", MqttClient.generateClientId(), null);
                }

                if (!mqttClient.isConnected()) {
                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setCleanSession(true);
                    mqttClient.connect(options);
                    Log.d("MQTT", "(Re)connected to broker");
                } else {
                    Log.d("MQTT", "Already connected to the broker");
                }

                // Subscribe to a topic and handle incoming messages
                mqttClient.subscribe("XO", 0, (topic, message) -> {
                    String receivedMessage = new String(message.getPayload());
                    interpretMessage(topic, receivedMessage);
                });

                Log.d("MQTT", "Subscribed to topic 'XO'");

            } catch (MqttException e) {
                e.printStackTrace();
                Log.e("MQTT", "Error while connecting or subscribing");
            }
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
        }).start();
    }

    private void interpretMessage(String topic, String message) {       // MQTT message interpretation
        if ("XO".equals(topic)) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Message received:" + message, Toast.LENGTH_LONG).show();
            });
        }
        else {
            runOnUiThread(() -> {
                Toast.makeText(this, "Wrong topic!", Toast.LENGTH_SHORT).show();
            });

        }
    }

    public void initGame() {
        Play = false;

        if (Play) {
            mySign = "X";
        }
        else {
            mySign = "O";
        }
        board[0][0] = "";
        board[1][0] = "";
        board[2][0] = "";
        board[0][1] = "";
        board[1][1] = "";
        board[2][1] = "";
        board[0][2] = "";
        board[1][2] = "";
        board[2][2] = "";
        redrawCells(buttons, board);

        showButtons(true);

        connectToMqttBroker();
    }

    public void startGame(View view) {
        Button button = (Button) view;
        if (button.getText().equals(getResources().getString(R.string.start))) {
            initGame();
            button.setText(R.string.resign);
            button.setOnLongClickListener(this::resignGame);   // adding long tap functionality
        }
    }

    public boolean initEndGame(View view) {
        int[] cell1 = {0, 0};  // First cell coordinates
        int[] cell2 = {1, 1};  // Second cell coordinates
        int[] cell3 = {2, 2};  // Third cell coordinates

        endGame("draw", cell1, cell2, cell3);
        return true;
    };
    public void endGame(String outcome, int[] cell1, int[] cell2, int[] cell3) {
        rootLayout.setEnabled(false);
        new Handler().postDelayed(() -> {
            rootLayout.setEnabled(true);
        }, 3100);

        if (outcome.equals("draw")) {
            Toast.makeText(MainActivity.this, "It is a draw...", Toast.LENGTH_SHORT).show();
            rootLayout.setEnabled(true);
        }
        else {
            int defaultColor = ContextCompat.getColor(this, R.color.light_grey);
            final int outcomeColor;
            if (outcome.equals("victory")) {
                outcomeColor = ContextCompat.getColor(this, R.color.olive_green);
            } else {
                outcomeColor = ContextCompat.getColor(this, R.color.red);
            }
            for (int i = 0; i < 3; i++ ) {
                new Handler().postDelayed(() -> {
                    buttons[cell1[0]][cell1[1]].setBackgroundTintList(ColorStateList.valueOf(outcomeColor));
                    buttons[cell2[0]][cell2[1]].setBackgroundTintList(ColorStateList.valueOf(outcomeColor));
                    buttons[cell3[0]][cell3[1]].setBackgroundTintList(ColorStateList.valueOf(outcomeColor));
                }, 50 + 1000*i);
                new Handler().postDelayed(() -> {
                    buttons[cell1[0]][cell1[1]].setBackgroundTintList(ColorStateList.valueOf(defaultColor));
                    buttons[cell2[0]][cell2[1]].setBackgroundTintList(ColorStateList.valueOf(defaultColor));
                    buttons[cell3[0]][cell3[1]].setBackgroundTintList(ColorStateList.valueOf(defaultColor));
                }, 550 + 1000*i);
            }

        }
        disconnectFromMqttBroker();


    }

    public boolean resignGame(View view) {             // Returns boolean to handle long tap event

        showYesNoResign();
        return true; // Allow further processing
    }

    public void opponentTurn(View view) {
        Button button = (Button) view;
        button.setText(R.string.your_turn);
        button.setOnLongClickListener(this::initEndGame);
        redrawCells(buttons, board);
        controlButtons(true);  // Enable the button

    }

    public void redrawCells(Button[][] _buttons, String[][] _board) {
        for (int i = 0; i < _buttons.length; i++) {
            for (int j = 0; j < _buttons[i].length; j++) {
                String cell = _board[i][j];
                _buttons[i][j].setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.light_grey));
                // Check if the cell is null
                if (cell.isEmpty()) {
                    _buttons[i][j].setText("?");  // Display question mark if cell is null
                }
                else if (cell.equals("0")) {
                    _buttons[i][j].setText("O");
                }
                else {
                    _buttons[i][j].setText("X");
                }
            }
        }
    }

    public void onCellClick(View view) {
        // Cast the view to a Button
        Button button = (Button) view;
        String buttonText = button.getText().toString(); // Get the button's text

        // Check if the cell is empty
        if (buttonText.equals("?")) {
            // Change the background color of the cell
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red));

            // Set the cell's text to "X"
            button.setText(mySign);
            controlButtons(false);
            Button button2 = findViewById(R.id.button); // Find the button by its ID
            button2.setText(R.string.your_opponent_s_turn); // Update the button's text
            String buttonIdName = getResources().getResourceEntryName(view.getId());
            MqttMessage message = new MqttMessage(buttonIdName.getBytes());
            try {
                mqttClient.publish("XO", message);
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
            int i = Character.getNumericValue(buttonIdName.charAt(6));      // x coord
            int j = Character.getNumericValue(buttonIdName.charAt(7));      // y coord
            if (mySign.equals("X")) {
                board[i][j] = "1";
            }
            else {
                board[i][j] = "0";
            }
            //Toast.makeText(this, board[0][0]+board[1][1]+board[2][2], Toast.LENGTH_SHORT).show();;
        }
        else {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.black));
        }
    }

}