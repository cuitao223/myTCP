package com.ouc.tcp.test;

import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_PACKET;

/**
 * 阶段 1：RDT2.0 校验和（检错）
 * CRC32(seq, ack, sum=0, payload) -> 低 16 位。
 */
public class CheckSum {

    public static short computeChkSum(TCP_PACKET tcpPack) {
        CRC32 crc = new CRC32();

        int originSum = tcpPack.getTcpH().getTh_sum();
        tcpPack.getTcpH().setTh_sum((short) 0);

        updateCRC(crc, tcpPack.getTcpH().getTh_seq());
        updateCRC(crc, tcpPack.getTcpH().getTh_ack());
        updateCRC(crc, tcpPack.getTcpH().getTh_sum());

        int[] data = tcpPack.getTcpS().getData();
        if (data != null) {
            for (int value : data) {
                updateCRC(crc, value);
            }
        }

        tcpPack.getTcpH().setTh_sum((short) originSum);
        return (short) (crc.getValue() & 0xFFFF);
    }

    private static void updateCRC(CRC32 crc, int value) {
        crc.update((value >>> 24) & 0xFF);
        crc.update((value >>> 16) & 0xFF);
        crc.update((value >>> 8) & 0xFF);
        crc.update(value & 0xFF);
    }
}

