/*
 * GNU LESSER GENERAL PUBLIC LICENSE
 *                       Version 3, 29 June 2007
 *
 * Copyright (C) 2007 Free Software Foundation, Inc. <http://fsf.org/>
 * Everyone is permitted to copy and distribute verbatim copies
 * of this license document, but changing it is not allowed.
 *
 * You can view LICENCE file for details. 
 *
 * @author The Dragonet Team
 */
package org.dragonet.net.packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.Getter;
import org.dragonet.net.DragonetSession;
import org.dragonet.net.packet.minecraft.PEPacket;
import org.dragonet.utilities.io.ArraySplitter;
import org.dragonet.utilities.io.PEBinaryReader;
import org.dragonet.utilities.io.PEBinaryWriter;

public class EncapsulatedPacket extends BinaryPacket {
    public int reliability;
    public boolean hasSplit = false;

    public int messageIndex;
    public int orderIndex;
    public int orderChannel;

    public int splitCount;
    public int splitID;
    public int splitIndex;

    public byte[] buffer;

    private EncapsulatedPacket() {
    }

    @Override
    public void encode() {
    }

    @Override
    public void decode() {
    }

    /**
     * Decode a bytes array into EncapsulatedPacket
     *
     * @param reader The packet reading context
     * @return The EncapsulatedPacket read
     */
    public static EncapsulatedPacket fromBinary(PEBinaryReader reader) {
        try {
            EncapsulatedPacket packet = new EncapsulatedPacket();

            int flags = reader.readByte();
            packet.reliability = (flags & 0b11100000) >> 5;
            packet.hasSplit = (flags & 0b00010000 >> 4) > 0;

            int length = (reader.readShort() & 0xFFFF) / 8;
            if (packet.reliability == 2 || packet.reliability == 3 || packet.reliability == 4 || packet.reliability == 6 || packet.reliability == 7) {
                packet.messageIndex = reader.readTriad();
            }

            if (packet.reliability == 1 || packet.reliability == 3 || packet.reliability == 4 || packet.reliability == 7) {
                packet.orderIndex = reader.readTriad();
                packet.orderChannel = reader.readByte() & 0xFF;
            }

            if (packet.hasSplit) {
                packet.splitCount = reader.readInt();
                packet.splitID = reader.readShort();
                packet.splitIndex = reader.readInt();
            }
            packet.buffer = reader.read(length);

            return packet;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Convert an Encapsulated packet into bytes array
     *
     * @param packet The packet you want to encode
     * @return Encoded bytes array
     */
    public static byte[] toBinary(EncapsulatedPacket packet) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PEBinaryWriter writer = new PEBinaryWriter(bos);
            writer.writeByte((byte) ((packet.reliability << 5) ^ (packet.hasSplit ? 0b0001 : 0x00)));
            writer.writeShort((short) ((packet.buffer.length << 3) & 0xFFFF));
            if (packet.reliability == 2 || packet.reliability == 3 || packet.reliability == 4 || packet.reliability == 6 || packet.reliability == 7) {
                writer.writeTriad(packet.messageIndex);
            }
            if (packet.reliability == 1 || packet.reliability == 3 || packet.reliability == 4 || packet.reliability == 7) {
                writer.writeTriad(packet.orderIndex);
                writer.writeByte((byte) (packet.orderChannel & 0xFF));
            }
            if (packet.hasSplit) {
                writer.writeInt(packet.splitCount);
                writer.writeShort((short) (packet.splitID & 0xFFFF));
                writer.writeInt(packet.splitIndex);
            }
            writer.write(packet.buffer);
            return bos.toByteArray();
        } catch (IOException e) {
        }
        return new byte[0];
    }

    /**
     * Automatically wrap PEPacket into a EncapsulatedPacket
     *
     * @param packet The PEPacket you want to encapsulate.
     * @return Wrapped EncapsulatedPacket
     */
    public static EncapsulatedPacket[] fromPEPacket(DragonetSession session, PEPacket packet) {
        packet.encode();
        byte[] data = packet.getData();
        if (data.length + 24 < session.getClientMTU()) {
            //Fit in one packet
            EncapsulatedPacket singlePacket = new EncapsulatedPacket();
            singlePacket.reliability = 2;
            singlePacket.hasSplit = false;
            singlePacket.messageIndex = session.getMessageIndex();
            singlePacket.buffer = data;
            session.setMessageIndex(session.getMessageIndex() + 1);
            return new EncapsulatedPacket[]{singlePacket};
        } else {
            //Not fit in one packet, need to be splitted
            byte[][] multipleData = ArraySplitter.splitArray(data, session.getClientMTU() - 24);
            EncapsulatedPacket[] encapsulatedPackets = new EncapsulatedPacket[multipleData.length];
            
            int currentSplitID = session.getSplitID();
            int currentSplitCount = multipleData.length;
            session.setSplitID((session.getSplitID() + 1) % 65535);
            
            int slice = 0;
            for (byte[] sliceData : multipleData) {
                encapsulatedPackets[slice] = new EncapsulatedPacket();
                encapsulatedPackets[slice].reliability = 2;
                encapsulatedPackets[slice].hasSplit = true;
                encapsulatedPackets[slice].splitID = currentSplitID;
                encapsulatedPackets[slice].splitCount = currentSplitCount;
                encapsulatedPackets[slice].splitIndex = slice;
                encapsulatedPackets[slice].messageIndex = session.getMessageIndex();
                encapsulatedPackets[slice].buffer = sliceData;
                session.setMessageIndex(session.getMessageIndex() + 1);
                slice++;
            }
            return encapsulatedPackets;
        }
    }
}
