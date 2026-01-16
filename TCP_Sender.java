package com.ouc.tcp.test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.TCP_PACKET;

/** 阶段 4：TCP（可靠传输，无拥塞控制）：累计 ACK + 单定时器 + 超时重传窗口内未确认。 */
public class TCP_Sender extends TCP_Sender_ADT {
    private static final long TIMEOUT = 3000L;
    private static final int WIN = 5;
    private static final int MSS = 100;
    private final Map<Integer, TCP_PACKET> win = new LinkedHashMap<Integer, TCP_PACKET>();
    private Timer timer;

    public TCP_Sender() { super(); initTCP_Sender(this); }

    @Override
    public void rdt_send(int dataIndex, int[] appData) {
        int seq = dataIndex * appData.length + 1;
        TCP_PACKET p = build(seq, appData);
        synchronized (this) {
            while (win.size() >= WIN) waitACK();
            win.put(seq, p);
            udt_send(p);
            if (win.size() == 1) startTimer();
        }
    }

    @Override
    public void udt_send(TCP_PACKET packet) { packet.getTcpH().setTh_eflag((byte) 7); client.send(packet); }

    @Override
    public void recv(TCP_PACKET recvPack) {
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) return;
        int ackNum = recvPack.getTcpH().getTh_ack();
        if (ackNum <= 0) return;
        synchronized (this) {
            Iterator<Map.Entry<Integer, TCP_PACKET>> it = win.entrySet().iterator();
            boolean progressed = false;
            while (it.hasNext()) {
                Map.Entry<Integer, TCP_PACKET> e = it.next();
                if (e.getKey() <= ackNum) { it.remove(); progressed = true; }
                else break;
            }
            if (progressed) {
                if (win.isEmpty()) stopTimer(); else startTimer();
                notifyAll();
            }
        }
    }

    @Override
    public void waitACK() { try { wait(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

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

    private void startTimer() {
        stopTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override public void run() {
                synchronized (TCP_Sender.this) {
                    for (TCP_PACKET p : win.values()) udt_send(p);
                    if (!win.isEmpty()) startTimer();
                }
            }
        }, TIMEOUT);
    }

    private void stopTimer() { if (timer != null) { timer.cancel(); timer.purge(); timer = null; } }
}

