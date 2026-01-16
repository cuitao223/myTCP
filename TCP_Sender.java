package com.ouc.tcp.test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.TCP_PACKET;

/** 阶段 4：SR 发送端（逐包 ACK + 每包计时器）。 */
public class TCP_Sender extends TCP_Sender_ADT {
    private static final long TIMEOUT = 3000L;
    private static final int WIN = 5;
    private static final int MSS = 100;

    private static class State {
        final TCP_PACKET p;
        boolean acked;
        Timer t;
        Runnable action;
        State(TCP_PACKET p) { this.p = p; }
        void start(Runnable a) {
            cancel();
            action = a;
            t = new Timer();
            t.schedule(new TimerTask(){@Override public void run(){action.run();}}, TIMEOUT, TIMEOUT);
        }
        void cancel(){ if(t!=null){t.cancel(); t.purge(); t=null;} }
    }

    private final Map<Integer, State> win = new LinkedHashMap<Integer, State>();

    public TCP_Sender() {
        super();
        initTCP_Sender(this);
    }

    @Override
    public void rdt_send(int dataIndex, int[] appData) {
        int seq = dataIndex * appData.length + 1;
        TCP_PACKET p = build(seq, appData);
        synchronized (this) {
            while (win.size() >= WIN) waitACK();
            State st = new State(p);
            win.put(seq, st);
            udt_send(p);
            st.start(() -> { synchronized (TCP_Sender.this) { if (win.containsKey(seq)) udt_send(p);} });
        }
    }

    @Override
    public void udt_send(TCP_PACKET packet) {
        packet.getTcpH().setTh_eflag((byte) 7);
        client.send(packet);
    }

    @Override
    public void recv(TCP_PACKET recvPack) {
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) return;
        int ackNum = recvPack.getTcpH().getTh_ack();
        if (ackNum <= 0) return;
        synchronized (this) {
            State st = win.get(ackNum);
            if (st != null) { st.acked = true; st.cancel(); }
            boolean progressed = false;
            Iterator<Map.Entry<Integer, State>> it = win.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, State> e = it.next();
                if (e.getValue().acked) { e.getValue().cancel(); it.remove(); progressed=true; }
                else break;
            }
            if (progressed) notifyAll();
        }
    }

    @Override
    public void waitACK() {
        try { wait(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private TCP_PACKET build(int seq, int[] data) {
        tcpH.setTh_ack(0);
        tcpH.setTh_seq(seq);
        tcpS.setData(data);
        TCP_PACKET p = new TCP_PACKET(tcpH, tcpS, destinAddr);
        tcpH.setTh_sum((short) 0);
        tcpH.setTh_sum(CheckSum.computeChkSum(p));
        p.setTcpH(tcpH); p.setTcpS(tcpS);
        return p;
    }
}

