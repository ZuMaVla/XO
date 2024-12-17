package com.example.cross_zero;

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
import android.util.Log;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;


public class MainActivity extends AppCompatActivity {
    String mySign;
    Boolean Play = false;
    Button[][] buttons = new Button[3][3];

    String[][] board = new String[3][3];

    private MqttClient mqttClient;

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
        try {
            if (mqttClient == null) {
                // Create a new client if it doesn't exist already
                mqttClient = new MqttClient("tcp://broker.hivemq.com:1883", MqttClient.generateClientId(), null);
            }


            if (!mqttClient.isConnected()) {        // Connect only if not already connected
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                mqttClient.connect(options);
                Log.d("MQTT", "Reconnected to broker");
            } else {
                Log.d("MQTT", "Already connected to the broker");
            }

            // Subscribe to a topic
            mqttClient.subscribe("test/topic", 0, (topic, message) -> {
                Log.d("MQTT", "Message received: " + new String(message.getPayload()));
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e("MQTT", "Error while connecting or subscribing");
        }
    }



    public void disconnectFromMqttBroker() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                // Disconnect from the broker
                mqttClient.disconnect();
                Log.d("MQTT", "Disconnected from the broker");

                // Optionally, close the client
                mqttClient.close();
                Log.d("MQTT", "MQTT client closed");

            } catch (MqttException e) {
                Log.e("MQTT", "Error while disconnecting: " + e.getMessage());
            }
        } else {
            Log.d("MQTT", "MQTT client is already disconnected or not initialized");
        }
    }


    public void startGame(View view) {
        showButtons(true);
        connectToMqttBroker();
    }

    public void opponentTurn(View view) {
        Button button = (Button) view;
        button.setText("Your turn!");
        redrawCells(buttons, board);
        controlButtons(true);  // Enable the button

    }

    public void redrawCells(Button[][] _buttons, String[][] _board) {
        for (int i = 0; i < _buttons.length; i++) {
            for (int j = 0; j < _buttons[i].length; j++) {
                String cell = _board[i][j];
                _buttons[i][j].setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.light_grey));
                // Check if the cell is null
                if (cell.equals("")) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (Play) {
            mySign = "X";
        }
        else {
            mySign = "O";
        }
        board[0][0] = "0";
        board[1][0] = "";
        board[2][0] = "";
        board[0][1] = "0";
        board[1][1] = "1";
        board[2][1] = "";
        board[0][2] = "1";
        board[1][2] = "";
        board[2][2] = "";

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
            button2.setText("Your opponent's turn..."); // Update the button's text
            String buttonIdName = getResources().getResourceEntryName(view.getId());
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