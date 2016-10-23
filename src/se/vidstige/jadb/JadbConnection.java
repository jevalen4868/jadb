package se.vidstige.jadb;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class JadbConnection implements ITransportFactory {

    private final String host;
    private final int port;

    private static final int DEFAULTPORT = 5037;

    public JadbConnection() throws IOException {
        this("localhost", DEFAULTPORT);
    }

    public JadbConnection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
    }

    public Transport createTransport() throws IOException {
        return new Transport(new Socket(host, port));
    }

    public void getHostVersion() throws IOException, JadbException {
        Transport main = createTransport();
        main.send("host:version");
        main.verifyResponse();
        main.close();
    }

    public List<JadbDevice> getDevices() throws IOException, JadbException {
        Transport devices = createTransport();

        devices.send("host:devices");
        devices.verifyResponse();
        String body = devices.readString();
        devices.close();
        return parseDevices(body);
    }

    public DeviceDetectionHandler getDevices(final DeviceDetectionListener listener) throws IOException, JadbException {
        final Transport devices = createTransport();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try
                {
                    devices.send("host:track-devices");
                    devices.verifyResponse();
                    boolean r = false;
                    do {
                        List<JadbDevice> list = parseDevices(devices.readString());
                        r = listener.detect(list);
                    } while(r);
                } catch(SocketException e) {
                    // socket closed from another thread
                } catch(Exception e) {
                    Thread t = Thread.currentThread();
                    t.getUncaughtExceptionHandler().uncaughtException(t, e);
                }
            }
        }).start();
        return new DeviceDetectionHandler(devices);
    }

    private List<JadbDevice> parseDevices(String body) {
        String[] lines = body.split("\n");
        ArrayList<JadbDevice> devices = new ArrayList<JadbDevice>(lines.length);
        for (String line : lines) {
            String[] parts = line.split("\t");
            if (parts.length > 1) {
                devices.add(new JadbDevice(parts[0], parts[1], this));
            }
        }
        return devices;
    }

    public JadbDevice getAnyDevice() {
        return JadbDevice.createAny(this);
    }
}
