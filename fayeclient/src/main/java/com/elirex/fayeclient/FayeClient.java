package com.elirex.fayeclient;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.NotYetConnectedException;
import java.util.Date;
import java.util.HashSet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * @author Sheng-Yuan Wang (2015/9/3).
 */
public class FayeClient {

    private static final String LOG_TAG = FayeClient.class.getSimpleName();

    private final String HANDSHAKE_CHANNEL = "/meta/handshake";
    private final String CONNECT_CHANNEL = "/meta/connect";
    private final String DISCONNECT_CHANNEL = "/meta/disconnect";
    private final String SUBSCRIBE_CHANNEL = "/meta/subscribe";
    private final String UNSUBSCRIBE_CHANNEL = "/meta/unsubscribe";

    private final int MESSAGE_ONOPEN = 1;
    private final int MESSAGE_ONCLOSE = 2;
    private final int MESSAGE_ONMESSAGE = 3;

    private WebSocket webSocket = null;
    private FayeClientListener listener = null;
    private HashSet<String> channels;
    private String serverUrl = "";
    private String authToken = "";
    private String accessToken = "";
    private String clientId = "";
    private boolean fayeConnected = false;
    private boolean webSocketConnected = false;
    private MetaMessage mMetaMessage;
    private Handler messageHandler;

    public FayeClient(String url, MetaMessage meta) {
        serverUrl = url;
        mMetaMessage = meta;
        channels = new HashSet<String>();
    }

    public FayeClient(String url, String authToken, String accessToken) {
        this(url, authToken, accessToken, "");
    }

    public FayeClient(String url, String authToken, String accessToken,
                      String channel) {
        serverUrl = url;
        this.authToken = authToken;
        this.accessToken = accessToken;
        channels = new HashSet<String>();
        if(!channels.contains(channel)) {
            channels.add(channel);
        }
    }

    {
        HandlerThread thread = new HandlerThread("FayeHandler");
        thread.start();
        messageHandler = new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch(msg.what) {
                    case MESSAGE_ONOPEN:
                        Log.i(LOG_TAG, "onOpen() executed");
                        webSocketConnected = true;
                        handShake();
                        break;

                    case MESSAGE_ONCLOSE:
                        Log.i(LOG_TAG, "onClosed() executed");
                        webSocketConnected = false;
                        fayeConnected = false;
                        if(listener != null && listener instanceof FayeClientListener) {
                            listener.onDisconnectedFromServer(FayeClient.this);
                        }
                        break;
                    case MESSAGE_ONMESSAGE:
                        try {
                            Log.i(LOG_TAG, "onMessage executed");
                            parseFayeMessage((String) msg.obj);
                        } catch (NotYetConnectedException e) {
                            // Do noting
                        }
                        break;
                }
            }
        };
    }

    /* Public Methods */
    public FayeClientListener getListener() {
        return listener;
    }

    public void setListener(FayeClientListener listener) {
        this.listener = listener;
    }

    public void addChannel(String channel) {
        channels.add(channel);
    }

    public boolean isWebsocketConnected() {
        return webSocketConnected;
    }

    public boolean isFayeConnected() {
        return fayeConnected;
    }

    public void connectToServer() {
        openWebSocketConnection();
    }

    public void disconnectFromServer() {
        for(String channel : channels) {
            unsubscribe(channel);
        }
        channels.clear();
        disconnect();
    }

    public void subscribeToChannel(String channel) {
        channels.add(channel);
        subscribe(channel);
    }

    public void subscribeToChannels(String... channels) {
        for(String channel : channels) {
            this.channels.add(channel);
            subscribe(channel);
        }
    }

    public void unsubscribeFromChannel(String channel) {
        if(channels.contains(channel)) {
            unsubscribe(channel);
            channels.remove(channel);
        }
    }

    public void unsubscribeFromChannels(String... channels) {
        for(String channel : channels) {
            unsubscribe(channel);
        }
    }

    public void unsubscribeFromAllChannels() {
        for(String channel : channels) {
            unsubscribe(channel);
        }
    }

    public void publish(String channel, String data) {
        publish(channel, data, null, null);
    }

    public void publish(String channel, String data, String ext, String id) {
        try {
            String publish = mMetaMessage.publish(channel, data, ext, id);
            webSocket.send(publish);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Build publish message to JSON error", e);
        }
    }

    /* Private Methods */
    private Socket getSSLWebSocket() {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, null, null);
            SSLSocketFactory factory = sslContext.getSocketFactory();
            return factory.createSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void openWebSocketConnection() {
        // Clean up any existing socket
        if(webSocket != null) {
            webSocket.close();
        }
        try {
            URI uri = new URI(serverUrl);
            webSocket = new WebSocket(uri, messageHandler);
            Log.d(LOG_TAG, "Scheme:" + uri.getScheme());
            if(uri.getScheme().equals("wss")) {
                webSocket.setSocket(getSSLWebSocket());
            }
            webSocket.connect();
        } catch (URISyntaxException e) {
            Log.e(LOG_TAG, "Server URL error", e);
        }
    }

    private void closeWebSocketConnection() {
        if(webSocket != null) {
            webSocket.close();
        }
        listener = null;
    }

    private void handShake() {
        try {
            String handshake = mMetaMessage.handShake();
            webSocket.send(handshake);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "HandShake message error", e);
        }
    }

    private void subscribe(String channel) {
        try {
            String subscribe = mMetaMessage.subscribe(channel);
            webSocket.send(subscribe);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Subscribe message error", e);
        }
    }

    private void unsubscribe(String channel) {
        try {
            String unsubscribe = mMetaMessage.unsubscribe("/" + channel);
            webSocket.send(unsubscribe);
            Log.i(LOG_TAG, "UnSubscribe:" + channel);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Unsubscribe message error", e);
        }
    }

    private void connect() {
        try {
            String connect = mMetaMessage.connect();
            webSocket.send(connect);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Connect message error", e);
        }
    }

    private void disconnect() {
        try {
            String disconnect = mMetaMessage.disconnect();
            webSocket.send(disconnect);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Disconnect message error", e);
        }
    }

    private void parseFayeMessage(String message) {
        Log.d(LOG_TAG, "Parse Faye Message:" + message);
        JSONArray arr = null;
        JSONObject obj = null;
        try {
            arr = new JSONArray(message);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        int length = arr.length();
        for(int i = 0; i < length; ++i) {
            obj = arr.optJSONObject(i);

            if(obj.optString("channel").equals(HANDSHAKE_CHANNEL)) {
                if(obj.optBoolean("successful")) {
                    mMetaMessage.setClient(obj.optString("clientId"));
                    if(listener != null && listener instanceof FayeClientListener) {
                        Log.d(LOG_TAG, "callback");
                        listener.onConnectedToServer(this);
                    }
                    connect();
                } else {
                    Log.e(LOG_TAG, "onMessage(): Error with bayeux handshake");
                }
            } else if(obj.optString("channel").equals(CONNECT_CHANNEL)) {
                if(obj.optBoolean("successful")) {
                    fayeConnected = true;
                    connect();
                } else {
                    Log.e(LOG_TAG, "onMessage(): Error connecting to faye");
                }
            } else if(obj.optString("channel").equals(DISCONNECT_CHANNEL)) {
                if(obj.optBoolean("successful")) {
                    fayeConnected = false;
                    closeWebSocketConnection();
                    if(listener != null && listener instanceof FayeClientListener) {
                        listener.onDisconnectedFromServer(this);
                    }
                } else {
                    Log.e(LOG_TAG, "onMessage(): Error disconnecting from faye");
                }
            } else if(obj.optString("channel").equals(SUBSCRIBE_CHANNEL)) {
                if(obj.optBoolean("successful")) {
                    fayeConnected = true;
                    Log.i(LOG_TAG, String.format(
                            "Subscribed to channel %s on fay",
                            obj.optString("subscription")));
                } else {
                    Log.e(LOG_TAG, String.format(
                            "Error subscribing to channel %s on faye with error %s",
                            obj.optString("subscription"),
                            obj.optString("error")));
                }
            } else if(obj.optString("channel").equals(UNSUBSCRIBE_CHANNEL)) {
                Log.e(LOG_TAG, String.format("Unsubscribed from channel %s on faye",
                        obj.optString("subscription")));
            } else {
                if(channels.contains(obj.optString("channel"))) {
                    if(obj.optString("data") != null) {
                        if(listener != null && listener instanceof FayeClientListener) {
                            listener.onMessageReceived(this, obj.optString("data"));
                        }
                    }
                } else {
                    Log.e(LOG_TAG, String.format("No match for channel %s",
                            obj.optString("channel")));
                }
            }
        }
    }

}
