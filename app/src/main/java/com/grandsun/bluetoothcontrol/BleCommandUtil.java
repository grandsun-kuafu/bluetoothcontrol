package com.grandsun.bluetoothcontrol;

public class BleCommandUtil {
    private static final int OFFSET_FLAGS = 2;
    private static final int OFFSET_PAYLOAD = 8;
    private static final int FLAG_CHECK_MASK = 0x01;
    private static final int OFFSET_VENDOR_ID = 4;
    private static final int LENGTH_VENDOR_ID = 2;
    private static final int OFFSET_COMMAND_ID = 6;
    private static final int LENGTH_COMMAND_ID = 2;
    private static final int VENDOR_ID = 0x000B;
    private static final byte SOF = (byte) 0xFF;
    private static final int OFFSET_SOF = 0;
    private static final int OFFSET_VERSION = 1;
    private static final int PROTOCOL_VERSION = 1;
    private static final int OFFSET_LENGTH = 3;

    private static final int BYTES_IN_INT = 4;
    private static final int BITS_IN_BYTE = 8;



    /**
     * 生成请求指令
     */
//    public static byte[] generateSendCommand(int commandId, byte[] payload) {
//        int length = payload.length + OFFSET_PAYLOAD;
//        byte[] bytes = new byte[length];
//
//        bytes[OFFSET_SOF] = SOF;
//        bytes[OFFSET_VERSION] = PROTOCOL_VERSION;
//        bytes[OFFSET_FLAGS] = 0x00;
//        bytes[OFFSET_LENGTH] = (byte) payload.length;
//        copyIntIntoByteArray(VENDOR_ID, bytes, OFFSET_VENDOR_ID, LENGTH_VENDOR_ID, false);
//        copyIntIntoByteArray(commandId, bytes, OFFSET_COMMAND_ID, LENGTH_COMMAND_ID, false);
//        System.arraycopy(payload, 0, bytes, OFFSET_PAYLOAD, payload.length);
//        return bytes;
//    }

    /**
     * 解析返回指令
     */
    public static BleCommand parseReceiveCommand(String uuid, byte[] source) {
//        int flags = source[OFFSET_FLAGS];
//        int payloadLength = source.length - OFFSET_PAYLOAD;
//        if ((flags & FLAG_CHECK_MASK) != 0) {
//            --payloadLength;
//        }
//        int vendorId = extractIntFromByteArray(source, OFFSET_VENDOR_ID, LENGTH_VENDOR_ID, false);
//        int commandId = extractIntFromByteArray(source, OFFSET_COMMAND_ID, LENGTH_COMMAND_ID, false);
//        byte[] payload = null;
//        if (payloadLength > 0) {
//            payload = new byte[payloadLength];
//            System.arraycopy(source, OFFSET_PAYLOAD, payload, 0, payloadLength);
//        }

        return new BleCommand(CommandUUID.getByCharactorUUID(uuid),source);
    }

    private static void copyIntIntoByteArray(int sourceValue, byte[] target, int targetOffset, int length, boolean reverse) {
        if (length < 0 | length > BYTES_IN_INT) {
            throw new IndexOutOfBoundsException("Length must be between 0 and " + BYTES_IN_INT);
        } else if (target.length < targetOffset + length) {
            throw new IndexOutOfBoundsException("The targeted location must be contained in the target array.");
        }
        if (reverse) {
            int shift = 0;
            int j = 0;
            for (int i = length - 1; i >= 0; i--) {
                int mask = 0xFF << shift;
                target[j + targetOffset] = (byte) ((sourceValue & mask) >> shift);
                shift += BITS_IN_BYTE;
                j++;
            }
        } else {
            int shift = (length - 1) * BITS_IN_BYTE;
            for (int i = 0; i < length; i++) {
                int mask = 0xFF << shift;
                target[i + targetOffset] = (byte) ((sourceValue & mask) >> shift);
                shift -= BITS_IN_BYTE;
            }
        }
    }

    private static int extractIntFromByteArray(byte[] source, int offset, int length, boolean reverse) {
        if (length < 0 | length > BYTES_IN_INT)
            throw new IndexOutOfBoundsException("Length must be between 0 and " + BYTES_IN_INT);
        int result = 0;
        int shift = (length - 1) * BITS_IN_BYTE;
        if (reverse) {
            for (int i = offset + length - 1; i >= offset; i--) {
                result |= ((source[i] & 0xFF) << shift);
                shift -= BITS_IN_BYTE;
            }
        } else {
            for (int i = offset; i < offset + length; i++) {
                result |= ((source[i] & 0xFF) << shift);
                shift -= BITS_IN_BYTE;
            }
        }
        return result;
    }

}
