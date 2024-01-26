package com.github.shadowsocks.net;

import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.parseNumericAddress;
import com.github.shadowsocks.utils.printLog;
import com.github.shadowsocks.utils.shutdown;
import kotlinx.coroutines.*;
import net.sourceforge.jsocks.Socks5DatagramSocket;
import net.sourceforge.jsocks.Socks5Proxy;
import org.xbill.DNS.*;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LocalDnsServer extends SocketListener implements CoroutineScope {

    private boolean forwardOnly = false;
    private boolean tcp = true;
    private Regex remoteDomainMatcher = null;
    private List<Subnet> localIpMatcher = Collections.emptyList();

    private static final long TIMEOUT = 10_000L;
    private static final long TTL = 120L;
    private static final int UDP_PACKET_SIZE = 1500;

    private DatagramSocket socket = new DatagramSocket(DataStore.portLocalDns, DataStore.listenAddress.parseNumericAddress());
    private FileDescriptor getFileDescriptor() {
        return ParcelFileDescriptor.fromDatagramSocket(socket).getFileDescriptor();
    }
    @Override
    public FileDescriptor getFileDescriptor() {
        return getFileDescriptor();
    }
    private Socks5Proxy tcpProxy = DataStore.proxy;
    private Socks5Proxy udpProxy = new Socks5Proxy("127.0.0.1", DataStore.portProxy);

    private Set<FileDescriptor> activeFds = Collections.newSetFromMap(new ConcurrentHashMap<FileDescriptor, Boolean>());
    private SupervisorJob job = new SupervisorJob();
    @Override
    public CoroutineContext getCoroutineContext() {
        return Dispatchers.Default + job + CoroutineExceptionHandler { _, t -> printLog(t); };
    }

    public LocalDnsServer(suspend (String) -> Array<InetAddress> localResolver, InetAddress remoteDns) {
        super("LocalDnsServer");
        this.localResolver = localResolver;
        this.remoteDns = remoteDns;
    }

    @Override
    public void run() {
        while (running) {
            DatagramPacket packet = new DatagramPacket(new byte[UDP_PACKET_SIZE], 0, UDP_PACKET_SIZE);
            try {
                socket.receive(packet);
                launch(start = CoroutineStart.UNDISPATCHED) {
                    resolve(packet);
                    socket.send(packet);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    private suspend <T> io(suspend CoroutineScope.() -> T block) {
        return withTimeout(TIMEOUT) { withContext(Dispatchers.IO, block); }
    }

    private suspend void resolve(DatagramPacket packet) {
        if (forwardOnly) return forward(packet);
        Message request;
        try {
            request = new Message(ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength()));
        } catch (IOException e) {
            printLog(e);
            return forward(packet);
        }
        if (request.getHeader().getOpcode() != Opcode.QUERY || request.getHeader().getRcode() != Rcode.NOERROR) return forward(packet);
        Record question = request.getQuestion();
        if (question.getType() != Type.A) return forward(packet);
        String host = question.getName().toString(true);
        if (remoteDomainMatcher != null && remoteDomainMatcher.containsMatchIn(host)) return forward(packet);
        Array<InetAddress> localResults;
        try {
            localResults = io { localResolver(host); }
        } catch (TimeoutCancellationException | UnknownHostException e) {
            return forward(packet);
        }
        if (localResults.isEmpty()) return forward(packet);
        if (localIpMatcher.isEmpty() || localIpMatcher.stream().anyMatch(subnet -> Arrays.stream(localResults).anyMatch(subnet::matches))) {
            Log.d("DNS", host + " (local) -> " + Arrays.toString(localResults));
            Message response = new Message(request.getHeader().getID());
            response.getHeader().setFlag(Flags.QR.toInt());
            if (request.getHeader().getFlag(Flags.RD.toInt())) response.getHeader().setFlag(Flags.RD.toInt());
            response.getHeader().setFlag(Flags.RA.toInt());
            response.addRecord(request.getQuestion(), Section.QUESTION);
            for (InetAddress address : localResults) {
                if (address instanceof Inet4Address) {
                    response.addRecord(new ARecord(request.getQuestion().getName(), DClass.IN, TTL, address));
                } else if (address instanceof Inet6Address) {
                    response.addRecord(new AAAARecord(request.getQuestion().getName(), DClass.IN, TTL, address));
                } else {
                    throw new IllegalStateException("Unsupported address " + address);
                }
            }
            byte[] wire = response.toWire();
            packet.setData(wire, 0, wire.length);
            return;
        }
        return forward(packet);
    }

    private suspend void forward(DatagramPacket packet) {
        if (tcp) {
            Socket socket = new Socket(tcpProxy);
            try {
                socket.connect(new InetSocketAddress(remoteDns, 53), 53);
                socket.getOutputStream().write(packet.getData(), packet.getOffset(), packet.getLength());
                socket.getOutputStream().flush();
                int read = socket.getInputStream().read(packet.getData(), 0, UDP_PACKET_SIZE);
                packet.setLength(read < 0 ? 0 : read);
            } finally {
                socket.close();
            }
        } else {
            Socks5DatagramSocket socket = new Socks5DatagramSocket(udpProxy, 0, null);
            try {
                InetAddress address = packet.getAddress();
                packet.setAddress(remoteDns);
                packet.setPort(53);
                packet.toString();
                Log.d("DNS", "Sending " + packet);
                socket.send(packet);
                Log.d("DNS", "Receiving " + packet);
                socket.receive(packet);
                Log.d("DNS", "Finished " + packet);
                packet.setAddress(address);
            } finally {
                socket.close();
            }
        }
    }

    private suspend <T extends Closeable> void useFd(T t, Consumer<T> block) {
        FileDescriptor fd;
        if (t instanceof Socket) {
            fd = ParcelFileDescriptor.fromSocket((Socket) t).getFileDescriptor();
        } else if (t instanceof DatagramSocket) {
            fd = getFileDescriptor();
        } else {
            throw new IllegalStateException("Unsupported type " + t.getClass() + " for obtaining FileDescriptor");
        }
        try {
            activeFds.add(fd);
            io { t.use(block); }
        } finally {
            fd.shutdown();
            activeFds.remove(fd);
        }
    }

    public suspend void shutdown() {
        running = false;
        job.cancel();
        close();
        activeFds.forEach(FileDescriptor::shutdown);
        job.join();
    }
}