package com.grandsun.bluetoothcontrol;

public class  BleCommand {

    private CommandUUID commandUUID;
    private byte[] bytes;

    public BleCommand(CommandUUID commandUUID, byte[] bytes) {
        this.commandUUID = commandUUID;
        this.bytes = bytes;
    }

    public CommandUUID getCommandUUID() {
        return commandUUID;
    }

    public void setCommandUUID(CommandUUID commandUUID) {
        this.commandUUID = commandUUID;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}
