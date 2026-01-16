package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.TCP_PACKET;

/**
 * 阶段 2：RDT2.2（停等 + dup-ack + 超时重传）
 * - 接收端不发 NACK，出错/乱序时重复上一次 ACK
 * - 发送端只接受“当前分组对应的 ACK”；若收到重复 ACK（上一次 ACK），立即重传当前分组
 *
 * RDT2.2 的经典假设是不发生丢包，因此不需要超时定时器；
 * 本实现按该假设移除了超时重传，仅依赖 dup-ack 触发重传。
 */
public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET lastSent;
    private boolean waitingAck = false;

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
            return; // ACK 损坏：忽略，继续等待（RDT2.2 假设不丢包）
        }

        int ackNum = recvPack.getTcpH().getTh_ack();
        if (!waitingAck || lastSent == null) return;

        if (ackNum == lastSent.getTcpH().getTh_seq()) {
            waitingAck = false;
            notifyAll();
        } else {
            // 收到重复 ACK（对上一个按序分组的确认）：认为当前分组出错，立刻重传
            udt_send(lastSent);
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
}
