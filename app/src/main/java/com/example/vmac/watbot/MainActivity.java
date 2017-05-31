package com.example.vmac.watbot;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.conversation.v1.ConversationService;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageRequest;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageResponse;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// IBM Watson SDK

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatRoomThreadAdapter mAdapter;
    private ArrayList messageArrayList;
    private EditText inputMessage;
    private ImageButton btnMic;
    private ImageButton btnSend;
    private Map<String, Object> context = new HashMap<>();

    private MicrophoneInputStream capture;
    private boolean listening = false;

    private final int REQ_CODE_SPEECH_INPUT = 100;
    private String room = "empty";
    private String temperature = "empty";
    static String person_id;

    JsonObjectRequest requestPost, requestGet;
    static String apiKey;



    String TAG = "PB-Danfixx";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final RequestQueue requestQueueStart = Volley.newRequestQueue(MainActivity.this);

        requestGet = getData("apikey");
        requestQueueStart.add(requestGet);               //Adds the request to the Requst Queue, which will send it to the intended address.
        if (apiKey != null) {
            Log.d(TAG, apiKey.toString());
        }

        person_id = getIntent().getStringExtra("Person_Id");

        inputMessage = (EditText) findViewById(R.id.message);
        btnMic = (ImageButton) findViewById(R.id.btn_mic);
        btnSend = (ImageButton) findViewById(R.id.btn_send);

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        messageArrayList = new ArrayList<>();
        mAdapter = new ChatRoomThreadAdapter(messageArrayList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);

        btnMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speechToText();
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkInternetConnection()) {
                    sendMessage();
                }
            }
        });
        sendMessage();
    }

    ;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.house:
                Intent intent = new Intent(this, MapsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void TextToSpeech(final String message) {
        final StreamPlayer player = new StreamPlayer();

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    TextToSpeech service = new TextToSpeech();
                    String username = getString(R.string.text_speech_username);
                    String password = getString(R.string.text_speech_password);
                    service.setUsernameAndPassword(username, password);
                    player.playStream(service.synthesize(message, Voice.GB_KATE).execute());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    // Sending a message to Watson Conversation Service
    private void sendMessage() {

        final RequestQueue requestQueue = Volley.newRequestQueue(MainActivity.this);

        final String inputmessageS = this.inputMessage.getText().toString().trim();
        Message inputMessageM = new Message();
        inputMessageM.setMessage(inputmessageS);
        inputMessageM.setId("1");
        messageArrayList.add(inputMessageM);


        JSONObject createBody = new JSONObject();                     //Creating the JSON Body for the request - it will be attached later --> getBody override method.
        try {
            createBody.put("_id", "Timestamp: " + System.currentTimeMillis());                 //Creates and puts in the values under the appropriate keys - When working with JSON it is required to use a try/catch.
            createBody.put("PersonId", person_id);
            createBody.put("InputMessage", inputmessageS);
        } catch (JSONException e) {
            Log.d(TAG,e.getMessage());
        }
        requestPost = postData(createBody);
        requestQueue.add(requestPost);              //Adds the request to the Requst Queue, which will send it to the intended address.

        //Database - timestamp, id, inputmessage

        this.inputMessage.setText("");
        mAdapter.notifyDataSetChanged();

        Thread thread = new Thread(new Runnable() {
            public void run() {

                try {
                    ConversationService service = new ConversationService(ConversationService.VERSION_DATE_2016_09_20);
                    String username = getString(R.string.conversation_username);        //Conversation API - username
                    String password = getString(R.string.conversation_password);        //Conversation API - password
                    service.setUsernameAndPassword(username, password);
                    MessageRequest newMessage = new MessageRequest.Builder().inputText(inputmessageS).context(context).build();
                    MessageResponse response = service.message(getString(R.string.conversation_workspace_id), newMessage).execute();  //Conversation API - workspace ID

                    if (response.getContext() != null) {
                        context.clear();
                        context = response.getContext();

                    }
                    Message outMessage = new Message();
                    if (response != null) {
                        if (response.getOutput() != null && response.getOutput().containsKey("text")) {
                            final String outputmessage = response.getOutput().get("text").toString().replace("[", "").replace("]", "");
                            String IPAResponse = response.getEntities().toString();


                            float floatTemperature;

                            outMessage.setMessage(outputmessage);
                            outMessage.setId("2");
                            messageArrayList.add(outMessage);
                            TextToSpeech(outputmessage);

                            JSONObject createBody = new JSONObject();                     //Creating the JSON Body for the request - it will be attached later --> getBody override method.
                            try {
                                createBody.put("_id", "Timestamp: " + System.currentTimeMillis());                 //Creates and puts in the values under the appropriate keys - When working with JSON it is required to use a try/catch.
                                createBody.put("PersonId", person_id);
                                createBody.put("OutputMessage", outputmessage);
                            } catch (JSONException e) {
                                Log.d(TAG,e.getMessage());
                            }
                            requestPost = postData(createBody);
                            requestQueue.add(requestPost);              //Adds the request to the Requst Queue, which will send it to the intended address.

                            //Database - timestamp, id and outputmessage

                                if (outputmessage.contains("Shutting")){
                                    Log.d(TAG, "Contains Shutting");

                                    try {
                                        JSONArray jsonObject = new JSONArray(IPAResponse);
                                        for (int i = 0; i < jsonObject.length(); i++){
                                            if (jsonObject.getJSONObject(i).get("entity").equals("rooms")){
                                                room = jsonObject.getJSONObject(i).get("value").toString();
                                                Log.d(TAG, room);

                                                int roomID = 0;
                                                switch (room) {
                                                    case "Dining room":
                                                        roomID = 2;
                                                        break;
                                                    case "Living room":
                                                        roomID = 1;
                                                        break;
                                                    case "Kitchen":
                                                        roomID = 3;
                                                        break;
                                                    case "Bedroom":
                                                        roomID = 4;
                                                        break;
                                                    case "Gaming room":
                                                        roomID = 5;
                                                        break;
                                                    case "Bathroom":
                                                        roomID = 6;
                                                        break;
                                                    case "Guest room":
                                                        roomID = 7;
                                                        break;
                                                }

                                                JSONObject jsonBody = new JSONObject();
                                                try {
                                                    jsonBody.put("Id", roomID);
                                                    jsonBody.put("Name", room);
                                                    jsonBody.put("AirTemperature", 0);
                                                    jsonBody.put("FloorTemperature", 0);
                                                    jsonBody.put("Setpoint", 6);
                                                    Log.d(TAG,jsonBody.toString());
                                                } catch (JSONException e) {
                                                    Toast.makeText(getApplicationContext(),
                                                            "Error: " + e.getMessage(),
                                                            Toast.LENGTH_LONG).show();
                                                }

                                                final String requestBody = jsonBody.toString();
                                                String url2 = getString(R.string.danfoss_api_room);

                                                StringRequest stringRequest = new StringRequest(Request.Method.PUT, url2,
                                                        new Response.Listener<String>() {
                                                            @Override
                                                            public void onResponse(String response) {
                                                                Log.d(TAG, "Response - " + response);
                                                                requestQueue.stop();
                                                            }
                                                        }, new Response.ErrorListener() {
                                                    @Override
                                                    public void onErrorResponse(VolleyError error) {
                                                        Log.e(TAG, "Response error \n" + error.networkResponse.statusCode);
                                                    }
                                                })

                                                {
                                                    @Override
                                                    public Map<String, String> getHeaders() throws AuthFailureError {
                                                        Map<String, String> params = new HashMap<String, String>();
                                                        params.put("Authorization", "Bearer " + apiKey);
                                                        Log.d(TAG, "Header is added");
                                                        return params;
                                                    }

                                                    @Override
                                                    public String getBodyContentType() {
                                                        return "application/json; charset=utf-8";
                                                    }

                                                    @Override
                                                    public byte[] getBody() {
                                                        try {
                                                            return requestBody == null ? null : requestBody.getBytes("utf-8");
                                                        } catch (UnsupportedEncodingException uee) {
                                                            VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                                                            return null;
                                                        }
                                                    }

                                                };

                                                requestQueue.add(stringRequest);

                                                JSONObject createBodyRoomnTemp = new JSONObject();                     //Creating the JSON Body for the request - it will be attached later --> getBody override method.
                                                try {
                                                    createBodyRoomnTemp.put("_id", "Timestamp: " + System.currentTimeMillis());                 //Creates and puts in the values under the appropriate keys - When working with JSON it is required to use a try/catch.
                                                    createBodyRoomnTemp.put("PersonId", person_id);
                                                    createBodyRoomnTemp.put("Room", room);
                                                    createBodyRoomnTemp.put("Temperature", temperature);
                                                } catch (JSONException e) {
                                                    Log.d(TAG,e.getMessage());
                                                }
                                                requestPost = postData(createBodyRoomnTemp);
                                                requestQueue.add(requestPost);              //Adds the request to the Requst Queue, which will send it to the intended address.

                                                //Database goes here - room, temperature, id and timestamp


                                                room = "empty";
                                                temperature = "empty";
                                            }
                                        }
                                    } catch (JSONException e) {
                                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                                        }

                                }else {
                                    Log.d(TAG, "Does not Contains Shutting");

                                    //Log.d(TAG, outputmessage);

                                    try {
                                        JSONArray jsonObject = new JSONArray(IPAResponse);

                                        for (int i = 0; i < jsonObject.length(); i++) {
                                            if (jsonObject.getJSONObject(i).get("entity").equals("rooms")) {

                                                room = jsonObject.getJSONObject(i).get("value").toString();
                                                Log.d(TAG, room);

                                                for (int j = 0; j < jsonObject.length(); j++) {
                                                    if (jsonObject.getJSONObject(j).get("entity").equals("sys-number")) {

                                                        temperature = jsonObject.getJSONObject(j).getString("value").toString();
                                                        Log.d(TAG, temperature);

                                                    }
                                                }
                                            } else if (jsonObject.getJSONObject(i).get("entity").equals("sys-number")) {
                                                temperature = jsonObject.getJSONObject(i).getString("value").toString();
                                                Log.d(TAG, temperature);

                                                for (int j = 0; j < jsonObject.length(); j++) {
                                                    if (jsonObject.getJSONObject(j).get("entity").equals("rooms")) {
                                                    room = jsonObject.getJSONObject(j).get("value").toString();
                                                    Log.d(TAG, room);
                                                    }
                                                }
                                            }
                                        }


                                        if (room != "empty" && temperature != "empty") {

                                            floatTemperature = Float.parseFloat(temperature);
                                            int roomID = 0;
                                            switch (room) {
                                                case "Dining room":
                                                    roomID = 2;
                                                    break;
                                                case "Living room":
                                                    roomID = 1;
                                                    break;
                                                case "Kitchen":
                                                    roomID = 3;
                                                    break;
                                                case "Bedroom":
                                                    roomID = 4;
                                                    break;
                                                case "Gaming room":
                                                    roomID = 5;
                                                    break;
                                                case "Bathroom":
                                                    roomID = 6;
                                                    break;
                                                case "Guest room":
                                                    roomID = 7;
                                                    break;
                                            }

                                            JSONObject jsonBody = new JSONObject();
                                            try {
                                                jsonBody.put("Id", roomID);
                                                jsonBody.put("Name", room);
                                                jsonBody.put("AirTemperature", 0);
                                                jsonBody.put("FloorTemperature", 0);
                                                jsonBody.put("Setpoint", floatTemperature);
                                                Log.d(TAG,jsonBody.toString());
                                            } catch (JSONException e) {
                                                Toast.makeText(getApplicationContext(),
                                                        "Error: " + e.getMessage(),
                                                        Toast.LENGTH_LONG).show();
                                            }

                                            final String requestBody = jsonBody.toString();

                                            String url2 = getString(R.string.danfoss_api_room);

                                            StringRequest stringRequest = new StringRequest(Request.Method.PUT, url2,
                                                    new Response.Listener<String>() {
                                                        @Override
                                                        public void onResponse(String response) {
                                                            Log.d(TAG, "Response - " + response);
                                                            requestQueue.stop();
                                                        }
                                                    }, new Response.ErrorListener() {
                                                @Override
                                                public void onErrorResponse(VolleyError error) {
                                                    Log.e(TAG, "Response error \n" + error.networkResponse.statusCode);
                                                }
                                            })

                                            {
                                                @Override
                                                public Map<String, String> getHeaders() throws AuthFailureError {
                                                    Map<String, String> params = new HashMap<String, String>();
                                                    //params.put("Content-Type", "application/json");
                                                    params.put("Authorization", "Bearer " + apiKey);
                                                    Log.d(TAG, "Header is added");
                                                    return params;
                                                }

                                                @Override
                                                public String getBodyContentType() {
                                                    return "application/json; charset=utf-8";
                                                }

                                                @Override
                                                public byte[] getBody() {
                                                    try {
                                                        return requestBody == null ? null : requestBody.getBytes("utf-8");
                                                    } catch (UnsupportedEncodingException uee) {
                                                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                                                        return null;
                                                    }
                                                }

                                            };

                                            requestQueue.add(stringRequest);

                                            JSONObject createBodyRoomnTemp = new JSONObject();                     //Creating the JSON Body for the request - it will be attached later --> getBody override method.
                                            try {
                                                createBodyRoomnTemp.put("_id", "Timestamp: " + System.currentTimeMillis());                 //Creates and puts in the values under the appropriate keys - When working with JSON it is required to use a try/catch.
                                                createBodyRoomnTemp.put("PersonId", person_id);
                                                createBodyRoomnTemp.put("Room", room);
                                                createBodyRoomnTemp.put("Temperature", temperature);
                                            } catch (JSONException e) {
                                                Log.d(TAG,e.getMessage());
                                            }
                                            requestPost = postData(createBodyRoomnTemp);
                                            requestQueue.add(requestPost);              //Adds the request to the Requst Queue, which will send it to the intended address.

                                            //Database goes here - Temperature, Room, Id and timestamp.

                                            room = "empty";
                                            temperature = "empty";
                                            //Log.d(TAG, jsonObject.getJSONObject(2).get("entity").toString());


                                        }
                                    } catch (JSONException e) {
                                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                                    }

                                }
                        }



                        runOnUiThread(new Runnable() {
                            public void run() {
                                mAdapter.notifyDataSetChanged();
                                if (mAdapter.getItemCount() > 1) {
                                    recyclerView.getLayoutManager().smoothScrollToPosition(recyclerView, null, mAdapter.getItemCount() - 1);

                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    private boolean checkInternetConnection() {
        // get Connectivity Manager object to check connection
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        // Check for network connections
        if (isConnected) {
            return true;
        } else {
            Toast.makeText(this, " No Internet Connection available ", Toast.LENGTH_LONG).show();
            return false;
        }

    }

    private void showMicText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                inputMessage.setText(text);
            }
        });
    }

    private void enableMicButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnMic.setEnabled(true);
            }
        });
    }

    /**
     * Showing google speech input dialog
     */
    private void speechToText() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
        }
    }

    /**
     * Receiving speech input
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    inputMessage.setText(result.get(0));
                    sendMessage();
                }
                break;
            }
        }
    }

    public JsonObjectRequest postData(JSONObject createBody) {
        requestPost = new JsonObjectRequest(Request.Method.POST, "https://16f65628-23da-4fc9-bfaf-74103b2a9509-bluemix.cloudant.com/danfixx", createBody,

                         /*[ABOVE] Create the request (JSON) for a POST method. This method includes both a header
                        and a body [BELOW - three overrides: Map (Header), getBodyContentType (Body Content Type) and getBody (Attach Body to Request)].


                        The request creation method requires five inputs: --> new JsonObjectRequest(1,2,3,4,5);
                            1. [ABOVE] Type of Request Method --> e.g. GET, PUT, POST etc.
                            2. [ABOVE] The URL --> "http://www.google.com" + necessary routing.
                            3. [ABOVE] A Null --> Currently I am not aware of what this refers to, but it has not been necessary until now.
                            4. [BELOW] Response Listener --> What to do when receiving a successful response from the server.
                            5. [BELOW] Response Error Listener --> What to do when receiving an unsuccessful response from the server.
                        */

                new Response.Listener<JSONObject>() {                       //4. Response Listener
                    @Override
                    public void onResponse(JSONObject response) {           //Finds response JSON Object on successful response.
                        Log.d(TAG, "onResponse = \n " + response);       //Logs the entire response as a String.

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {                //5. Response Error Listener
                Log.e(TAG, "response error \n" + error.networkResponse.statusCode);         //Logs error code --> e.g. 404 (Not Found), 401 (Unauthorized) etc...
            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {

                        /*Header for the Send Request --> Includes Authorization method
                        and token. In this case BASIC AUTHENTICATION with username and
                        password combined converted into Base64 Requires a try/catch*/

                try {
                    Map<String, String> map = new HashMap<String, String>();
                    String key = "Authorization";
                    String encodedString = Base64.encodeToString(String.format("%s:%s", "boadedecteddrowlaingscas", "5c88a3a419c79e2baa1fd2858a89dcdfd00f40e9").getBytes(), Base64.NO_WRAP);

                    //[ABOVE] Username and Password converted into Base64. ...format("%s:%s", "username", "password")...

                    String value = String.format("Basic %s", encodedString);

                            /*[ABOVE]In case of NO username and password, but only an API KEY --> Replace encodedString
                            with API KEY and change "Basic" to "Bearer". ...format("Basic %s", encodedString)... --> format("Bearer %s", API KEY)*/

                    map.put(key, value);            //Attaches the value (Basic [Username/Password -- Base64]) to the appropriate key (Authorization) and returns it to the request (overriding previous header value.)
                    return map;
                } catch (Exception e) {
                    Log.e(TAG, "Authentication Filure");
                }
                return super.getParams();
            }
        };
        return requestPost;
    }

    public JsonObjectRequest getData(String dataId) {
        requestGet = new JsonObjectRequest(Request.Method.GET, "https://16f65628-23da-4fc9-bfaf-74103b2a9509-bluemix.cloudant.com/danfixx/" + dataId, null,

                        /*[ABOVE] Create the request (JSON) for a GET method - It is unlikly that a get method would need a Body
                        and it is therefore not included. This decision has been made to keep the code as simple as possible.
                        If a body is required, the code and method for implementing it can be found in the POST/PUT method request.
                        This request only includes a Header override [BELOW - three overrides: Map (Header)].

                        The request creation method requires five inputs: --> new JsonObjectRequest(1,2,3,4,5);
                            1. [ABOVE] Type of Request Method --> e.g. GET, PUT, POST etc.
                            2. [ABOVE] The URL --> "http://www.google.com" + necessary routing.
                            3. [ABOVE] A Null --> Currently I am not aware of what this refers to, but it has not been necessary until now.
                            4. [BELOW] Response Listener --> What to do when receiving a successful response from the server.
                            5. [BELOW] Response Error Listener --> What to do when receiving an unsuccessful response from the server.
                        */

                new Response.Listener<JSONObject>() {               //4. Response Listener
                    @Override
                    public void onResponse(JSONObject response) {               //Finds response JSON Object on successful response.
                        Log.d(TAG, "onResponse = \n " + response);           //Logs the entire response as a String.
                        try {
                            apiKey = response.getString("apikey");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {                   //5. Response Error Listener
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "response error \n" + error.networkResponse.statusCode);         //Logs error code --> e.g. 404 (Not Found), 401 (Unauthorized) etc...
            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {

                        /*Header for the Send Request --> Includes Authorization method
                        and token. In this case BASIC AUTHENTICATION with username and
                        password combined converted into Base64 Requires a try/catch*/

                try {
                    Map<String, String> map = new HashMap<String, String>();
                    String key = "Authorization";
                    String encodedString = Base64.encodeToString(String.format("%s:%s", getString(R.string.database_username), getString(R.string.database_password)).getBytes(), Base64.NO_WRAP);

                    //[ABOVE] Username and Password converted into Base64. ...format("%s:%s", "username", "password")...

                    String value = String.format("Basic %s", encodedString);

                            /*[ABOVE]In case of NO username and password, but only an API KEY --> Replace encodedString
                            with API KEY and change "Basic" to "Bearer". ...format("Basic %s", encodedString)... --> format("Bearer %s", API KEY)*/

                    map.put(key, value);                    //Attaches the value (Basic [Username/Password -- Base64]) to the appropriate key (Authorization) and returns it to the request (overriding previous header value.)
                    return map;
                } catch (Exception e) {
                    Log.e(TAG, "Authentication Filure");
                }
                return super.getParams();
            }

        };
        return requestGet;
    }
}




