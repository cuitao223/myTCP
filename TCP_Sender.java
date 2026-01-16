package com.ouc.tcp.test;

import java.util.Timer;
import java.util.TimerTask;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.TCP_PACKET;

/**
 * 阶段 3：RDT3.0（停等 + 超时重传，应对丢包/ACK丢失）
 * - 接收端使用 dup-ack（重复 ACK），发送端只接受“确认当前分组”的 ACK
 * - 超时重传 lastSent
 */
public class TCP_Sender extends TCP_Sender_ADT {

    private static final long TIMEOUT = 3000L;
    private static final int MSS = 100;

    private TCP_PACKET lastSent;
    private boolean waitingAck = false;
    private Timer timer;

    public TCP_Sender() {
        super();
        this.initTCP_Sender(this);
    }

    @Override
    public synchronized void rdt_send(int dataIndex, int[] appData) {
        while (waitingAck) {
            waitACK();
        }

        tcpH.setTh_ack(0);
        tcpH.setTh_seq(dataIndex * appData.length + 1);
        tcpS.setData(appData);
        TCP_PACKET packet = new TCP_PACKET(tcpH, tcpS, destinAddr);
        tcpH.setTh_sum((short) 0);
        tcpH.setTh_sum(CheckSum.computeChkSum(packet));
        packet.setTcpH(tcpH);
        packet.setTcpS(tcpS);

        lastSent = packet;
        waitingAck = true;
        udt_send(packet);
        startTimer();
        waitACK();
    }

    @Override
    public void udt_send(TCP_PACKET packet) {
        packet.getTcpH().setTh_eflag((byte) 7);
        this.client.send(packet);
    }

    @Override
    public synchronized void recv(TCP_PACKET recvPack) {
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
            return;
        }
        int ackNum = recvPack.getTcpH().getTh_ack();
        if (!waitingAck || lastSent == null) return;
        if (ackNum == lastSent.getTcpH().getTh_seq()) {
            waitingAck = false;
            cancelTimer();
            notifyAll();
        }
    }

    @Override
    public synchronized void waitACK() {
        try {
            wait(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void startTimer() {
        cancelTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (TCP_Sender.this) {
                    if (waitingAck && lastSent != null) {
                        udt_send(lastSent);
                        startTimer();
                    }
                }
            }
        }, TIMEOUT);
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }
}

