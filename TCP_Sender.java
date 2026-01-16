package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.TCP_PACKET;

/**
 * 阶段 1：RDT2.0 发送端（仅检错 + 停等）
 *
 * - 只发送一个分组并等待 ACK/NACK
 * - 本阶段不考虑丢包/延迟（主要验证 CheckSum）
 */
public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET lastSent;

    public TCP_Sender() {
        super();
        this.initTCP_Sender(this);
    }

    @Override
    public synchronized void rdt_send(int dataIndex, int[] appData) {
        // 生成并发送数据包（使用父类初始化的 tcpH/tcpS）
        tcpH.setTh_ack(0);
        tcpH.setTh_seq(dataIndex * appData.length + 1);
        tcpS.setData(appData);
        TCP_PACKET packet = new TCP_PACKET(tcpH, tcpS, destinAddr);
        tcpH.setTh_sum((short) 0);
        tcpH.setTh_sum(CheckSum.computeChkSum(packet));
        packet.setTcpH(tcpH);
        packet.setTcpS(tcpS);

        lastSent = packet;
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
        // ACK 包校验失败：当作 NACK，直接重发
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
            if (lastSent != null) {
                udt_send(lastSent);
            }
            return;
        }

        int ackNum = recvPack.getTcpH().getTh_ack();
        if (ackNum <= 0) { // NACK
            if (lastSent != null) {
                udt_send(lastSent);
            }
        } else {
            // ACK：结束等待
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
}

