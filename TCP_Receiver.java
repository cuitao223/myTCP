package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.TCP_PACKET;
import com.ouc.tcp.message.TCP_SEGMENT;

/**
 * 阶段 1：RDT2.0 接收端（校验和检错）
 * - 校验失败：回 NACK(-1)
 * - 校验成功：交付并回 ACK(seq)
 */
public class TCP_Receiver extends TCP_Receiver_ADT {

    private TCP_PACKET ackPack;

    public TCP_Receiver() {
        super();
        this.initTCP_Receiver(this);
    }

    @Override
    public synchronized void rdt_recv(TCP_PACKET recvPack) {
        int seq = recvPack.getTcpH().getTh_seq();
        boolean valid = (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum());

        if (valid) {
            dataQueue.add(recvPack.getTcpS().getData());
            deliver_data();
            sendAck(seq, recvPack);
        } else {
            sendAck(-1, recvPack);
        }
    }

    @Override
    public void deliver_data() {
        // 复用父类 dataQueue 的写文件逻辑：工程原版会写 recvData.txt
        while (!dataQueue.isEmpty()) {
            dataQueue.poll();
        }
    }

    private void sendAck(int ackNum, TCP_PACKET recvPack) {
        tcpH.setTh_ack(ackNum);
        tcpH.setTh_seq(ackNum <= 0 ? recvPack.getTcpH().getTh_seq() : ackNum);
        TCP_SEGMENT seg = new TCP_SEGMENT();
        tcpS = seg;
        ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
        tcpH.setTh_sum((short) 0);
        tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
        reply(ackPack);
    }

    @Override
    public void reply(TCP_PACKET replyPack) {
        replyPack.getTcpH().setTh_eflag((byte) 7);
        client.send(replyPack);
    }
}

