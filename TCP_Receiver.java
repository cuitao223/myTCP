package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.TCP_PACKET;
import com.ouc.tcp.message.TCP_SEGMENT;

/** 阶段 4：SR 接收端（窗口内乱序缓存 + 逐包 ACK）。 */
public class TCP_Receiver extends TCP_Receiver_ADT {
    private static final int MSS = 100;
    private static final int WIN = 5;
    private int expectedSeq = 1;
    private final Map<Integer, int[]> buf = new HashMap<Integer, int[]>();
    private TCP_PACKET ackPack;

    public TCP_Receiver() { super(); initTCP_Receiver(this); }

    @Override
    public synchronized void rdt_recv(TCP_PACKET recvPack) {
        int seq = recvPack.getTcpH().getTh_seq();
        boolean valid = (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum());
        if (!valid) return;
        int windowEnd = expectedSeq + (WIN - 1) * MSS;
        if (seq < expectedSeq) { sendAck(seq, recvPack); return; }
        if (seq > windowEnd) return;
        int[] data = recvPack.getTcpS().getData();
        if (seq == expectedSeq) {
            dataQueue.add(data);
            expectedSeq += (data == null ? MSS : data.length);
            while (buf.containsKey(expectedSeq)) {
                int[] d = buf.remove(expectedSeq);
                dataQueue.add(d);
                expectedSeq += (d == null ? MSS : d.length);
            }
            deliver_data();
        } else {
            buf.putIfAbsent(seq, data);
        }
        sendAck(seq, recvPack);
    }

    @Override
    public void deliver_data() {
        File fw = new File("recvData.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fw, true))) {
            while (!dataQueue.isEmpty()) {
                int[] data = dataQueue.poll();
                if (data == null) continue;
                for (int v : data) writer.write(v + "\n");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void sendAck(int ackNum, TCP_PACKET recvPack) {
        tcpH.setTh_ack(ackNum);
        tcpH.setTh_seq(ackNum <= 0 ? expectedSeq : ackNum);
        TCP_SEGMENT seg = new TCP_SEGMENT();
        tcpS = seg;
        ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
        tcpH.setTh_sum((short) 0);
        tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
        reply(ackPack);
    }

    @Override
    public void reply(TCP_PACKET replyPack) { replyPack.getTcpH().setTh_eflag((byte) 7); client.send(replyPack); }
}

