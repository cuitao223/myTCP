package com.ouc.tcp.test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.TCP_PACKET;

/** 阶段 5：TCP Tahoe（累计 ACK + 单定时器 + 慢开始/拥塞避免 + 超时/3dupACK）。 */
public class TCP_Sender extends TCP_Sender_ADT {
    private static final long TIMEOUT = 3000L;
    private static final int MSS = 100;
    private final Map<Integer, TCP_PACKET> win = new LinkedHashMap<Integer, TCP_PACKET>();
    private Timer timer;

    private double cwnd = 1.0;
    private double ssthresh = 8.0;
    private int lastAck = -1;
    private int dupAck = 0;

    public TCP_Sender() { super(); initTCP_Sender(this); }

    @Override
    public void rdt_send(int dataIndex, int[] appData) {
        int seq = dataIndex * appData.length + 1;
        TCP_PACKET p = build(seq, appData);
        synchronized (this) {
            while (win.size() >= allowedWindow()) waitACK();
            win.put(seq, p);
            udt_send(p);
            if (win.size() == 1) startTimer();
        }
    }

    private int allowedWindow() { return Math.max(1, (int) Math.floor(cwnd)); }

    @Override
    public void udt_send(TCP_PACKET packet) { packet.getTcpH().setTh_eflag((byte) 7); client.send(packet); }

    @Override
    public void recv(TCP_PACKET recvPack) {
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) return;
        int ackNum = recvPack.getTcpH().getTh_ack();
        if (ackNum <= 0) return;
        synchronized (this) {
            if (ackNum == lastAck) {
                dupAck++;
                if (dupAck == 3) on3DupAck();
            } else {
                dupAck = 0;
            }
            lastAck = ackNum;

            int newly = 0;
            Iterator<Map.Entry<Integer, TCP_PACKET>> it = win.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, TCP_PACKET> e = it.next();
                if (e.getKey() <= ackNum) { it.remove(); newly++; }
                else break;
            }
            if (newly > 0) {
                for (int i = 0; i < newly; i++) onNewAck();
                if (win.isEmpty()) stopTimer(); else startTimer();
                notifyAll();
            }
        }
    }

    private void onNewAck() {
        if (cwnd < ssthresh) cwnd += 1.0;
        else cwnd += 1.0 / cwnd;
    }

    private void on3DupAck() {
        if (win.isEmpty()) return;
        int base = win.keySet().iterator().next();
        TCP_PACKET p = win.get(base);
        if (p != null) udt_send(p);
        ssthresh = Math.max(1.0, cwnd / 2.0);
        cwnd = 1.0; // Tahoe
    }

    @Override
    public void waitACK() { try { wait(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    private TCP_PACKET build(int seq, int[] data) {
        tcpH.setTh_ack(0); tcpH.setTh_seq(seq); tcpS.setData(data);
        TCP_PACKET p = new TCP_PACKET(tcpH, tcpS, destinAddr);
        tcpH.setTh_sum((short) 0); tcpH.setTh_sum(CheckSum.computeChkSum(p));
        p.setTcpH(tcpH); p.setTcpS(tcpS);
        return p;
    }

    private void startTimer() {
        stopTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override public void run() {
                synchronized (TCP_Sender.this) {
                    if (win.isEmpty()) { stopTimer(); return; }
                    // Tahoe：超时后重传窗口内所有未确认
                    ssthresh = Math.max(1.0, cwnd / 2.0);
                    cwnd = 1.0;
                    for (TCP_PACKET p : win.values()) udt_send(p);
                    startTimer();
                }
            }
        }, TIMEOUT);
    }

    private void stopTimer() { if (timer != null) { timer.cancel(); timer.purge(); timer = null; } }
}

