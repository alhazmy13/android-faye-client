package net.alhazmy13.myapplication;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.elirex.fayeclient.FayeClient;
import com.elirex.fayeclient.FayeClientListener;
import com.elirex.fayeclient.MetaMessage;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Initial Meta Message
        MetaMessage meta = new MetaMessage();
        // Set handshake's ext and id
        JSONObject jsonExt = new JSONObject();
        JSONObject jsonId = new JSONObject();
        try {
            jsonExt.put("key", 123456);
            jsonId.put("user", "abc");
        } catch(Exception e) {

        }
        meta.setAllExt(jsonExt.toString());
        //meta.setHandshakeExt(jsonExt.toString());
        // Initinal FayeClient

        final FayeClient mClient = new FayeClient("wws://homaily.com:8001/faye", meta);

        // Set FayeClient listener
        mClient.setListener(new FayeClientListener() {
            @Override
            public void onConnectedServer(FayeClient fc) {
                Log.i(LOG_TAG, "Connected");
                fc.subscribeChannel("/messages");


                if(mClient.isConnectedServer()) {
                    // Normal publish

                    // Include ext and id
                    JSONObject jsonExt = new JSONObject();
                    JSONObject jsonId = new JSONObject();
                    try {
                        jsonExt.put("token", 123456);
                        jsonExt.put("user_id",123);
                    } catch(Exception e) {

                    }

                    mClient.publish("/messages", "The message include ext and id", jsonExt.toString(), jsonId.toString());
                }
            }

            @Override
            public void onDisconnectedServer(FayeClient fc) {
                Log.i(LOG_TAG, "Disconnected");
            }

            @Override
            public void onReceivedMessage(FayeClient fc, String msg) {
                Log.i(LOG_TAG, "Message: " + msg);
            }
        });

// Connect to server
        mClient.connectServer();
        ;
    }
}
