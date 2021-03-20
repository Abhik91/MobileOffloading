package com.example.mobileoffloading;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.StrictMode;
import android.text.Html;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;



public class MainActivity extends AppCompatActivity {

    public static String EXTRA_TEXT = "com.example.mobileoffloading.EXTRA_TEXT";

    WifiManager wifiManager;
    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;
    IntentFilter intentFilter;
    Button btnDiscover;
    ListView listView;
    TextView connectionStatus;
    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    ArrayList<Parcelable> dummy = new ArrayList<>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;
    TextView battery, read_msg_box;
    Button btLocation, btBattery, btnSend, btnCompute, btnDisconnect;
    TextView t1, t2, t3, t4, t5;
    FusedLocationProviderClient fusedLocationClient;
    EditText matrix1, matrix2;
    public long globalStart;

    public ArrayList<String> addressMap = new ArrayList<String>();
    public HashMap<String, Object> sendReceiveRegister = new HashMap<>(); // Used internally
    public HashMap<String, String> slaveInformationMap = new HashMap<>(); // Used to send so, needs to be String, String
    public HashMap<String, String> batteryLevels = new HashMap<>();
    public String OWNER = "SLAVE";
    public int[][] A1 = new int[4][4];
    public int[][] A2 = new int[4][4];
    public int[][] Out = new int[4][4];
    public static int counter = 0;

    String data = null;
    static final int MESSAGE_READ = 1;

    String uniqueID = UUID.randomUUID().toString();

    ServerClass serverClass;
    ClientClass clientClass;
    SendReceive sendReceive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        read_msg_box = findViewById(R.id.readMsg);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        globalStart-=800;



        initialize();
        callListener();
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    if (tempMsg.contains("\"time\"") && tempMsg.contains("\"i\"")) {
                        try {
                            slaveComputation(tempMsg);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else if (tempMsg.contains("offloadDone")) {
                        try {
                            uponReceiving(tempMsg);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else if (tempMsg.contains("periodicResponse")) {
                        sendPeriodicResponse();
                    } else {
                        try {
                            initialConnect(tempMsg);
                            // masterCompute();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    break;
            }
            return true;
        }
    });

    /**
     * Extracting battery information from here. The battery information is stored as part of the
     * slaveInformationMap
     * */
    BroadcastReceiver batteryInfo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
//            battery.setText("Battery Level: " + batteryLevel + "%");
            slaveInformationMap.put("battery", batteryLevel + "");
        }
    };

    /**
     * Using the geocoder we are extracting the longitude and latitude values.
     * */
    private void getLocation() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            try {
                                // Logic to handle location object
                                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
//                                t1.setText(Html.fromHtml("<b>Latitude : </b>" + addresses.get(0).getLatitude()));
//                                t2.setText(Html.fromHtml("<b>Longitude : </b>" + addresses.get(0).getLongitude()));
                                data = (new StringBuilder()).append(addresses.get(0).getLatitude()).append("\n").append(addresses.get(0).getLongitude()).append("\n").toString();
                                slaveInformationMap.put("lattitude", addresses.get(0).getLatitude() + "");
                                slaveInformationMap.put("longitude", addresses.get(0).getLongitude() + "");
                                saveToTxt(data);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }

    /**
     * Used to disconnect a particular peer device forcibly if it fails to respond.
     * We decided not to use this function for now as we can just reassign the task to other devices
     * without having to disconnect.
     * */
    public void disconnectPeers(int position) {
        final WifiP2pDevice device = deviceArray[findPos(addressMap.get(position))]; //get device name
        System.out.println("Device Name is - " + device.deviceName);
        if (device.status == WifiP2pDevice.CONNECTED) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Toast.makeText(getApplicationContext(), device.deviceName + " removed", Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onFailure(int reason) {
                    Toast.makeText(getApplicationContext(), device.deviceName + " was not removed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void saveToTxt(String content) {
        try {
            File path = getExternalFilesDir(null);
            File file = new File(path, "Location.txt");
            FileWriter writer = new FileWriter(file);
            writer.append(content);
            writer.flush();
            writer.close();
        } catch (IOException e) {
        }
    }

    private void callListener() {

        btnDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Discover Peers
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        System.out.println("XY Found");
                        connectionStatus.setText("Discovery Started");
                    }

                    @Override
                    public void onFailure(int reason) {
                        System.out.println("Peer not Found");
                        connectionStatus.setText("Discovery Failed");
                    }
                });


            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                connect(position);
            }
        });

        btLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Get the Location Information
                if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    getLocation();
                }
                else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 44);
                }

                System.out.println("Getting Battery information in btLocation");

                //Get Battery details
                MainActivity.this.registerReceiver(MainActivity.this.batteryInfo, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            }

        });

        btnCompute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                A1 = convertStringToArray(String.valueOf(matrix1.getText()));
                A2 = convertStringToArray(String.valueOf(matrix2.getText()));
                try {
                    globalStart = System.currentTimeMillis();
                    masterCompute();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JSONObject jsonObj = new JSONObject(slaveInformationMap);
                String msgString = jsonObj.toString();
                String msg = msgString;
                sendReceive.write(msg.getBytes());
            }
        });


    }

    private void connect(int position) {
        final WifiP2pDevice device = deviceArray[position];
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Connected to " + device.deviceName, Toast.LENGTH_SHORT).show();
                addressMap.add(device.deviceAddress);
//                try {
//                    offloading(0); // DELETE THIS CODE
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(), "Not Connected to " + device.deviceName, Toast.LENGTH_SHORT).show();


            }
        });
    }

    private void initialize() {

        btnDiscover = (Button) findViewById(R.id.buttonDiscover);
        listView = (ListView) findViewById(R.id.peerListView);
        connectionStatus = (TextView)findViewById(R.id.connectionStatus);
        btnSend = (Button)findViewById(R.id.sendButton);
        btnCompute = (Button)findViewById(R.id.btnCompute);
        //btnDisconnect = (Button) findViewById(R.id.buttonDisconnect);
        btLocation = (Button)findViewById(R.id.getButton);
        matrix1 = (EditText) findViewById(R.id.Matrix1Text);
        matrix2 = (EditText) findViewById(R.id.Matrix2Text);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        assert manager != null;
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

        //Create an intent filter and add the same intents that your broadcast receiver checks for
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);


    }


    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (!peerList.getDeviceList().equals(peers)) {
                //Clear the peers

                peers.clear();
                peers.addAll(peerList.getDeviceList());
                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];
                int index = 0;

                for (WifiP2pDevice device : peerList.getDeviceList()) {
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                listView.setAdapter(arrayAdapter);


            }

            if (peers.size() == 0) {
                Toast.makeText(getApplicationContext(), "No Device Found", Toast.LENGTH_SHORT).show();

            }

        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            final InetAddress groupOwnerAddress = info.groupOwnerAddress;

            if (info.groupFormed && info.isGroupOwner) {
                connectionStatus.setText("Host");
                serverClass = new ServerClass();
                serverClass.start();
            } else if (info.groupFormed) {
                connectionStatus.setText("Client");
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
            }


        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    public class ServerClass extends Thread {
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8888);
                socket = serverSocket.accept();
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SendReceive extends Thread {
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(Socket skt) {
            socket = skt;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (socket != null) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ClientClass extends Thread {
        Socket socket;
        String hostAdd;

        public ClientClass(InetAddress hostAddress) {
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, 8888), 500);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Before sending any data, this function is invoked. The connectIndex function conencts to
     * a target mac address which was already acquired when the owner of the other device consents to
     * participate in the offloading process.
     * */
    public void connectIndex(int deviceIndex) {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int reason) {
            }
        });
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        WifiP2pDevice device = deviceArray[findPos(addressMap.get(deviceIndex))];
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int reason) {
            }
        });
    }

    /**
     * Finds the position of the given mac address in the device array and returns it.
     * */
    public int findPos(String mac) {
        for (int i = 0; i < deviceArray.length; i++) {
            if (deviceArray[i].deviceAddress.equalsIgnoreCase(mac)) {
                return i;
            }
        }
        return 0;
    }


    /**
     * Initiates the Offloading Process. Can be invoked only by the device that hits the Compute button
     * This function divides the matrix into 1 or 2 parts based on the number of slave devices present and
     * it sends to all the Slave devices connected one by one. When the master offloads data, it also puts
     * the details of the Slave device in the sendReceiveRegister variable. Details include Mac Address, and
     * data sent, time allotted.
    */
    public void masterCompute() throws InterruptedException {
        Toast.makeText(getApplicationContext(), "Master side tasks distributed", Toast.LENGTH_SHORT).show();
        connectionStatus.setText("Master");
        startPeriodicMonitoring(); // To start periodic monitoring of slaves
        for (int i = 0; i < addressMap.size(); i++) {
            System.out.println("Inside Master Compute for addressMap for i - " + i);
            try {
                HashMap<String, String> generatedMap = offloading(i);
                HashMap<String, Object> offloadRegister = new HashMap<>();
                long time = System.currentTimeMillis() + Integer.parseInt(generatedMap.get("time").toString());
                offloadRegister.put("recoverBy", time);
                offloadRegister.put("i", generatedMap.get("i"));
                offloadRegister.put("j", generatedMap.get("j"));
                sendReceiveRegister.put(addressMap.get(i), offloadRegister);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            //disconnectPeers(i);
        }
    }

    /**
     * This function generates the datamap to be sent to the slave devices from the master during offloading
     * */
    public HashMap<String, String> generateMap(int index) {
        HashMap<String, String> dataMap = new HashMap<>();
        dataMap.put("index", index + "");
        for (int i = index * 2; i < index * 2 + 4; i++) {
            dataMap.put(0 + "", arrayToString(A1[0])); // You need to do 0*4, 0,0
            dataMap.put(1 + "", arrayToString(A1[1])); // You need to do 1*4, 1,0
            dataMap.put(2 + "", arrayToString(A1[2])); // You need to do 2*4, 2,0
            dataMap.put(3 + "", arrayToString(A1[3])); // You need to do 3*4, 3,0
            dataMap.put((i + 4) + "", arrayToString(getTranspose(A2)[i])); //Extract column 1
        }
        dataMap.put("i", index + "");
        dataMap.put("j", index + "");
        dataMap.put("time", (((index + 1) * 50)) + "");
        dataMap.put("mac", addressMap.get(index));// mac of the device this data is going to
        return dataMap;
    }

    public int[][] getTranspose(int[][] A2) {
        int[][] returnArr = new int[4][4];
        for (int i = 0; i < A2.length; i++) {
            for (int j = 0; j < A2[0].length; j++) {
                returnArr[i][j] = A2[j][i];
            }
        }
        return returnArr;
    }

    /**
     * This function is run on the Master and is only invoked by the MasterCompute().
     * This is used to divide the given tasks into 1 or 2 slave devices at most. It can be scaled
     * to support multiple devices in future.
     * */
    public HashMap<String, String> offloading(int index) throws JSONException {
        connectIndex(index);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        HashMap dataMap = generateMap(0);
        JSONObject jsonObj = new JSONObject(dataMap);
        String jsonString = jsonObj.toString();
        String msg = jsonString;

        sendReceive.write(msg.getBytes());
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return dataMap;
    }

    public String arrayToString(int[] A) {
        String arrayString = "{";
        for (int i = 0; i < A.length; i++) {
            arrayString = arrayString + A[i] + ",";
        }
        arrayString = arrayString.substring(0, arrayString.length() - 1);
        arrayString = arrayString + "}";
        return arrayString;
    }

    /**
     * Does the computation part on the Slave device. This function is accessible only if a
     * Master sends dataMap with time allotted and the data values. This function extracts the
     * information and sends to the array multiplication functions and returns the final JSON variable
     * */
    public void slaveComputation(String jsonString) throws JSONException {
        Toast.makeText(getApplicationContext(), "Offload computation started", Toast.LENGTH_SHORT).show();
        connectionStatus.setText("Slave");
        long start = System.currentTimeMillis();
        JSONObject jsonObject = new JSONObject(jsonString);
        String mac = jsonObject.getString("mac");
        addressMap.add(mac);
        //disconnectPeers(addressMap.size()-1);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String[] iValues = {"0", "1", "2", "3"};
        String[] jValues = {};
        if (jsonObject.get("index").equals("0")) { // Slave 0
            jValues = new String[]{"4", "5", "6", "7"};
        } else if (jsonObject.get("index").equals("1")) {
            jValues = new String[]{"6", "7"};
        } // Implemented only for 2 offloading slaves
        HashMap<String, String> dataMap = new HashMap<>();
        for (int i = 0; i < iValues.length; i++) {
            for (int j = 0; j < jValues.length; j++) {
                dataMap.put(iValues[i] + "," + (((int) jValues[j].charAt(0)) - 4 - 48), arrayRCMult(jsonObject.get(iValues[i]) + "", jsonObject.get(jValues[j]) + ""));
            }
        }
        int time = Integer.parseInt(jsonObject.get("time") + "");

        dataMap.put("offloadDone", "true");
        dataMap.put("mac", mac); //To remove from sendReceiveRegister
        long end = System.currentTimeMillis();
        long difference = end - start;
        if (difference < time) {
            try {
                Thread.sleep(time - (difference));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        connectIndex(0); // Connecting after this particular period of time
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        JSONObject jsonObj = new JSONObject(dataMap);
        String returnString = jsonObj.toString();
        String msg = returnString;
        Toast.makeText(getApplicationContext(), "Slave side computation completed", Toast.LENGTH_SHORT).show();
        sendReceive.write(msg.getBytes());
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }// Resend done. Write code to read this particular data

    }

    /**
     * This does the offloaded row by column multiplication.
     * We are sending only i row and j column, and the two arrays are multiplied and
     * summed up and returned as a String
     * */
    public String arrayRCMult(String iV, String jV) {
        int sum = 0;
        for (int i = 0; i < iV.length(); i++) {
            if (iV.charAt(i) >= '0' && iV.charAt(i) <= '9') {
                sum = sum + (((int) (iV.charAt(i)) - 48) * ((int) (jV.charAt(i)) - 48));
            }
        }
        return sum + "";
    }

    /**
     * This function does the recombination part on the Master side. The function is invoked when a slave responds with
     * the output of the offloaded computation. The given responses are assigned to the various indices in the Out[][] array
     */
    public void uponReceiving(String jsonString) throws JSONException {
        Toast.makeText(getApplicationContext(), "Tasks from slave devices received", Toast.LENGTH_SHORT).show();
        JSONObject jsonObject = new JSONObject(jsonString);
        boolean status = Boolean.parseBoolean(jsonObject.get("offloadDone") + "");
        String macAddress = jsonObject.getString("mac");
        jsonObject.remove("offloadDone");
        jsonObject.remove("mac");
        Iterator<String> keysToCopyIterator = jsonObject.keys();
        List<String> keysList = new ArrayList<String>();
        while (keysToCopyIterator.hasNext()) {
            String key = (String) keysToCopyIterator.next();
            keysList.add(key);
        }
        for (int i = 0; i < keysList.size(); i++) {
            int iVal = (int) keysList.get(i).toString().charAt(0) - 48;
            int jVal = (int) keysList.get(i).toString().charAt(2) - 48;
            Out[iVal][jVal] = Integer.parseInt(jsonObject.get(keysList.get(i)).toString());
            System.out.println("Out Value - " + Out[iVal][jVal]);
        }
        sendReceiveRegister.remove(macAddress);
        generateOutputs();
    }

    /**
     * Calculates the total time taken to do the computation and also the output matrix is formatted according to
     *     the editText alignment for the next page.
    * */
    public void generateOutputs() {
        String row1, row2, row3, row4, est1, est2;
        row1 = ar2String(Out[0]);
        row2 = ar2String(Out[1]);
        row3 = ar2String(Out[2]);
        row4 = ar2String(Out[3]);
        est1 = "Estimate Time using Offloading is: " + (System.currentTimeMillis() - globalStart) + "ms \n(each failure requires additional 250 ms, probability of failure is subject to battery level and distance)";
        long currentT = System.currentTimeMillis();
        computeMatrix();
        est2 = "Estimate Time without Offloading is: " + (System.currentTimeMillis() - currentT + 1) + "ms"; // WRITE ACTUAL MULT HERE
        navigateResultScreen(row1,row2,row3,row4,est1,est2);
    }

    /**
     * Code for regular matrix multiplication without using offloading on Master.
     * */
    public void computeMatrix() {
        int[][] outMat = new int[4][4];
        for(int i=0; i<A1.length; i++) {
            for(int j = 0; j<A2.length; j++) {
                outMat[i][j] = 0;
            }
        }
        for(int i=0; i<A1.length; i++) {
            for(int j = 0; j<A2.length; j++) {
                for(int k=0; k<A2.length; k++) {
                    outMat[i][j] = outMat[i][j] + A1[i][k]*A2[k][j];
                }
            }
        }
    }

    public String ar2String(int[] arr) {
        String out = "";
        for(int i = 0; i<arr.length; i++) {
            out = out + arr[i] + "\t\t";
        }
        return out;
    }

    public void initialConnect(String tempMsg) throws JSONException {
        read_msg_box.setText(tempMsg);
        JSONObject jsonObject = new JSONObject(tempMsg);
        //batteryLevels.put(addressMap.get(counter++%addressMap.size()), String.valueOf(jsonObject.get("battery"))); // Recording all battery levels according to the Mac Address
    }

    /**
     * This function performs periodic monitoring of all slaves connected to the master as a separate thread.
     * Every 5000 ms, a request is sent and a response is received. The response contains current battery levels,
     * GPS location. These details are updated in the sendReceiveRegister variable.
     * * */
    public void startPeriodicMonitoring() throws InterruptedException {
        Thread periodMonitoring = new Thread();
        periodMonitoring.start();
        while (sendReceiveRegister.size() > 0) {
            Thread.sleep(5000);
            Set<String> keysToCopyIterator = sendReceiveRegister.keySet();
            Object[] keyList = keysToCopyIterator.toArray();
            for(int i=0; i<keyList.length; i++) {
                connectIndex(addressMap.indexOf(keyList[i]));
                Thread.sleep(50);
                sendReceive.write("periodicMonitor".getBytes());
                Thread.sleep(50);
                HashMap<String, Object> deviceMap = (HashMap<String, Object>) sendReceiveRegister.get(String.valueOf(keyList[i]));
                if(Integer.parseInt(deviceMap.get("battery").toString()) < 20) {
                    failureRecovery(String.valueOf(keyList[i])); //Perform failure recovery
                }
            }
        }
    }

    /**
     *
     *     Invoked only by periodicMonitoring. If a device seems disconnected due to given threshold of battery level less than 0,
     *     or fails to respond to periodicMonitoring then failureRecovery is initiated. The data assigned to the failed Slave, is
     *     reassigned to the next avaialble slave device. The device may be idle after completing its job or it may be idle since the
     *     beginning. We have not implemented any algorithm to choose which slave to choose in case of failure recovery.
     *     * */
    public void failureRecovery(String macID) throws InterruptedException {
        Toast.makeText(getApplicationContext(), macID + " is at critical battery level!", Toast.LENGTH_SHORT).show();
        HashMap<String, Object> deviceMap = (HashMap<String, Object>) sendReceiveRegister.get(macID);
        Set keySet = sendReceiveRegister.keySet();
        for(int i=0; i<addressMap.size(); i++) {
            if(!keySet.contains(addressMap.get(i))) { // There is some idle Slave, has connected but not been assigned a job or has completed its job
                connectIndex(i);
                sendReceiveRegister.remove(macID);
                sendReceiveRegister.put(addressMap.get(i), deviceMap);
                HashMap<String, String> dataMap = generateMap(i);
                JSONObject jsonObject = new JSONObject(dataMap);
                String jsonString = jsonObject.toString();
                sendReceive.write(jsonString.getBytes());
                Thread.sleep(50);
            }
        }
    }

    /**
     * This function is run on the slave devices. The function responds to the request given by the periodicMonitoring
     *     function call from the Master.
     * */
    public void sendPeriodicResponse() {
        getLocation();
        JSONObject jsonObj = new JSONObject(slaveInformationMap);
        String msg = jsonObj.toString();
        sendReceive.write(msg.getBytes());
    }

    public int[][] convertStringToArray(String input) {
        input = input.trim().replaceAll("\\ ", "");
        input = input+",";
        int[][] outArr = new int[4][4];
        int loopI = 0, loopJ = 0;
        input = input.replaceAll("\\{","");
        input = input.substring(0,input.length()-2) + ",";
        String[] items = input.split("\\},");
        for(int i=0; i<items.length; i++) {
            String item = items[i] + ",";
            String[] itemRow = item.split(",");
            for(int j=0; j<itemRow.length; j++) {
                outArr[i][j] = Integer.parseInt(itemRow[j].trim() + "");
            }
        }
        return outArr;
    }

    /**
     * This function navigates to the next screen in the app. It send the output of the output matrix computed using
     *     the offloading process.
    */
    public void navigateResultScreen(String row1, String row2, String row3, String row4, String estimation1, String estimation2 ){
        Intent intent = new Intent(MainActivity.this, ComputeActivity.class);
        intent.putExtra("row1", row1);
        intent.putExtra("row2", row2);
        intent.putExtra("row3", row3);
        intent.putExtra("row4", row4);
        intent.putExtra("estimation1", estimation1);
        intent.putExtra("estimation2", estimation2);
        startActivity(intent);
    }
}